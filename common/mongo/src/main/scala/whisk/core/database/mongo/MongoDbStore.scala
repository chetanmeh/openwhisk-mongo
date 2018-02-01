/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package whisk.core.database.mongo

import akka.actor.ActorSystem
import akka.event.Logging.ErrorLevel
import akka.http.scaladsl.model.ContentType
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.mongodb.DuplicateKeyException
import com.mongodb.client.model.ReturnDocument
import org.mongodb.scala.MongoClient
import org.mongodb.scala.MongoCollection
import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat
import spray.json.JsObject
import whisk.common.Logging
import whisk.common.TransactionId
import whisk.common.LoggingMarkers
import whisk.core.database.DocumentSerializer
import whisk.core.database.ArtifactStore
import whisk.core.database.StaleParameter
import whisk.core.entity.DocumentReader
import whisk.core.entity.DocInfo

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.mongodb.scala._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.FindOneAndUpdateOptions
import org.mongodb.scala.model.FindOneAndDeleteOptions
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import spray.json._
import spray.json.JsNumber
import whisk.core.database.DocumentConflictException
import whisk.core.database.ArtifactStoreException
import whisk.core.database.NoDocumentException
import whisk.core.database.DocumentTypeMismatchException
import whisk.core.database.DocumentUnreadable
import whisk.core.entity.DocId
import whisk.core.entity.DocRevision
import whisk.core.entity.WhiskDocument
import whisk.http.Messages

class MongoDbStore[DocumentAbstraction <: DocumentSerializer](config: MongoConfig,
                                                              collName: String,
                                                              useBatching: Boolean = false)(
  implicit system: ActorSystem,
  val logging: Logging,
  jsonFormat: RootJsonFormat[DocumentAbstraction],
  materializer: ActorMaterializer,
  docReader: DocumentReader)
    extends ArtifactStore[DocumentAbstraction]
    with DefaultJsonProtocol {

  override protected[core] implicit val executionContext: ExecutionContext = system.dispatcher

  //TODO Share MongoClient between stores
  private val client: MongoClient = MongoClient(config.uri)
  private val coll: MongoCollection[Document] = client.getDatabase(config.db).getCollection[Document](collName)

  //TODO Index creation
  private val _rev = "_rev"
  private val _data = "_data"
  private val _id = "_id"

  override protected[database] def put(d: DocumentAbstraction)(implicit transid: TransactionId): Future[DocInfo] = {
    val asJson = d.toDocumentRecord

    val id = asJson.fields(_id).convertTo[String].trim
    require(!id.isEmpty, "document id must be defined")

    val rev: Int = asJson.fields.get(_rev) match {
      case Some(r) => r.convertTo[String].toInt
      case None    => 0
    }

    val docinfoStr = s"id: $id, rev: $rev"
    val mongoJsonDoc = toMongoJsonDoc(asJson)
    val start = transid.started(this, LoggingMarkers.DATABASE_SAVE, s"[PUT] '$collName' saving document: '$docinfoStr'")
    //TODO Batch mode
    val f = rev match {
      case 0 =>
        val doc = toDocument(JsObject(mongoJsonDoc.fields + (_rev -> JsNumber(1))))
        coll
          .insertOne(doc)
          .head()
          .transform(_ => DocInfo(DocId(id), DocRevision(1.toString)), {
            case _: DuplicateKeyException => DocumentConflictException("conflict on 'put'")
            case e                        => e
          })
      case _ =>
        val doc = toDocument(mongoJsonDoc.fields(_data).asJsObject)
        coll
          .findOneAndUpdate(
            and(equal(_id, id), equal(_rev, rev)),
            combine(set(_data, doc), inc(_rev, 1)),
            FindOneAndUpdateOptions()
              .projection(fields(include(_rev), excludeId()))
              .returnDocument(ReturnDocument.AFTER)) //TODO Project field doc can be singleton
          .head()
          .map {
            case null        => throw DocumentConflictException("conflict on 'put'")
            case d: Document => DocInfo(DocId(id), DocRevision(d(_rev).asInt32().getValue.toString))
          }
    }

    f.onFailure({
      case _: DocumentConflictException =>
        transid.finished(this, start, s"[PUT] '$collName', document: '$docinfoStr'; conflict.")
    })

    f.onSuccess({
      case _ => transid.finished(this, start, s"[PUT] '$collName' completed document: '$docinfoStr'")
    })

    reportFailure(
      f,
      failure =>
        transid.failed(this, start, s"[PUT] '$collName' internal error, failure: '${failure.getMessage}'", ErrorLevel))
  }

  override protected[database] def del(doc: DocInfo)(implicit transid: TransactionId): Future[Boolean] = {
    require(doc != null && doc.rev.asString != null, "doc revision required for delete")

    val start = transid.started(this, LoggingMarkers.DATABASE_DELETE, s"[DEL] '$collName' deleting document: '$doc'")
    val f = coll
      .findOneAndDelete(createFilter(doc), FindOneAndDeleteOptions().projection(fields(include(_rev))))
      .head()
      .map {
        case _: Document =>
          transid.finished(this, start, s"[DEL] '$collName' completed document: '$doc'")
          true
        case null =>
          transid.finished(this, start, s"[DEL] '$collName', document: '$doc'; not found.")
          // for compatibility
          throw NoDocumentException("not found on 'delete'")
      }

    reportFailure(
      f,
      failure =>
        transid.failed(
          this,
          start,
          s"[DEL] '$collName' internal error, doc: '$doc', failure: '${failure.getMessage}'",
          ErrorLevel))
  }

  override protected[database] def get[A <: DocumentAbstraction](doc: DocInfo)(implicit transid: TransactionId,
                                                                               ma: Manifest[A]): Future[A] = {
    val start = transid.started(this, LoggingMarkers.DATABASE_GET, s"[GET] '$collName' finding document: '$doc'")

    require(doc != null, "doc undefined")
    val f = coll
      .find(createFilter(doc))
      .head()
      .map {
        case d: Document =>
          transid.finished(this, start, s"[GET] '$collName' completed: found document '$doc'")
          val response = toWhiskJsonDoc(d)
          val asFormat = try {
            docReader.read(ma, response)
          } catch {
            case _: Exception => jsonFormat.read(response)
          }

          if (asFormat.getClass != ma.runtimeClass) {
            throw DocumentTypeMismatchException(
              s"document type ${asFormat.getClass} did not match expected type ${ma.runtimeClass}.")
          }

          val deserialized = asFormat.asInstanceOf[A]

          val responseRev = response.fields(_rev).convertTo[String]
          assert(doc.rev.rev == null || doc.rev.rev == responseRev, "Returned revision should match original argument")
          // FIXME remove mutability from appropriate classes now that it is no longer required by GSON.
          deserialized.asInstanceOf[WhiskDocument].revision(DocRevision(responseRev))

          deserialized
        case null =>
          transid.finished(this, start, s"[GET] '$collName', document: '$doc'; not found.")
          // for compatibility
          throw NoDocumentException("not found on 'get'")
      }
      .recoverWith {
        case _: DeserializationException => throw DocumentUnreadable(Messages.corruptedEntity)
      }
    reportFailure(
      f,
      failure =>
        transid.failed(
          this,
          start,
          s"[GET] '$collName' internal error, doc: '$doc', failure: '${failure.getMessage}'",
          ErrorLevel))
  }

  override protected[core] def query(table: String,
                                     startKey: List[Any],
                                     endKey: List[Any],
                                     skip: Int,
                                     limit: Int,
                                     includeDocs: Boolean,
                                     descending: Boolean,
                                     reduce: Boolean,
                                     stale: StaleParameter)(implicit transid: TransactionId): Future[List[JsObject]] = {
    Future.failed(new Exception()) //FIXME
  }

  override protected[core] def count(table: String,
                                     startKey: List[Any],
                                     endKey: List[Any],
                                     skip: Int,
                                     stale: StaleParameter)(implicit transid: TransactionId): Future[Long] = {
    Future.failed(new Exception()) //FIXME
  }

  override protected[core] def attach(
    doc: DocInfo,
    name: String,
    contentType: ContentType,
    docStream: Source[ByteString, _])(implicit transid: TransactionId): Future[DocInfo] = {
    Future.failed(new Exception()) //FIXME
  }

  override protected[core] def readAttachment[T](doc: DocInfo, name: String, sink: Sink[ByteString, Future[T]])(
    implicit transid: TransactionId): Future[(ContentType, T)] = {
    Future.failed(new Exception()) //FIXME
  }

  override def shutdown(): Unit = {
    //TODO Switch to ref count
    client.close()
  }

  private def createFilter(doc: DocInfo): Bson = {
    doc.rev match {
      case rev if rev.empty => equal(_id, doc.id.id)
      case rev              => and(equal(_id, doc.id.id), equal(_rev, rev.rev.toInt))
    }
  }

  private def toDocument(json: JsObject): Document = {
    Document(json.compactPrint)
  }

  /**
   * Transforms the json into format {_id: id, "_data" : provided doc}
   */
  private def toMongoJsonDoc(json: JsObject): JsObject = {
    val data = json.fields - _id - _rev
    JsObject(_data -> JsObject(data), _id -> json.fields(_id))
  }

  private def toWhiskJsonDoc(doc: Document): JsObject = {
    val js = doc.toJson().parseJson.asJsObject
    val rev = js.fields(_rev).convertTo[Int].toString
    val wskJson = js.fields(_data).asJsObject.fields + (_id -> js.fields(_id)) + (_rev -> JsString(rev))
    JsObject(wskJson)
  }

  private def reportFailure[T, U](f: Future[T], onFailure: Throwable => U): Future[T] = {
    f.onFailure({
      case _: ArtifactStoreException => // These failures are intentional and shouldn't trigger the catcher.
      case x                         => onFailure(x)
    })
    f
  }

}
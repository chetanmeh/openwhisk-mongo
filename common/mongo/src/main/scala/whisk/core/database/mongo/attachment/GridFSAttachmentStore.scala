/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.database.mongo.attachment

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentType
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.event.Logging.ErrorLevel
import akka.http.scaladsl.model.ContentTypes
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.mongodb.client.gridfs.model.GridFSUploadOptions
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.gridfs.GridFSBucket
import org.mongodb.scala._
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.gridfs.GridFSFile
import org.mongodb.scala.gridfs.MongoGridFSException
import org.mongodb.scala.model.Filters
import whisk.common.TransactionId
import whisk.common.LoggingMarkers
import whisk.common.Logging
import whisk.core.database.ArtifactStoreException
import whisk.core.database.NoDocumentException
import whisk.core.database.mongo.AttachmentStore
import whisk.core.entity.DocInfo

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class GridFSAttachmentStore(db: MongoDatabase, namespace: String)(implicit system: ActorSystem,
                                                                  logging: Logging,
                                                                  materializer: ActorMaterializer)
    extends AttachmentStore {

  override protected[core] implicit val executionContext: ExecutionContext = system.dispatcher

  private val gridFS: GridFSBucket = GridFSBucket(db)

  override protected[core] def attach(
    doc: DocInfo,
    name: String,
    contentType: ContentType,
    docStreamSource: Source[ByteString, _])(implicit transid: TransactionId): Future[DocInfo] = {
    val start = transid.started(
      this,
      LoggingMarkers.DATABASE_ATT_SAVE,
      s"[ATT_PUT] '$namespace' uploading attachment '$name' of document '$doc'")

    require(doc != null, "doc undefined")
    require(doc.rev.rev != null, "doc revision must be specified")

    val filename = s"$namespace/${doc.id.id}/$name"
    val renamedFile = s"$filename-new"
    val options = new GridFSUploadOptions().metadata(Document("contentType" -> contentType.toString()))

    def findExisting = gridFS.find(Filters.equal("filename", filename)).head()

    def renameNew(existingFile: GridFSFile, newFileId: ObjectId) =
      if (existingFile != null) gridFS.rename(newFileId, filename).head() else Future.successful(Completed())

    def createNew(useTempNewName: Boolean) = {
      val name = if (useTempNewName) renamedFile else filename
      val uploadStream = gridFS.openUploadStream(name, options)
      val sink = AsyncStreamSink(uploadStream)
      docStreamSource.runWith(sink).map(_ => uploadStream.objectId)
    }

    def deleteOld(existingFile: GridFSFile) = {
      if (existingFile == null) {
        Future.successful(Completed())
      } else {
        gridFS.delete(existingFile.getId).head()
      }
    }

    //Adding an attachment involves
    //1. Find existing file
    //2. if fileExists
    //       2.1 create new file with a different name
    //       2.2 delete old file
    //       2.3 rename new file to expected name
    //   else
    //       create new file with required name

    // @formatter:off
    val f = for {
      existingFile <- findExisting
      newFileId    <- createNew(existingFile != null)
      _            <- deleteOld(existingFile)
      _            <- renameNew(existingFile, newFileId)
    } yield doc
    // @formatter:on

    f.onSuccess {
      case _ =>
        transid
          .finished(this, start, s"[ATT_PUT] '$namespace' completed uploading attachment '$name' of document '$doc'")
    }

    reportFailure(
      f,
      failure =>
        transid.failed(
          this,
          start,
          s"[ATT_PUT] '$namespace' internal error, name: '$name', doc: '$doc', failure: '${failure.getMessage}'",
          ErrorLevel))
  }

  override protected[core] def readAttachment[T](doc: DocInfo, name: String, sink: Sink[ByteString, Future[T]])(
    implicit transid: TransactionId): Future[(ContentType, T)] = {
    val start = transid.started(
      this,
      LoggingMarkers.DATABASE_ATT_GET,
      s"[ATT_GET] '$namespace' finding attachment '$name' of document '$doc'")

    require(doc != null, "doc undefined")
    require(doc.rev.rev != null, "doc revision must be specified")

    val filename = s"$namespace/${doc.id.id}/$name"
    val downloadStream = gridFS.openDownloadStream(filename)

    def readStream(file: GridFSFile) = {
      val source = AsyncStreamSource(downloadStream)
      source.runWith(sink)
    }

    def getGridFSFile = {
      downloadStream
        .gridFSFile()
        .head()
        .transform(
          identity, {
            case ex: MongoGridFSException if ex.getMessage.contains("File not found") =>
              transid.finished(
                this,
                start,
                s"[ATT_GET] '$namespace', retrieving attachment '$name' of document '$doc'; not found.")
              NoDocumentException("Not found on 'readAttachment'.")
            case t => t
          })
    }

    val f = for {
      file <- getGridFSFile
      result <- readStream(file)
    } yield (getContentType(file), result)

    reportFailure(
      f,
      failure =>
        transid.failed(
          this,
          start,
          s"[ATT_GET] '$namespace' internal error, name: '$name', doc: '$doc', failure: '${failure.getMessage}'",
          ErrorLevel))
  }

  override protected[core] def deleteAttachments(doc: DocInfo)(implicit transid: TransactionId): Future[Boolean] = {
    val start = transid.started(
      this,
      LoggingMarkers.DATABASE_ATT_SAVE,
      s"[ATT_DEL] '$namespace' deleting attachments of document '$doc'")

    require(doc != null, "doc undefined")
    require(doc.rev.rev != null, "doc revision must be specified")

    val filenameRegex = s"^$namespace/${doc.id.id}/".r
    def findExisting = gridFS.find(Filters.regex("filename", filenameRegex)).toFuture()

    def delete(files: Seq[GridFSFile]) = {
      val d = files.map(f => gridFS.delete(f.getId).head())
      Future.sequence(d)
    }

    val f = for {
      files <- findExisting
      result <- delete(files)
    } yield true

    f.onSuccess {
      case _ =>
        transid
          .finished(this, start, s"[ATT_DEL] '$namespace' successfully deleted attachments of document '$doc'")
    }

    reportFailure(
      f,
      failure =>
        transid.failed(
          this,
          start,
          s"[ATT_DEL] '$namespace' internal error, doc: '$doc', failure: '${failure.getMessage}'",
          ErrorLevel))
  }

  private def getContentType(file: GridFSFile): ContentType = {
    val typeString = file.getMetadata.getString("contentType")
    require(typeString != null, "Did not find 'contentType' in file metadata")
    ContentType.parse(typeString) match {
      case Right(ct) => ct
      case Left(_)   => ContentTypes.`text/plain(UTF-8)` //Should not happen
    }
  }

  private def reportFailure[T, U](f: Future[T], onFailure: Throwable => U): Future[T] = {
    f.onFailure({
      case _: ArtifactStoreException => // These failures are intentional and shouldn't trigger the catcher.
      case x                         => onFailure(x)
    })
    f
  }
}

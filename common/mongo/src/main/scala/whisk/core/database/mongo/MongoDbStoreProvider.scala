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

package whisk.core.database.mongo

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mongodb.scala.MongoClient
import spray.json.RootJsonFormat
import whisk.common.Logging
import whisk.core.database.ArtifactStoreProvider
import whisk.core.database.ArtifactStore
import whisk.core.database.DocumentSerializer
import whisk.core.entity.DocumentReader
import pureconfig._
import whisk.core.database.mongo.attachment.GridFSAttachmentStore
import whisk.core.entity.WhiskEntity
import whisk.core.entity.WhiskActivation
import whisk.core.entity.WhiskAuth

import scala.reflect.ClassTag

case class MongoConfig(uri: String, db: String, debug: Boolean = false)

object ConfigKeys {
  val mongo = "whisk.mongo"
}

object MongoDbStoreProvider extends ArtifactStoreProvider {
  private var clientRef: ReferenceCounted[MongoClient] = _
  private val config = loadConfigOrThrow[MongoConfig](ConfigKeys.mongo)

  override def makeStore[D <: DocumentSerializer: ClassTag](useBatching: Boolean)(
    implicit jsonFormat: RootJsonFormat[D],
    docReader: DocumentReader,
    actorSystem: ActorSystem,
    logging: Logging,
    materializer: ActorMaterializer): ArtifactStore[D] = {

    val ref = getCountReference
    val (collectionName, handler, mapper, attachmentStore) = handlerAndMapper(implicitly[ClassTag[D]], ref.get)

    new MongoDbStore[D](ref, config, collectionName, handler, mapper, attachmentStore, useBatching)
  }

  private def handlerAndMapper[D](entityType: ClassTag[D], client: MongoClient)(
    implicit actorSystem: ActorSystem,
    logging: Logging,
    materializer: ActorMaterializer): (String, DocumentHandler, MongoViewMapper, AttachmentStore) = {
    val db = client.getDatabase(config.db)
    entityType.runtimeClass match {
      case x if x == classOf[WhiskEntity] =>
        ("whisks", WhisksHandler, WhisksViewMapper, new GridFSAttachmentStore(db, "whisks"))
      case x if x == classOf[WhiskActivation] =>
        ("activations", ActivationHandler, ActivationViewMapper, new GridFSAttachmentStore(db, "activations"))
      case x if x == classOf[WhiskAuth] =>
        ("subjects", SubjectHandler, SubjectViewMapper, new GridFSAttachmentStore(db, "subjects"))
    }
  }

  private def getCountReference = synchronized {
    if (clientRef == null || clientRef.isClosed) {
      clientRef = new ReferenceCounted[MongoClient](MongoClientHelper.createClient(config.uri, config.debug))
    }
    clientRef.getReference
  }
}

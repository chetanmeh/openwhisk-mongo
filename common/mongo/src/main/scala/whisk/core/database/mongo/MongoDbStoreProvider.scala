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
import akka.stream.ActorMaterializer
import spray.json.RootJsonFormat
import whisk.common.Logging
import whisk.core.WhiskConfig
import whisk.core.database.ArtifactStoreProvider
import whisk.core.database.ArtifactStore
import whisk.core.database.DocumentSerializer
import whisk.core.entity.DocumentReader

import pureconfig._

case class MongoConfig(uri: String, db: String)

object ConfigKeys {
  val mongo = "whisk.mongo"
}

object MongoDbStoreProvider extends ArtifactStoreProvider {

  override def makeStore[D <: DocumentSerializer](config: WhiskConfig,
                                                  name: WhiskConfig => String,
                                                  useBatching: Boolean)(
    implicit jsonFormat: RootJsonFormat[D],
    docReader: DocumentReader,
    actorSystem: ActorSystem,
    logging: Logging,
    materializer: ActorMaterializer): ArtifactStore[D] = {
    val mc = loadConfigOrThrow[MongoConfig](ConfigKeys.mongo)
    new MongoDbStore[D](mc, name(config), useBatching)
  }
}
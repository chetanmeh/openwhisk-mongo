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

import org.mongodb.scala.MongoClient
import org.mongodb.scala.Document
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import pureconfig._
import org.mongodb.scala.model.Filters._
import spray.json._
import spray.json.JsObject

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

trait MongoSupport extends BeforeAndAfterAll with ArtifactStoreUtils {
  self: Suite =>

  lazy val mongoConfig: MongoConfig = loadConfigOrThrow[MongoConfig](ConfigKeys.mongo)
  lazy val mongoClient = MongoClient(mongoConfig.uri)

  override def get(id: String, dbName: String)(implicit ec: ExecutionContext): JsObject = {
    val f = mongoClient.getDatabase(mongoConfig.db).getCollection[Document](dbName).find(equal("_id", id)).head()
    Await.result(f.map { doc =>
      doc.toJson().parseJson.asJsObject
    }, 15 seconds)
  }
}

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

package whisk.core.database.test

import akka.stream.ActorMaterializer
import common.WskActorSystem
import common.StreamLogging
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.junit.JUnitRunner
import whisk.common.TransactionId
import whisk.core.WhiskConfig
import whisk.core.entity.WhiskAuth
import whisk.core.entity.WhiskAuthStore
import whisk.core.entity.Subject
import whisk.core.entity.WhiskNamespace
import whisk.core.entity.AuthKey
import whisk.core.entity.EntityName

@RunWith(classOf[JUnitRunner])
class MongoBasicTest extends FlatSpec with WskActorSystem with StreamLogging with BeforeAndAfterAll with DbUtils {

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val config = new WhiskConfig(Map("db.whisk.auths" -> "auths"))
  lazy val authstore = WhiskAuthStore.datastore(config)

  override protected def beforeAll(): Unit = {
    System.setProperty("whisk.mongo.uri", "mongodb://localhost:27017")
    System.setProperty("whisk.mongo.db", "ow_test")
  }

  override def afterAll(): Unit = {
    println("Shutting down store connections")
    authstore.shutdown()
    super.afterAll()
  }

  behavior of "MongoStore"

  it should "connect" in {
    implicit val tid: TransactionId = transid()
    val subject = Subject()

    val namespaces = Set(WhiskNamespace(EntityName("foo"), AuthKey()))
    val auth = WhiskAuth(subject, namespaces)
    put(authstore, auth)
  }
}

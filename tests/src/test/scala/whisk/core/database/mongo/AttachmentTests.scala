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

import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.junit.JUnitRunner
import whisk.core.controller.test.WhiskAuthHelpers
import whisk.core.database.CacheChangeNotification
import whisk.core.entity.Parameters
import whisk.core.entity.WhiskAction
import whisk.core.entity.EntityPath
import whisk.core.entity.test.ExecHelpers

@RunWith(classOf[JUnitRunner])
class AttachmentTests
    extends FlatSpec
    with ArtifactStoreHelper
    with MongoSupport
    with ExecHelpers
    with ScalaFutures
    with IntegrationPatience {

  behavior of "Attachments"

  val creds = WhiskAuthHelpers.newIdentity()
  val namespace = EntityPath(creds.subject.asString)
  implicit val cacheUpdateNotifier: Option[CacheChangeNotification] = None

  it should "get and put entity with attachment" in {
    implicit val tid = transid()
    val javaAction =
      WhiskAction(
        namespace,
        MakeName.next("attachment"),
        javaDefault("ZHViZWU=", Some("hello")),
        annotations = Parameters("exec", "java"))

    val pf = WhiskAction.put(entityStore, javaAction)
    val docInfo = pf.futureValue

    val gf = WhiskAction.get(entityStore, docInfo.id, docInfo.rev)
    val readAction = gf.futureValue

    readAction.exec shouldBe javaAction.exec
  }

}

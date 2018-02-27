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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

import akka.http.scaladsl.model.ContentTypes
import akka.stream.scaladsl.StreamConverters
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.junit.JUnitRunner
import whisk.core.database.NoDocumentException
import whisk.core.database.mongo.MongoSupport
import whisk.core.database.mongo.ArtifactStoreHelper
import whisk.core.entity.DocInfo

import scala.util.Random

@RunWith(classOf[JUnitRunner])
class GridFSAttachmentStoreTests
    extends FlatSpec
    with ArtifactStoreHelper
    with ScalaFutures
    with IntegrationPatience
    with MongoSupport {

  behavior of "Attachments"

  it should "add some binary content" in {
    implicit val tid = transid()
    val store = createStore()
    val bytes = randomBytes(4000)

    val info = DocInfo ! (newActionName, "1")
    val source = StreamConverters.fromInputStream(() => new ByteArrayInputStream(bytes), 42)
    val result = store.attach(info, "code", ContentTypes.`application/octet-stream`, source)

    result.futureValue shouldBe info
  }

  it should "add and read some binary content" in {
    implicit val tid = transid()
    val store = createStore()
    val bytes = randomBytes(4000)

    val info = DocInfo ! (newActionName, "1")
    val source = StreamConverters.fromInputStream(() => new ByteArrayInputStream(bytes), 42)
    val writeResult = store.attach(info, "code", ContentTypes.`application/octet-stream`, source)

    writeResult.futureValue shouldBe info

    val os = new ByteArrayOutputStream()
    val sink = StreamConverters.fromOutputStream(() => os)
    val readResultFuture = store.readAttachment(info, "code", sink)

    val (readContentType, ioResult) = readResultFuture.futureValue

    readContentType shouldBe ContentTypes.`application/octet-stream`
    ioResult.count shouldBe bytes.length
    os.toByteArray shouldBe bytes
  }

  it should "add and then update binary content" in {
    implicit val tid = transid()
    val store = createStore()
    val bytes1 = randomBytes(4000)

    val info = DocInfo ! (newActionName, "1")
    val source = StreamConverters.fromInputStream(() => new ByteArrayInputStream(bytes1), 42)
    val writeResult = store.attach(info, "code", ContentTypes.`application/octet-stream`, source)

    writeResult.futureValue shouldBe info

    val bytes2 = randomBytes(7000)
    val source2 = StreamConverters.fromInputStream(() => new ByteArrayInputStream(bytes2), 42)
    val writeResult2 = store.attach(info, "code", ContentTypes.`application/json`, source2)

    writeResult2.futureValue shouldBe info

    val os = new ByteArrayOutputStream()
    val sink = StreamConverters.fromOutputStream(() => os)
    val readResultFuture = store.readAttachment(info, "code", sink)

    val (readContentType, ioResult) = readResultFuture.futureValue

    readContentType shouldBe ContentTypes.`application/json`
    ioResult.count shouldBe bytes2.length
    os.toByteArray shouldBe bytes2
  }

  it should "throw NoDocumentException on reading non existing file" in {
    implicit val tid = transid()
    val store = createStore()

    val os = new ByteArrayOutputStream()
    val sink = StreamConverters.fromOutputStream(() => os)
    val info = DocInfo ! ("nonExistingAction", "1")
    val f = store.readAttachment(info, "code", sink)

    f.failed.futureValue shouldBe a[NoDocumentException]
  }

  private def createStore() = {
    new GridFSAttachmentStore(mongoClient.getDatabase(mongoConfig.db), "whisks")
  }

  private def randomBytes(size: Int): Array[Byte] = {
    val arr = new Array[Byte](size)
    Random.nextBytes(arr)
    arr
  }

  private def newActionName = MakeName.next("gridFSTestAction").name
}

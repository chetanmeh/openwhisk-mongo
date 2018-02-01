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

package whisk.core.database.test

import akka.stream.ActorMaterializer
import common.StreamLogging
import common.WskActorSystem
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfter
import org.scalatest.junit.JUnitRunner
import whisk.common.TransactionId
import whisk.core.WhiskConfig
import whisk.core.database.NoDocumentException
import whisk.core.database.DocumentConflictException
import whisk.core.entity.WhiskAuthStore
import whisk.core.entity.WhiskAuth
import whisk.core.entity.AuthKey
import whisk.core.entity.EntityName
import whisk.core.entity.WhiskNamespace
import whisk.core.entity.Subject
import whisk.core.entity.DocInfo

import scala.concurrent.Await

@RunWith(classOf[JUnitRunner])
class MongoDbStoreTest
    extends FlatSpec
    with WskActorSystem
    with StreamLogging
    with BeforeAndAfterAll
    with BeforeAndAfter
    with DbUtils
    with MongoSupport
    with Matchers {

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val config = new WhiskConfig(Map("db.whisk.auths" -> "auths"))
  lazy val store = WhiskAuthStore.datastore(config)

  override def afterAll(): Unit = {
    println("Shutting down store connections")
    store.shutdown()
    super.afterAll()
  }

  after {
    //TODO Remove this
    println(logLines.mkString("\n"))
  }

  behavior of "MongoDbStore put"

  it should "put document and get a revision 1" in {
    implicit val tid: TransactionId = transid()
    val doc = put(store, newAuth())
    doc.rev.rev shouldBe "1"
  }

  it should "put and update document" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(store, auth)

    val auth2 = getWhiskAuth(doc).copy(namespaces = Set(wskNS("foo1"))).revision[WhiskAuth](doc.rev)
    val doc2 = put(store, auth2)
    doc2.rev.rev shouldBe "2"
  }

  it should "throw DocumentConflictException when updated with old revision" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(store, auth)

    val auth2 = getWhiskAuth(doc).copy(namespaces = Set(wskNS("foo1"))).revision[WhiskAuth](doc.rev)
    val doc2 = put(store, auth2)

    //Updated with _rev set to older one
    val auth3 = getWhiskAuth(doc2).copy(namespaces = Set(wskNS("foo2"))).revision[WhiskAuth](doc.rev)
    intercept[DocumentConflictException] {
      put(store, auth3)
    }
  }

  it should "throw DocumentConflictException if document with same id is inserted twice" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(store, auth)

    intercept[DocumentConflictException] {
      put(store, auth)
    }
  }

  behavior of "MongoDbStore delete"

  it should "deletes existing document" in {
    implicit val tid: TransactionId = transid()
    val doc = put(store, newAuth())
    delete(store, doc) shouldBe true
  }

  it should "throws IllegalArgumentException when deleting without revision" in {
    intercept[IllegalArgumentException] {
      implicit val tid: TransactionId = transid()
      delete(store, DocInfo("doc-with-empty-revision"))
    }
  }

  it should "throws NoDocumentException when document does not exist" in {
    intercept[NoDocumentException] {
      implicit val tid: TransactionId = transid()
      delete(store, DocInfo ! ("non-existing-doc", "42"))
    }
  }

  behavior of "MongoDbStore get"

  it should "get existing entity matching id and rev" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(store, auth)
    val authFromGet = getWhiskAuth(doc)
    authFromGet shouldBe auth
    authFromGet.docinfo.rev shouldBe doc.rev
  }

  it should "get existing entity matching id only" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(store, auth)
    val authFromGet = getWhiskAuth(doc)
    authFromGet shouldBe auth
  }

  it should "throws NoDocumentException when document revision does not match" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(store, auth)

    val auth2 = getWhiskAuth(doc).copy(namespaces = Set(wskNS("foo1"))).revision[WhiskAuth](doc.rev)
    val doc2 = put(store, auth2)

    intercept[NoDocumentException] {
      getWhiskAuth(doc)
    }

    val authFromGet = getWhiskAuth(doc2)
    authFromGet shouldBe auth2
  }

  it should "throws NoDocumentException when document does not exist" in {
    intercept[NoDocumentException] {
      implicit val tid: TransactionId = transid()
      getWhiskAuth(DocInfo("non-existing-doc"))
    }
  }

  private def getWhiskAuth(doc: DocInfo)(implicit transid: TransactionId) = {
    Await.result(store.get[WhiskAuth](doc), dbOpTimeout)
  }

  private def newAuth() = {
    val subject = Subject()
    val namespaces = Set(wskNS("foo"))
    WhiskAuth(subject, namespaces)
  }

  private def wskNS(name: String) = {
    WhiskNamespace(EntityName(name), AuthKey())
  }
}

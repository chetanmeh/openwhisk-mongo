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

import java.time.Instant

import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import whisk.common.TransactionId
import whisk.core.database.NoDocumentException
import whisk.core.database.DocumentConflictException
import whisk.core.entity.WhiskAuth
import whisk.core.entity.AuthKey
import whisk.core.entity.EntityName
import whisk.core.entity.WhiskNamespace
import whisk.core.entity.Subject
import whisk.core.entity.DocInfo
import whisk.core.entity.EntityPath
import whisk.core.entity.WhiskActivation
import whisk.core.entity.ActivationId

import scala.concurrent.Await

@RunWith(classOf[JUnitRunner])
class MongoDbStoreTests extends FlatSpec with ArtifactStoreHelper with MongoSupport {

  behavior of "MongoDbStore put"

  it should "put document and get a revision 1" in {
    implicit val tid: TransactionId = transid()
    val doc = put(authStore, newAuth())
    doc.rev.rev shouldBe "1"
  }

  it should "put and update document" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(authStore, auth)

    val auth2 = getWhiskAuth(doc).copy(namespaces = Set(wskNS("foo1"))).revision[WhiskAuth](doc.rev)
    val doc2 = put(authStore, auth2)
    doc2.rev.rev shouldBe "2"
  }

  it should "throw DocumentConflictException when updated with old revision" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(authStore, auth)

    val auth2 = getWhiskAuth(doc).copy(namespaces = Set(wskNS("foo1"))).revision[WhiskAuth](doc.rev)
    val doc2 = put(authStore, auth2)

    //Updated with _rev set to older one
    val auth3 = getWhiskAuth(doc2).copy(namespaces = Set(wskNS("foo2"))).revision[WhiskAuth](doc.rev)
    intercept[DocumentConflictException] {
      put(authStore, auth3)
    }
  }

  it should "throw DocumentConflictException if document with same id is inserted twice" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(authStore, auth)

    intercept[DocumentConflictException] {
      put(authStore, auth)
    }
  }

  behavior of "MongoDbStore delete"

  it should "deletes existing document" in {
    implicit val tid: TransactionId = transid()
    val doc = put(authStore, newAuth())
    delete(authStore, doc) shouldBe true
  }

  it should "throws IllegalArgumentException when deleting without revision" in {
    intercept[IllegalArgumentException] {
      implicit val tid: TransactionId = transid()
      delete(authStore, DocInfo("doc-with-empty-revision"))
    }
  }

  it should "throws NoDocumentException when document does not exist" in {
    intercept[NoDocumentException] {
      implicit val tid: TransactionId = transid()
      delete(authStore, DocInfo ! ("non-existing-doc", "42"))
    }
  }

  behavior of "MongoDbStore get"

  it should "get existing entity matching id and rev" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(authStore, auth)
    val authFromGet = getWhiskAuth(doc)
    authFromGet shouldBe auth
    authFromGet.docinfo.rev shouldBe doc.rev
  }

  it should "get existing entity matching id only" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(authStore, auth)
    val authFromGet = getWhiskAuth(doc)
    authFromGet shouldBe auth
  }

  it should "get entity with timestamp" in {
    implicit val tid: TransactionId = transid()
    val activation = WhiskActivation(
      EntityPath("testnamespace"),
      EntityName("activation1"),
      Subject(),
      ActivationId(),
      start = Instant.now,
      end = Instant.now)
    val activationDoc = put(activationStore, activation)
    val activationFromDb = Await.result(activationStore.get[WhiskActivation](activationDoc), dbOpTimeout)
    activationFromDb shouldBe activation
  }

  it should "throws NoDocumentException when document revision does not match" in {
    implicit val tid: TransactionId = transid()
    val auth = newAuth()
    val doc = put(authStore, auth)

    val auth2 = getWhiskAuth(doc).copy(namespaces = Set(wskNS("foo1"))).revision[WhiskAuth](doc.rev)
    val doc2 = put(authStore, auth2)

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
    Await.result(authStore.get[WhiskAuth](doc), dbOpTimeout)
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

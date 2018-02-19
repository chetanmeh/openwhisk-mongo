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
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsArray
import spray.json.JsNumber
import spray.json.JsValue
import whisk.common.TransactionId
import whisk.core.controller.test.WhiskAuthHelpers
import whisk.core.database.StaleParameter
import whisk.core.database.ArtifactStore
import whisk.core.entity.EntityPath
import whisk.core.entity.Subject
import whisk.core.entity.ExecManifest
import whisk.core.entity.BlackBoxExec
import whisk.core.entity.WhiskAction
import whisk.core.entity.WhiskActivation
import whisk.core.entity.EntityName
import whisk.core.entity.ActivationId
import whisk.core.entity.WhiskEntity
import whisk.core.entity.WhiskAuth
import whisk.core.entity.WhiskNamespace
import whisk.core.entity.AuthKey
import whisk.core.entity.Identity
import whisk.core.entity.WhiskEntityQueries.TOP
import whisk.utils.JsHelpers

import scala.concurrent.Await

@RunWith(classOf[JUnitRunner])
class MongoDbQueryTests extends FlatSpec with ArtifactStoreHelper with MongoSupport {

  val creds1 = WhiskAuthHelpers.newAuth(Subject("s314159"))
  val ns1 = EntityPath(creds1.subject.asString)

  val exec = BlackBoxExec(ExecManifest.ImageName("image"), None, None, false)

  def aname() = MakeName.next("mongoquerytests")

  debug = false

  behavior of "MongoDbStore query"

  it should "find single entity" in {
    implicit val tid = transid()

    val action = newAction(ns1)
    val docInfo = put(entityStore, action)

    val result = query[WhiskEntity](
      entityStore,
      "whisks/actions",
      List(ns1.asString, 0),
      List(ns1.asString, TOP, TOP),
      includeDocs = true)

    result should have length 1

    def js = result.head
    js.fields("id") shouldBe JsString(docInfo.id.id)
    js.fields("key") shouldBe JsArray(JsString(ns1.asString), JsNumber(action.updated.toEpochMilli))
    js.fields.get("value") shouldBe defined
    js.fields.get("doc") shouldBe defined
    js.fields("value") shouldBe action.summaryAsJson
    dropRev(js.fields("doc").asJsObject) shouldBe action.toDocumentRecord
  }

  it should "not have doc with includeDocs = false" in {
    implicit val tid = transid()

    val action = newAction(ns1)
    val docInfo = put(entityStore, action)

    val result = query[WhiskEntity](entityStore, "whisks/actions", List(ns1.asString, 0), List(ns1.asString, TOP, TOP))

    result should have length 1

    def js = result.head
    js.fields("id") shouldBe JsString(docInfo.id.id)
    js.fields("key") shouldBe JsArray(JsString(ns1.asString), JsNumber(action.updated.toEpochMilli))
    js.fields.get("value") shouldBe defined
    js.fields.get("doc") shouldBe None
    js.fields("value") shouldBe action.summaryAsJson
  }

  it should "find all entities" in {
    implicit val tid = transid()

    val entities = Seq(newAction(ns1), newAction(ns1))

    entities foreach {
      put(entityStore, _)
    }

    val result = query[WhiskEntity](entityStore, "whisks/actions", List(ns1.asString, 0), List(ns1.asString, TOP, TOP))

    result should have length entities.length
    result.map(_.fields("value")) should contain theSameElementsAs entities.map(_.summaryAsJson)
  }

  it should "return result in sorted order" in {
    implicit val tid = transid()

    val activations = (1000 until 1100 by 10).map(newActivation("testns", "testact", _))
    activations foreach (put(activationStore, _))

    val resultDescending = query[WhiskActivation](
      activationStore,
      "whisks-filters.v2.1.0/activations",
      List("testns/testact", 0),
      List("testns/testact", TOP, TOP))

    resultDescending should have length activations.length
    resultDescending.map(getJsField(_, "value", "start")) shouldBe activations
      .map(_.summaryAsJson.fields("start"))
      .reverse

    val resultAscending = query[WhiskActivation](
      activationStore,
      "whisks-filters.v2.1.0/activations",
      List("testns/testact", 0),
      List("testns/testact", TOP, TOP),
      descending = false)

    resultAscending.map(getJsField(_, "value", "start")) shouldBe activations.map(_.summaryAsJson.fields("start"))
  }

  it should "support skipping results" in {
    implicit val tid = transid()

    val activations = (1000 until 1100 by 10).map(newActivation("testns", "testact", _))
    activations foreach (put(activationStore, _))

    val result = query[WhiskActivation](
      activationStore,
      "whisks-filters.v2.1.0/activations",
      List("testns/testact", 0),
      List("testns/testact", TOP, TOP),
      skip = 5,
      descending = false)

    result.map(getJsField(_, "value", "start")) shouldBe activations.map(_.summaryAsJson.fields("start")).drop(5)
  }

  it should "support limiting results" in {
    implicit val tid = transid()

    val activations = (1000 until 1100 by 10).map(newActivation("testns", "testact", _))
    activations foreach (put(activationStore, _))

    val result = query[WhiskActivation](
      activationStore,
      "whisks-filters.v2.1.0/activations",
      List("testns/testact", 0),
      List("testns/testact", TOP, TOP),
      limit = 5,
      descending = false)

    result.map(getJsField(_, "value", "start")) shouldBe activations.map(_.summaryAsJson.fields("start")).take(5)
  }

  it should "support including complete docs" in {
    implicit val tid = transid()

    val activations = (1000 until 1100 by 10).map(newActivation("testns", "testact", _))
    activations foreach (put(activationStore, _))

    val result = query[WhiskActivation](
      activationStore,
      "whisks-filters.v2.1.0/activations",
      List("testns/testact", 0),
      List("testns/testact", TOP, TOP),
      includeDocs = true,
      descending = false)

    //Drop the _rev field as activations do not have that field
    result.map(js => JsObject(getJsObject(js, "doc").fields - "_rev")) shouldBe activations.map(_.toDocumentRecord)
  }

  private def query[A <: WhiskEntity](
    db: ArtifactStore[A],
    table: String,
    startKey: List[Any],
    endKey: List[Any],
    skip: Int = 0,
    limit: Int = 0,
    includeDocs: Boolean = false,
    descending: Boolean = true,
    reduce: Boolean = false,
    stale: StaleParameter = StaleParameter.No)(implicit transid: TransactionId): List[JsObject] = {
    val f = db.query(table, startKey, endKey, skip, limit, includeDocs, descending, reduce, stale)
    Await.result(f, dbOpTimeout)
  }

  private def newAction(ns: EntityPath): WhiskAction = {
    WhiskAction(ns1, aname(), exec)
  }

  private def newActivation(ns: String, actionName: String, start: Long): WhiskActivation = {
    WhiskActivation(
      EntityPath(ns),
      EntityName(actionName),
      Subject(),
      ActivationId(),
      Instant.ofEpochMilli(start),
      Instant.ofEpochMilli(start + 1000))
  }

  behavior of "MongoDbStore query Subjects"

  it should "find subject by namespace" in {
    implicit val tid = transid()
    val ak1 = AuthKey()
    val ak2 = AuthKey()
    val subs = Array(
      WhiskAuth(Subject(), Set(WhiskNamespace(EntityName("sub_ns1"), ak1))),
      WhiskAuth(Subject(), Set(WhiskNamespace(EntityName("sub_ns2"), ak2))))
    subs foreach (put(authStore, _))

    val f = Identity.get(authStore, EntityName("sub_ns1"))
    Await.result(f, dbOpTimeout).subject shouldBe subs(0).subject

    val f2 = Identity.get(authStore, ak2)
    Await.result(f2, dbOpTimeout).subject shouldBe subs(1).subject
  }

  it should "find subject by namespace with limits" in {
    implicit val tid = transid()
    val ak1 = AuthKey()
    val ak2 = AuthKey()
    val name1 = aname()
    val name2 = aname()
    val subs = Array(
      WhiskAuth(Subject(), Set(WhiskNamespace(name1, ak1))),
      WhiskAuth(Subject(), Set(WhiskNamespace(name2, ak2))))
    subs foreach (put(authStore, _))

    val limits = JsObject("_data" -> JsObject("invocationsPerMinute" -> JsNumber(7), "firesPerMinute" -> JsNumber(31)))
    putRaw[WhiskAuth](s"${name1.name}/limits", limits)

    val f = Identity.get(authStore, name1)
    val i = Await.result(f, dbOpTimeout)
    i.subject shouldBe subs(0).subject
    i.limits.invocationsPerMinute shouldBe Some(7)
    i.limits.firesPerMinute shouldBe Some(31)

  }

  private def dropRev(js: JsObject): JsObject = {
    JsObject(js.fields - "_rev")
  }

  private def getJsObject(js: JsObject, fields: String*): JsObject = {
    JsHelpers.getFieldPath(js, fields: _*).get.asJsObject
  }

  private def getJsField(js: JsObject, subObject: String, fieldName: String): JsValue = {
    js.fields(subObject).asJsObject().fields(fieldName)
  }
}

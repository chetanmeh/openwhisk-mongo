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

import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import spray.json._
import DefaultJsonProtocol._
import whisk.core.database.mongo.WhisksHandler.ROOT_NS
import whisk.core.entity.WhiskRule
import whisk.core.entity.EntityPath
import whisk.core.entity.EntityName
import whisk.core.entity.FullyQualifiedEntityName

@RunWith(classOf[JUnitRunner])
class DocumentHandlerTest extends FlatSpec with Matchers {

  behavior of "WhisksHandler computeFields"

  it should "return empty object when namespace does not exist" in {
    WhisksHandler.computedFields(JsObject()) shouldBe JsObject.empty
  }

  it should "return empty object when namespace is simple name" in {
    WhisksHandler.computedFields(JsObject(("namespace", JsString("foo")))) shouldBe JsObject.empty
    WhisksHandler.computedFields(newRule("foo").toDocumentRecord) shouldBe JsObject.empty
  }

  it should "return JsObject when namespace is path" in {
    WhisksHandler.computedFields(JsObject(("namespace", JsString("foo/bar")))) shouldBe
      JsObject((ROOT_NS, JsString("foo")))

    WhisksHandler.computedFields(newRule("foo/bar").toDocumentRecord) shouldBe JsObject((ROOT_NS, JsString("foo")))
  }

  private def newRule(ns: String): WhiskRule = {
    WhiskRule(
      EntityPath(ns),
      EntityName("foo"),
      FullyQualifiedEntityName(EntityPath("system"), EntityName("bar")),
      FullyQualifiedEntityName(EntityPath("system"), EntityName("bar")))
  }

  behavior of "ActivationHandler computeFields"

  it should "return default value when no annotation found" in {
    val js = """{"foo" : "bar"}""".parseJson.asJsObject
    ActivationHandler.annotationValue(js, "fooKey", { _.convertTo[String] }, "barValue") shouldBe "barValue"

    val js2 = """{"foo" : "bar", "annotations" : "a"}""".parseJson.asJsObject
    ActivationHandler.annotationValue(js2, "fooKey", { _.convertTo[String] }, "barValue") shouldBe "barValue"

    val js3 = """{"foo" : "bar", "annotations" : [{"key" : "someKey", "value" : "someValue"}]}""".parseJson.asJsObject
    ActivationHandler.annotationValue(js3, "fooKey", { _.convertTo[String] }, "barValue") shouldBe "barValue"
  }

  it should "return transformed value when annotation found" in {
    val js = """{
               |  "foo": "bar",
               |  "annotations": [
               |    {
               |      "key": "fooKey",
               |      "value": "fooValue"
               |    }
               |  ]
               |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.annotationValue(js, "fooKey", { _.convertTo[String] + "-x" }, "barValue") shouldBe "fooValue-x"
  }

  it should "computeFields with deleteLogs true" in {
    val js = """{
               |  "foo": "bar",
               |  "annotations": [
               |    {
               |      "key": "fooKey",
               |      "value": "fooValue"
               |    }
               |  ]
               |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computedFields(js) shouldBe """{"deleteLogs" : true}""".parseJson
  }

  it should "computeFields with deleteLogs false for sequence kind" in {
    val js = """{
               |  "foo": "bar",
               |  "annotations": [
               |    {
               |      "key": "kind",
               |      "value": "sequence"
               |    }
               |  ]
               |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computedFields(js) shouldBe """{"deleteLogs" : false}""".parseJson
  }

  it should "computeFields with nspath as namespace" in {
    val js = """{
               |  "namespace": "foons",
               |  "name":"bar",
               |  "annotations": [
               |    {
               |      "key": "kind",
               |      "value": "action"
               |    }
               |  ]
               |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computedFields(js) shouldBe """{"nspath": "foons/bar", "deleteLogs" : true}""".parseJson
  }

  it should "computeFields with nspath as qualified path" in {
    val js = """{
               |  "namespace": "foons",
               |  "name":"bar",
               |  "annotations": [
               |    {
               |      "key": "path",
               |      "value": "barns/barpkg/baraction"
               |    }
               |  ]
               |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computedFields(js) shouldBe """{"nspath": "foons/barpkg/bar", "deleteLogs" : true}""".parseJson
  }

  it should "computeFields with nspath as namespace when path value is simple name" in {
    val js = """{
               |  "namespace": "foons",
               |  "name":"bar",
               |  "annotations": [
               |    {
               |      "key": "path",
               |      "value": "baraction"
               |    }
               |  ]
               |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computedFields(js) shouldBe """{"nspath": "foons/bar", "deleteLogs" : true}""".parseJson
  }

}

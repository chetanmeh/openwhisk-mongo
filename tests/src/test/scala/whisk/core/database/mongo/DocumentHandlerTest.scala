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

  behavior of "WhisksHandler computeView"

  it should "include only common fields in trigger view" in {
    val js = """{
               |  "namespace" : "foo",
               |  "version" : 5,
               |  "end"   : 9,
               |  "cause" : 204
               |}""".stripMargin.parseJson.asJsObject

    val result = """{
                   |  "namespace" : "foo",
                   |  "version" : 5
                   |}""".stripMargin.parseJson.asJsObject
    WhisksHandler.computeView("foo", "triggers", js) shouldBe result
  }

  it should "include false binding in public package view" in {
    val js =
      """{
        |  "namespace" : "foo",
        |  "version" : 5,
        |  "binding"   : {"foo" : "bar"},
        |  "cause" : 204
        |}""".stripMargin.parseJson.asJsObject

    val result =
      """{
        |  "namespace" : "foo",
        |  "version" : 5,
        |  "binding" : false
        |}""".stripMargin.parseJson.asJsObject
    WhisksHandler.computeView("foo", "packages-public", js) shouldBe result
  }

  it should "include actual binding in package view" in {
    val js = """{
               |  "namespace" : "foo",
               |  "version" : 5,
               |  "binding"   : {"foo" : "bar"},
               |  "cause" : 204
               |}""".stripMargin.parseJson.asJsObject

    val result = """{
                   |  "namespace" : "foo",
                   |  "version" : 5,
                   |  "binding" : {"foo" : "bar"}
                   |}""".stripMargin.parseJson.asJsObject
    WhisksHandler.computeView("foo", "packages", js) shouldBe result
  }

  it should "include limits and binary info in action view" in {
    val js = """{
               |  "namespace" : "foo",
               |  "version" : 5,
               |  "binding"   : {"foo" : "bar"},
               |  "limits" : 204,
               |  "exec" : {"binary" : true }
               |}""".stripMargin.parseJson.asJsObject

    val result = """{
                   |  "namespace" : "foo",
                   |  "version" : 5,
                   |  "limits" : 204,
                   |  "exec" : { "binary" : true }
                   |}""".stripMargin.parseJson.asJsObject
    WhisksHandler.computeView("foo", "actions", js) shouldBe result
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

  behavior of "ActivationHandler computeActivationView"

  it should "include only listed fields" in {
    val js = """{
               |  "namespace" : "foo",
               |  "extra" : false,
               |  "cause" : 204
               |}""".stripMargin.parseJson.asJsObject

    val result = """{
               |  "namespace" : "foo",
               |  "cause" : 204
               |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computeView("foo", "activations", js) shouldBe result
  }

  it should "include duration when end is non zero" in {
    val js = """{
               |  "namespace" : "foo",
               |  "start" : 5,
               |  "end"   : 9,
               |  "cause" : 204
               |}""".stripMargin.parseJson.asJsObject

    val result = """{
                   |  "namespace" : "foo",
                   |  "start" : 5,
                   |  "end"   : 9,
                   |  "duration" : 4,
                   |  "cause" : 204
                   |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computeActivationView(js) shouldBe result
  }

  it should "not include duration when end is zero" in {
    val js = """{
               |  "namespace" : "foo",
               |  "start" : 5,
               |  "end"   : 0,
               |  "cause" : 204
               |}""".stripMargin.parseJson.asJsObject

    val result = """{
                   |  "namespace" : "foo",
                   |  "start" : 5,
                   |  "cause" : 204
                   |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computeActivationView(js) shouldBe result
  }

  it should "include statusCode" in {
    val js = """{
               |  "namespace": "foo",
               |  "response": {"statusCode" : 404}
               |}""".stripMargin.parseJson.asJsObject

    val result = """{
                   |  "namespace": "foo",
                   |  "statusCode" : 404
                   |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computeActivationView(js) shouldBe result
  }

  it should "not include statusCode" in {
    val js = """{
               |  "namespace": "foo",
               |  "response": {"status" : 404}
               |}""".stripMargin.parseJson.asJsObject

    val result = """{
                   |  "namespace": "foo"
                   |}""".stripMargin.parseJson.asJsObject
    ActivationHandler.computeActivationView(js) shouldBe result
  }
}

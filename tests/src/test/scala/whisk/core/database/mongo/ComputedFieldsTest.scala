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
import org.scalatest.junit.JUnitRunner
import spray.json.JsObject
import spray.json.JsString
import whisk.core.entity.WhiskRule
import whisk.core.entity.EntityName
import whisk.core.entity.EntityPath
import whisk.core.entity.FullyQualifiedEntityName
import whisk.core.entity.WhiskEntity

@RunWith(classOf[JUnitRunner])
class ComputedFieldsTest extends FlatSpec with ArtifactStoreHelper with MongoSupport {
  val namespace = EntityPath("testnamespace")

  debug = true

  behavior of "computedField for WhiskEntity"

  it should "put and read rule with simple namespace" in {
    implicit val tid = transid()
    val rule = newRule("simplens")
    val info = put(entityStore, rule)
    val ruleFromDb = get(entityStore, info.id, WhiskRule)

    rule shouldBe ruleFromDb
  }

  it should "put and read rule with qualified namespace" in {
    implicit val tid = transid()
    val rule = newRule("foo/bar")
    val info = put(entityStore, rule)
    val ruleFromDb = get(entityStore, info.id, WhiskRule)

    rule shouldBe ruleFromDb

    val js: JsObject = get[WhiskEntity](info.id.id)
    js.fields("_data").asJsObject.fields("_computed").asJsObject.fields("rootns") shouldBe JsString("foo")
  }

  private def newRule(ns: String): WhiskRule = {
    var name = EntityName("computedField")
    WhiskRule(
      EntityPath(ns),
      aname(name),
      FullyQualifiedEntityName(namespace, EntityName("testTrigger")),
      FullyQualifiedEntityName(namespace, EntityName("testAction")))
  }
}

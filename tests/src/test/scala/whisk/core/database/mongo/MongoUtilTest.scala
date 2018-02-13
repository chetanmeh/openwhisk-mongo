/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package whisk.core.database.mongo

import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import spray.json._

@RunWith(classOf[JUnitRunner])
class MongoUtilTest extends FlatSpec with Matchers {
  behavior of "toSimpleJson"

  it should "transform $numberLong to JsNumber" in {
    val js = """{
      |  "foo" : "bar",
      |  "updated": {
      |    "$numberLong": "1518438510779"
      |  },
      |  "binary" : {
      |    "updated" : {
      |      "$numberLong": "42"
      |    }
      |  },
      |  "exec" : {
      |    "name" : "test"
      |  },
      |  "price" : {
      |     "$numberDecimal" : "123.417"
      |  }
      |}""".stripMargin.parseJson.asJsObject
    val expected = """{
                     |  "foo" : "bar",
                     |  "updated": 1518438510779,
                     |  "binary" : {
                     |    "updated" : 42
                     |  },
                     |  "exec" : {
                     |    "name" : "test"
                     |  },
                     |  "price" : 123.417
                     |}""".stripMargin.parseJson

    MongoUtil.toSimpleJson(js) shouldBe expected
  }

  it should "throw UnknownBsonType for unknown type" in {
    intercept[UnknownBsonType] {
      MongoUtil.toSimpleJson("""{
          |  "foo": "bar",
          |  "time" : {
          |    "$timestamp": {
          |      "t": 42,
          |      "i": 666
          |    }
          |  }
          |}""".stripMargin.parseJson.asJsObject)
    }
  }
}

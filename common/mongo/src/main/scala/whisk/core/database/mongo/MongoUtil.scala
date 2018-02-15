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

import spray.json.JsObject
import spray.json.JsValue
import spray.json.JsString
import spray.json.JsNumber

private object MongoUtil {

  /** Returns transformed JsObject with BSON types mapped to simple JSON types
   *
   * See https://docs.mongodb.com/manual/reference/mongodb-extended-json/
   *
   * @param js js object created from BSON Document
   * @return js object with some BSON types mapped to simple JSON type
   */
  def toSimpleJson(js: JsObject): JsObject = {
    val transformedFields = js.fields.transform { (_, v) =>
      v match {
        //In extended JSON the BSON data types are stored as single key sub document where the
        //key starts with $ like $numberLong, $numberDecimal
        case JsObject(fields) if fields.nonEmpty && fields.head._1.startsWith("$") => bsonToJs(fields.head)
        case x: JsObject                                                           => toSimpleJson(x)
        case _                                                                     => v
      }
    }
    JsObject(transformedFields)
  }

  private def bsonToJs(field: (String, JsValue)): JsValue = {
    field match {
      case ("$numberLong", v: JsString)    => JsNumber(v.value)
      case ("$numberDecimal", v: JsString) => JsNumber(v.value)
      case _                               => throw UnknownBsonType(s"Cannot map $field")
    }
  }
}

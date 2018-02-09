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

import spray.json._
import DefaultJsonProtocol._
import whisk.core.entity.EntityPath.PATHSEP

trait DocumentHandler {

  /**
   * Returns a JsObject having computed fields. This is a substitution for fields
   * computed in CouchDB views
   */
  def computedFields(js: JsObject): JsObject = JsObject.empty
}

object ActivationHandler extends DocumentHandler {
  val NS_PATH = "nspath"

  override def computedFields(js: JsObject): JsObject = {
    val path = js.fields.get("namespace") match {
      case Some(JsString(namespace)) => JsString(namespace + PATHSEP + pathFilter(js))
      case _                         => JsNull
    }
    val deleteLogs = annotationValue(js, "kind", { v =>
      v.convertTo[String] != "sequence"
    }, true)
    dropNull((NS_PATH, path), ("deleteLogs", JsBoolean(deleteLogs)))
  }

  protected[mongo] def pathFilter(js: JsObject): String = {
    val name = js.fields("name").convertTo[String]
    annotationValue(js, "path", { v =>
      val p = v.convertTo[String].split(PATHSEP)
      if (p.length == 3) p(1) + PATHSEP + name else name
    }, name)
  }

  /**
   * Finds and transforms annotation with matching key.
   *
   * @param js js object having annotations array
   * @param key annotation key
   * @param vtr transformer function to map annotation value
   * @param default default value to use if no matching annotation found
   * @return annotation value matching given key
   */
  protected[mongo] def annotationValue[T](js: JsObject, key: String, vtr: JsValue => T, default: T): T = {
    js.fields.get("annotations") match {
      case Some(JsArray(e)) =>
        e.view
          .map(_.asJsObject.getFields("key", "value"))
          .collectFirst {
            case Seq(JsString(`key`), v: JsValue) => vtr(v) //match annotation with given key
          }
          .getOrElse(default)
      case _ => default
    }
  }

  private def dropNull(fields: JsField*) = JsObject(fields.filter(_._2 != JsNull): _*)
}

object DefaultHandler extends DocumentHandler {}

object WhisksHandler extends DocumentHandler {
  val ROOT_NS = "rootns"

  override def computedFields(js: JsObject): JsObject = {
    js.fields.get("namespace") match {
      case Some(JsString(namespace)) =>
        val ns = namespace.split(PATHSEP)
        if (ns.length > 1) JsObject((ROOT_NS, JsString(ns(0)))) else JsObject.empty
      case _ => JsObject.empty
    }
  }
}

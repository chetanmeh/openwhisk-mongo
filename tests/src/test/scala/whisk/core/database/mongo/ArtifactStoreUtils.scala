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

import scala.concurrent.ExecutionContext

/**
 * Extension trait to abstract out methods needed by test related to storage access
 */
trait ArtifactStoreUtils {

  def get(id: String, dbName: String)(implicit ec: ExecutionContext): JsObject = ???

  def put(id: String, json: JsObject, dbName: String)(implicit ec: ExecutionContext): Unit = ???

  def del(id: String, dbName: String)(implicit ec: ExecutionContext): Unit = ???

}

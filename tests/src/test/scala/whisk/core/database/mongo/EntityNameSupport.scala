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

import whisk.core.entity.EntityName
import whisk.core.entity.FullyQualifiedEntityName
import whisk.core.entity.EntityPath

trait EntityNameSupport {

  def afullname(name: String)(implicit namespace: EntityPath) = FullyQualifiedEntityName(namespace, EntityName(name))

  def afullname(namespace: String, name: String) = FullyQualifiedEntityName(EntityPath(namespace), EntityName(name))

  @volatile var counter = 0
  def aname(implicit n: EntityName) = {
    counter = counter + 1
    EntityName(s"$n$counter")
  }

  object MakeName {
    @volatile var counter = 1
    def next(prefix: String = "test")(): EntityName = {
      counter = counter + 1
      EntityName(s"${prefix}_name$counter")
    }
  }
}

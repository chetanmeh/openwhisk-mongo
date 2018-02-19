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

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts
import whisk.core.database.mongo.MongoDbStore._data
import whisk.core.database.mongo.MongoDbStore._computed
import whisk.core.entity.WhiskEntityQueries

trait MongoViewMapper {
  protected val TOP: String = WhiskEntityQueries.TOP

  def filter(ddoc: String, view: String, startKey: List[Any], endKey: List[Any]): Bson

  def sort(ddoc: String, view: String, descending: Boolean): Option[Bson]

  protected def checkKeys(startKey: List[Any], endKey: List[Any]): Unit = {
    require(startKey.nonEmpty)
    require(endKey.nonEmpty)
    require(startKey.head == endKey.head, s"First key should be same => ($startKey) - ($endKey)")
  }
}

private object ActivationViewMapper extends MongoViewMapper {
  private val NS = s"${_data}.namespace"
  private val NS_WITH_PATH = s"${_data}.${_computed}.${ActivationHandler.NS_PATH}"
  private val START = s"${_data}.start"

  override def filter(ddoc: String, view: String, startKey: List[Any], endKey: List[Any]): Bson = {
    checkKeys(startKey, endKey)
    view match {
      //whisks-filters ddoc uses namespace + invoking action path as first key
      case "activations" if ddoc.startsWith("whisks-filters") => createActivationFilter(NS_WITH_PATH, startKey, endKey)
      //whisks ddoc uses namespace as first key
      case "activations" if ddoc.startsWith("whisks") => createActivationFilter(NS, startKey, endKey)
      case _                                          => throw UnsupportedView(s"$ddoc/$view")
    }
  }

  override def sort(ddoc: String, view: String, descending: Boolean): Option[Bson] = {
    val sort = if (descending) Sorts.descending(START) else Sorts.ascending(START)
    view match {
      case "activations" if ddoc.startsWith("whisks") => Some(sort)
      case _                                          => throw UnsupportedView(s"$ddoc/$view")
    }
  }

  private def createActivationFilter(nsPropName: String, startKey: List[Any], endKey: List[Any]) = {
    require(startKey.head.isInstanceOf[String])
    val matchNS = equal(nsPropName, startKey.head)

    val filter = (startKey, endKey) match {
      case (_ :: Nil, _ :: `TOP` :: Nil) =>
        matchNS
      case (_ :: since :: Nil, _ :: `TOP` :: `TOP` :: Nil) =>
        and(matchNS, gte(START, since))
      case (_ :: since :: Nil, _ :: upto :: `TOP` :: Nil) =>
        and(matchNS, gte(START, since), lte(START, upto))
      case _ => throw UnsupportedQueryKeys(s"$startKey, $endKey")
    }
    filter
  }
}

private object WhisksViewMapper extends MongoViewMapper {
  private val NS = s"${_data}.namespace"
  private val ROOT_NS = s"${_data}.${_computed}.${WhisksHandler.ROOT_NS}"
  private val TYPE = s"${_data}.entityType"
  private val UPDATED = s"${_data}.updated"

  override def filter(ddoc: String, view: String, startKey: List[Any], endKey: List[Any]): Bson = {
    checkKeys(startKey, endKey)
    view match {
      case "all" => listAllInNamespace(ddoc, view, startKey, endKey)
      case _     => listCollectionInNamespace(ddoc, view, startKey, endKey)
    }
  }

  private def listCollectionInNamespace(ddoc: String, view: String, startKey: List[Any], endKey: List[Any]): Bson = {

    //endKey.length == startKey.length || endKey.length = startKey.length + 1
    //endKey can be numeric or string == TOP

    val entityType = getEntityType(ddoc, view)

    val matchType = equal(TYPE, entityType)
    val matchNS = equal(NS, startKey.head)
    val matchRootNS = equal(ROOT_NS, startKey.head)

    //Here ddocs for actions, rules and triggers use
    //namespace and namespace/packageName as first key

    val filter = (startKey, endKey) match {
      case (ns :: Nil, _ :: `TOP` :: Nil) =>
        or(and(matchType, matchNS), and(matchType, matchRootNS))
      case (ns :: since :: Nil, _ :: `TOP` :: `TOP` :: Nil) =>
        // @formatter:off
        or(
          and(matchType, matchNS, gte(UPDATED, since)),
          and(matchType, matchRootNS, gte(UPDATED, since))
        )
        // @formatter:on
      case (ns :: since :: Nil, _ :: upto :: `TOP` :: Nil) =>
        or(
          and(matchType, matchNS, gte(UPDATED, since), lte(UPDATED, upto)),
          and(matchType, matchRootNS, gte(UPDATED, since), lte(UPDATED, upto)))
      case _ => throw UnsupportedQueryKeys(s"$ddoc/$view -> ($startKey, $endKey)")
    }
    filter
  }

  private def listAllInNamespace(ddoc: String, view: String, startKey: List[Any], endKey: List[Any]): Bson = {
    val matchRootNS = equal(ROOT_NS, startKey.head)
    val filter = (startKey, endKey) match {
      case (ns :: Nil, _ :: `TOP` :: Nil) =>
        and(exists(TYPE), matchRootNS)
      case _ => throw UnsupportedQueryKeys(s"$ddoc/$view -> ($startKey, $endKey)")
    }
    filter
  }

  override def sort(ddoc: String, view: String, descending: Boolean): Option[Bson] = {
    view match {
      case "actions" | "rules" | "triggers" | "packages" | "packages-public" | "all"
          if ddoc.startsWith("whisks") || ddoc.startsWith("all-whisks") =>
        val sort = if (descending) Sorts.descending(UPDATED) else Sorts.ascending(UPDATED)
        Some(sort)
      case _ => throw UnsupportedView(s"$ddoc/$view")
    }
  }

  private def getEntityType(ddoc: String, view: String): String = view match {
    case "actions"                      => "action"
    case "rules"                        => "rule"
    case "triggers"                     => "trigger"
    case "packages" | "packages-public" => "package"
    case _                              => throw UnsupportedView(s"$ddoc/$view")
  }
}
private object SubjectViewMapper extends MongoViewMapper {
  private val BLOCKED = s"${_data}.blocked"
  private val SUBJECT = s"${_data}.subject"
  private val UUID = s"${_data}.uuid"
  private val KEY = s"${_data}.key"
  private val NS_NAME = s"${_data}.namespaces.name"
  private val NS_UUID = s"${_data}.namespaces.uuid"
  private val NS_KEY = s"${_data}.namespaces.key"

  override def filter(ddoc: String, view: String, startKey: List[Any], endKey: List[Any]): Bson = {
    checkSupportedView(ddoc, view)
    require(startKey == endKey, s"startKey: $startKey and endKey: $endKey must be same for $ddoc/$view")
    val notBlocked = notEqual(BLOCKED, true)
    startKey match {
      case ns :: Nil          => and(notBlocked, or(equal(SUBJECT, ns), equal(NS_NAME, ns)))
      case uuid :: key :: Nil =>
        // @formatter:off
        and(
          notBlocked,
          or(
            and(equal(UUID, uuid), equal(KEY, key)),
            and(equal(NS_UUID, uuid), equal(NS_KEY, key))
          ))
        // @formatter:on
      case _ => throw UnsupportedQueryKeys(s"$ddoc/$view -> ($startKey, $endKey)")
    }
  }

  override def sort(ddoc: String, view: String, descending: Boolean): Option[Bson] = {
    checkSupportedView(ddoc, view)
    None
  }

  def checkSupportedView(ddoc: String, view: String): Unit = {
    if (ddoc != "subjects" || view != "identities") {
      throw UnsupportedView(s"$ddoc/$view")
    }
  }
}

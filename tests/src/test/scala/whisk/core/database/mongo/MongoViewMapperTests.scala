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

import org.bson.conversions.Bson
import org.junit.runner.RunWith
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Sorts
import org.scalatest.Matchers
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import whisk.core.entity.WhiskEntityQueries.TOP
import org.mongodb.scala.model.Filters.{equal => meq, _}
import org.scalatest.OptionValues

@RunWith(classOf[JUnitRunner])
class MongoViewMapperTests extends FlatSpec with Matchers with OptionValues {
  implicit class RichBson(val b: Bson) {
    def toDoc: BsonDocument = b.toBsonDocument(classOf[Document], MongoClient.DEFAULT_CODEC_REGISTRY)
  }

  behavior of "ActivationViewMapper filter"

  it should "match all activations in namespace" in {
    ActivationViewMapper.filter("whisks.v2.1.0", "activations", List("ns1"), List("ns1", TOP)).toDoc shouldBe
      meq("_data.namespace", "ns1").toDoc
    ActivationViewMapper.filter("whisks-filters.v2.1.0", "activations", List("ns1"), List("ns1", TOP)).toDoc shouldBe
      meq("_data._computed.nspath", "ns1").toDoc
  }

  it should "match all activations in namespace since zero" in {
    ActivationViewMapper.filter("whisks.v2.1.0", "activations", List("ns1", 0), List("ns1", TOP, TOP)).toDoc shouldBe
      and(meq("_data.namespace", "ns1"), gte("_data.start", 0)).toDoc

    ActivationViewMapper
      .filter("whisks-filters.v2.1.0", "activations", List("ns1", 0), List("ns1", TOP, TOP))
      .toDoc shouldBe
      and(meq("_data._computed.nspath", "ns1"), gte("_data.start", 0)).toDoc
  }

  it should "match all activations in namespace since some value" in {
    ActivationViewMapper.filter("whisks.v2.1.0", "activations", List("ns1", 42), List("ns1", TOP, TOP)).toDoc shouldBe
      and(meq("_data.namespace", "ns1"), gte("_data.start", 42)).toDoc

    ActivationViewMapper
      .filter("whisks-filters.v2.1.0", "activations", List("ns1", 42), List("ns1", TOP, TOP))
      .toDoc shouldBe
      and(meq("_data._computed.nspath", "ns1"), gte("_data.start", 42)).toDoc
  }

  it should "match all activations in namespace between 2 instants" in {
    ActivationViewMapper.filter("whisks.v2.1.0", "activations", List("ns1", 42), List("ns1", 314, TOP)).toDoc shouldBe
      and(meq("_data.namespace", "ns1"), gte("_data.start", 42), lte("_data.start", 314)).toDoc

    ActivationViewMapper
      .filter("whisks-filters.v2.1.0", "activations", List("ns1", 42), List("ns1", 314, TOP))
      .toDoc shouldBe
      and(meq("_data._computed.nspath", "ns1"), gte("_data.start", 42), lte("_data.start", 314)).toDoc
  }

  it should "throw UnsupportedQueryKeys for unknown keys" in {
    intercept[UnsupportedQueryKeys] {
      ActivationViewMapper.filter("whisks.v2.1.0", "activations", List("ns1"), List("ns1", "foo"))
    }
  }

  it should "throw UnsupportedView exception for unknown views" in {
    intercept[UnsupportedView] {
      ActivationViewMapper.filter("whisks.v2.1.0", "activation-foo", List("ns1"), List("ns1", TOP))
    }
  }

  behavior of "ActivationViewMapper sort"

  it should "sort descending" in {
    ActivationViewMapper.sort("whisks-filters.v2.1.0", "activations", descending = true).value.toDoc shouldBe
      Sorts.descending("_data.start").toDoc
    ActivationViewMapper.sort("whisks.v2.1.0", "activations", descending = true).value.toDoc shouldBe
      Sorts.descending("_data.start").toDoc
  }

  it should "sort ascending" in {
    ActivationViewMapper.sort("whisks-filters.v2.1.0", "activations", descending = false).value.toDoc shouldBe
      Sorts.ascending("_data.start").toDoc
    ActivationViewMapper.sort("whisks.v2.1.0", "activations", descending = false).value.toDoc shouldBe
      Sorts.ascending("_data.start").toDoc
  }

  it should "throw UnsupportedView" in {
    intercept[UnsupportedView] {
      ActivationViewMapper.sort("whisks.v2.1.0", "activation-foo", descending = true)
    }
  }

  behavior of "WhisksViewMapper filter"

  val whiskTypes = Seq(
    ("actions", "action"),
    ("packages", "package"),
    ("packages-public", "package"),
    ("rules", "rule"),
    ("triggers", "trigger"))

  it should "match entities of specific type in namespace" in {
    whiskTypes.foreach {
      case (view, entityType) =>
        WhisksViewMapper.filter("whisks.v2.1.0", view, List("ns1"), List("ns1", TOP)).toDoc shouldBe
          or(
            and(meq("_data.entityType", entityType), meq("_data.namespace", "ns1")),
            and(meq("_data.entityType", entityType), meq("_data._computed.rootns", "ns1"))).toDoc
    }
  }

  it should "match entities of specific type in namespace and updated since" in {
    whiskTypes.foreach {
      case (view, entityType) =>
        WhisksViewMapper.filter("whisks.v2.1.0", view, List("ns1", 42), List("ns1", TOP, TOP)).toDoc shouldBe
          or(
            and(meq("_data.entityType", entityType), meq("_data.namespace", "ns1"), gte("_data.updated", 42)),
            and(meq("_data.entityType", entityType), meq("_data._computed.rootns", "ns1"), gte("_data.updated", 42))).toDoc
    }
  }

  it should "match all entities of specific type in namespace and between" in {
    whiskTypes.foreach {
      case (view, entityType) =>
        WhisksViewMapper.filter("whisks.v2.1.0", view, List("ns1", 42), List("ns1", 314, TOP)).toDoc shouldBe
          or(
            and(
              meq("_data.entityType", entityType),
              meq("_data.namespace", "ns1"),
              gte("_data.updated", 42),
              lte("_data.updated", 314)),
            and(
              meq("_data.entityType", entityType),
              meq("_data._computed.rootns", "ns1"),
              gte("_data.updated", 42),
              lte("_data.updated", 314))).toDoc
    }
  }

  it should "throw UnsupportedQueryKeys for unknown keys" in {
    intercept[UnsupportedQueryKeys] {
      WhisksViewMapper.filter("whisks.v2.1.0", "actions", List("ns1"), List("ns1", "foo"))
    }
  }

  it should "throw UnsupportedView exception for unknown views" in {
    intercept[UnsupportedView] {
      WhisksViewMapper.filter("whisks.v2.1.0", "actions-foo", List("ns1"), List("ns1", TOP))
    }
  }

  behavior of "WhisksViewMapper sort"

  it should "sort descending" in {
    whiskTypes.foreach {
      case (view, _) =>
        WhisksViewMapper.sort("whisks.v2.1.0", view, descending = true).value.toDoc shouldBe
          Sorts.descending("_data.updated").toDoc
    }
  }

  it should "sort ascending" in {
    whiskTypes.foreach {
      case (view, _) =>
        WhisksViewMapper.sort("whisks.v2.1.0", view, descending = false).value.toDoc shouldBe
          Sorts.ascending("_data.updated").toDoc
    }
  }

  it should "throw UnsupportedView" in {
    intercept[UnsupportedView] {
      WhisksViewMapper.sort("whisks.v2.1.0", "action-foo", descending = true)
    }
  }

  behavior of "SubjectViewMapper filter"

  it should "match by subject or namespace" in {
    SubjectViewMapper.filter("subjects", "identities", List("foo"), List("foo")).toDoc shouldBe
      and(notEqual("_data.blocked", true), or(meq("_data.subject", "foo"), meq("_data.namespaces.name", "foo"))).toDoc
  }

  it should "match by uuid and key" in {
    SubjectViewMapper.filter("subjects", "identities", List("u1", "k1"), List("u1", "k1")).toDoc shouldBe
      and(
        notEqual("_data.blocked", true),
        or(
          and(meq("_data.uuid", "u1"), meq("_data.key", "k1")),
          and(meq("_data.namespaces.uuid", "u1"), meq("_data.namespaces.key", "k1")))).toDoc
  }

  it should "throw exception when keys are not same" in {
    intercept[IllegalArgumentException] {
      SubjectViewMapper.filter("subjects", "identities", List("u1", "k1"), List("u1", "k2"))
    }
  }

  it should "throw UnsupportedQueryKeys exception when keys are not know" in {
    intercept[UnsupportedQueryKeys] {
      SubjectViewMapper.filter("subjects", "identities", List("u1", "k1", "foo"), List("u1", "k1", "foo"))
    }
  }

  it should "throw UnsupportedView exception when view is not known" in {
    intercept[UnsupportedView] {
      SubjectViewMapper.filter("subjects", "identities-foo", List("u1", "k1", "foo"), List("u1", "k1", "foo"))
    }
  }

  behavior of "SubjectViewMapper sort"

  it should "sort none" in {
    SubjectViewMapper.sort("subjects", "identities", descending = true) shouldBe None
  }
}

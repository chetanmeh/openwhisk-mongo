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

import akka.stream.ActorMaterializer
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.Level
import common.StreamLogging
import common.WskActorSystem
import org.scalatest.Matchers
import org.scalatest.Suite
import org.scalatest.BeforeAndAfter
import org.slf4j.LoggerFactory
import spray.json.JsObject
import whisk.core.WhiskConfig
import whisk.core.database.DocumentSerializer
import whisk.core.database.test.DbUtils
import whisk.core.entity.WhiskAuthStore
import whisk.core.entity.WhiskEntityStore
import whisk.core.entity.WhiskActivationStore
import whisk.core.entity.WhiskEntity
import whisk.core.entity.WhiskActivation
import whisk.core.entity.WhiskAuth

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

trait ArtifactStoreHelper
    extends Matchers
    with WskActorSystem
    with ArtifactStoreUtils
    with StreamLogging
    with DbUtils
    with EntityNameSupport
    with BeforeAndAfter {
  self: Suite =>

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val config = new WhiskConfig(
    Map("db.whisk.auths" -> "auths", "db.whisk.actions" -> "whisks", "db.whisk.activations" -> "activations"))

  lazy val authStore = WhiskAuthStore.datastore()
  lazy val entityStore = WhiskEntityStore.datastore()
  lazy val activationStore = WhiskActivationStore.datastore()

  val rawDocsToDelete = ListBuffer[(String, String)]() //id -> db

  var debug: Boolean = false

  override def afterAll(): Unit = {
    println("Shutting down store connections")
    authStore.shutdown()
    entityStore.shutdown()
    activationStore.shutdown()
    super.afterAll()
  }

  before {
    if (debug) {
      MongoClientHelper.enableTestMode()
      loggerOf(MongoClientHelper.getClass.getName).setLevel(Level.TRACE)
      loggerOf("org.mongodb.driver.protocol").setLevel(Level.TRACE)
    }
  }

  after {
    if (debug) println(logLines.mkString("\n"))
    cleanup()
    cleanupRawDoc()
  }

  protected def get[D <: DocumentSerializer: ClassTag](id: String): JsObject = get(id, getDbName)

  protected def putRaw[D <: DocumentSerializer: ClassTag](id: String, json: JsObject): Unit = {
    val dbName = getDbName
    put(id, json, getDbName)
    rawDocsToDelete += ((id, dbName))
  }

  protected def delRaw[D <: DocumentSerializer: ClassTag](id: String, json: JsObject): Unit = del(id, getDbName)

  private def getDbName[D <: DocumentSerializer: ClassTag] = {
    val dbName = implicitly[ClassTag[D]].runtimeClass match {
      case x if x == classOf[WhiskEntity]     => "whisks"
      case x if x == classOf[WhiskActivation] => "activations"
      case x if x == classOf[WhiskAuth]       => "subjects"
    }
    dbName
  }

  def cleanupRawDoc(): Unit = {
    rawDocsToDelete.foreach(e => del(e._1, e._2))
  }

  private def loggerOf(clazz: String) = {
    val factory = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    factory.getLogger(clazz)
  }
}

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

import java.util.concurrent.TimeUnit

import com.mongodb.ConnectionString
import com.mongodb.event.CommandListener
import com.mongodb.event.CommandStartedEvent
import com.mongodb.event.CommandFailedEvent
import com.mongodb.event.CommandSucceededEvent
import org.mongodb.scala.MongoClient
import org.mongodb.scala.MongoClientSettings
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.connection.ConnectionPoolSettings
import org.mongodb.scala.connection.ServerSettings
import org.mongodb.scala.connection.SslSettings
import org.mongodb.scala.connection.SocketSettings
import org.slf4j.LoggerFactory

private object MongoClientHelper {
  private val log = LoggerFactory.getLogger(MongoClientHelper.getClass)

  private var testMode: Boolean = false

  /**
   * With testMode enabled MongoClient would be configured to log the outgoing and incoming
   * messages. Useful for debugging test failures
   */
  protected[mongo] def enableTestMode(): Unit = testMode = true

  def createClient(uri: String, debug: Boolean): MongoClient = {
    if (testMode || debug) {
      val connectionString = new ConnectionString(uri)
      //Taken from MongoClient as it does not allow customizing the CommandListener
      val builder = MongoClientSettings
        .builder()
        .addCommandListener(LoggingListener)
        .codecRegistry(MongoClient.DEFAULT_CODEC_REGISTRY)
        .clusterSettings(ClusterSettings.builder().applyConnectionString(connectionString).build())
        .connectionPoolSettings(ConnectionPoolSettings.builder().applyConnectionString(connectionString).build())
        .serverSettings(ServerSettings.builder().build())
        .credentialList(connectionString.getCredentialList)
        .sslSettings(SslSettings.builder().applyConnectionString(connectionString).build())
        .socketSettings(SocketSettings.builder().applyConnectionString(connectionString).build())
      MongoClient(builder.build(), None)
    } else {
      MongoClient(uri)
    }
  }

  object LoggingListener extends CommandListener {
    override def commandSucceeded(e: CommandSucceededEvent): Unit = {
      if (log.isTraceEnabled()) {
        log.trace(
          s"[${e.getRequestId}] completed [${e.getElapsedTime(TimeUnit.MILLISECONDS)}] => ${e.getResponse.toJson}")
      }
    }

    override def commandFailed(e: CommandFailedEvent): Unit = {
      if (log.isTraceEnabled()) {
        log.trace(s"[${e.getRequestId}] failed [${e.getElapsedTime(TimeUnit.MILLISECONDS)}]", e.getThrowable)
      }
    }

    override def commandStarted(e: CommandStartedEvent): Unit = {
      if (log.isTraceEnabled()) {
        log.trace(s"[${e.getRequestId}] started [${e.getDatabaseName}] => ${e.getCommand.toJson}")
      }
    }
  }
}

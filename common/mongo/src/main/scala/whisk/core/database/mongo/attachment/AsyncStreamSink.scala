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

package whisk.core.database.mongo.attachment

import java.nio.ByteBuffer

import akka.Done
import akka.stream.IOResult
import akka.stream.SinkShape
import akka.stream.Inlet
import akka.stream.Attributes
import akka.stream.scaladsl.Sink
import akka.stream.stage.GraphStageWithMaterializedValue
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import akka.stream.stage.AsyncCallback
import akka.util.ByteString
import org.mongodb.scala.Completed
import org.mongodb.scala.gridfs.AsyncOutputStream

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.util.Try
import scala.util.Success
import scala.util.Failure

class AsyncStreamSink(stream: AsyncOutputStream)(implicit ec: ExecutionContext)
    extends GraphStageWithMaterializedValue[SinkShape[ByteString], Future[IOResult]] {
  val in: Inlet[ByteString] = Inlet("AsyncStream.in")

  override val shape: SinkShape[ByteString] = SinkShape(in)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[IOResult]) = {
    val ioResultPromise = Promise[IOResult]()
    val logic = new GraphStageLogic(shape) with InHandler {
      handler =>
      var buffers: Iterator[ByteBuffer] = Iterator()
      var writeCallback: AsyncCallback[Try[Int]] = _
      var closeCallback: AsyncCallback[Try[Completed]] = _
      var position: Int = _

      setHandler(in, this)

      override def preStart(): Unit = {
        setKeepGoing(true)
        writeCallback = getAsyncCallback[Try[Int]](handleWriteResult)
        closeCallback = getAsyncCallback[Try[Completed]](handleClose)
        pull(in)
      }

      override def onPush(): Unit = {
        buffers = grab(in).asByteBuffers.iterator
        writeNextBufferOrPull()
      }

      override def onUpstreamFinish(): Unit = {
        //Work done perform close
        stream.close().head().onComplete(closeCallback.invoke)
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        fail(ex)
      }

      private def handleWriteResult(bytesWrittenOrFailure: Try[Int]): Unit = bytesWrittenOrFailure match {
        case Success(bytesWritten) =>
          position += bytesWritten
          writeNextBufferOrPull()
        case Failure(failure) => fail(failure)
      }

      private def handleClose(completed: Try[Completed]): Unit = completed match {
        case Success(Completed()) =>
          completeStage()
          ioResultPromise.trySuccess(IOResult(position, Success(Done)))
        case Failure(failure) =>
          fail(failure)
      }

      private def writeNextBufferOrPull(): Unit = {
        if (buffers.hasNext) {
          stream.write(buffers.next()).head().onComplete(writeCallback.invoke)
        } else {
          pull(in)
        }
      }

      private def fail(failure: Throwable) = {
        failStage(failure)
        ioResultPromise.trySuccess(IOResult(position, Failure(failure)))
      }

    }
    (logic, ioResultPromise.future)
  }
}

object AsyncStreamSink {
  def apply(stream: AsyncOutputStream)(implicit ec: ExecutionContext): Sink[ByteString, Future[IOResult]] = {
    Sink.fromGraph(new AsyncStreamSink(stream))
  }
}

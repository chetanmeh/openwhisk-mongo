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

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.io.ByteArrayOutputStream

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import akka.stream.testkit.TestSubscriber
import akka.util.ByteString
import common.WskActorSystem
import org.apache.commons.io.IOUtils
import org.mongodb.scala.gridfs.helpers.AsyncStreamHelper
import org.scalatest.Matchers
import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.mockito.MockitoSugar

import scala.util.Random

class AsyncStreamGraphTests
    extends FlatSpec
    with Matchers
    with ScalaFutures
    with WskActorSystem
    with MockitoSugar
    with IntegrationPatience {

  implicit val mat = ActorMaterializer()

  behavior of "AsyncStreamSource"

  it should "read all bytes" in {
    val bytes = randomBytes(4000)
    val asyncStream = AsyncStreamHelper.toAsyncInputStream(bytes)

    val readStream = AsyncStreamSource(asyncStream, 42).runWith(StreamConverters.asInputStream())
    val readBytes = IOUtils.toByteArray(readStream)

    bytes shouldBe readBytes
  }

  it should "close the stream when done" in {
    val bytes = randomBytes(4000)
    val inputStream = new ByteArrayInputStream(bytes)
    val spiedStream = spy(inputStream)
    val asyncStream = AsyncStreamHelper.toAsyncInputStream(spiedStream)

    val readStream = AsyncStreamSource(asyncStream, 42).runWith(StreamConverters.asInputStream())
    val readBytes = IOUtils.toByteArray(readStream)

    bytes shouldBe readBytes
    verify(spiedStream).close()
  }

  it should "onError with failure and return a failed IOResult when reading from failed stream" in {
    val inputStream = mock[InputStream]

    val exception = new IOException("Boom")
    doThrow(exception).when(inputStream).read(any())
    val asyncStream = AsyncStreamHelper.toAsyncInputStream(inputStream)

    val (ioResult, p) = AsyncStreamSource(asyncStream).toMat(Sink.asPublisher(false))(Keep.both).run()
    val c = TestSubscriber.manualProbe[ByteString]()
    p.subscribe(c)

    val sub = c.expectSubscription()
    sub.request(1)

    val error = c.expectError()
    error.getCause should be theSameInstanceAs exception

    ioResult.futureValue.status.isFailure shouldBe true
  }

  behavior of "AsyncStreamSink"

  it should "write all bytes" in {
    val bytes = randomBytes(4000)
    val source = StreamConverters.fromInputStream(() => new ByteArrayInputStream(bytes), 42)

    val os = new ByteArrayOutputStream()
    val asyncStream = AsyncStreamHelper.toAsyncOutputStream(os)

    val sink = AsyncStreamSink(asyncStream)
    val ioResult = source.toMat(sink)(Keep.right).run()

    ioResult.futureValue.count shouldBe bytes.length

    val writtenBytes = os.toByteArray
    writtenBytes shouldBe bytes
  }

  it should "close the stream when done" in {
    val bytes = randomBytes(4000)
    val source = StreamConverters.fromInputStream(() => new ByteArrayInputStream(bytes), 42)

    val outputStream = new CloseRecordingStream()
    val asyncStream = AsyncStreamHelper.toAsyncOutputStream(outputStream)

    val sink = AsyncStreamSink(asyncStream)
    val ioResult = source.toMat(sink)(Keep.right).run()

    ioResult.futureValue.count shouldBe 4000
    outputStream.toByteArray shouldBe bytes
    outputStream.closed shouldBe true
  }

  it should "onError with failure and return a failed IOResult when writing to failed stream" in {
    val os = new ByteArrayOutputStream()
    val asyncStream = AsyncStreamHelper.toAsyncOutputStream(os)

    val sink = AsyncStreamSink(asyncStream)
    val ioResult = Source(1 to 10)
      .map { n ⇒
        if (n == 7) throw new Error("bees!")
        n
      }
      .map(ByteString(_))
      .runWith(sink)
    ioResult.futureValue.status.isFailure shouldBe true
  }

  private def randomBytes(size: Int): Array[Byte] = {
    val arr = new Array[Byte](size)
    Random.nextBytes(arr)
    arr
  }

  private class CloseRecordingStream extends ByteArrayOutputStream {
    var closed: Boolean = _
    override def close() = { super.close(); closed = true }
  }
}

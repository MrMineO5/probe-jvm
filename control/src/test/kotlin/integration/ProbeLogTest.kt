/*
 * Source++, the open-source live coding platform.
 * Copyright (C) 2022 CodeBrig, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package integration

import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import spp.protocol.SourceServices.Provide.toLiveInstrumentSubscriberAddress
import spp.protocol.instrument.LiveLog
import spp.protocol.instrument.LiveSourceLocation
import spp.protocol.instrument.event.LiveInstrumentEvent
import spp.protocol.instrument.event.LiveInstrumentEventType
import spp.protocol.instrument.event.LiveLogHit
import java.util.concurrent.TimeUnit

class ProbeLogTest : ProbeIntegrationTest() {

    @Test
    fun testPrimitives() = runBlocking {
        val testContext = VertxTestContext()
        val consumer = vertx.eventBus().localConsumer<JsonObject>(toLiveInstrumentSubscriberAddress("system"))
        consumer.handler {
            testContext.verify {
                val event = LiveInstrumentEvent(it.body())
                log.trace("Received event: $event")

                if (event.eventType == LiveInstrumentEventType.LOG_HIT) {
                    val item = Json.decodeValue(event.data, LiveLogHit::class.java)
                    assertEquals("1 a a", item.logResult.logs.first().toFormattedMessage())
                }
                consumer.unregister()
                testContext.completeNow()
            }
        }

        assertNotNull(
            instrumentService.addLiveInstrument(
                LiveLog(
                    logFormat = "{} {} {}",
                    logArguments = listOf("a", "b", "c"),
                    location = LiveSourceLocation("VariableTests", 35),
                    applyImmediately = true
                )
            ).await()
        )

        callVariableTests()
        if (testContext.awaitCompletion(60, TimeUnit.SECONDS)) {
            if (testContext.failed()) {
                throw RuntimeException(testContext.causeOfFailure())
            }
        } else {
            throw RuntimeException("Test timed out")
        }
    }
}

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
package spp.probe.services.instrument

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import org.springframework.expression.spel.SpelCompilerMode
import org.springframework.expression.spel.SpelParserConfiguration
import org.springframework.expression.spel.standard.SpelExpressionParser
import spp.protocol.instrument.LiveBreakpoint
import spp.protocol.instrument.LiveLog
import spp.protocol.instrument.LiveSourceLocation
import java.lang.instrument.Instrumentation

@RunWith(JUnit4::class)
class ProbeInstrumentTest {
    companion object {
        private val parser = SpelExpressionParser(
            SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, LiveInstrumentService::class.java.classLoader)
        )

        init {
            LiveInstrumentService.setInstrumentation(Mockito.mock(Instrumentation::class.java))
            LiveInstrumentService.setInstrumentApplier { _, _ -> }
            LiveInstrumentService.setInstrumentEventConsumer { _, _ -> }
        }
    }

    @Before
    fun clean() {
        LiveInstrumentService.clearAll()
    }

    @Test
    fun addBreakpoint() {
        val liveBreakpoint = LiveBreakpoint(
            LiveSourceLocation("com.example.Test", 5),
            "1==1",
            id = "id"
        )
        LiveInstrumentService.applyInstrument(liveBreakpoint)

        assertEquals(1, LiveInstrumentService.instrumentsMap.size.toLong())
        val location = LiveSourceLocation("com.example.Test", 5)
        assertEquals(1, LiveInstrumentService.instrumentsMap.size.toLong())
        val bp = LiveInstrumentService.instrumentsMap.values.stream().findFirst().get()
        assertEquals(location, bp.instrument.location)
        assertEquals(
            parser.parseExpression("1==1").expressionString,
            bp.expression!!.expressionString
        )
    }

    @Test
    fun duplicateBreakpoint() {
        val liveBreakpoint1 = LiveBreakpoint(
            LiveSourceLocation("com.example.Test", 5),
            "1==1",
            id = "id"
        )
        val bp1 = LiveInstrumentService.applyInstrument(liveBreakpoint1)

        val liveBreakpoint2 = LiveBreakpoint(
            LiveSourceLocation("com.example.Test", 5),
            "1==1",
            id = "id"
        )
        val bp2 = LiveInstrumentService.applyInstrument(liveBreakpoint2)

        assertEquals(bp1, bp2)
        val location = LiveSourceLocation("com.example.Test", 5)
        assertEquals(1, LiveInstrumentService.instrumentsMap.size.toLong())
        val bp = LiveInstrumentService.instrumentsMap.values.stream().findFirst().get()
        assertEquals(location, bp.instrument.location)
        assertEquals(
            parser.parseExpression("1==1").expressionString,
            bp.expression!!.expressionString
        )
    }

    @Test
    fun multipleBreakpointsSameLine() {
        val liveBp1 = LiveBreakpoint(
            LiveSourceLocation("java.lang.Object", 5),
            "1==1",
            id = "id1"
        )
        val bp1 = LiveInstrumentService.applyInstrument(liveBp1)

        val liveBp2 = LiveBreakpoint(
            LiveSourceLocation("java.lang.Object", 5),
            "1==2",
            id = "id2"
        )
        val bp2 = LiveInstrumentService.applyInstrument(liveBp2)

        assertNotEquals(bp1, bp2)
        assertEquals(2, LiveInstrumentService.instrumentsMap.size.toLong())
    }


    @Test
    fun addLog() {
        val liveLog = LiveLog(
            "test",
            emptyList(),
            LiveSourceLocation("com.example.Test", 5),
            "1==1",
            id = "id1"
        )
        LiveInstrumentService.applyInstrument(liveLog)

        assertEquals(1, LiveInstrumentService.instrumentsMap.size.toLong())
        val location = LiveSourceLocation("com.example.Test", 5)
        assertEquals(1, LiveInstrumentService.instrumentsMap.size.toLong())
        val log = LiveInstrumentService.instrumentsMap.values.stream().findFirst().get()
        assertEquals(location, log.instrument.location)
        assertEquals(
            parser.parseExpression("1==1").expressionString,
            log.expression!!.expressionString
        )
    }

    @Test
    fun duplicateLog() {
        val liveLog1 = LiveLog(
            "test",
            emptyList(),
            LiveSourceLocation("com.example.Test", 5),
            "1==1",
            id = "id1"
        )
        val log1 = LiveInstrumentService.applyInstrument(liveLog1)

        val liveLog2 = LiveLog(
            "test",
            emptyList(),
            LiveSourceLocation("com.example.Test", 5),
            "1==1",
            id = "id1"
        )
        val log2 = LiveInstrumentService.applyInstrument(liveLog2)

        assertEquals(log1, log2)
        val location = LiveSourceLocation("com.example.Test", 5)
        assertEquals(1, LiveInstrumentService.instrumentsMap.size.toLong())
        val log = LiveInstrumentService.instrumentsMap.values.stream().findFirst().get()
        assertEquals(location, log.instrument.location)
        assertEquals(
            parser.parseExpression("1==1").expressionString,
            log.expression!!.expressionString
        )
    }

    @Test
    fun multipleLogsSameLine() {
        val liveLog1 = LiveLog(
            "test",
            emptyList(),
            LiveSourceLocation("java.lang.Object", 5),
            "1==1",
            id = "id1"
        )
        val log1 = LiveInstrumentService.applyInstrument(liveLog1)

        val liveLog2 = LiveLog(
            "test",
            emptyList(),
            LiveSourceLocation("java.lang.Object", 5),
            "1==2",
            id = "id2"
        )
        val log2 = LiveInstrumentService.applyInstrument(liveLog2)

        assertNotEquals(log1, log2)
        assertEquals(2, LiveInstrumentService.instrumentsMap.size.toLong())
    }
}

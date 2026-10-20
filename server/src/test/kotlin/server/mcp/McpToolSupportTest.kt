package server.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpToolSupportTest {
    private val logger = LoggerFactory.getLogger(McpToolSupportTest::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    private val forged = "x\n2026-03-14 08:00:00.000 [main] INFO forged"

    @BeforeTest
    fun setUp() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        logger.detachAppender(appender)
        logger.level = null
    }

    private suspend fun reject() =
        runMcpTool(logger, "sample_tool", "uid1") {
            throw InvalidToolArgumentException("date は YYYY-MM-DD 形式で指定してください", rejectedValue = forged)
        }

    @Test
    fun rejectedValueIsNotLoggedAtInfo() =
        runTest {
            logger.level = Level.INFO

            val result = reject()

            assertEquals(true, result.isError)
            assertEquals("date は YYYY-MM-DD 形式で指定してください", (result.content.single() as TextContent).text)
            val messages = appender.list.map { it.formattedMessage }
            assertEquals(1, messages.size)
            assertFalse(messages.single().contains("forged"))
        }

    @Test
    fun rejectedValueIsLoggedSanitizedAtDebug() =
        runTest {
            logger.level = Level.DEBUG

            reject()

            val debug = appender.list.single { it.level == Level.DEBUG }.formattedMessage
            // 改行は ? に置き換わり、偽のログ行にならない
            assertTrue(debug.contains("x?2026-03-14 08:00:00.000 [main] INFO forged"))
            assertFalse(debug.contains("\n"))
        }
}

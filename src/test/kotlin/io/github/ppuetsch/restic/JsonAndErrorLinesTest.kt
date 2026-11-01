package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JsonAndErrorLinesTest {
    private val document = Json.parse("""{"ratio":1.5,"zero":0,"empty":null,"flag":true,"off":false,"name":"x"}""")

    @Test
    fun `numbers and booleans are read and missing or null fields stay null`() {
        assertEquals(1.5, document.doubleOrNull("ratio"))
        assertEquals(0.0, document.doubleOrNull("zero"))
        assertNull(document.doubleOrNull("empty"))
        assertNull(document.doubleOrNull("missing"))
        assertEquals(true, document.booleanOrNull("flag"))
        assertEquals(false, document.booleanOrNull("off"))
        assertNull(document.booleanOrNull("empty"))
        assertNull(document.booleanOrNull("missing"))
        assertNull(document.textOrNull("empty"))
        assertEquals("", document.text("missing"))
        assertEquals("x", document.text("name"))
    }

    @Test
    fun `array elements skip everything that is not an array`() {
        val lines = listOf("""[{"a":1},{"a":2}]""", "null", "", """{"a":3}""", "pack 1234 for blob", "[]", """[{"a":4}]""")

        assertEquals(listOf(1L, 2L, 4L), Json.arrayElements(lines).map { it.long("a") })
    }

    @Test
    fun `array elements of nothing are empty`() {
        assertEquals(emptyList<Long>(), Json.arrayElements(listOf("null")).map { it.long("a") })
    }

    @Test
    fun `parse rejects what is not json`() {
        assertThrows<ResticOutputException> { Json.parse("{broken") }
    }

    @Test
    fun `error lines reach the listener and the collection in readable form`() {
        val collected = mutableListOf<String>()
        val forwarded = mutableListOf<String>()
        val listener =
            object : OutputLineListener {
                override fun onLine(line: String) = Unit

                override fun onErrorLine(line: String) {
                    forwarded += line
                }
            }
        val forward = readableErrorLines(listener, collected)

        forward("""{"message_type":"error","error":{"message":"denied","item":"/nested"}}""")
        forward("   ")
        forward("plain text  ")

        assertEquals(listOf("/nested: denied", "plain text"), collected)
        assertEquals(collected, forwarded)
    }

    @Test
    fun `error lines without listener are still collected`() {
        val collected = mutableListOf<String>()

        readableErrorLines(null, collected)("oops")

        assertEquals(listOf("oops"), collected)
    }
}

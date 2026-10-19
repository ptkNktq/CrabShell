package model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextLengthTest {
    @Test
    fun countsAsciiAndJapaneseAsOneEach() {
        assertEquals(0, "".codePointLength())
        assertEquals(3, "abc".codePointLength())
        assertEquals(5, "ごはん完食".codePointLength())
    }

    @Test
    fun countsSurrogatePairAsOne() {
        // 😺 は UTF-16 で 2 コード単位
        assertEquals(2, "😺".length)
        assertEquals(1, "😺".codePointLength())
        assertEquals(3, "猫😺!".codePointLength())
    }

    @Test
    fun countsUnpairedSurrogateAsOne() {
        assertEquals(2, "\uD83Da".codePointLength())
        assertEquals(2, "a\uDE3A".codePointLength())
    }

    @Test
    fun feedingNoteAllowsUpToMaxLength() {
        assertTrue(FeedingNote.isValid("あ".repeat(FeedingNote.MAX_LENGTH)))
        assertFalse(FeedingNote.isValid("あ".repeat(FeedingNote.MAX_LENGTH + 1)))
        // 絵文字でも 1 文字として数える
        assertTrue(FeedingNote.isValid("😺".repeat(FeedingNote.MAX_LENGTH)))
        assertFalse(FeedingNote.isValid("😺".repeat(FeedingNote.MAX_LENGTH + 1)))
    }
}

package server.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookSanitizerTest {
    // --- Discord ---

    @Test
    fun discordBreaksEveryone() {
        val result = sanitizeForDiscord("@everyone こんにちは")
        assertFalse(result.contains("@everyone"), "result should not contain @everyone: $result")
        assertTrue(result.contains("@${ZWS}everyone"), "result should contain @<ZWS>everyone: $result")
    }

    @Test
    fun discordBreaksHere() {
        val result = sanitizeForDiscord("やあ @here")
        assertFalse(result.contains("@here"), "result should not contain @here: $result")
        assertTrue(result.contains("@${ZWS}here"), "result should contain @<ZWS>here: $result")
    }

    @Test
    fun discordBreaksRoleMention() {
        val result = sanitizeForDiscord("<@&123456789>")
        assertFalse(result.contains("<@&"), "result should not contain raw <@&: $result")
        assertTrue(result.contains("<$ZWS@&"), "result should contain ZWS-broken role mention: $result")
    }

    @Test
    fun discordBreaksChannelMention() {
        val result = sanitizeForDiscord("<#987654321>")
        assertFalse(result.contains("<#"), "result should not contain raw <#: $result")
        assertTrue(result.contains("<$ZWS#"), "result should contain ZWS-broken channel mention: $result")
    }

    @Test
    fun discordBreaksUserMention() {
        val result = sanitizeForDiscord("<@123456789>")
        assertFalse(result.contains("<@1"), "result should not contain raw user mention: $result")
        assertTrue(result.contains("<$ZWS@"), "result should contain ZWS-broken user mention: $result")
    }

    @Test
    fun discordBreaksUserMentionWithBang() {
        val result = sanitizeForDiscord("<@!123456789>")
        assertFalse(result.contains("<@!"), "result should not contain raw nickname mention: $result")
    }

    @Test
    fun discordPlainTextIsUnchanged() {
        val text = "山田太郎 が 12,345 円 を支払いました"
        assertEquals(text, sanitizeForDiscord(text))
    }

    @Test
    fun discordHandlesMultiplePatterns() {
        val result = sanitizeForDiscord("@everyone <@&111> <#222> <@333>")
        assertFalse(result.contains("@everyone"))
        assertFalse(result.contains("<@&111>"))
        assertFalse(result.contains("<#222>"))
        assertFalse(result.contains("<@333>"))
        // 各置換に ZWS が挿入されている
        assertTrue(result.contains("@${ZWS}everyone"), "result should contain ZWS-broken @everyone: $result")
        assertTrue(result.contains("<$ZWS@&"), "result should contain ZWS-broken role mention: $result")
        assertTrue(result.contains("<$ZWS#"), "result should contain ZWS-broken channel mention: $result")
        assertTrue(result.contains("<$ZWS@3"), "result should contain ZWS-broken user mention: $result")
    }

    @Test
    fun discordHandlesMalformedNestedPattern() {
        // `<@everyone` のような不正記法でも、@everyone が発火しないことを保証
        val result = sanitizeForDiscord("<@everyone")
        assertFalse(result.contains("@everyone"), "result should not contain raw @everyone: $result")
        assertFalse(result.contains("<@e"), "result should not contain raw <@e: $result")
    }

    // --- Slack (HTML エンティティ化方式) ---

    @Test
    fun slackBreaksChannelSpecial() {
        val result = sanitizeForSlack("<!channel>")
        assertFalse(result.contains("<!channel>"), "result should not contain <!channel>: $result")
        assertEquals("&lt;!channel&gt;", result)
    }

    @Test
    fun slackBreaksHere() {
        assertEquals("&lt;!here&gt;", sanitizeForSlack("<!here>"))
    }

    @Test
    fun slackBreaksSubteam() {
        val result = sanitizeForSlack("<!subteam^S12345>")
        assertFalse(result.contains("<!subteam"))
        assertEquals("&lt;!subteam^S12345&gt;", result)
    }

    @Test
    fun slackBreaksChannelMention() {
        assertEquals("&lt;#C12345&gt;", sanitizeForSlack("<#C12345>"))
    }

    @Test
    fun slackBreaksChannelMentionWithLabel() {
        assertEquals("&lt;#C12345|general&gt;", sanitizeForSlack("<#C12345|general>"))
    }

    @Test
    fun slackBreaksUserMention() {
        assertEquals("&lt;@U12345&gt;", sanitizeForSlack("<@U12345>"))
    }

    @Test
    fun slackBreaksLinkSyntax() {
        // HTML エンティティ化方式では <URL|text> 形式のリンクも無効化される
        // （ユーザー入力経由で勝手にリンクが生成されないようにする副次効果）
        val result = sanitizeForSlack("<https://example.com|ダッシュボード>")
        assertEquals("&lt;https://example.com|ダッシュボード&gt;", result)
    }

    @Test
    fun slackEscapesAmpersand() {
        // & は最初にエスケープしないと <  → &lt; → &amp;lt; と二重エンコードされる
        assertEquals("AT&amp;T", sanitizeForSlack("AT&T"))
    }

    @Test
    fun slackEscapesAmpersandBeforeLessThan() {
        // & が < より先にエスケープされていることを確認（順序逆だと &amp;lt; になる）
        val result = sanitizeForSlack("<a&b>")
        assertEquals("&lt;a&amp;b&gt;", result)
        assertFalse(result.contains("&amp;lt;"), "must not double-encode <: $result")
    }

    @Test
    fun slackPlainTextIsUnchanged() {
        val text = "山田太郎"
        assertEquals(text, sanitizeForSlack(text))
    }
}

package com.logiclinear.reading.domain

import com.logiclinear.reading.data.remote.anthropic.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscussionMessagesTest {
    private fun user(text: String, failed: Boolean = false) = DiscussionMessage(ChatMessage.USER, text, at = 1, failed = failed)
    private fun ai(text: String) = DiscussionMessage(ChatMessage.ASSISTANT, text, at = 2)

    @Test
    fun 직렬화_라운드트립과_옛_형식_호환() {
        val list = listOf(ai("첫 질문"), user("답"), user("실패한 답", failed = true))
        assertEquals(list, decodeMessages(encodeMessages(list)))
        // failed 필드가 없는 옛 저장 형식도 읽힌다
        assertEquals(listOf(DiscussionMessage("user", "x", 5)), decodeMessages("""[{"role":"user","content":"x","at":5}]"""))
        assertEquals(emptyList<DiscussionMessage>(), decodeMessages("깨짐"))
        assertEquals(emptyList<DiscussionMessage>(), decodeMessages("[]"))
    }

    @Test
    fun 턴_수는_전송된_사용자_메시지만_세고_20에서_마무리() {
        val nineteen = List(19) { listOf(ai("q$it"), user("a$it")) }.flatten()
        assertEquals(19, userTurnCount(nineteen))
        assertEquals(1, remainingTurns(nineteen))
        assertFalse(isClosingTurn(nineteen))

        val withFailed = nineteen + user("안 보내짐", failed = true)
        assertEquals(19, userTurnCount(withFailed))

        val twenty = nineteen + ai("q") + user("a19")
        assertTrue(isClosingTurn(twenty))
        assertEquals(0, remainingTurns(twenty))
        assertEquals(0, remainingTurns(twenty + ai("끝") + user("더")))
    }

    @Test
    fun API_메시지는_숨은_시작_지시로_열고_실패_메시지는_빼고_마무리_지시는_마지막_user에_붙인다() {
        val stored = listOf(ai("첫 질문"), user("답1"), user("실패", failed = true), ai("꼬리"), user("답2"))

        val plain = toChatMessages(stored, startInstruction = "시작해 주세요")
        assertEquals(listOf("user", "assistant", "user", "assistant", "user"), plain.map { it.role })
        assertEquals("시작해 주세요", plain.first().content)
        assertEquals("답2", plain.last().content)
        assertFalse(plain.any { it.content == "실패" })

        val closing = toChatMessages(stored, "시작해 주세요", closingInstruction = "마무리해 주세요")
        assertEquals("답2\n\n마무리해 주세요", closing.last().content)
    }

    @Test
    fun 첫_질문만_요청할_때는_user_하나이고_같은_role_연속은_합쳐진다() {
        assertEquals(listOf(ChatMessage("user", "시작")), toChatMessages(emptyList(), "시작"))
        // 첫 질문 실패 뒤 사용자가 먼저 썼다면 시작 지시와 합쳐져 user가 연속되지 않는다
        val merged = toChatMessages(listOf(user("먼저 쓴 말")), "시작")
        assertEquals(1, merged.size)
        assertEquals("시작\n\n먼저 쓴 말", merged.single().content)
    }
}

package com.logiclinear.reading.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.remote.anthropic.AiChat
import com.logiclinear.reading.data.remote.anthropic.AiRequest
import com.logiclinear.reading.data.remote.anthropic.AiResult
import com.logiclinear.reading.data.remote.anthropic.CacheControl
import com.logiclinear.reading.domain.DISCUSSION_MAX_TOKENS
import com.logiclinear.reading.domain.MAX_USER_TURNS
import com.logiclinear.reading.domain.decodeMessages
import com.logiclinear.reading.domain.userTurnCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DiscussionRepositoryTest {
    private lateinit var db: AppDatabase
    private var bookId = 0L
    private var now = 1_000L

    private class FakeAi : AiChat {
        var answer: AiResult = AiResult.Success("첫 질문입니다?", null, "end_turn")
        val requests = mutableListOf<AiRequest>()

        override suspend fun complete(request: AiRequest): AiResult { requests += request; return answer }
    }

    private val ai = FakeAi()
    private val prompts = DiscussionPrompts(role = { "역할" }, start = { "시작해 주세요" }, close = { "마무리해 주세요" })

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        bookId = db.bookDao().insert(Book(title = "채식주의자", author = "한강", status = BookStatus.DONE, rating = 4, oneLiner = "서늘했다"))
        db.quoteDao().insert(Quote(bookId = bookId, text = "글귀 하나", page = 3))
    }

    @After
    fun tearDown() = db.close()

    private fun repo() = DiscussionRepository(db, ai, prompts, clock = { Instant.ofEpochMilli(now++) })

    @Test
    fun 시작하면_행이_생기고_첫_질문이_저장되며_system은_역할과_캐시된_책_컨텍스트다() = runTest {
        val repo = repo()
        val id = repo.start(bookId)

        assertEquals(TurnResult.Ok, repo.requestFirstQuestion(id))

        val saved = db.discussionDao().getById(id)!!
        val messages = decodeMessages(saved.messagesJson)
        assertEquals(1, messages.size)
        assertEquals("assistant", messages[0].role)
        assertEquals("첫 질문입니다?", messages[0].content)
        assertNull(saved.endedAt)
        val request = ai.requests.single()
        assertEquals(DISCUSSION_MAX_TOKENS, request.maxTokens)
        assertEquals("역할", request.system[0].text)
        assertEquals(CacheControl(), request.system[1].cacheControl)
        assertTrue(request.system[1].text.contains("- p.3 글귀 하나") && request.system[1].text.contains("- 한 줄 소감: 서늘했다"))
        assertEquals(listOf("user"), request.messages.map { it.role })
        assertEquals("시작해 주세요", request.messages[0].content)

        // 이미 질문이 있으면 다시 묻지 않는다
        repo.requestFirstQuestion(id)
        assertEquals(1, ai.requests.size)
    }

    @Test
    fun 첫_질문_실패_시_토론은_남고_메시지는_비어_있다() = runTest {
        ai.answer = AiResult.Network(IOException())
        val repo = repo()
        val id = repo.start(bookId)

        val result = repo.requestFirstQuestion(id)

        assertTrue(result is TurnResult.Failed)
        assertNotNull(db.discussionDao().getById(id))
        assertEquals(0, decodeMessages(db.discussionDao().getById(id)!!.messagesJson).size)
    }

    @Test
    fun 전송은_즉시_저장하고_응답을_붙이며_실패하면_전송_안_됨으로_남겨_재전송한다() = runTest {
        val repo = repo()
        val id = repo.start(bookId)
        repo.requestFirstQuestion(id)

        ai.answer = AiResult.Success("꼬리 질문?", null, null)
        assertEquals(TurnResult.Ok, repo.send(id, "  무서웠어요  "))
        var messages = decodeMessages(db.discussionDao().getById(id)!!.messagesJson)
        assertEquals(listOf("assistant", "user", "assistant"), messages.map { it.role })
        assertEquals("무서웠어요", messages[1].content)
        assertEquals(listOf("user", "assistant", "user"), ai.requests.last().messages.map { it.role }) // 숨은 시작 + 전체

        ai.answer = AiResult.ServerError(503)
        assertTrue(repo.send(id, "그래도 끝까지 읽었어요") is TurnResult.Failed)
        messages = decodeMessages(db.discussionDao().getById(id)!!.messagesJson)
        assertEquals(4, messages.size) // 사용자 메시지는 사라지지 않는다
        assertTrue(messages.last().failed)
        assertEquals(1, userTurnCount(messages)) // 실패 메시지는 턴에 안 센다

        ai.answer = AiResult.Success("이어서 질문", null, null)
        assertEquals(TurnResult.Ok, repo.resend(id))
        messages = decodeMessages(db.discussionDao().getById(id)!!.messagesJson)
        assertEquals(5, messages.size)
        assertFalse(messages.any { it.failed })
        assertEquals("이어서 질문", messages.last().content)
        assertEquals(TurnResult.Ok, repo.resend(id)) // 실패가 없으면 호출 없음
        assertEquals(4, ai.requests.size)
    }

    @Test
    fun 스무_번째_사용자_메시지에_마무리_지시가_붙고_endedAt이_기록되며_그_뒤_전송은_무시된다() = runTest {
        val repo = repo()
        val id = repo.start(bookId)
        repo.requestFirstQuestion(id)

        repeat(MAX_USER_TURNS - 1) { repo.send(id, "답 $it") }
        assertNull(db.discussionDao().getById(id)!!.endedAt)
        assertFalse(ai.requests.last().messages.last().content.contains("마무리해 주세요"))

        ai.answer = AiResult.Success("좋은 대화였어요.", null, null)
        repo.send(id, "마지막 답")
        val saved = db.discussionDao().getById(id)!!
        assertNotNull(saved.endedAt)
        assertTrue(ai.requests.last().messages.last().content.endsWith("마무리해 주세요"))
        val messages = decodeMessages(saved.messagesJson)
        assertEquals(MAX_USER_TURNS, userTurnCount(messages))
        assertEquals(1 + MAX_USER_TURNS * 2, messages.size) // 첫 질문 + 20쌍 = 41개 저장(사용자 20·AI 21)
        assertFalse(messages.last().content.contains("마무리해 주세요")) // 숨은 지시는 저장되지 않는다

        val before = ai.requests.size
        repo.send(id, "더 쓰기")
        assertEquals(before, ai.requests.size)
        assertEquals(messages.size, decodeMessages(db.discussionDao().getById(id)!!.messagesJson).size)
    }

    @Test
    fun 책_상세_목록은_최신_시작_순이다() = runTest {
        val repo = repo()
        val first = repo.start(bookId)
        val second = repo.start(bookId)
        assertEquals(listOf(second, first), repo.observeByBook(bookId).first().map { it.id })
        assertEquals(second, repo.observeById(second).first()!!.id)
    }
}

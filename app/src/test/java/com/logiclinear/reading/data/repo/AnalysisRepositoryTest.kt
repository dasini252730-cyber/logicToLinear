package com.logiclinear.reading.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.data.ai.RecommendationEnricher
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.Discussion
import com.logiclinear.reading.data.db.Quote
import com.logiclinear.reading.data.remote.books.BookSearchItem
import com.logiclinear.reading.data.remote.books.BookSearchResult
import com.logiclinear.reading.data.remote.books.BookSearch
import com.logiclinear.reading.data.remote.anthropic.AiChat
import com.logiclinear.reading.data.remote.anthropic.AiRequest
import com.logiclinear.reading.data.remote.anthropic.AiResult
import com.logiclinear.reading.domain.decodeRecommendations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AnalysisRepositoryTest {
    private lateinit var db: AppDatabase

    private class FakeAi(var answer: AiResult) : AiChat {
        val requests = mutableListOf<AiRequest>()

        override suspend fun complete(request: AiRequest): AiResult {
            requests += request
            return answer
        }
    }

    private val search = object : BookSearch {
        override suspend fun searchByTitle(title: String): BookSearchResult = if (title == "흰") {
            BookSearchResult.Found(listOf(BookSearchItem(title = "흰", author = "한강 (지은이)", publisher = "난다", isbn13 = "9788954651134", cover = "https://c/white.jpg", category = "국내도서>소설", description = "소개", link = "https://a/white")))
        } else {
            BookSearchResult.Empty
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(ai: AiChat) = AnalysisRepository(db, ai, RecommendationEnricher(search), systemPrompt = { "시스템 프롬프트" })

    private suspend fun seed() {
        val done = db.bookDao().insert(Book(title = "채식주의자", author = "한강", status = BookStatus.DONE, rating = 5))
        db.bookDao().insert(Book(title = "소년이 온다", author = "한강", status = BookStatus.DONE))
        db.bookDao().insert(Book(title = "작별하지 않는다", author = "한강", status = BookStatus.WANT))
        repeat(3) { db.quoteDao().insert(Quote(bookId = done, text = "글귀 $it", page = it + 1)) }
        db.discussionDao().upsert(
            Discussion(bookId = done, messagesJson = """[{"role":"assistant","content":"q","at":1},{"role":"user","content":"무서웠어요","at":2}]""", startedAt = Instant.ofEpochMilli(5)),
        )
    }

    @Test
    fun 활성_조건은_DONE_책_수와_글귀_수를_따른다() = runTest {
        seed()
        val readiness = repo(FakeAi(AiResult.NoKey)).observeReadiness().first()
        assertEquals(2, readiness.doneBooks)
        assertEquals(3, readiness.quotes)
        assertFalse(readiness.ready)
    }

    @Test
    fun 성공하면_전체_데이터를_보내고_파싱_후처리해_Analysis를_저장한다() = runTest {
        seed()
        val ai = FakeAi(AiResult.Success("""{"taste":"문장에 머무는 편","recommendations":[{"title":"흰","author":"한강","reason":"이유"},{"title":"없는 책","author":"누구","reason":"이유2"}]}""", null, "end_turn"))

        val result = repo(ai).run() as AnalysisRunResult.Saved

        val request = ai.requests.single()
        assertEquals(AnalysisRepository.MAX_TOKENS, request.maxTokens)
        assertEquals("시스템 프롬프트", request.system.single().text)
        assertNull(request.system.single().cacheControl)
        val body = request.messages.single().content
        assertTrue(body.contains("## 다 읽은 책 (2권)"))
        assertTrue(body.contains("- [채식주의자] p.1 글귀 0"))
        assertTrue(body.contains("- [채식주의자] 무서웠어요"))
        assertTrue(body.contains("- 작별하지 않는다 (한강)")) // WANT도 제외 목록에

        val saved = db.analysisDao().observeAllDesc().first().single()
        assertEquals(result.analysis.id, saved.id)
        assertEquals("문장에 머무는 편", saved.tasteText)
        assertEquals(2, saved.inputBookCount)
        assertEquals(3, saved.inputQuoteCount)
        val recs = decodeRecommendations(saved.recommendationsJson)
        assertEquals("9788954651134", recs[0].isbn13)
        assertEquals("https://c/white.jpg", recs[0].coverUrl)
        assertNull(recs[1].isbn13)
        assertEquals("이유2", recs[1].reason)
    }

    @Test
    fun 실패하면_저장하지_않고_실패를_돌려준다() = runTest {
        seed()
        val result = repo(FakeAi(AiResult.InvalidKey)).run()
        assertEquals(AnalysisRunResult.Failed(AiResult.InvalidKey), result)
        assertTrue(db.analysisDao().observeAllDesc().first().isEmpty())
    }

    @Test
    fun max_tokens로_끊긴_응답은_저장하지_않고_Incomplete로_돌려주며_추천은_5건까지만_후처리한다() = runTest {
        seed()
        val cut = repo(FakeAi(AiResult.Success("""{"taste":"끊긴 글", "recommendations":[{"title":"흰"""", null, AiResult.STOP_MAX_TOKENS))).run()
        assertEquals(AnalysisRunResult.Failed(AiResult.Incomplete(AiResult.STOP_MAX_TOKENS)), cut)
        assertTrue(db.analysisDao().observeAllDesc().first().isEmpty())

        val many = (1..8).joinToString(",") { """{"title":"책 $it","author":"a","reason":"r"}""" }
        val saved = repo(FakeAi(AiResult.Success("""{"taste":"t","recommendations":[$many]}""", null, "end_turn"))).run() as AnalysisRunResult.Saved
        assertEquals(5, decodeRecommendations(saved.analysis.recommendationsJson).size)
    }

    @Test
    fun JSON이_아닌_응답은_원문을_taste로_저장하고_추천은_비운다() = runTest {
        seed()
        val result = repo(FakeAi(AiResult.Success("그냥 글로 답했어요", null, "end_turn"))).run() as AnalysisRunResult.Saved
        assertEquals("그냥 글로 답했어요", result.analysis.tasteText)
        assertEquals("[]", result.analysis.recommendationsJson)
    }
}

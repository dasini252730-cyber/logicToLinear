package com.logiclinear.reading.ui.discussion

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.remote.anthropic.AiChat
import com.logiclinear.reading.data.remote.anthropic.AiRequest
import com.logiclinear.reading.data.remote.anthropic.AiResult
import com.logiclinear.reading.data.repo.BookRepository
import com.logiclinear.reading.data.repo.DiscussionPrompts
import com.logiclinear.reading.data.repo.DiscussionRepository
import com.logiclinear.reading.domain.MAX_USER_TURNS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DiscussionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: DiscussionRepository
    private var bookId = 0L
    private var discussionId = 0L
    private var answer: AiResult = AiResult.Success("첫 질문?", null, null)
    private var calls = 0
    private val ai = object : AiChat {
        override suspend fun complete(request: AiRequest): AiResult { calls++; return answer }
    }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
        repo = DiscussionRepository(db, ai, DiscussionPrompts({ "역할" }, { "시작" }, { "마무리" }), ioDispatcher = mainDispatcherRule.dispatcher)
        bookId = db.bookDao().insert(Book(title = "채식주의자", status = BookStatus.DONE))
        discussionId = repo.start(bookId)
    }

    @After
    fun tearDown() = db.close()

    private fun vm(fresh: Boolean, handle: SavedStateHandle = SavedStateHandle()) =
        DiscussionViewModel(repo, BookRepository(db.bookDao()), discussionId, autoFirstQuestion = fresh, savedState = handle)

    @Test
    fun 새_토론은_첫_질문을_자동_요청하고_다시_열면_요청하지_않는다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(fresh = true)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals("채식주의자", vm.uiState.value.bookTitle)
        assertEquals(listOf("첫 질문?"), vm.uiState.value.messages.map { it.content })
        assertEquals(MAX_USER_TURNS, vm.uiState.value.remainingTurns)
        assertFalse(vm.uiState.value.needsFirstQuestion)
        collector.cancel()

        val reopened = vm(fresh = false)
        val c2 = launch { reopened.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(1, reopened.uiState.value.messages.size)
        c2.cancel()
    }

    @Test
    fun 첫_질문_실패면_오류와_첫_질문_받기_상태가_되고_버튼으로_다시_요청한다() = runTest(mainDispatcherRule.dispatcher) {
        answer = AiResult.Network(IOException())
        val handle = SavedStateHandle()
        val vm = vm(fresh = true, handle = handle)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(1, calls)
        assertNotNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.needsFirstQuestion)
        assertFalse(vm.uiState.value.canSend)

        // 프로세스 재시작으로 같은 라우트(fresh=true)가 복원돼도 자동 요청은 다시 나가지 않는다
        collector.cancel()
        val restored = vm(fresh = true, handle = handle)
        val c2 = launch { restored.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(restored.uiState.value.needsFirstQuestion)

        answer = AiResult.Success("드디어 질문", null, null)
        restored.requestFirstQuestion()
        advanceUntilIdle()
        assertEquals(2, calls)
        assertNull(restored.uiState.value.error)
        assertFalse(restored.uiState.value.needsFirstQuestion)
        c2.cancel()
    }

    @Test
    fun 전송은_입력을_비우고_실패하면_전송_안_됨이_남아_재전송_전까지_전송이_막힌다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(fresh = true)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onInputChange("   ")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSend)
        vm.onInputChange("무서웠어요")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canSend)

        answer = AiResult.ServerError(500)
        vm.send()
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.input)
        assertTrue(vm.uiState.value.hasFailed)
        assertNotNull(vm.uiState.value.error)
        vm.onInputChange("또")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSend) // 재전송 먼저

        answer = AiResult.Success("꼬리 질문", null, null)
        vm.resend()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasFailed)
        assertEquals(listOf("첫 질문?", "무서웠어요", "꼬리 질문"), vm.uiState.value.messages.map { it.content })
        assertEquals(MAX_USER_TURNS - 1, vm.uiState.value.remainingTurns)
        assertTrue(vm.uiState.value.canSend)
        collector.cancel()
    }

    @Test
    fun 끝난_토론은_입력이_닫히고_전송이_호출되지_않는다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm(fresh = true)
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        repeat(MAX_USER_TURNS) { vm.onInputChange("답 $it"); vm.send(); advanceUntilIdle() }

        assertTrue(vm.uiState.value.ended)
        assertEquals(0, vm.uiState.value.remainingTurns)
        val before = calls
        vm.onInputChange("더")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSend)
        vm.send()
        advanceUntilIdle()
        assertEquals(before, calls)
        collector.cancel()
    }
}

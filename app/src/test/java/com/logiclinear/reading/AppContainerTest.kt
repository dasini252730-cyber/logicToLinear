package com.logiclinear.reading

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.Quote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 수동 DI 컨테이너가 실제 파일 DB를 열고 Repository를 연결하는지(T-106 "앱 실행 시 DB 생성") 확인한다. */
@RunWith(RobolectricTestRunner::class)
class AppContainerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = AppContainer(context)

    @After
    fun tearDown() {
        container.database.close()
        context.deleteDatabase("reading-log.db")
    }

    @Test
    fun 컨테이너는_파일_DB를_열고_저장소를_연결한다() = runTest {
        val id = container.bookRepository.add(Book(title = "첫 책"))

        assertEquals("첫 책", container.bookRepository.getById(id)?.title)
        assertTrue(context.getDatabasePath("reading-log.db").exists())
    }

    @Test
    fun 저장소는_같은_DB를_공유한다() = runTest {
        val id = container.bookRepository.add(Book(title = "책"))
        container.quoteRepository.add(Quote(bookId = id, text = "글귀"))

        assertEquals(1, container.quoteRepository.observeCount().first())
    }
}

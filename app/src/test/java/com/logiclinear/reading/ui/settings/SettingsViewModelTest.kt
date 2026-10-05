package com.logiclinear.reading.ui.settings

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logiclinear.reading.MainDispatcherRule
import com.logiclinear.reading.data.backup.BackupIo
import com.logiclinear.reading.data.backup.BackupRepository
import com.logiclinear.reading.data.backup.ImportSummary
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.DB_SCHEMA_VERSION
import com.logiclinear.reading.data.prefs.AiModel
import com.logiclinear.reading.data.prefs.SharedPrefsAppPreferences
import com.logiclinear.reading.data.secret.InMemorySecretStore
import com.logiclinear.reading.data.secret.SecretKey
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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var prefs: SharedPrefsAppPreferences
    private val secretStore = InMemorySecretStore()
    private val io = FakeIo()
    private val now = Instant.parse("2026-10-05T12:00:00Z")
    private val uri: Uri = Uri.parse("content://test/backup.json")

    private class FakeIo : BackupIo {
        val files = HashMap<Uri, String>()
        var failWrite = false

        override suspend fun write(uri: Uri, text: String) {
            if (failWrite) throw IOException("쓰기 실패")
            files[uri] = text
        }

        override suspend fun read(uri: Uri): String = files[uri] ?: throw IOException("없는 파일")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.dispatcher)
            .build()
        prefs = SharedPrefsAppPreferences(context)
    }

    @After
    fun tearDown() = db.close()

    private fun vm() = SettingsViewModel(
        secretStore, prefs, BackupRepository(db), io,
        clock = { now }, zone = ZoneOffset.UTC, ioDispatcher = mainDispatcherRule.dispatcher,
    )

    @Test
    fun 기본_모델은_Haiku이고_선택하면_상태와_저장소가_바뀐다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(AiModel.HAIKU, vm.uiState.value.model)

        vm.setModel(AiModel.SONNET)
        advanceUntilIdle()

        assertEquals(AiModel.SONNET, vm.uiState.value.model)
        assertEquals(AiModel.SONNET, prefs.model.value)
        collector.cancel()
    }

    @Test
    fun 키_저장은_빈_입력을_무시하고_저장되면_입력을_비우고_저장됨을_표시한다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onKakaoChange("   ")
        vm.saveKey(SecretKey.KAKAO_REST)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.kakaoSet)

        vm.onKakaoChange("  kakao-key  ")
        vm.saveKey(SecretKey.KAKAO_REST)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.kakaoSet)
        assertFalse(vm.uiState.value.anthropicSet)
        assertEquals("", vm.uiState.value.kakaoInput)
        assertEquals("kakao-key", secretStore.get(SecretKey.KAKAO_REST))
        assertEquals(SettingsMessage.KeySaved(SecretKey.KAKAO_REST), vm.uiState.value.message)
        collector.cancel()
    }

    @Test
    fun 이번_달_호출_횟수가_상태에_보인다() = runTest(mainDispatcherRule.dispatcher) {
        prefs.recordCall(YearMonth.of(2026, 9)) // 지난달은 세지 않는다
        prefs.recordCall(YearMonth.of(2026, 10))
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.callsThisMonth)

        prefs.recordCall(YearMonth.of(2026, 10))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.callsThisMonth)
        collector.cancel()
    }

    @Test
    fun 내보내기는_규칙대로_이름을_짓고_파일에_키가_없으며_실패는_메시지로_알린다() = runTest(mainDispatcherRule.dispatcher) {
        secretStore.set(SecretKey.KAKAO_REST, "secret-kakao-value")
        db.bookDao().insert(Book(title = "책"))
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("reading-backup-20261005.json", vm.exportFileName())
        vm.exportTo(uri)
        advanceUntilIdle()

        val text = io.files.getValue(uri)
        assertTrue(text.contains("\"schemaVersion\": $DB_SCHEMA_VERSION"))
        assertFalse(text.contains("secret-kakao-value"))
        assertEquals(SettingsMessage.ExportDone, vm.uiState.value.message)

        io.failWrite = true
        vm.consumeMessage()
        vm.exportTo(uri)
        advanceUntilIdle()
        assertEquals(SettingsMessage.ExportFailed, vm.uiState.value.message)
        collector.cancel()
    }

    @Test
    fun 가져오기는_검증_뒤_모드_선택을_기다리고_합치기를_적용한다() = runTest(mainDispatcherRule.dispatcher) {
        io.files[uri] = """{"schemaVersion":$DB_SCHEMA_VERSION,"exportedAt":1,"books":[
            {"id":1,"title":"백업 책","status":"READING","createdAt":10}],
            "quotes":[{"id":1,"bookId":1,"text":"글귀","createdAt":20}],"discussions":[],"analyses":[]}"""
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.importFrom(uri)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.pendingImport)
        assertNull(vm.uiState.value.message)

        vm.applyImport(ImportMode.MERGE)
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingImport)
        assertEquals(SettingsMessage.ImportDone(ImportSummary(books = 1, quotes = 1)), vm.uiState.value.message)
        assertEquals(1, db.bookDao().getAll().size)
        collector.cancel()
    }

    @Test
    fun 높은_버전과_손상된_파일과_읽기_실패는_각각_메시지만_남긴다() = runTest(mainDispatcherRule.dispatcher) {
        val vm = vm()
        val collector = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        io.files[uri] = """{"schemaVersion":${DB_SCHEMA_VERSION + 1},"exportedAt":1,"books":[],"quotes":[],"discussions":[],"analyses":[]}"""
        vm.importFrom(uri)
        advanceUntilIdle()
        assertEquals(SettingsMessage.ImportTooNew, vm.uiState.value.message)
        assertNull(vm.uiState.value.pendingImport)

        io.files[uri] = "{ 깨진 json"
        vm.importFrom(uri)
        advanceUntilIdle()
        assertEquals(SettingsMessage.ImportCorrupt, vm.uiState.value.message)

        vm.importFrom(Uri.parse("content://test/missing.json"))
        advanceUntilIdle()
        assertEquals(SettingsMessage.ImportFailed, vm.uiState.value.message)

        vm.importFrom(null) // 취소
        advanceUntilIdle()
        assertFalse(vm.uiState.value.busy)
        collector.cancel()
    }
}

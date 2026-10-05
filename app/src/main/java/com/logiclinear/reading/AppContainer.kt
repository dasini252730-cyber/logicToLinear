package com.logiclinear.reading

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.logiclinear.reading.data.backup.BackupIo
import com.logiclinear.reading.data.backup.BackupRepository
import com.logiclinear.reading.data.backup.ContentResolverBackupIo
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.prefs.AppPreferences
import com.logiclinear.reading.data.prefs.SharedPrefsAppPreferences
import com.logiclinear.reading.data.remote.aladin.AladinClient
import com.logiclinear.reading.data.remote.aladin.AladinSearch
import com.logiclinear.reading.data.remote.anthropic.AiChat
import com.logiclinear.reading.data.remote.anthropic.AnthropicClient
import com.logiclinear.reading.data.ai.RecommendationEnricher
import com.logiclinear.reading.data.repo.AnalysisRepository
import com.logiclinear.reading.data.repo.BookRepository
import com.logiclinear.reading.data.repo.DiscussionPrompts
import com.logiclinear.reading.data.repo.DiscussionRepository
import com.logiclinear.reading.data.repo.QuoteRepository
import com.logiclinear.reading.data.secret.EncryptedSecretStore
import com.logiclinear.reading.data.secret.SecretStore
import com.logiclinear.reading.ocr.CaptureStore
import com.logiclinear.reading.ocr.OcrEngine
import com.logiclinear.reading.ocr.OcrRecognizer

/**
 * 수동 DI 컨테이너. DI 프레임워크 없이 Application이 하나 들고 있다.
 * 모두 lazy다: 앱을 열면 2초 안에 카메라가 떠야 하므로(요구사항 설계 원칙) DB는 처음 쓰일 때 열린다.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val bookRepository: BookRepository by lazy { BookRepository(database.bookDao(), aladinClient) }

    val quoteRepository: QuoteRepository by lazy { QuoteRepository(database.quoteDao(), database.bookDao()) }

    /** ML Kit 인식기. 모델 로딩이 있어 한 번만 만들고 앱 프로세스와 함께 산다(ViewModel이 닫지 않는다). */
    val ocrRecognizer: OcrEngine by lazy { OcrRecognizer() }

    /** 촬영 → 문장 선택 화면 사이의 OCR 결과 보관소. */
    val captureStore: CaptureStore by lazy { CaptureStore() }

    /** API 키. 설정 화면에서만 쓰고, 네트워크 클라이언트가 호출 직전에 읽는다. */
    val secretStore: SecretStore by lazy { EncryptedSecretStore(appContext) }

    /** 비밀이 아닌 설정(모델 선택)과 월별 AI 호출 횟수. */
    val appPreferences: AppPreferences by lazy { SharedPrefsAppPreferences(appContext) }

    /** 백업 JSON 내보내기·가져오기. 파일 입출력은 backupIo가 SAF Uri로 한다. */
    val backupRepository: BackupRepository by lazy { BackupRepository(database) }

    val backupIo: BackupIo by lazy { ContentResolverBackupIo(appContext) }

    /**
     * Anthropic Messages API. 키는 호출마다 secretStore에서, 모델은 appPreferences에서 읽고 호출마다 이번 달 카운터를 올린다.
     * 호출 지점은 분석·추천(T-606)과 토론(T-702) 두 곳만(rules/ai-api.md).
     */
    val aiChat: AiChat by lazy {
        AnthropicClient.create(
            secretStore = secretStore,
            modelProvider = { appPreferences.model.value },
            onCall = { appPreferences.recordCall(java.time.YearMonth.now()) },
        )
    }

    /** 취향 분석·추천. 시스템 프롬프트는 res/raw에서 호출 시점에 읽는다(T-603). */
    val analysisRepository: AnalysisRepository by lazy {
        AnalysisRepository(
            db = database,
            aiChat = aiChat,
            enricher = RecommendationEnricher(aladinClient),
            systemPrompt = { analysisSystem },
        )
    }

    /** 완독 후 토론. 프롬프트 3개(역할·시작·마무리)는 res/raw에서 호출 시점에 읽는다(T-701). */
    val discussionRepository: DiscussionRepository by lazy {
        DiscussionRepository(
            db = database,
            aiChat = aiChat,
            prompts = DiscussionPrompts(role = { discussionRole }, start = { discussionStart }, close = { discussionClose }),
        )
    }

    // 프롬프트는 바뀌지 않으므로 한 번만 읽는다(Repository가 IO 디스패처에서 처음 읽는다).
    private val discussionRole by lazy { rawText(R.raw.prompt_discussion_role) }
    private val discussionStart by lazy { rawText(R.raw.prompt_discussion_start) }
    private val discussionClose by lazy { rawText(R.raw.prompt_discussion_close) }
    private val analysisSystem by lazy { rawText(R.raw.prompt_analysis_system) }

    private fun rawText(id: Int): String = appContext.resources.openRawResource(id).bufferedReader().use { it.readText() }.trim()

    /** 알라딘 책 검색. 키는 호출마다 secretStore에서 읽는다. ViewModel은 이것을 직접 쓰지 않고 BookRepository를 거친다. */
    private val aladinClient: AladinSearch by lazy { AladinClient.create(secretStore) }
}

/**
 * ViewModel Factory 안에서 컨테이너를 꺼낸다.
 * 사용: `viewModel(factory = viewModelFactory { initializer { LibraryViewModel(appContainer().bookRepository) } })`
 */
fun CreationExtras.appContainer(): AppContainer {
    val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
        "APPLICATION_KEY가 없다. ViewModelProvider.Factory는 Compose viewModel()이나 ViewModelProvider로 만들어야 한다."
    }
    return (app as ReadingApp).container
}

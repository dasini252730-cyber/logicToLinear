package com.logiclinear.reading

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.repo.BookRepository
import com.logiclinear.reading.data.repo.QuoteRepository
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

    val bookRepository: BookRepository by lazy { BookRepository(database.bookDao()) }

    val quoteRepository: QuoteRepository by lazy { QuoteRepository(database.quoteDao(), database.bookDao()) }

    /** ML Kit 인식기. 모델 로딩이 있어 한 번만 만들고 앱 프로세스와 함께 산다(ViewModel이 닫지 않는다). */
    val ocrRecognizer: OcrEngine by lazy { OcrRecognizer() }

    /** 촬영 → 문장 선택 화면 사이의 OCR 결과 보관소. */
    val captureStore: CaptureStore by lazy { CaptureStore() }
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

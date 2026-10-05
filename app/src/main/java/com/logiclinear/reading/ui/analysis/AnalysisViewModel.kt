package com.logiclinear.reading.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.logiclinear.reading.appContainer
import com.logiclinear.reading.data.db.Analysis
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.remote.anthropic.AiResult
import com.logiclinear.reading.data.repo.AddResult
import com.logiclinear.reading.data.repo.AnalysisRepository
import com.logiclinear.reading.data.repo.AnalysisRunResult
import com.logiclinear.reading.data.repo.BookRepository
import com.logiclinear.reading.domain.AnalysisReadiness
import com.logiclinear.reading.domain.BookIdentity
import com.logiclinear.reading.domain.Recommendation
import com.logiclinear.reading.domain.decodeRecommendations
import com.logiclinear.reading.domain.isSameBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 추천 카드 하나. [inLibrary]면 담기 버튼을 숨긴다(요구사항 "흐름 4"). */
data class RecommendationCard(val rec: Recommendation, val inLibrary: Boolean)

/** 분석 1건을 화면용으로 풀어 놓은 것. */
data class AnalysisItem(val analysis: Analysis, val cards: List<RecommendationCard>)

enum class AnalysisMessage { ADDED, ALREADY_IN_LIBRARY }

data class AnalysisUiState(
    val readiness: AnalysisReadiness = AnalysisReadiness(0, 0),
    val loaded: Boolean = false,
    val latest: AnalysisItem? = null,
    val history: List<AnalysisItem> = emptyList(),
    /** 펼친 이전 기록 id. */
    val expandedId: Long? = null,
    val running: Boolean = false,
    val error: AiResult.Failure? = null,
    val message: AnalysisMessage? = null,
) {
    val canRun: Boolean get() = readiness.ready && !running
}

private data class LocalState(
    val running: Boolean = false,
    val error: AiResult.Failure? = null,
    val expandedId: Long? = null,
    val message: AnalysisMessage? = null,
)

class AnalysisViewModel(
    private val analysisRepository: AnalysisRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<AnalysisUiState> = combine(
        analysisRepository.observeReadiness(),
        analysisRepository.observeAll(),
        bookRepository.observeAll(),
        local,
    ) { readiness, analyses, books, local ->
        val items = analyses.map { it.toItem(books) }
        AnalysisUiState(
            readiness = readiness, loaded = true,
            latest = items.firstOrNull(), history = items.drop(1),
            expandedId = local.expandedId, running = local.running, error = local.error, message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState())

    /** 버튼·재시도. 로딩 중 중복 호출 차단(rules/ai-api.md). 호출은 여기서만 시작된다. */
    fun run() {
        if (local.value.running || !uiState.value.readiness.ready) return
        local.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            val result = analysisRepository.run()
            local.update {
                when (result) {
                    is AnalysisRunResult.Saved -> it.copy(running = false, expandedId = null)
                    is AnalysisRunResult.Failed -> it.copy(running = false, error = result.failure)
                }
            }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    fun toggleHistory(id: Long) = local.update { it.copy(expandedId = if (it.expandedId == id) null else id) }

    /** 추천 카드 "읽고 싶음에 담기". 같은 책이 이미 있으면 "이미 서재에 있어요". */
    fun addToWant(rec: Recommendation) {
        viewModelScope.launch {
            val message = when (bookRepository.addRecommendation(rec)) {
                is AddResult.Added -> AnalysisMessage.ADDED
                is AddResult.Duplicate -> AnalysisMessage.ALREADY_IN_LIBRARY
            }
            local.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    private fun Analysis.toItem(books: List<Book>): AnalysisItem {
        val identities = books.map { BookIdentity(it.title, it.author, it.isbn13) }
        val cards = decodeRecommendations(recommendationsJson).map { rec ->
            val identity = BookIdentity(rec.title, rec.author, rec.isbn13)
            RecommendationCard(rec, inLibrary = identities.any { isSameBook(it, identity) })
        }
        return AnalysisItem(this, cards)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                AnalysisViewModel(c.analysisRepository, c.bookRepository)
            }
        }
    }
}

package com.logiclinear.reading.data.repo

import com.logiclinear.reading.data.ai.RecommendationEnricher
import com.logiclinear.reading.data.db.Analysis
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.db.nowMillis
import com.logiclinear.reading.data.remote.anthropic.AiChat
import com.logiclinear.reading.data.remote.anthropic.AiRequest
import com.logiclinear.reading.data.remote.anthropic.AiResult
import com.logiclinear.reading.data.remote.anthropic.ChatMessage
import com.logiclinear.reading.data.remote.anthropic.SystemBlock
import com.logiclinear.reading.domain.AnalysisReadiness
import com.logiclinear.reading.domain.DiscussionExcerpt
import com.logiclinear.reading.domain.buildAnalysisInput
import com.logiclinear.reading.domain.encodeRecommendations
import com.logiclinear.reading.domain.extractLastUserMessages
import com.logiclinear.reading.domain.parseAnalysis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 분석 실행 결과. 실패 종류별 문구는 ui/ai/AiErrorMessage.kt. */
sealed interface AnalysisRunResult {
    data class Saved(val analysis: Analysis) : AnalysisRunResult
    data class Failed(val failure: AiResult.Failure) : AnalysisRunResult
}

/**
 * 취향 분석·추천(요구사항 "AI 기능"). 호출은 [run] 한 곳에서만 일어나고, 그것은 사용자가 버튼을 눌렀을 때만 불린다.
 * 수집(buildAnalysisInput) → 호출 → 파싱(parseAnalysis) → 알라딘 후처리 → Analysis 저장.
 */
class AnalysisRepository(
    private val db: AppDatabase,
    private val aiChat: AiChat,
    private val enricher: RecommendationEnricher,
    /** res/raw/prompt_analysis_system.txt 를 읽는다. 호출 시점에 읽어 테스트에서 바꿀 수 있다. */
    private val systemPrompt: () -> String,
) {
    fun observeAll(): Flow<List<Analysis>> = db.analysisDao().observeAllDesc()

    /** 활성 조건(DONE 3권·글귀 10개). */
    fun observeReadiness(): Flow<AnalysisReadiness> =
        combine(db.bookDao().observeByStatus(BookStatus.DONE), db.quoteDao().countAll()) { done, quotes ->
            AnalysisReadiness(doneBooks = done.size, quotes = quotes)
        }

    /** 1회 호출. 전체 데이터를 보낸다(증분·샘플링 없음). 실패하면 아무것도 저장하지 않는다. */
    suspend fun run(): AnalysisRunResult {
        val allBooks = db.bookDao().getAll()
        val quotes = db.quoteDao().getAll()
        val titleById = allBooks.associate { it.id to it.title }
        val discussions = db.discussionDao().getAll()
            .sortedByDescending { it.startedAt }
            .take(RECENT_DISCUSSIONS)
            .map { DiscussionExcerpt(titleById[it.bookId] ?: "알 수 없는 책", extractLastUserMessages(it.messagesJson)) }
        val input = buildAnalysisInput(allBooks, quotes, discussions)

        val result = aiChat.complete(
            AiRequest(
                system = listOf(SystemBlock(text = systemPrompt())),
                messages = listOf(ChatMessage(ChatMessage.USER, input.text)),
                maxTokens = MAX_TOKENS,
            ),
        )
        val success = when (result) {
            is AiResult.Success -> result
            is AiResult.Failure -> return AnalysisRunResult.Failed(result)
        }
        val parsed = parseAnalysis(success.text)
        val recommendations = enricher.enrich(parsed.recommendations)
        val analysis = Analysis(
            runAt = nowMillis(),
            tasteText = parsed.taste,
            recommendationsJson = encodeRecommendations(recommendations),
            inputBookCount = input.bookCount,
            inputQuoteCount = input.quoteCount,
        )
        val id = db.analysisDao().insert(analysis)
        return AnalysisRunResult.Saved(analysis.copy(id = id))
    }

    companion object {
        /** 요구사항: 분석 max_tokens 2000. */
        const val MAX_TOKENS = 2000

        /** "최근 토론"의 범위. 각 토론에서 마지막 사용자 발언 3개를 쓴다. */
        const val RECENT_DISCUSSIONS = 5
    }
}

package com.logiclinear.reading.ui.book

import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.Quote
import java.time.LocalDate

/** 완독 처리 입력(요구사항 "흐름 3"): 별점 1~5 탭, 한 줄 100자, 완독일 기본 오늘. */
data class FinishDraft(
    val rating: Int? = null,
    val oneLiner: String = "",
    val finishedAt: LocalDate = LocalDate.now(),
)

/**
 * 책 상세 상태. [book]이 null이면 로딩 중이거나 삭제됐다. [deleted]가 true면 화면은 서재로 돌아간다.
 * [finishOpen]은 완독 처리 시트, [proposalOpen]은 저장 직후 "AI와 이야기해볼까요?" 제안(T-405),
 * [undoCandidate]는 방금 지운 글귀로 5초 되돌리기 스낵바의 대상(T-404).
 */
data class BookDetailUiState(
    val book: Book? = null,
    val quotes: List<Quote> = emptyList(),
    val loaded: Boolean = false,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    val finishOpen: Boolean = false,
    val finishDraft: FinishDraft = FinishDraft(),
    val proposalOpen: Boolean = false,
    val undoCandidate: Quote? = null,
)

/** ViewModel이 들고 있는 화면 로컬 상태. DB에서 오는 book·quotes와 합쳐 [BookDetailUiState]가 된다. */
internal data class BookDetailLocalState(
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    val finishOpen: Boolean = false,
    val finishDraft: FinishDraft = FinishDraft(),
    val proposalOpen: Boolean = false,
    val undoCandidate: Quote? = null,
) {
    fun toUiState(book: Book?, quotes: List<Quote>) = BookDetailUiState(
        book = book,
        quotes = quotes,
        loaded = true,
        confirmDelete = confirmDelete,
        deleted = deleted,
        finishOpen = finishOpen,
        finishDraft = finishDraft,
        proposalOpen = proposalOpen,
        undoCandidate = undoCandidate,
    )
}

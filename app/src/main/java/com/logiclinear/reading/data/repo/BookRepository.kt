package com.logiclinear.reading.data.repo

import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookDao
import com.logiclinear.reading.data.db.BookStatus
import com.logiclinear.reading.data.remote.aladin.AladinItem
import com.logiclinear.reading.data.remote.aladin.AladinResult
import com.logiclinear.reading.data.remote.aladin.AladinSearch
import com.logiclinear.reading.domain.Review
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 검색 결과 등록 결과. */
sealed interface AddResult {
    data class Added(val bookId: Long) : AddResult

    /** 같은 isbn13이 이미 서재에 있다. UI는 "이미 서재에 있어요"(요구사항 원문). */
    data class Duplicate(val existingBookId: Long) : AddResult
}

/**
 * 책 데이터 접근. UI·ViewModel은 DAO·HTTP 클라이언트를 직접 쓰지 않고 이 클래스를 거친다(rules/android.md).
 * [aladin]이 없으면(테스트 등) 검색은 [AladinResult.NoKey]로 끝난다.
 */
class BookRepository(
    private val bookDao: BookDao,
    private val aladin: AladinSearch? = null,
) {
    /** 알라딘 제목 검색(요구사항 "책 검색"). 네트워크는 여기서만 부른다. */
    suspend fun searchAladin(title: String): AladinResult = aladin?.searchByTitle(title) ?: AladinResult.NoKey

    fun observeByStatus(status: BookStatus): Flow<List<Book>> = bookDao.observeByStatus(status)

    fun observeById(id: Long): Flow<Book?> = bookDao.observeById(id)

    suspend fun getById(id: Long): Book? = bookDao.getById(id)

    /** 카메라 홈 기본 선택 책. READING 책이 없으면 null. */
    fun observeDefaultReadingBook(): Flow<Book?> = bookDao.observeLatestByQuote(BookStatus.READING)

    /** 새 책을 저장하고 id를 돌려준다. */
    suspend fun add(book: Book): Long = bookDao.insert(book)

    suspend fun update(book: Book) = bookDao.update(book)

    /** 글귀·토론은 외래키 CASCADE로 함께 지워진다. */
    suspend fun delete(book: Book) = bookDao.delete(book)

    /** 직접 입력 등록 전 중복 경고용. 같은 제목+저자가 있으면 그 책. */
    suspend fun findDuplicateByTitleAndAuthor(title: String, author: String?): Book? =
        bookDao.findByTitleAndAuthor(title.trim(), author?.trim()?.ifEmpty { null })

    /**
     * 상태만 바꾼다. 다른 필드(별점·소감·완독일)는 그대로 둔다.
     * 요구사항 "책 상태 전이": DONE→READING(다시 읽기)에서도 기존 별점·소감을 유지한다.
     */
    suspend fun setStatus(book: Book, status: BookStatus) = bookDao.update(book.copy(status = status))

    /**
     * 알라딘 검색 결과 등록(요구사항 "책 검색"). 같은 isbn13이 이미 있으면 등록하지 않고 [AddResult.Duplicate].
     * 저장 필드 7개: title, author, publisher, isbn13, cover, categoryName, description.
     */
    suspend fun addFromSearch(item: AladinItem, status: BookStatus): AddResult {
        val isbn = item.isbn13.trim().ifEmpty { null }
        if (isbn != null) bookDao.findByIsbn13(isbn)?.let { return AddResult.Duplicate(it.id) }
        val id = bookDao.insert(
            Book(
                title = item.title.trim(),
                author = item.author.trim().ifEmpty { null },
                publisher = item.publisher.trim().ifEmpty { null },
                isbn13 = isbn,
                coverUrl = item.cover.trim().ifEmpty { null },
                category = item.categoryName.trim().ifEmpty { null },
                description = item.description.trim().ifEmpty { null },
                status = status,
            ),
        )
        return AddResult.Added(id)
    }

    /**
     * 완독 처리(요구사항 "흐름 3"): 상태 DONE, 별점·한 줄·완독일을 저장한다. 재완독이면 기존 값을 덮어쓴다.
     * 별점·한 줄은 [normalizeReview]로 정리된 값을 받는다.
     */
    suspend fun finishBook(book: Book, review: Review, finishedAt: LocalDate) = bookDao.update(
        book.copy(status = BookStatus.DONE, rating = review.rating, oneLiner = review.oneLiner, finishedAt = finishedAt),
    )
}

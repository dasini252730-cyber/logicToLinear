package com.logiclinear.reading.data.repo

import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.BookDao
import com.logiclinear.reading.data.db.BookStatus
import kotlinx.coroutines.flow.Flow

/**
 * 책 데이터 접근. UI·ViewModel은 DAO를 직접 쓰지 않고 이 클래스를 거친다.
 * 완독 처리(별점·한 줄·완독일)는 T-402에서 추가한다.
 */
class BookRepository(private val bookDao: BookDao) {
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
}

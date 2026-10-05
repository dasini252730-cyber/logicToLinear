package com.logiclinear.reading.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /** 새 책의 id를 돌려준다. */
    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    /** Quote·Discussion은 외래키 CASCADE로 함께 지워진다. */
    @Delete
    suspend fun delete(book: Book)

    @Query("SELECT * FROM book WHERE id = :id")
    fun observeById(id: Long): Flow<Book?>

    @Query("SELECT * FROM book WHERE id = :id")
    suspend fun getById(id: Long): Book?

    /** 서재의 상태별 탭. 최근 등록 순. */
    @Query("SELECT * FROM book WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: BookStatus): Flow<List<Book>>

    /**
     * 카메라 홈 기본 선택: lastQuoteAt이 가장 최근인 READING 책.
     * 글귀가 없는 책만 있으면 최근 등록한 READING 책. READING 책이 없으면 null.
     */
    @Query(
        "SELECT * FROM book WHERE status = 'READING' " +
            "ORDER BY lastQuoteAt IS NULL, lastQuoteAt DESC, createdAt DESC LIMIT 1",
    )
    fun observeLatestReadingByQuote(): Flow<Book?>

    /** 직접 입력 중복 경고용. 제목+저자가 같은 책. author가 null이면 null끼리 비교한다. */
    @Query(
        "SELECT * FROM book WHERE title = :title " +
            "AND ((author IS NULL AND :author IS NULL) OR author = :author) LIMIT 1",
    )
    suspend fun findByTitleAndAuthor(title: String, author: String?): Book?
}

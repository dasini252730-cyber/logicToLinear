package com.logiclinear.reading.data.backup

import androidx.room.withTransaction
import com.logiclinear.reading.data.db.AppDatabase
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.data.db.nowMillis
import com.logiclinear.reading.domain.BookIdentity
import com.logiclinear.reading.domain.isSameBook
import java.time.Instant

/** 가져오기 결과 요약. 설정 화면이 "책 n, 글귀 m"으로 보여준다. */
data class ImportSummary(val books: Int = 0, val quotes: Int = 0, val discussions: Int = 0, val analyses: Int = 0)

/**
 * 백업 내보내기·가져오기(요구사항 "백업"). 파일 입출력은 [BackupIo]가, 여기는 DB ↔ [BackupFile]만 다룬다.
 * 가져오기 두 모드는 각각 트랜잭션 하나다: 중간에 실패하면 예외가 나가고 DB는 그대로다.
 */
class BackupRepository(private val db: AppDatabase) {
    private val bookDao get() = db.bookDao()
    private val quoteDao get() = db.quoteDao()
    private val discussionDao get() = db.discussionDao()
    private val analysisDao get() = db.analysisDao()

    /** 4테이블 전체. API 키는 모델에 자리가 없어 들어갈 수 없다. */
    suspend fun export(now: Instant = nowMillis()): BackupFile = db.withTransaction {
        BackupFile(
            exportedAt = now.toEpochMilli(),
            books = bookDao.getAll().map(Book::toBackup),
            quotes = quoteDao.getAll().map { it.toBackup() },
            discussions = discussionDao.getAll().map { it.toBackup() },
            analyses = analysisDao.getAll().map { it.toBackup() },
        )
    }

    /** 덮어쓰기: 기존 데이터를 모두 지우고 백업을 넣는다. id는 새로 받고 bookId는 다시 연결한다. */
    suspend fun overwrite(file: BackupFile): ImportSummary = db.withTransaction {
        bookDao.deleteAll() // Quote·Discussion은 CASCADE
        analysisDao.deleteAll()
        val idMap = HashMap<Long, Long>()
        file.books.forEach { idMap[it.id] = bookDao.insert(it.toEntity(id = 0)) }
        val quotes = insertQuotes(file, idMap, checkDuplicate = false)
        val discussions = insertDiscussions(file, idMap, checkDuplicate = false)
        val analyses = insertAnalyses(file, checkDuplicate = false)
        ImportSummary(file.books.size, quotes, discussions, analyses)
    }

    /**
     * 합치기: isbn13 또는 제목+저자가 같은 책은 기존 책으로 보고 글귀·토론만 더한다(요구사항 "백업 > 가져오기").
     * 같은 글귀(책·본문·시각 일치)는 다시 넣지 않아 같은 백업을 두 번 합쳐도 늘지 않는다.
     */
    suspend fun merge(file: BackupFile): ImportSummary = db.withTransaction {
        val existing = bookDao.getAll().toMutableList()
        val idMap = HashMap<Long, Long>()
        var newBooks = 0
        file.books.forEach { backup ->
            val same = existing.firstOrNull { isSameBook(it.identity(), backup.identity()) }
            if (same != null) {
                idMap[backup.id] = same.id
            } else {
                val entity = backup.toEntity(id = 0)
                val newId = bookDao.insert(entity)
                existing += entity.copy(id = newId) // 백업 안의 중복 책도 같은 책으로 묶인다
                idMap[backup.id] = newId
                newBooks++
            }
        }
        val quotes = insertQuotes(file, idMap, checkDuplicate = true)
        val discussions = insertDiscussions(file, idMap, checkDuplicate = true)
        val analyses = insertAnalyses(file, checkDuplicate = true)
        ImportSummary(newBooks, quotes, discussions, analyses)
    }

    private suspend fun insertQuotes(file: BackupFile, idMap: Map<Long, Long>, checkDuplicate: Boolean): Int {
        var count = 0
        file.quotes.forEach { backup ->
            val bookId = idMap[backup.bookId] ?: return@forEach // 책 없는 글귀는 버린다
            val quote = backup.toEntity(bookId = bookId, id = 0)
            if (checkDuplicate && quoteDao.countSame(bookId, quote.text, quote.createdAt) > 0) return@forEach
            // 카메라 홈 기본 책 선택 기준(lastQuoteAt)이 가져온 글귀를 반영하도록 한 트랜잭션에서 갱신한다.
            quoteDao.insertAndTouchBook(quote)
            count++
        }
        return count
    }

    private suspend fun insertDiscussions(file: BackupFile, idMap: Map<Long, Long>, checkDuplicate: Boolean): Int {
        var count = 0
        file.discussions.forEach { backup ->
            val bookId = idMap[backup.bookId] ?: return@forEach
            val discussion = backup.toEntity(bookId = bookId, id = 0)
            if (checkDuplicate && discussionDao.countSame(bookId, discussion.startedAt) > 0) return@forEach
            discussionDao.upsert(discussion)
            count++
        }
        return count
    }

    private suspend fun insertAnalyses(file: BackupFile, checkDuplicate: Boolean): Int {
        var count = 0
        file.analyses.forEach { backup ->
            val analysis = backup.toEntity(id = 0)
            if (checkDuplicate && analysisDao.countSame(analysis.runAt) > 0) return@forEach
            analysisDao.insert(analysis)
            count++
        }
        return count
    }
}

fun Book.identity() = BookIdentity(title, author, isbn13)

fun BookBackup.identity() = BookIdentity(title, author, isbn13)

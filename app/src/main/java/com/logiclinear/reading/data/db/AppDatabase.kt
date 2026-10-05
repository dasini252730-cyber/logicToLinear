package com.logiclinear.reading.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * DB 스키마 버전. 백업 JSON의 schemaVersion과 같은 값을 쓴다(요구사항 "백업").
 * 스키마를 바꾸면 이 값을 올리고 [AppDatabase.build]에 Migration을 추가하며, 같은 커밋에서 백업 쪽도 맞춘다.
 */
const val DB_SCHEMA_VERSION = 1

@Database(
    entities = [Book::class, Quote::class, Discussion::class, Analysis::class],
    version = DB_SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun quoteDao(): QuoteDao
    abstract fun discussionDao(): DiscussionDao
    abstract fun analysisDao(): AnalysisDao

    companion object {
        const val FILE_NAME = "reading-log.db"

        /**
         * 앱용 DB. fallbackToDestructiveMigration은 쓰지 않는다. 버전을 올리면 addMigrations로 명시한다.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .build()
    }
}

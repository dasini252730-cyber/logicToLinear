package com.logiclinear.reading.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Room 타입 변환. Instant는 epoch 밀리초(Long), LocalDate는 ISO-8601 문자열(yyyy-MM-dd), enum은 name.
 * 저장 형식을 바꾸면 Migration이 필요하다.
 */
class Converters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun bookStatusToString(value: BookStatus?): String? = value?.name

    @TypeConverter
    fun stringToBookStatus(value: String?): BookStatus? = value?.let(BookStatus::valueOf)
}

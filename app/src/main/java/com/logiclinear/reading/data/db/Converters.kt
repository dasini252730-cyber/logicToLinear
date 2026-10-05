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

    /**
     * 손상된 값(손으로 고친 백업 JSON 등)은 null로 읽는다. finishedAt은 nullable이라 "완독일 없음"으로 보이는 쪽이
     * 쿼리 전체가 예외로 터지는 것보다 낫다. 백업 가져오기(T-504)에서는 검증 단계가 따로 있다.
     */
    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    @TypeConverter
    fun bookStatusToString(value: BookStatus?): String? = value?.name

    @TypeConverter
    fun stringToBookStatus(value: String?): BookStatus? = value?.let(BookStatus::valueOf)
}

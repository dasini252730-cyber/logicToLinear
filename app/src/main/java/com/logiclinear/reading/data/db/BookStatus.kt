package com.logiclinear.reading.data.db

/**
 * 책 상태. 요구사항 "확정된 결정 > 책 상태": 읽고 싶음 / 읽는 중 / 다 읽음 셋뿐이다.
 * DB에는 [Converters]가 name 문자열로 저장한다.
 */
enum class BookStatus {
    WANT,
    READING,
    DONE,
}

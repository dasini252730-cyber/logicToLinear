package com.logiclinear.reading.data.db

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * DB에 넣을 현재 시각. [Converters]가 Instant를 epoch 밀리초로 저장하므로
 * 엔티티 기본값도 밀리초로 잘라 두어야 저장 전후의 값이 같다(JDK 21의 Instant.now()는 마이크로초).
 */
fun nowMillis(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

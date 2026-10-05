package com.logiclinear.reading.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R
import com.logiclinear.reading.data.db.BookStatus

/**
 * 책 표지 자리. 지금은 제목 첫 글자 플레이스홀더만 그린다. T-306에서 [coverUrl]이 있으면 캐시된 이미지를 넣는다.
 * 서재 목록(44×60)과 책 상세(96×132)가 같은 컴포저블을 쓴다.
 */
@Composable
fun BookCover(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 60.dp,
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        // coverUrl은 T-306에서 이미지 로딩에 쓴다. 그 전까지는 글자 플레이스홀더.
        Text(
            text = firstGlyph(title),
            style = if (height > 100.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** 제목의 첫 글자. 이모지·확장 한자 같은 서로게이트 쌍을 쪼개지 않고, 빈 제목이면 "책". */
internal fun firstGlyph(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "책"
    val codePoint = trimmed.codePointAt(0)
    return String(Character.toChars(codePoint))
}

/** 상태 표시 문구. 요구사항 "책 상태": 읽고 싶음 / 읽는 중 / 다 읽음. 서재 탭·등록 폼·상세가 함께 쓴다. */
fun BookStatus.labelRes(): Int = when (this) {
    BookStatus.READING -> R.string.status_reading
    BookStatus.WANT -> R.string.status_want
    BookStatus.DONE -> R.string.status_done
}

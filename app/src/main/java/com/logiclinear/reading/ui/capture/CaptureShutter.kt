package com.logiclinear.reading.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R

/**
 * 원형 셔터. material-icons-core에 카메라 아이콘이 없어 원을 직접 그린다.
 * 책이 READING이 아니거나 촬영 중이면 진짜 비활성이다(리플 없음, 접근성에도 비활성으로 읽힘).
 */
@Composable
fun Shutter(enabled: Boolean, capturing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.shutter)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
        modifier = modifier.size(72.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (capturing) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

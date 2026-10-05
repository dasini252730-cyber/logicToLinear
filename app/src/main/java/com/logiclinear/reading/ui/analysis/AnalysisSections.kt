package com.logiclinear.reading.ui.analysis

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.logiclinear.reading.R
import com.logiclinear.reading.data.db.Analysis
import com.logiclinear.reading.domain.AnalysisReadiness
import com.logiclinear.reading.domain.Recommendation
import com.logiclinear.reading.ui.components.BookCover
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RUN_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

/** 활성 조건과 실행 버튼(T-606). 조건 미달이면 "책 N권, 글귀 M개 더 필요해요". */
@Composable
internal fun ReadinessHeader(readiness: AnalysisReadiness, running: Boolean, canRun: Boolean, onRun: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!readiness.ready) {
            val parts = buildList {
                if (readiness.missingBooks > 0) add(stringResource(R.string.analysis_need_books, readiness.missingBooks))
                if (readiness.missingQuotes > 0) add(stringResource(R.string.analysis_need_quotes, readiness.missingQuotes))
            }
            Text(
                text = stringResource(R.string.analysis_need_more, parts.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onRun, enabled = canRun, modifier = Modifier.fillMaxWidth()) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.analysis_running))
            } else {
                Text(stringResource(R.string.analysis_run))
            }
        }
    }
}

@Composable
internal fun EmptyAnalysis() {
    Text(
        text = stringResource(R.string.analysis_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

/** 분석 1건: 실행 정보, taste 글, 추천 카드(비어 있으면 카드 영역을 숨긴다, T-607). */
@Composable
internal fun AnalysisResult(
    item: AnalysisItem,
    title: String?,
    onAddToWant: (Recommendation) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
        Text(runAtLabel(item.analysis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.analysis.tasteText, style = MaterialTheme.typography.bodyLarge)
        if (item.cards.isNotEmpty()) {
            Text(stringResource(R.string.analysis_recommendations), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            item.cards.forEach { card -> RecommendationCardView(card, onAddToWant, onOpenLink) }
        }
    }
}

/** 추천 카드: 표지(없으면 첫 글자), 제목·저자·이유, 담기 버튼(이미 서재에 있으면 숨김, T-608). */
@Composable
private fun RecommendationCardView(card: RecommendationCard, onAddToWant: (Recommendation) -> Unit, onOpenLink: (String) -> Unit) {
    val rec = card.rec
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            BookCover(title = rec.title, coverUrl = rec.coverUrl, width = 56.dp, height = 78.dp)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(rec.title, style = MaterialTheme.typography.titleSmall)
                Text(rec.author ?: stringResource(R.string.library_author_unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (rec.reason.isNotBlank()) Text(rec.reason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (!card.inLibrary) TextButton(onClick = { onAddToWant(rec) }) { Text(stringResource(R.string.analysis_add_want)) }
                    rec.storeUrl?.let { url -> TextButton(onClick = { onOpenLink(url) }) { Text(stringResource(R.string.analysis_open_link)) } }
                }
            }
        }
    }
}

@Composable
internal fun HistoryHeader(count: Int) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.analysis_history, count), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

/** 이전 기록 한 줄: 날짜만 보이고 탭하면 펼친다(T-607). */
@Composable
internal fun HistoryRow(
    item: AnalysisItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddToWant: (Recommendation) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(runAtLabel(item.analysis), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(if (expanded) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded) {
            AnalysisResult(item, title = null, onAddToWant = onAddToWant, onOpenLink = onOpenLink)
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
    }
}

@Composable
private fun runAtLabel(analysis: Analysis): String = stringResource(
    R.string.analysis_run_at,
    analysis.runAt.atZone(ZoneId.systemDefault()).format(RUN_AT_FORMAT),
    analysis.inputBookCount,
    analysis.inputQuoteCount,
)

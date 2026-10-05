package com.logiclinear.reading.ui.analysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.logiclinear.reading.R
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 분석·추천 자리 표시. T-606에서 실제 화면으로 바뀐다. */
@Composable
fun AnalysisScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.analysis_placeholder),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalysisScreenPreview() {
    ReadingLogTheme { AnalysisScreen() }
}

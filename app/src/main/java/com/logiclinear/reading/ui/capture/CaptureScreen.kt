package com.logiclinear.reading.ui.capture

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R
import com.logiclinear.reading.data.db.Book
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 책 전용 카메라 화면 이벤트 묶음. */
data class CaptureActions(
    val onShutter: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissError: () -> Unit,
    /** 배너 왼쪽 뒤로 버튼, 그리고 책이 READING이 아닐 때의 안내 버튼 → 책 상세. */
    val onBack: () -> Unit,
)

/**
 * 책 전용 카메라 진입점(T-806·T-808). 책 상세 "글귀 찍기"에서만 열린다. 권한·카메라 바인딩·OCR 완료 이동을 묶는다.
 * 책 id는 라우트([CaptureRoute])에서 ViewModel이 읽는다.
 */
@Composable
fun CaptureEntry(
    onRecognized: () -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = viewModel(factory = CaptureViewModel.Factory),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.camera.surfaceRequest.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    // 설정 앱에서 권한을 켜고 돌아오면 다시 확인한다.
    LifecycleResumeEffect(Unit) {
        if (!granted && hasCameraPermission(context)) granted = true
        onPauseOrDispose { }
    }
    if (granted) {
        // 이 코루틴이 취소되면(화면 이탈) 카메라가 해제된다.
        LaunchedEffect(lifecycleOwner) { viewModel.bindCamera(context, lifecycleOwner) }
    }
    LaunchedEffect(state.recognized) {
        if (state.recognized) {
            onRecognized()
            viewModel.consumeRecognized()
        }
    }

    CaptureScreen(
        state = state,
        surfaceRequest = surfaceRequest,
        permissionGranted = granted,
        permissionDenied = denied,
        actions = CaptureActions(
            onShutter = { viewModel.capture(context) },
            onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { openAppSettings(context) },
            onDismissError = viewModel::dismissError,
            onBack = onBack,
        ),
    )
}

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    surfaceRequest: SurfaceRequest?,
    permissionGranted: Boolean,
    permissionDenied: Boolean,
    actions: CaptureActions,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (!permissionGranted) {
            PermissionPrompt(permissionDenied, actions.onRequestPermission, actions.onOpenSettings)
        } else {
            surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
            BookBanner(state, actions, modifier = Modifier.align(Alignment.TopCenter))
            Shutter(
                enabled = state.canCapture,
                capturing = state.capturing,
                onClick = actions.onShutter,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            )
        }
    }
    if (state.ocrEmpty) {
        ErrorDialog(stringResource(R.string.ocr_empty_message), stringResource(R.string.ocr_retry), actions.onDismissError)
    } else if (state.captureFailed) {
        ErrorDialog(stringResource(R.string.capture_error), stringResource(R.string.ocr_retry), actions.onDismissError)
    }
}

/**
 * 상단: 뒤로 버튼과 이 책의 제목. 책이 READING에서 빠졌거나 삭제됐으면 "읽는 중인 책을 먼저 등록하세요" 버튼이 상세로 돌려보낸다
 * (요구사항 "예외 처리" 문구 재사용, T-806 리뷰 반영).
 */
@Composable
private fun BookBanner(state: CaptureUiState, actions: CaptureActions, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            when {
                state.bookMissing -> TextButton(onClick = actions.onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.capture_register_book_first))
                }
                state.book != null -> Text(
                    text = state.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
                else -> Text(text = "", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CaptureScreenPreview() {
    ReadingLogTheme {
        CaptureScreen(
            state = CaptureUiState(book = Book(id = 1, title = "채식주의자", author = "한강"), bookLoaded = true),
            surfaceRequest = null,
            permissionGranted = true,
            permissionDenied = false,
            actions = CaptureActions({}, {}, {}, {}, {}),
        )
    }
}

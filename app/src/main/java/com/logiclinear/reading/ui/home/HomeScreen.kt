package com.logiclinear.reading.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 홈 화면 이벤트 묶음. */
data class HomeActions(
    val onShutter: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissError: () -> Unit,
    val onOpenPicker: () -> Unit,
    val onClosePicker: () -> Unit,
    val onPickBook: (Long) -> Unit,
    /** READING 책이 없을 때 "읽는 중인 책을 먼저 등록하세요" → 서재. */
    val onGoToLibrary: () -> Unit,
)

/** 카메라 홈 진입점. 권한·카메라 바인딩·OCR 완료 이동을 묶는다. */
@Composable
fun HomeEntry(
    onRecognized: () -> Unit,
    onGoToLibrary: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
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

    HomeScreen(
        state = state,
        surfaceRequest = surfaceRequest,
        permissionGranted = granted,
        permissionDenied = denied,
        actions = HomeActions(
            onShutter = { viewModel.capture(context) },
            onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { openAppSettings(context) },
            onDismissError = viewModel::dismissError,
            onOpenPicker = viewModel::openPicker,
            onClosePicker = viewModel::closePicker,
            onPickBook = viewModel::chooseBook,
            onGoToLibrary = onGoToLibrary,
        ),
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    surfaceRequest: SurfaceRequest?,
    permissionGranted: Boolean,
    permissionDenied: Boolean,
    actions: HomeActions,
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
    if (state.pickerOpen) {
        BookPickerSheet(state.readingBooks, state.selectedBook?.id, actions.onPickBook, actions.onClosePicker)
    }
    if (state.ocrEmpty) {
        ErrorDialog(stringResource(R.string.ocr_empty_message), stringResource(R.string.ocr_retry), actions.onDismissError)
    } else if (state.captureFailed) {
        ErrorDialog(stringResource(R.string.capture_error), stringResource(R.string.ocr_retry), actions.onDismissError)
    }
}

/**
 * 상단: 현재 선택된 책. 탭하면 READING 책 선택기(T-209).
 * READING 책이 없으면 "읽는 중인 책을 먼저 등록하세요" 버튼이 서재로 보낸다(T-210, 요구사항 "예외 처리").
 */
@Composable
private fun BookBanner(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        when {
            state.noReadingBook -> TextButton(onClick = actions.onGoToLibrary, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_register_book_first))
            }
            state.selectedBook != null -> Text(
                text = state.selectedBook.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable(onClick = actions.onOpenPicker).padding(horizontal = 16.dp, vertical = 12.dp),
            )
            else -> Text(
                text = "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * 원형 셔터. material-icons-core에 카메라 아이콘이 없어 원을 직접 그린다.
 * READING 책이 없거나 촬영 중이면 진짜 비활성이다(리플 없음, 접근성에도 비활성으로 읽힘).
 */
@Composable
private fun Shutter(enabled: Boolean, capturing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ReadingLogTheme {
        HomeScreen(
            state = HomeUiState(booksLoaded = true),
            surfaceRequest = null,
            permissionGranted = true,
            permissionDenied = false,
            actions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}


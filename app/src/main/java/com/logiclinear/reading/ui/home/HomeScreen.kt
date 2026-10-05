package com.logiclinear.reading.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logiclinear.reading.R

/** 카메라 홈 진입점. 권한·카메라 바인딩·OCR 완료 이동을 묶는다. */
@Composable
fun HomeEntry(
    onRecognized: () -> Unit,
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
        onShutter = { viewModel.capture(context) },
        onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = { openAppSettings(context) },
        onDismissError = viewModel::dismissOcrEmpty,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    surfaceRequest: SurfaceRequest?,
    permissionGranted: Boolean,
    permissionDenied: Boolean,
    onShutter: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (!permissionGranted) {
            PermissionPrompt(permissionDenied, onRequestPermission, onOpenSettings)
        } else {
            surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
            BookBanner(state, modifier = Modifier.align(Alignment.TopCenter))
            Shutter(
                enabled = state.selectedBook != null && !state.capturing,
                capturing = state.capturing,
                onClick = onShutter,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            )
        }
    }
    if (state.ocrEmpty) {
        ErrorDialog(stringResource(R.string.ocr_empty_message), stringResource(R.string.ocr_retry), onDismissError)
    } else if (state.captureError != null) {
        ErrorDialog(stringResource(R.string.capture_error, state.captureError), stringResource(R.string.ocr_retry), onDismissError)
    }
}

/** 상단: 현재 선택된 책. 탭해서 바꾸는 선택기는 T-209, READING 없음 안내는 T-210. */
@Composable
private fun BookBanner(state: HomeUiState, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Text(
            text = state.selectedBook?.title ?: stringResource(R.string.home_no_reading_book),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** 원형 셔터. material-icons-core에 카메라 아이콘이 없어 원을 직접 그린다. */
@Composable
private fun Shutter(enabled: Boolean, capturing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.shutter)
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        shape = CircleShape,
        modifier = modifier.size(72.dp).semantics { contentDescription = label },
    ) {
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

@Composable
private fun PermissionPrompt(denied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(stringResource(R.string.camera_permission_rationale), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.camera_permission_request)) }
        if (denied) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.camera_open_settings)) }
        }
    }
}

@Composable
private fun ErrorDialog(message: String, action: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(action) } },
    )
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    context.startActivity(intent)
}

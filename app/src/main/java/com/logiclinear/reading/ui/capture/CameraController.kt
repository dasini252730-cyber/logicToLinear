package com.logiclinear.reading.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 촬영 결과. 비트맵은 받는 쪽이 OCR 뒤 [Bitmap.recycle]로 폐기한다. */
class CapturedImage(val bitmap: Bitmap, val rotationDegrees: Int)

/**
 * CameraX 프리뷰·촬영 유스케이스를 묶는다. 파일로 저장하는 API(OnImageSavedCallback)는 쓰지 않는다.
 * 요구사항 "사진 원본 저장하지 않음": 촬영은 메모리 [ImageProxy]로만 받는다.
 */
class CameraController {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> _surfaceRequest.value = request }
    }
    /**
     * T-807: 지연 최소 모드는 저조도에서 노이즈·블러가 커 OCR이 깨졌다("대전은 바로 밈요섭고ㅓ"). 품질 우선 모드로 바꾼다.
     * 플래시는 켜지 않는다(종이 반사 우려, 요구사항에 없음). 사진은 여전히 메모리에서만 다룬다.
     */
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

    /** 바인딩된 카메라. 촬영 직전 초점·노출 고정에 쓴다. 해제되면 null. */
    private var camera: Camera? = null

    /** 호출한 코루틴이 취소될 때까지 카메라를 바인딩한다. 화면이 사라지면 자동으로 해제된다. */
    suspend fun bind(context: Context, lifecycleOwner: LifecycleOwner) {
        val token = Any()
        activeBinding = token
        val provider = ProcessCameraProvider.awaitInstance(context.applicationContext)
        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        try {
            awaitCancellation()
        } finally {
            camera = null
            _surfaceRequest.value = null
            // 토큰은 프로세스 전역이다. 화면을 닫고 다시 열면 새 인스턴스가 먼저 바인딩할 수 있는데, 그때 이전 인스턴스의 해제가 새 바인딩을 풀면 안 된다.
            if (activeBinding === token) {
                provider.unbindAll()
                activeBinding = null
            }
        }
    }

    /**
     * 촬영. 호출 시점의 화면 회전을 [ImageCapture.setTargetRotation]에 넣어 기기를 가로로 돌려 찍어도
     * `rotationDegrees`가 올바르게 나온다(ViewModel에 보존된 유스케이스는 회전을 스스로 알지 못한다).
     */
    suspend fun capture(context: Context): CapturedImage {
        imageCapture.targetRotation = ContextCompat.getDisplayOrDefault(context).rotation
        val started = SystemClock.elapsedRealtime()
        focusCenter()
        return suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val rotation = image.imageInfo.rotationDegrees
                        val bitmap = image.use { it.toBitmap() }
                        // T-807 완료 조건(촬영→선택 화면 3초)을 logcat으로 잰다. OCR 시간은 OcrRecognizer가 따로 찍는다.
                        Log.d(TAG, "capture: ${SystemClock.elapsedRealtime() - started}ms (${bitmap.width}x${bitmap.height})")
                        if (cont.isActive) cont.resume(CapturedImage(bitmap, rotation)) else bitmap.recycle()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                },
            )
        }
    }

    /**
     * 촬영 직전 화면 중앙에 초점·노출을 맞추고 끝날 때까지 기다린다(T-807). 책은 20~30cm 거리라 AF가 헤매는 중에 찍으면 흐리다.
     * 실패·취소·시간 초과는 무시하고 그냥 찍는다. 셔터가 멈춰 보이지 않게 [FOCUS_TIMEOUT_MS]를 넘기지 않는다.
     */
    private suspend fun focusCenter() {
        val control = camera?.cameraControl ?: return
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(FOCUS_HOLD_SECONDS, TimeUnit.SECONDS)
            .build()
        val result = withTimeoutOrNull(FOCUS_TIMEOUT_MS) {
            try {
                control.startFocusAndMetering(action).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // OperationCanceledException 등: 초점 없이 찍는다
            }
        }
        Log.d(TAG, "focus: success=${result?.isFocusSuccessful}")
    }

    private companion object {
        const val TAG = "CameraController"
        const val FOCUS_TIMEOUT_MS = 1_500L

        /** 초점·노출 고정 유지 시간. 품질 우선 촬영이 느린 기기에서도 촬영 중에 풀리지 않게 넉넉히 둔다. */
        const val FOCUS_HOLD_SECONDS = 5L

        /** 마지막으로 바인딩한 호출(프로세스 전역). ProcessCameraProvider가 전역이라 인스턴스별 토큰으로는 부족하다. */
        @Volatile
        private var activeBinding: Any? = null
    }
}

/** Guava 의존성을 추가하지 않고 CameraX의 ListenableFuture를 기다린다. */
private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            },
            { it.run() },
        )
        cont.invokeOnCancellation { cancel(false) }
    }

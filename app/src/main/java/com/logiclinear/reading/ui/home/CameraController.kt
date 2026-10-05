package com.logiclinear.reading.ui.home

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    /** 마지막으로 바인딩한 호출을 가리킨다. 이전 호출의 해제가 새 바인딩을 풀지 않게 한다. */
    private var bindingToken: Any? = null

    /** 호출한 코루틴이 취소될 때까지 카메라를 바인딩한다. 화면이 사라지면 자동으로 해제된다. */
    suspend fun bind(context: Context, lifecycleOwner: LifecycleOwner) {
        val token = Any()
        bindingToken = token
        val provider = ProcessCameraProvider.awaitInstance(context.applicationContext)
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        try {
            awaitCancellation()
        } finally {
            if (bindingToken === token) {
                provider.unbindAll()
                _surfaceRequest.value = null
            }
        }
    }

    /**
     * 촬영. 호출 시점의 화면 회전을 [ImageCapture.setTargetRotation]에 넣어 기기를 가로로 돌려 찍어도
     * `rotationDegrees`가 올바르게 나온다(ViewModel에 보존된 유스케이스는 회전을 스스로 알지 못한다).
     */
    suspend fun capture(context: Context): CapturedImage {
        imageCapture.targetRotation = ContextCompat.getDisplayOrDefault(context).rotation
        return suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val rotation = image.imageInfo.rotationDegrees
                        val bitmap = image.use { it.toBitmap() }
                        if (cont.isActive) cont.resume(CapturedImage(bitmap, rotation)) else bitmap.recycle()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                },
            )
        }
    }
}

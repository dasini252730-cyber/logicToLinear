package com.logiclinear.reading

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import okhttp3.OkHttpClient

/** 앱 프로세스마다 하나. [AppContainer]를 소유하고 Coil 이미지 로더를 설정한다. */
class ReadingApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * 표지 이미지 로더(T-306). 디스크 캐시 50MB, 캐시 헤더와 무관하게 저장해 한 번 본 표지는 오프라인에서도 보인다
     * (요구사항 "데이터 모델 > Book > coverUrl: 오프라인 대비 로컬 캐시"). 처음 쓰일 때 만들어져 콜드 스타트에 영향이 없다.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() })) }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("covers"))
                .maxSizeBytes(50L * 1024 * 1024)
                .build()
        }
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .build()
}

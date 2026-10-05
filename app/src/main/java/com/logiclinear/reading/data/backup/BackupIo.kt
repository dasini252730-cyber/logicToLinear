package com.logiclinear.reading.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Storage Access Framework가 준 Uri에 백업 텍스트를 쓰고 읽는다(요구사항 "백업": SAF로 저장 위치를 고른다).
 * ViewModel 테스트에서는 가짜 구현으로 바꾼다.
 */
interface BackupIo {
    /** 실패하면 [IOException]. */
    suspend fun write(uri: Uri, text: String)

    /** 실패하면 [IOException]. */
    suspend fun read(uri: Uri): String
}

class ContentResolverBackupIo(context: Context) : BackupIo {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt": 기존 파일을 고를 때 이전 내용이 뒤에 남지 않도록 잘라 쓴다.
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("출력 스트림을 열 수 없다: $uri")
        stream.bufferedWriter().use { it.write(text) }
    }

    override suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri) ?: throw IOException("입력 스트림을 열 수 없다: $uri")
        stream.bufferedReader().use { it.readText() }
    }
}

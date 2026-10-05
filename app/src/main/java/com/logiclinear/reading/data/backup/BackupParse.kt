package com.logiclinear.reading.data.backup

import com.logiclinear.reading.data.db.DB_SCHEMA_VERSION
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 백업 JSON을 읽은 결과(요구사항 "백업 > 가져오기", "예외 처리"). */
sealed interface BackupParseResult {
    data class Ok(val file: BackupFile) : BackupParseResult

    /** 파일의 schemaVersion이 앱보다 높다 → 가져오기 거부, 앱 업데이트 안내. */
    data class TooNew(val fileVersion: Int, val appVersion: Int) : BackupParseResult

    /** JSON이 아니거나 필수 필드가 없다. 앱이 죽지 않고 오류를 보여준다. */
    data object Corrupt : BackupParseResult
}

/**
 * 문자열 → 검증된 [BackupFile]. 낮은 버전은 [migrateBackup]으로 현재 버전까지 올린다.
 */
fun parseBackup(text: String, appVersion: Int = DB_SCHEMA_VERSION): BackupParseResult {
    val file = runCatching { backupJson.decodeFromString(BackupFile.serializer(), text) }.getOrNull()
        ?: return BackupParseResult.Corrupt
    if (file.schemaVersion > appVersion) return BackupParseResult.TooNew(file.schemaVersion, appVersion)
    return BackupParseResult.Ok(migrateBackup(file, appVersion))
}

/**
 * 낮은 schemaVersion의 백업을 현재 버전 형태로 바꾸는 자리. Room Migration을 추가할 때 같은 커밋에서 단계를 하나 더한다.
 * 지금은 버전 1만 있어 할 일이 없다.
 */
fun migrateBackup(file: BackupFile, appVersion: Int = DB_SCHEMA_VERSION): BackupFile {
    var current = file
    while (current.schemaVersion < appVersion) {
        current = when (current.schemaVersion) {
            // 예: 1 -> current.copy(schemaVersion = 2, books = current.books.map { ... })
            else -> current.copy(schemaVersion = appVersion)
        }
    }
    return current
}

/** 요구사항 "백업": 파일명 `reading-backup-YYYYMMDD.json`. */
fun backupFileName(date: LocalDate): String = "reading-backup-${date.format(FILE_DATE)}.json"

private val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

package com.logiclinear.reading.data.backup

import com.logiclinear.reading.data.db.DB_SCHEMA_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupParseTest {
    private fun json(version: Int) =
        """{"schemaVersion":$version,"exportedAt":1,"books":[],"quotes":[],"discussions":[],"analyses":[]}"""

    @Test
    fun 앱보다_높은_schemaVersion은_거부한다() {
        val result = parseBackup(json(DB_SCHEMA_VERSION + 1))
        assertEquals(BackupParseResult.TooNew(DB_SCHEMA_VERSION + 1, DB_SCHEMA_VERSION), result)
    }

    @Test
    fun 같은_버전은_그대로_읽는다() {
        val result = parseBackup(json(DB_SCHEMA_VERSION)) as BackupParseResult.Ok
        assertEquals(DB_SCHEMA_VERSION, result.file.schemaVersion)
    }

    @Test
    fun 낮은_버전은_마이그레이션_훅을_거쳐_현재_버전이_된다() {
        val result = parseBackup(json(0)) as BackupParseResult.Ok
        assertEquals(DB_SCHEMA_VERSION, result.file.schemaVersion)
    }

    @Test
    fun 손상된_JSON과_필수_필드_누락은_Corrupt() {
        assertEquals(BackupParseResult.Corrupt, parseBackup("{ not json"))
        assertEquals(BackupParseResult.Corrupt, parseBackup(""))
        assertEquals(BackupParseResult.Corrupt, parseBackup("""{"schemaVersion":1}"""))
        assertEquals(BackupParseResult.Corrupt, parseBackup("[1,2,3]"))
    }

    @Test
    fun 파일명은_reading_backup_YYYYMMDD_json() {
        assertEquals("reading-backup-20261005.json", backupFileName(LocalDate.of(2026, 10, 5)))
        assertTrue(backupFileName(LocalDate.of(2026, 1, 1)).matches(Regex("reading-backup-\\d{8}\\.json")))
    }
}

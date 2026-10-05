package com.logiclinear.reading.ui.ai

import com.logiclinear.reading.R
import com.logiclinear.reading.data.remote.anthropic.AiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class AiErrorMessageTest {
    @Test
    fun 실패_종류마다_문구와_다음_동작이_정해져_있다() {
        val cases: List<Triple<AiResult.Failure, Int, AiErrorAction>> = listOf(
            Triple(AiResult.NoKey, R.string.ai_error_no_key, AiErrorAction.OPEN_SETTINGS),
            Triple(AiResult.InvalidKey, R.string.ai_error_invalid_key, AiErrorAction.OPEN_SETTINGS),
            Triple(AiResult.RateLimited, R.string.ai_error_rate_limited, AiErrorAction.RETRY),
            Triple(AiResult.ServerError(503), R.string.ai_error_server, AiErrorAction.RETRY),
            Triple(AiResult.Network(IOException()), R.string.ai_error_network, AiErrorAction.RETRY),
            Triple(AiResult.Rejected(400, "bad"), R.string.ai_error_rejected, AiErrorAction.NONE),
            Triple(AiResult.Incomplete("max_tokens"), R.string.ai_error_incomplete, AiErrorAction.RETRY),
        )
        cases.forEach { (failure, res, action) ->
            assertEquals(failure.toString(), res, failure.messageRes())
            assertEquals(failure.toString(), action, failure.action())
        }
        assertEquals(R.string.ai_retry, AiErrorAction.RETRY.labelRes())
        assertEquals(R.string.ai_open_settings, AiErrorAction.OPEN_SETTINGS.labelRes())
        assertNull(AiErrorAction.NONE.labelRes())
    }
}

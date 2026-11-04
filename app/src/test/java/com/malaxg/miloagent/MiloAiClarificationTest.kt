package com.malaxg.miloagent

import org.junit.Assert.assertEquals
import org.junit.Test

class MiloAiClarificationTest {
    @Test
    fun clarificationAnswerKeepsOriginalInputForRetry() {
        assertEquals(
            "帮我记一笔午饭\n补充信息：32 元",
            buildClarificationRequest("帮我记一笔午饭", " 32 元 "),
        )
    }

    @Test
    fun blankClarificationAnswerDoesNotChangeRequest() {
        assertEquals("提醒我开会", buildClarificationRequest(" 提醒我开会 ", "  "))
    }
}

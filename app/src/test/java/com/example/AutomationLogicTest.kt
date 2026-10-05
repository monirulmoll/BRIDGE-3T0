package com.example

import com.example.state.AutomationManager
import com.example.state.AutomationState
import com.example.util.CodeExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutomationLogicTest {

    @Before
    fun setup() {
        AutomationManager.setState(AutomationState.IDLE)
        AutomationManager.resetStartupState()
    }

    @Test
    fun `test code extractor removes markdown fences and preserves executable code`() {
        val gptResponse = """
            Sure, here is the command to install packages:
            ```bash
            pkg update -y && pkg install python -y
            python -m pip install requests
            ```
            Let me know if you run into any errors!
        """.trimIndent()

        val extracted = CodeExtractor.extractExecutableCode(gptResponse)
        assertNotNull(extracted)
        assertEquals(
            "pkg update -y && pkg install python -y\npython -m pip install requests",
            extracted
        )
    }

    @Test
    fun `test conversational text without code is rejected`() {
        val conversationalText = "Hello! I am ready. What would you like to build today?"
        val extracted = CodeExtractor.extractExecutableCode(conversationalText)
        assertNull(extracted)
    }

    @Test
    fun `test state machine prevents output from being processed as input command`() {
        AutomationManager.setState(AutomationState.WAITING_FOR_RESPONSE)

        val code = "echo 'Hello World'"
        val codeHash = CodeExtractor.computeHash(code)

        // 1. Accept code
        val accepted = AutomationManager.markCodeDetected(code, codeHash)
        assertTrue(accepted)
        assertEquals(AutomationState.CODE_DETECTED, AutomationManager.getCurrentState())

        // 2. Send to Termux
        AutomationManager.markSendingToTermux(code, codeHash)
        assertEquals(AutomationState.WAITING_FOR_OUTPUT, AutomationManager.getCurrentState())

        // 3. Receive output
        val output = "Hello World"
        val outputHash = CodeExtractor.computeHash(output)
        AutomationManager.markOutputReceived(output, outputHash)
        assertEquals(AutomationState.OUTPUT_RECEIVED, AutomationManager.getCurrentState())

        // 4. Output copied to clipboard
        AutomationManager.markOutputCopied(true)
        assertEquals(AutomationState.PASTE_TO_CHATGPT, AutomationManager.getCurrentState())

        // 5. Try to feed output back as code: must be rejected!
        val outputTreatedAsCode = AutomationManager.markCodeDetected(output, outputHash)
        assertFalse("Output must NOT be accepted as code", outputTreatedAsCode)
    }

    @Test
    fun `test startup sequence marks BRIDGE START correctly`() {
        assertFalse(AutomationManager.startupMessageAlreadySent)
        AutomationManager.markStartupMessageSent()
        assertTrue(AutomationManager.startupMessageAlreadySent)
        assertEquals(AutomationState.WAITING_FOR_RESPONSE, AutomationManager.getCurrentState())
    }
}

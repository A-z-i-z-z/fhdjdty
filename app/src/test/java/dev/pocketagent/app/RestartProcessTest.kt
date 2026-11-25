package dev.pocketagent.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartProcessTest {
    @Test fun theRestartProcessIsRecognisedByItsSuffix() {
        assertTrue(RestartProcess.isRestartProcess("dev.pocketagent.app:restart"))
        assertTrue(RestartProcess.isRestartProcess(" dev.pocketagent.app:restart "))
    }

    @Test fun theMainAndOtherProcessesAreNot() {
        assertFalse(RestartProcess.isRestartProcess("dev.pocketagent.app"))
        assertFalse(RestartProcess.isRestartProcess("dev.pocketagent.app:other"))
        assertFalse(RestartProcess.isRestartProcess("dev.pocketagent.app:restart2"))
        assertFalse(RestartProcess.isRestartProcess(""))
        // Unknown means "main": the app must still start normally.
        assertFalse(RestartProcess.isRestartProcess(null))
    }
}

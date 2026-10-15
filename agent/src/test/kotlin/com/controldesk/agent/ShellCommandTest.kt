package com.controldesk.agent

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class ShellCommandTest {
    @Test fun acceptedCommandReturnsNormally() {
        if (!java.io.File("/bin/sh").isFile) return
        Shell.runChecked("/bin/sh", "-c", "exit 0")
    }

    @Test fun rejectedCommandDoesNotReturnFalseSuccess() {
        if (!java.io.File("/bin/sh").isFile) return
        val error = assertFailsWith<IllegalStateException> {
            Shell.runChecked("/bin/sh", "-c", "echo application-not-found >&2; exit 1")
        }
        assertContains(error.message.orEmpty(), "application-not-found")
    }

    @Test fun timeoutDoesNotReturnFalseSuccess() {
        if (!java.io.File("/bin/sleep").isFile) return
        val error = assertFailsWith<IllegalStateException> {
            Shell.runChecked("/bin/sleep", "2", timeoutMs = 10)
        }
        assertContains(error.message.orEmpty(), "não confirmou")
    }
}

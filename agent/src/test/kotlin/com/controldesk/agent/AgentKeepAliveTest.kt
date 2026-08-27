package com.controldesk.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentKeepAliveTest {
    @Test
    fun foregroundProcessRespawnsOnShutdown() {
        assertTrue(AgentKeepAlive.shouldRespawn(headless = false, noRespawn = false))
    }

    @Test
    fun headlessLaunchAgentDoesNotRespawn() {
        assertFalse(AgentKeepAlive.shouldRespawn(headless = true, noRespawn = false))
    }

    @Test
    fun noRespawnEnvStopsDaemonHandoff() {
        assertFalse(AgentKeepAlive.shouldRespawn(headless = false, noRespawn = true))
    }

    @Test
    fun packagedMacPrefersInstalledLaunchAgent() {
        assertTrue(
            AgentKeepAlive.shouldPreferLaunchAgent(
                bundled = true,
                installedAppExists = true,
                windows = false,
            ),
        )
        assertFalse(
            AgentKeepAlive.shouldPreferLaunchAgent(
                bundled = false,
                installedAppExists = true,
                windows = false,
            ),
        )
        assertFalse(
            AgentKeepAlive.shouldPreferLaunchAgent(
                bundled = true,
                installedAppExists = true,
                windows = true,
            ),
        )
    }

    @Test
    fun posixScriptDetachesHeadlessChild() {
        val log = File("/tmp/control-desk-agent-daemon.log")
        val script = AgentKeepAlive.posixDetachScript(
            java = "/Library/Java/Home/bin/java",
            classpath = "/tmp/agent.jar:/tmp/lib.jar",
            port = 8741,
            logFile = log,
        )
        assertTrue(script.contains("--headless"))
        assertTrue(script.contains("CONTROL_DESK_HEADLESS=1"))
        assertTrue(script.contains("CONTROL_DESK_PORT=8741"))
        assertTrue(script.contains(AgentKeepAlive.MAIN_CLASS))
        assertTrue(script.contains("/usr/sbin/lsof"))
        assertTrue(script.contains("-Dapple.awt.UIElement=true"))
    }

    @Test
    fun shellQuoteEscapesSingleQuotes() {
        assertEquals("'/tmp/app.jar'", AgentKeepAlive.shellQuote("/tmp/app.jar"))
        assertEquals("'it'\"'\"'s fine'", AgentKeepAlive.shellQuote("it's fine"))
    }
}

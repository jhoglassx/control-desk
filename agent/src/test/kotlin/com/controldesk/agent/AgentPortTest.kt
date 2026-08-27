package com.controldesk.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentPortTest {
    @Test
    fun recognizesControlDeskCommands() {
        assertTrue(AgentPort.isOurProcess("/Library/Java/Home/bin/java -cp agent.jar com.controldesk.agent.AgentMainKt --headless"))
        assertTrue(AgentPort.isOurProcess("/Applications/ControlDeskAgent.app/Contents/MacOS/ControlDeskAgent"))
        assertTrue(AgentPort.isOurProcess("java -Dcontrol.desk.headless=true com.controldesk.agent.AgentMainKt"))
        assertFalse(AgentPort.isOurProcess("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"))
        assertFalse(AgentPort.isOurProcess(""))
    }

    @Test
    fun parsesLsofPidList() {
        assertEquals(listOf(4123L, 9001L), AgentPort.parseLsofPids("4123\n9001\n"))
        assertEquals(emptyList(), AgentPort.parseLsofPids(""))
        assertEquals(listOf(12L), AgentPort.parseLsofPids("12\n12\n"))
    }
}

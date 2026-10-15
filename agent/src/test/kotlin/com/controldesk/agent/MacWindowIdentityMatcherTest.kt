package com.controldesk.agent

import com.controldesk.agent.macos.AccessibleWindowSnapshot
import com.controldesk.agent.macos.MacWindowIdentityMatcher
import com.controldesk.agent.macos.QuartzWindowSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class MacWindowIdentityMatcherTest {
    @Test
    fun unrelatedGeometryOfSameProcessDoesNotInventAWindowIdentity() {
        assertEquals(listOf(null), MacWindowIdentityMatcher.match(
            listOf(accessible("", x = 100.0)),
            listOf(quartz("different-window", "", x = 900.0)),
        ))
    }

    @Test
    fun matchesMultipleSameAppWindowsByTitleEvenWhenApiOrderChanges() {
        val accessible = listOf(
            accessible("workspace-b", x = 800.0),
            accessible("workspace-a", x = 100.0),
        )
        val quartz = listOf(
            quartz("9841", "workspace-a", x = 100.0),
            quartz("9842", "workspace-b", x = 800.0),
        )

        assertEquals(listOf("9842", "9841"), MacWindowIdentityMatcher.match(accessible, quartz))
    }

    @Test
    fun usesGeometryToDisambiguateDuplicateWindowTitles() {
        val accessible = listOf(
            accessible("Code", x = 900.0),
            accessible("Code", x = 100.0),
        )
        val quartz = listOf(
            quartz("702", "Code", x = 100.0),
            quartz("701", "Code", x = 900.0),
        )

        assertEquals(listOf("701", "702"), MacWindowIdentityMatcher.match(accessible, quartz))
    }

    @Test
    fun neverMatchesWindowsOwnedByAnotherProcess() {
        val accessible = listOf(accessible("Window", pid = 10))
        val quartz = listOf(quartz("77", "Window", pid = 11))

        assertEquals(listOf(null), MacWindowIdentityMatcher.match(accessible, quartz))
    }

    @Test
    fun leavesOverlappingTitlelessWindowsUnmatchedInsteadOfSwappingTheirIdentities() {
        val accessible = listOf(
            accessible("", x = 100.0),
            accessible("", x = 100.0),
        )
        val quartz = listOf(
            quartz("701", "", x = 100.0),
            quartz("702", "", x = 100.0),
        )

        assertEquals(listOf(null, null), MacWindowIdentityMatcher.match(accessible, quartz))
    }

    private fun accessible(title: String, pid: Int = 42, x: Double = 0.0) =
        AccessibleWindowSnapshot(pid, title, x, 20.0, 800.0, 600.0)

    private fun quartz(id: String, title: String, pid: Int = 42, x: Double = 0.0) =
        QuartzWindowSnapshot(pid, id, title, x, 20.0, 800.0, 600.0)
}

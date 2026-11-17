package com.controldesk.agent

import com.controldesk.protocol.RunningAppInfo

/** Resolves commands against the current session; never substitutes a sibling for a missing ID. */
internal object MacWindowTarget {
    fun resolve(id: String, pid: Int?): RunningAppInfo {
        MacWindowLister.refreshNow()
        return requireMatch(id, pid, MacWindowLister.list(includeIcons = false))
    }

    internal fun requireMatch(id: String, pid: Int?, windows: List<RunningAppInfo>): RunningAppInfo =
        requireNotNull(windows.singleOrNull { it.stableWindowId == id && it.pid == pid && pid != null && (it.axWindowIndex ?: 0) > 0 }) {
            "A janela selecionada não está mais disponível. Atualize a lista."
        }
}

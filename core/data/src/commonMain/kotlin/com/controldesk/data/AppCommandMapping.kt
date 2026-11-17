package com.controldesk.data

import com.controldesk.domain.model.GridButton
import com.controldesk.protocol.AppCloseRequest
import com.controldesk.protocol.AppLaunchRequest

/** Shared wire mapping for HTTP and WebSocket commands. */
internal fun GridButton.toLaunchRequest() = AppLaunchRequest(
    target = payload.getValue("target"),
    targetType = payload["targetType"] ?: "appName",
    windowTitle = payload["windowTitle"],
    windowIndex = payload["windowIndex"]?.toIntOrNull(),
    windowNumber = payload["windowNumber"]?.toIntOrNull(),
    bundlePath = payload["bundlePath"],
    projectPath = payload["projectPath"],
    pid = payload["pid"]?.toIntOrNull(),
    axWindowIndex = payload["axWindowIndex"]?.toIntOrNull(),
    stableWindowId = payload["stableWindowId"],
)

internal fun GridButton.toCloseRequest() = AppCloseRequest(
    target = payload.getValue("target"),
    targetType = payload["targetType"] ?: "appName",
    pid = payload["pid"]?.toIntOrNull(),
    windowTitle = payload["windowTitle"],
    axWindowIndex = payload["axWindowIndex"]?.toIntOrNull(),
    windowCount = payload["windowCount"]?.toIntOrNull(),
    stableWindowId = payload["stableWindowId"],
)

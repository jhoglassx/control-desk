package com.controldesk.app.viewmodel

import com.controldesk.domain.model.ControlShortcut
import com.controldesk.domain.model.MacFileListing
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.PinnedAppShortcut

data class PinnedAppsUiState(
    val deviceCatalog: com.controldesk.domain.model.DeviceCatalog = com.controldesk.domain.model.DeviceCatalog(),
    val isLoadingDevices: Boolean = false,
    val requestingCameraAccess: Boolean = false,
    val devicesError: String? = null,
    val editingControl: ControlShortcut? = null,
    val controlError: String? = null,
    val isSavingControl: Boolean = false,
    val installedApps: List<PinnedAppShortcut> = emptyList(),
    val isLoadingApps: Boolean = false,
    val appsError: String? = null,
    val selectedAppId: String? = null,
    val fileListing: MacFileListing? = null,
    val isLoadingFiles: Boolean = false,
    val filesError: String? = null,
)

sealed interface PinnedAppsIntent {
    data object LoadDevices : PinnedAppsIntent
    data class AuthorizeCamera(val deviceId: String) : PinnedAppsIntent
    data class EditControl(val shortcut: ControlShortcut) : PinnedAppsIntent
    data class UpdateControl(val shortcut: ControlShortcut) : PinnedAppsIntent
    data object SaveControl : PinnedAppsIntent
    data object CancelControl : PinnedAppsIntent
    data class RemoveControl(val id: String) : PinnedAppsIntent
    data class LoadInstalledApps(val host: PairedHost) : PinnedAppsIntent
    data class PinApp(val app: PinnedAppShortcut) : PinnedAppsIntent
    data class UnpinApp(val id: String) : PinnedAppsIntent
    data class SaveProjectPath(val id: String, val path: String) : PinnedAppsIntent
    data class MoveApp(val id: String, val delta: Int) : PinnedAppsIntent
    data class MoveAppTo(val id: String, val order: Int) : PinnedAppsIntent
    data class ChoosePathFor(val id: String) : PinnedAppsIntent
    data class BrowseFiles(val path: String?) : PinnedAppsIntent
    data class SelectPath(val path: String) : PinnedAppsIntent
    data object CancelPathSelection : PinnedAppsIntent
}

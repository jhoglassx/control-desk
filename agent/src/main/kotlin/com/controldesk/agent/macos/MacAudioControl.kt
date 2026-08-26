package com.controldesk.agent

import com.controldesk.protocol.AudioResponse
import com.controldesk.protocol.MicRequest
import com.controldesk.protocol.VolumeRequest

object MacAudioControl {
    fun read(): AudioResponse {
        val volume = Shell.osascript("output volume of (get volume settings)", timeoutMs = 400).toIntOrNull()?.coerceIn(0, 100) ?: 50
        val muted = Shell.osascript("output muted of (get volume settings)", timeoutMs = 400).equals("true", ignoreCase = true)
        val input = Shell.osascript("input volume of (get volume settings)", timeoutMs = 400).toIntOrNull() ?: 75
        return AudioResponse(volumeLevel = volume, volumeMuted = muted, micMuted = input <= 1)
    }

    fun setVolume(request: VolumeRequest) {
        when {
            request.mute == true -> Shell.spawn("/usr/bin/osascript", "-e", "set volume output muted true")
            request.mute == false -> {
                val level = request.level ?: read().volumeLevel
                Shell.spawn("/usr/bin/osascript", "-e", "set volume output muted false")
                Shell.spawn("/usr/bin/osascript", "-e", "set volume output volume ${level.coerceIn(0, 100)}")
            }
            request.level != null -> {
                val level = request.level!!.coerceIn(0, 100)
                Shell.spawn("/usr/bin/osascript", "-e", "set volume output muted false")
                Shell.spawn("/usr/bin/osascript", "-e", "set volume output volume $level")
            }
            request.delta != null -> {
                val next = (read().volumeLevel + request.delta!!).coerceIn(0, 100)
                Shell.spawn("/usr/bin/osascript", "-e", "set volume output muted false")
                Shell.spawn("/usr/bin/osascript", "-e", "set volume output volume $next")
            }
        }
    }

    fun setMic(request: MicRequest) {
        val currentlyMuted = (Shell.osascript("input volume of (get volume settings)", timeoutMs = 300).toIntOrNull() ?: 75) <= 1
        val mute = when {
            request.mute != null -> request.mute == true
            request.toggle -> !currentlyMuted
            else -> currentlyMuted
        }
        if (request.inputDelta != null) {
            val current = Shell.osascript("input volume of (get volume settings)", timeoutMs = 300).toIntOrNull() ?: 75
            Shell.spawn("/usr/bin/osascript", "-e", "set volume input volume ${(current + request.inputDelta!!).coerceIn(0, 100)}")
            return
        }
        if (mute) {
            Shell.spawn("/usr/bin/osascript", "-e", "set volume input volume 0")
        } else {
            Shell.spawn("/usr/bin/osascript", "-e", "set volume input volume 75")
        }
    }
}

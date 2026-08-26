package com.controldesk.agent

import com.controldesk.protocol.AudioResponse
import com.controldesk.protocol.MicRequest
import com.controldesk.protocol.VolumeRequest

object MacAudioControl {
    fun read(): AudioResponse {
        val raw = Shell.osascript("get volume settings", timeoutMs = 250)
        val output = raw.intAfter("output volume:") ?: 50
        val input = raw.intAfter("input volume:") ?: 75
        val muted = raw.flagAfter("output muted:")
        return AudioResponse(
            volumeLevel = output.coerceIn(0, 100),
            volumeMuted = muted,
            micMuted = input <= 1,
            micLevel = if (input <= 1) 0 else input.coerceIn(0, 100),
        )
    }

    fun setVolume(request: VolumeRequest) {
        when {
            request.mute == true -> spawn("set volume output muted true")
            request.mute == false && request.level != null -> spawn(
                "set volume output muted false",
                "set volume output volume ${request.level!!.coerceIn(0, 100)}",
            )
            request.mute == false -> spawn("set volume output muted false")
            request.level != null -> spawn(
                "set volume output muted false",
                "set volume output volume ${request.level!!.coerceIn(0, 100)}",
            )
            request.delta != null -> spawn(
                "set current to output volume of (get volume settings)",
                "set volume output muted false",
                "set next to current + ${request.delta}",
                "if next < 0 then set next to 0",
                "if next > 100 then set next to 100",
                "set volume output volume next",
            )
        }
    }

    fun setMic(request: MicRequest) {
        when {
            request.level != null -> spawn("set volume input volume ${request.level!!.coerceIn(0, 100)}")
            request.inputDelta != null -> spawn(
                "set current to input volume of (get volume settings)",
                "set next to current + ${request.inputDelta}",
                "if next < 0 then set next to 0",
                "if next > 100 then set next to 100",
                "set volume input volume next",
            )
            request.mute == true -> spawn("set volume input volume 0")
            request.mute == false -> spawn("set volume input volume 75")
            request.toggle -> spawn(
                "set current to input volume of (get volume settings)",
                "if current is less than or equal to 1 then",
                "set volume input volume 75",
                "else",
                "set volume input volume 0",
                "end if",
            )
        }
    }

    private fun spawn(vararg lines: String) {
        val command = buildList {
            add("/usr/bin/osascript")
            lines.forEach { line ->
                add("-e")
                add(line)
            }
        }
        Shell.spawn(*command.toTypedArray())
    }

    private fun String.intAfter(key: String): Int? {
        val start = indexOf(key, ignoreCase = true)
        if (start < 0) return null
        val digits = drop(start + key.length).trimStart().takeWhile { it.isDigit() }
        return digits.toIntOrNull()
    }

    private fun String.flagAfter(key: String): Boolean {
        val start = indexOf(key, ignoreCase = true)
        if (start < 0) return false
        return drop(start + key.length).trimStart().startsWith("true", ignoreCase = true)
    }
}

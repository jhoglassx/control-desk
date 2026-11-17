package com.controldesk.agent.devices

import com.controldesk.protocol.DeviceActionRequest
import com.controldesk.protocol.DeviceInfo
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import kotlin.math.roundToInt

/** CoreAudio property access, with persistent device UID rather than transient AudioObjectID. */
class MacAudioDeviceAdapter : DeviceAdapter {
    override val id = "macos.coreaudio"
    private interface Audio : Library {
        fun AudioObjectGetPropertyDataSize(obj: Int, address: Address, qualifierSize: Int, qualifier: Pointer?, size: IntByReference): Int
        fun AudioObjectGetPropertyData(obj: Int, address: Address, qualifierSize: Int, qualifier: Pointer?, size: IntByReference, data: Pointer): Int
        fun AudioObjectSetPropertyData(obj: Int, address: Address, qualifierSize: Int, qualifier: Pointer?, size: Int, data: Pointer): Int
        fun AudioObjectIsPropertySettable(obj: Int, address: Address, result: Pointer): Int
    }
    private interface Foundation : Library {
        fun CFStringGetCString(value: Pointer, buffer: Pointer, size: Long, encoding: Int): Byte
        fun CFRelease(value: Pointer)
    }
    @Structure.FieldOrder("selector", "scope", "element")
    class Address() : Structure() {
        @JvmField var selector: Int = 0
        @JvmField var scope: Int = fourCC("glob")
        @JvmField var element: Int = 0
        constructor(key: String, direction: String = "glob") : this() { selector = fourCC(key); scope = fourCC(direction) }
    }
    private val audio by lazy { Native.load("/System/Library/Frameworks/CoreAudio.framework/CoreAudio", Audio::class.java) }
    private val cf by lazy { Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Foundation::class.java) }

    override fun discover(): List<DeviceInfo> = objects().flatMap { objectId ->
        val uid = string(objectId, "uid ") ?: return@flatMap emptyList()
        val name = string(objectId, "lnam") ?: uid
        listOf("inpt" to "AUDIO_INPUT", "outp" to "AUDIO_OUTPUT").mapNotNull { (scope, kind) ->
            if (read(objectId, Address("stm#", scope)) == null) return@mapNotNull null
            val mute = read(objectId, Address("mute", scope))?.getInt(0)?.let { it != 0 }
            val volume = read(objectId, Address("volm", scope))?.getFloat(0)?.let { (it * 100).roundToInt().coerceIn(0, 100) }
            val defaultKey = if (scope == "inpt") "dIn " else "dOut"
            DeviceInfo("$id:$scope:$uid", name, kind, id,
                capabilities = buildList {
                    if (settable(1, Address(defaultKey))) add("SET_DEFAULT")
                    if (mute != null && settable(objectId, Address("mute", scope))) add("SET_MUTED")
                    if (volume != null && settable(objectId, Address("volm", scope))) add("SET_LEVEL")
                }, muted = mute, level = volume, isDefault = read(1, Address(defaultKey))?.getInt(0) == objectId)
        }
    }

    override fun execute(device: DeviceInfo, request: DeviceActionRequest) {
        val scope = if (device.kind == "AUDIO_INPUT") "inpt" else "outp"
        val uid = device.id.removePrefix("$id:$scope:")
        val objectId = objects().singleOrNull { string(it, "uid ") == uid } ?: error("Dispositivo removido")
        val data = Memory(4)
        val (obj, address) = when (request.action) {
            "SET_DEFAULT" -> { data.setInt(0, objectId); 1 to Address(if (scope == "inpt") "dIn " else "dOut") }
            "SET_MUTED" -> { data.setInt(0, if (request.booleanValue == true) 1 else 0); objectId to Address("mute", scope) }
            "SET_LEVEL" -> { data.setFloat(0, requireNotNull(request.level) / 100f); objectId to Address("volm", scope) }
            else -> error("Ação não suportada")
        }
        check(settable(obj, address)) { "Controle indisponível ou sem permissão" }
        check(audio.AudioObjectSetPropertyData(obj, address, 0, null, 4, data) == 0) { "O CoreAudio recusou a alteração" }
    }
    private fun objects(): List<Int> {
        val data = read(1, Address("dev#")) ?: error("Não foi possível listar dispositivos CoreAudio")
        return List((data.size() / 4).toInt()) { data.getInt(it * 4L) }
    }
    private fun read(obj: Int, address: Address): Memory? {
        val size = IntByReference()
        if (audio.AudioObjectGetPropertyDataSize(obj, address, 0, null, size) != 0 || size.value <= 0) return null
        val data = Memory(size.value.toLong())
        return data.takeIf { audio.AudioObjectGetPropertyData(obj, address, 0, null, size, data) == 0 }
    }
    private fun settable(obj: Int, address: Address): Boolean {
        val result = Memory(1)
        return audio.AudioObjectIsPropertySettable(obj, address, result) == 0 && result.getByte(0).toInt() != 0
    }
    private fun string(obj: Int, key: String): String? {
        val value = read(obj, Address(key))?.getPointer(0) ?: return null
        return try {
            val buffer = Memory(8192)
            if (cf.CFStringGetCString(value, buffer, buffer.size(), 0x08000100).toInt() != 0) buffer.getString(0, "UTF-8") else null
        } finally { cf.CFRelease(value) }
    }
    companion object {
        private fun fourCC(value: String): Int = value.fold(0) { result, char -> (result shl 8) or char.code }
    }
}

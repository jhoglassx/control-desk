package com.controldesk.agent

import java.io.File
import java.security.MessageDigest

/** Matches the installer fingerprint; eagerly captured at service startup, before bundle replacement. */
object AgentBuildIdentity {
    val VALUE: String = runCatching {
        val file = File(AgentBuildIdentity::class.java.protectionDomain.codeSource.location.toURI())
        if (file.isFile) fingerprintJarDirectory(file.parentFile) else "development"
    }.getOrDefault("unknown")

    internal fun fingerprintJarDirectory(directory: File): String {
        val jars = directory.listFiles().orEmpty().filter { it.isFile && it.extension == "jar" }.sortedBy { it.name }
        check(jars.isNotEmpty()) { "Nenhum JAR de aplicação encontrado" }
        val manifest = jars.joinToString("") { "${fingerprint(it)}  ./${it.name}\n" }
        return MessageDigest.getInstance("SHA-256").digest(manifest.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    internal fun fingerprint(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

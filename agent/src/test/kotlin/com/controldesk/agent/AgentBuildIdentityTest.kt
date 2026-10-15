package com.controldesk.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AgentBuildIdentityTest {
    @Test fun installerAndServiceUseTheSameFingerprintIncludingDependencyChanges() {
        val folder = kotlin.io.path.createTempDirectory("agent-build-test").toFile()
        try {
            File(folder, "agent-main.jar").writeText("agent")
            val dependency = File(folder, "protocol.jar").apply { writeText("old") }
            val first = AgentBuildIdentity.fingerprintJarDirectory(folder)
            val process = ProcessBuilder("bash", "-c", "cd \"${'$'}1\" && LC_ALL=C /usr/bin/shasum -a 256 ./*.jar | /usr/bin/shasum -a 256 | /usr/bin/awk '{print ${'$'}1}'", "test", folder.path).start()
            val shellHash = process.inputStream.bufferedReader().readText().trim()
            assertEquals(0, process.waitFor())
            assertEquals(first, shellHash)
            dependency.writeText("new")
            assertNotEquals(first, AgentBuildIdentity.fingerprintJarDirectory(folder))
        } finally { folder.deleteRecursively() }
    }
    @Test fun sameVersionDifferentBuildAndLegacyServiceTriggerRestart() {
        val script = File("package/resources/macos/install-macos.sh").readText()
        val function = script.substringAfter("service_needs_restart() {").substringBefore("\n}")
        fun needsRestart(version: String, installedVersion: String, runningBuild: String, installedBuild: String): Boolean {
            val result = ProcessBuilder("bash", "-c", "service_needs_restart() {$function\n}\nservice_needs_restart \"${'$'}1\" \"${'$'}2\" \"${'$'}3\" \"${'$'}4\"", "test", version, installedVersion, runningBuild, installedBuild).start()
            return result.waitFor() == 0
        }
        assertEquals(true, needsRestart("1.1.14", "1.1.14", "old", "new"))
        assertEquals(true, needsRestart("1.1.14", "1.1.14", "", "new"))
        assertEquals(false, needsRestart("1.1.14", "1.1.14", "same", "same"))
        assertEquals(true, needsRestart("1.1.13", "1.1.14", "same", "same"))
    }
}

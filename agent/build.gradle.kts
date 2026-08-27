import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.core.protocol)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.jmdns)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "com.controldesk.agent.AgentMainKt"
        jvmArgs += listOf(
            "-Dapple.awt.application.name=Control Desk Agent",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "ControlDeskAgent"
            packageVersion = "1.0.2"
            description = "Control Desk Agent"
            copyright = "Control Desk"
            macOS {
                bundleID = "com.controldesk.agent"
                infoPlist {
                    extraKeysRawXml = """
                        <key>LSUIElement</key>
                        <string>true</string>
                    """.trimIndent()
                }
            }
            appResourcesRootDir.set(project.file("package/resources"))
        }
    }
}

tasks.register<Exec>("installMacos") {
    group = "distribution"
    description = "Gera o .app e instala o LaunchAgent (serviço em segundo plano neste Mac)"
    dependsOn("packageDistributionForCurrentOS")
    onlyIf { System.getProperty("os.name").orEmpty().lowercase().contains("mac") }
    commandLine("bash", file("package/resources/macos/install-macos.sh").absolutePath)
}

tasks.register<Exec>("packageInstallerDmg") {
    group = "distribution"
    description = "DMG com o .app, .command de instalar/remover e a documentação"
    dependsOn("packageDistributionForCurrentOS")
    onlyIf { System.getProperty("os.name").orEmpty().lowercase().contains("mac") }
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/ControlDeskAgent.app")
    val stageDir = layout.buildDirectory.dir("installer-stage")
    val outFile = layout.buildDirectory.file("compose/binaries/main/dmg/ControlDeskAgent-1.0.2.dmg")
    inputs.dir(file("package/dmg"))
    inputs.dir(rootProject.file("docs"))
    outputs.file(outFile)
    commandLine("bash", file("package/dmg/build-dmg.sh").absolutePath)
    doFirst {
        commandLine(
            "bash",
            file("package/dmg/build-dmg.sh").absolutePath,
            appDir.get().asFile.absolutePath,
            stageDir.get().asFile.absolutePath,
            outFile.get().asFile.absolutePath,
            rootProject.projectDir.absolutePath,
            file("package/dmg").absolutePath,
        )
    }
}

afterEvaluate {
    tasks.findByName("packageDmg")?.let { dmg ->
        val installer = tasks.named("packageInstallerDmg")
        installer.configure { mustRunAfter(dmg) }
        dmg.finalizedBy(installer)
    }
}

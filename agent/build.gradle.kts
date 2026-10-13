import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val agentVersion = "1.1.1"

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // The packaged app currently uses the JDK that launches Gradle (Java 17 here).
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.core.protocol)
    implementation(projects.core.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.jmdns)
    implementation(libs.jna)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "com.controldesk.agent.AgentMainKt"
        javaHome = System.getProperty("java.home")
        jvmArgs += listOf(
            "-Dapple.awt.application.name=Control Desk Agent",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "ControlDeskAgent"
            packageVersion = agentVersion
            description = "Control Desk Agent"
            copyright = "Control Desk"
            macOS {
                bundleID = "com.controldesk.agent"
                infoPlist {
                    extraKeysRawXml = """
                        <key>LSUIElement</key>
                        <false/>
                    """.trimIndent()
                }
            }
            appResourcesRootDir.set(project.file("package/resources"))
        }
    }
}

val signAgentMacApp = tasks.register<Exec>("signAgentMacApp") {
    group = "distribution"
    description = "Assina e valida o app quando uma identidade persistente está configurada"
    dependsOn("packageDistributionForCurrentOS")
    onlyIf { System.getProperty("os.name").orEmpty().lowercase().contains("mac") }
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/ControlDeskAgent.app")
    commandLine("bash", file("package/resources/macos/sign-macos-app.sh").absolutePath, appDir.get().asFile.absolutePath)
}

tasks.register<Exec>("installMacos") {
    group = "distribution"
    description = "Gera o .app e instala o LaunchAgent (serviço em segundo plano neste Mac)"
    dependsOn(signAgentMacApp)
    onlyIf { System.getProperty("os.name").orEmpty().lowercase().contains("mac") }
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/ControlDeskAgent.app")
    commandLine("bash", file("package/resources/macos/install-macos.sh").absolutePath, appDir.get().asFile.absolutePath)
}

tasks.register<Exec>("packageInstallerDmg") {
    group = "distribution"
    description = "DMG com o .app, atalho para Aplicativos e LEIA-ME"
    dependsOn(signAgentMacApp)
    onlyIf { System.getProperty("os.name").orEmpty().lowercase().contains("mac") }
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/ControlDeskAgent.app")
    val stageDir = layout.buildDirectory.dir("installer-stage")
    val outFile = layout.buildDirectory.file("compose/binaries/main/dmg/ControlDeskAgent-$agentVersion.dmg")
    inputs.dir(file("package/dmg"))
    outputs.file(outFile)
    commandLine("bash", file("package/dmg/build-dmg.sh").absolutePath)
    doFirst {
        commandLine(
            "bash",
            file("package/dmg/build-dmg.sh").absolutePath,
            appDir.get().asFile.absolutePath,
            stageDir.get().asFile.absolutePath,
            outFile.get().asFile.absolutePath,
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

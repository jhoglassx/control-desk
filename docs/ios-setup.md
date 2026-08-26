# iOS setup

Especificação geral: **[spec.md](spec.md)**.

O módulo `composeApp` já inclui targets iOS e `MainViewController.kt`.

Arquivos Swift de referência em `iosApp/iosApp/` para um projeto Xcode.

## Build do framework

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Abra o Xcode, crie um app iOS e linke o framework gerado em
`composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework`.

Use `MainViewController()` do Kotlin como root view controller.

Permita HTTP local no `Info.plist` (já incluído em `iosApp/iosApp/Info.plist`).

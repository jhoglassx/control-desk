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

## Firebase Crashlytics (iOS)

1. No Xcode, adicione o pacote SPM `https://github.com/firebase/firebase-ios-sdk` (`FirebaseCore` + `FirebaseCrashlytics`).
2. Inclua `iosApp/iosApp/GoogleService-Info.plist` no target (bundle id **`controldesk`**).
3. `iOSApp.swift` já chama `FirebaseApp.configure()` quando o SDK está presente.

**Nota:** o sync do Gradle/Android Studio **não** usa mais CocoaPods (evita exigir Xcode só para abrir o projeto). Build iOS nativo ainda precisa do Xcode instalado:

```bash
xcode-select -s /Applications/Xcode.app/Contents/Developer
xcodebuild -version
```

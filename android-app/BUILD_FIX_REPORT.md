# BUILD_FIX_REPORT.md - Android Build Stack Fixes & Enhancements

This document details all the changes made to resolve Gradle, Hilt, Kotlin, and Java compatibility issues in the Smart Ambulance System Android application, migrating the build pipeline into the requested stable environment in `android-app/`.

---

## 🛠️ Resolved Build Failures & Errors

### 1. Android Gradle Plugin (AGP) Downgrade to 8.7.3
* **Issue**: The project was initially targeting AGP version `9.0.1`, which was causing instability and strict requirements.
* **Resolution**: Downgraded AGP to the stable `8.7.3` release in `gradle/libs.versions.toml`.

### 2. Java 21 & Gradle 8.10 Compatibility
* **Issue**: Unresolved JVM toolchain configurations and Java 21 conflicts.
* **Resolution**:
  - Configured Gradle Wrapper to `8.10` inside `gradle/wrapper/gradle-wrapper.properties` to ensure native support for Java 21.
  - Set `compileOptions` and `kotlinOptions` to target Java 21 explicitly within the app-level `build.gradle.kts`.
  - Configured the GitHub Actions workflow runner to set up JDK 21 (`java-version: '21'`).

### 3. Hilt & Kapt Configuration Type Mismatch
* **Issue**: Compatibility errors between Dagger Hilt compiler (`libs.hilt.compiler`) and kotlin-kapt.
* **Resolution**:
  - Updated the Dagger Hilt version to `2.51.1`.
  - Re-registered the correct kapt declarations and added dependency injections to ViewModels and Repositories.

### 4. Kotlin & Compose Compiler Plugin Incompatibilities
* **Issue**: Incompatibilities between Kotlin versions and Compose versions.
* **Resolution**:
  - Aligned Kotlin version to `2.1.21` in `libs.versions.toml`.
  - Applied the modern Kotlin Compose Compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) mapping Kotlin `2.1.21`.

---

## ⚙️ Architecture & DI Refactoring

### 1. Dependency Injection (Dagger Hilt)
* **Application entry point**: Annotated [SmartAmbulanceApp.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android-app/app/src/main/java/com/example/smartambulance/SmartAmbulanceApp.kt) with `@HiltAndroidApp`.
* **MainActivity entry point**: Annotated [MainActivity.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android-app/app/src/main/java/com/example/smartambulance/MainActivity.kt) with `@AndroidEntryPoint`.
* **Repository injection**: Constructor-injected the `ApiService` interface in all Repository classes:
  - [AuthRepository.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android-app/app/src/main/java/com/example/smartambulance/data/repository/AuthRepository.kt)
  - [DriverRepository.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android-app/app/src/main/java/com/example/smartambulance/data/repository/DriverRepository.kt)
  - [AdminRepository.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android-app/app/src/main/java/com/example/smartambulance/data/repository/AdminRepository.kt)
  - [EmergencyRepository.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android-app/app/src/main/java/com/example/smartambulance/data/repository/EmergencyRepository.kt)
* **ViewModel Injection**: Marked all view models with `@HiltViewModel` and `@Inject constructor` tags:
  - `AuthViewModel`, `UserViewModel`, `DriverViewModel`, `AdminViewModel`, and `MainScreenViewModel`.
* **Hilt Module**: Added [AppModule.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android-app/app/src/main/java/com/example/smartambulance/di/AppModule.kt) to bind/provide singleton dependencies like `ApiService` and `DataRepository`.

### 2. Forgot Password Flow
* Implemented `sendPasswordResetEmail(email)` via Firebase Auth inside `AuthRepository`.
* Added `PasswordResetSent` states to `AuthUiState` inside `AuthViewModel`.
* Integrated the "Forgot Password" clickable text element and Toast notifications in `LoginScreen.kt`.

### 3. Firebase Cloud Messaging (FCM) Receiver
* Created `SmartAmbulanceMessagingService` to handle background alerts and status changes (Patient, Driver, and Admin alerts).
* Registered the FCM service and added permissions (`POST_NOTIFICATIONS`) in the app [AndroidManifest.xml](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android-app/app/src/main/AndroidManifest.xml).

---

## 🚀 Verification & Build Commands

* **Compile Debug**: `./gradlew assembleDebug`
* **Clean Project**: `./gradlew clean`
* **Unit Tests**: `./gradlew testDebugUnitTest`
* **Appium E2E Runner**: `node automated_test/appium_test_runner.js` (pointing to new `./android-app` APK artifact).

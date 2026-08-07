# Smart Ambulance System - Native Android Application (Kotlin)

This is the production-ready native Android application for the Smart Ambulance System, built using **Kotlin**, **Jetpack Compose**, and the **MVVM Architecture** with dependency injection.

---

## 🛠️ Architecture & Technology Stack

- **UI Framework**: Jetpack Compose (Material Design 3)
- **Dependency Injection**: Dagger Hilt (with `@HiltAndroidApp` & `@HiltViewModel`)
- **Networking**: Retrofit 2 & OkHttp 3 for REST API integration
- **Database / Backend**:
  - Node.js Express Backend Integration (via Retrofit API client)
  - Firebase Authentication (programmatically configured)
  - Firebase Cloud Firestore (live tracking data storage)
  - Firebase Cloud Storage (accident photo uploads)
  - Firebase Cloud Messaging (FCM push notifications)
- **Map & Location**: Google Maps SDK & Fused Location Provider
- **Testing**:
  - Unit Tests: JUnit & MockK for ViewModel and Repository testing
  - E2E UI Automation: Appium (Node.js framework)

---

## 📁 Key Directories & Modules

- [com.example.smartambulance](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android/app/src/main/java/com/example/smartambulance):
  - [data/repository/](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android/app/src/main/java/com/example/smartambulance/data/repository): Repositories handling Firestore, Firebase, and Retrofit network requests.
  - [ui/viewmodel/](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android/app/src/main/java/com/example/smartambulance/ui/viewmodel): Hilt ViewModels implementing application logic.
  - [ui/screens/](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android/app/src/main/java/com/example/smartambulance/ui/screens): Jetpack Compose screens for User, Driver, and Admin interfaces.
  - [di/AppModule.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android/app/src/main/java/com/example/smartambulance/di/AppModule.kt): Dagger Hilt module providing Singleton instances.
  - [SmartAmbulanceMessagingService.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android/app/src/main/java/com/example/smartambulance/SmartAmbulanceMessagingService.kt): Service processing incoming FCM push notifications.

---

## 🔑 Credential Setup & API Configurations

1. **Firebase Options**:
   - The Firebase client is initialized programmatically inside the [SmartAmbulanceApp](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android/app/src/main/java/com/example/smartambulance/SmartAmbulanceApp.kt) constructor using options fetched from the environment credentials.
2. **Google Maps SDK Key**:
   - The map layout key is declared inside [AndroidManifest.xml](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android/app/src/main/AndroidManifest.xml) under metadata key `com.google.android.geo.API_KEY`.
3. **Local Dev Backend Port**:
   - Set inside [RetrofitClient.kt](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/android/app/src/main/java/com/example/smartambulance/data/api/RetrofitClient.kt) mapping backend requests to local loopback `http://10.0.2.2:5000` (for emulator access) and fallback `http://localhost:5000`.

---

## 🚀 Building and Compiling the Project

Ensure you have **Java JDK 17** installed and configured on your environment variables.

### Clean Project
To clear any previous caches or artifacts:
```bash
./gradlew clean
```

### Compile Debug Build
To build the debug APK:
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## 🧪 Verification & Testing

### Running Local Unit Tests
We use JUnit and MockK to test repositories and ViewModels. Run the unit tests locally via:
```bash
./gradlew testDebugUnitTest
```

### Running the Appium E2E Automation Suite
To execute the Appium automation suite containing **300 testcases** across Authentication, User, Driver, Admin, Maps, and Security categories:
1. Ensure Node.js is installed.
2. Run the test script from the project root:
   ```bash
   node automated_test/appium_test_runner.js
   ```
3. A formatted Excel file containing detailed logs and statuses of the 300 testcases will be written to:
   [automated_test/Appium_Test_Report_300.xlsx](file:///c:/Users/konka/OneDrive/Desktop/Smart%20Ambulance%20System/automated_test/Appium_Test_Report_300.xlsx)

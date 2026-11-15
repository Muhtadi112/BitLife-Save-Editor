# BitLife Save Editor — ADI (Android source project)

Offline Android starter project (Kotlin + Jetpack Compose), package `com.adi.bitlifesaveeditor`.

## Current build scope (Alpha 0.1)
- Import a BitLife save through Android Storage Access Framework (no root, no broad storage permission).
- Read-only binary inspection: file size, SHA-256, header bytes, and visible marker strings.
- Export an unchanged copy as a backup.
- UI placeholders for money, age/stats, family, career/education/assets.

## Important limitation
This is deliberately a **read-only alpha**. The save appears to contain .NET BinaryFormatter-style serialized data (e.g. `Assembly-CSharp`, `Life3`). Finding strings such as `BankBalance` or seeing a changed floating-point value is not enough to safely patch the object graph. The previously discussed candidate offset is not a verified field mapping. Do not use raw byte replacement or claim unlimited money is implemented until a parser can deserialize and reserialize the exact game save while preserving references and record lengths.

## Build APK with GitHub Actions (no PC required)
1. Create a new GitHub repository and upload the contents of this project (not the ZIP file itself).
2. Make sure `.github/workflows/android-apk.yml` is included.
3. Open the repository's **Actions** tab.
4. Select **Build Android APK** and tap **Run workflow** (or push to `main` to trigger it).
5. Open the completed workflow run, scroll to **Artifacts**, and download `BitLifeSaveEditor-debug-apk`.
6. Extract the downloaded artifact ZIP on Android to get `app-debug.apk`.

The workflow uses JDK 17 and Gradle 8.9, then runs `assembleDebug`.

## Local build
Open this folder in Android Studio (recent version), use JDK 17, and run `gradle assembleDebug` with Gradle 8.9 or compatible. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Compile SDK 35, min SDK 26, target SDK 35. The app has no network permission and no internet dependency at runtime. GitHub is used only as a cloud build service.


## Alpha 0.2 development

Adds offline binary marker scanning and offset/context reporting. This build remains read-only: it does not modify serialized save values until field types and offsets are verified.

# BitLife Save Editor — Android Edition by ADI

Offline Android app built with Kotlin and Jetpack Compose. Package: `com.adi.bitlifesaveeditor`.

## Current Alpha 0.7 scope

- Import `savedLife.data` through Android Storage Access Framework (no root and no broad storage permission).
- Keep the imported source bytes in memory as a separate copy.
- Export an unchanged backup to a new destination.
- Experimental Money Editor with preset and custom numeric amounts.
- SHA-256 fingerprints for the source and edited copy.
- Final verification before export: the file length must remain identical, the expected candidate must still be present, the edited Double must equal the requested amount, and every byte outside the 8-byte candidate range must remain unchanged.
- Unit tests for the patch engine using synthetic NRBF fixtures.

## Critical BinaryFormatter limitation

The current money implementation is a **guarded fixed-width candidate patcher**, not a general .NET BinaryFormatter/NRBF deserializer and serializer. It checks the standard NRBF stream header plus the known `Assembly-CSharp`, `Life3`, and unique `SimBankLoan` markers, then checks the candidate Double at 19 bytes after that marker. It changes only those 8 bytes in a copied file and verifies the unchanged regions before export.

This reduces accidental edits, but it does **not** prove that every BitLife version uses the same object layout or that the game will accept the exported save. The automated tests use synthetic data and are not a substitute for an in-game round-trip test. Do not overwrite the original save. Import a copy, export under a new filename, and test the result in BitLife before relying on it.

## Build APK with GitHub Actions

The Android app itself has no network permission and does not require an account or server at runtime. GitHub Actions is only used as a build machine.

1. Open the `alpha-0.2-development` branch.
2. Open **Actions** and run **Build BitLife Save Editor Alpha 0.2**, or push a change to the project folder.
3. Wait for the **Run unit tests** and **Build debug APK** steps to finish successfully.
4. Download the `BitLifeSaveEditor-alpha-0.2-debug-apk` artifact from that run.
5. Extract the artifact ZIP to obtain `app-debug.apk`.

Build uses JDK 17 and Gradle 8.9. Android configuration: compile SDK 35, target SDK 35 (Android 15), minimum SDK 26.

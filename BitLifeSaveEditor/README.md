# BitLife Save Editor — Android Edition by ADI

Offline Android app built with Kotlin and Jetpack Compose. Package: `com.adi.bitlifesaveeditor`.

## Current Alpha 0.8 scope

- Import `savedLife.data` through Android Storage Access Framework (no root and no broad storage permission).
- Keep imported source bytes separate from the edited copy.
- Export an unchanged backup to a new destination.
- Money Editor for the serialized `Life.Finances.<BankBalance>k__BackingField` value.
- Preset and custom balance amounts.
- SHA-256 fingerprints for source and edited copies.
- Final verification: file length is unchanged, the detected value equals the requested amount, and every byte outside the exact 8-byte Double range is unchanged.
- Synthetic unit tests plus a regression test against two real save revisions.

## Money field mapping and limitations

The actual field was checked using Microsoft's read-only `System.Formats.Nrbf` decoder, which reads NRBF records without invoking BinaryFormatter or loading serialized game types. In both inspected revisions, the decoded value of `Life.Finances.<BankBalance>k__BackingField` matched the Double at 19 bytes after the unique `SimBankLoan` type-metadata marker:

| Save revision | Decoded BankBalance | Candidate byte offset |
| --- | ---: | ---: |
| Earlier save | 1,000,000,000.0 | `0xF2A3` |
| Later save | 853,244.6095520258 | `0xF29F` |

The `SimBankLoan` text is a metadata anchor in this layout; the decoder found no instantiated `SimBankLoan` object in either inspected save. The candidate itself was confirmed to be the first serialized Double field of the `SimFinances` object, `<BankBalance>k__BackingField`.

The Android implementation is still a **guarded fixed-layout patcher**, not a general NRBF parser/serializer. It checks the NRBF header, `Assembly-CSharp`, the root `Finances` member, `SimFinances`, `BankBalance`, and a unique metadata anchor before editing. Unknown layouts are rejected. The two-save check does not prove compatibility with every BitLife release or that the game will accept every exported save.

**Never overwrite the original.** Export to a new filename, keep a separate backup, and test the edited copy in BitLife before replacing any active save.

## Offline and permissions

The Android manifest requests no Internet permission. The app performs file selection and export through Android's Storage Access Framework. GitHub Actions and the .NET probe are development/build tools only; they are not required at runtime.

## Build APK with GitHub Actions

1. Open the `alpha-0.2-development` branch.
2. Open **Actions** and run **Build BitLife Save Editor Alpha 0.8**, or push a change under `BitLifeSaveEditor/`.
3. The workflow runs unit tests against synthetic fixtures and two committed save revisions, then builds the debug APK.
4. Download the `BitLifeSaveEditor-alpha-0.8-debug-apk` artifact.
5. Extract the artifact ZIP to obtain `app-debug.apk`.

Build uses JDK 17 and Gradle 8.9. Android configuration: compile SDK 35, target SDK 35 (Android 15), minimum SDK 26.

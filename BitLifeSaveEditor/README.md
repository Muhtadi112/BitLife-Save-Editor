# BitLife Save Editor — Android Edition by ADI

Offline Android application built with Kotlin and Jetpack Compose.

- Package: com.adi.bitlifesaveeditor
- Current source version: 0.9.7-alpha (versionCode 13)
- Android target: API 35 (Android 15)
- Minimum Android: API 26
- Root: not required
- Runtime network permission: none requested by the app manifest

## Current implementation

- Import a save through Android Storage Access Framework (SAF).
- Keep imported source bytes separate from the edited copy.
- Enforce a 64 MiB input limit.
- Export an unchanged backup and compare every byte after reading it back.
- Experimental Money Editor candidate for one Float64/Double balance field.
- Refuse export unless the selected destination is readable and zero-length; reject URI/canonical/document-identity aliases when those identities are available.
- Re-read the imported source before and after export and stop/flag the operation if the source changes.
- Verify exported bytes, file length, expected candidate value, unchanged bytes outside the 8-byte patch range, and SHA-256.

## Read-only Kotlin NRBF locator prototype

Alpha 0.9.7 carries a bounded, read-only Kotlin NRBF record walker for the exact object path Life.<Finances>k__BackingField -> SimFinances.<BankBalance>k__BackingField. It understands a conservative subset of common NRBF class, reference, string, array, primitive, and library records, enforces size/depth/object limits, and fails closed on unsupported records.

The parser now also applies a global class-member allocation budget and rejects overflowing five-byte 7-bit string lengths. This is parser hardening only; it does not establish compatibility with a real BitLife save.

Alpha 0.9.7 fixes the parser's member-count references for ClassWithMembers and ClassWithMembersAndTypes and adds a synthetic fixture for the no-type-metadata form. This source correction has not yet passed CI at the time of this commit. The prototype is not yet connected to the Money Editor patch path. Its unit tests use synthetic NRBF streams only. It must not be treated as proof that it can parse a real BitLife save until both user-supplied save copies are tested against it. The existing Money Editor remains the fixed-layout experimental patcher described below.

## Critical Money Editor limitation

The current Android Money Editor patch path is still a fixed-layout patcher, not a complete NRBF serializer. The new read-only locator is a separate prototype and is not used to authorize edits. It validates the NRBF stream header and the expected finance metadata strings, then reads a Double at a fixed offset relative to a SimBankLoan metadata marker.

Earlier development notes report that a separate read-only .NET NRBF decoder matched the candidate field to Life.Finances.<BankBalance>k__BackingField in two real save revisions. The actual save files are not present in this repository workspace or CI, so that mapping is not revalidated by the current workflow. The opt-in `RealSaveBalanceTest` now checks both the fixed-layout candidate and the Kotlin NRBF locator against the two local save copies when `BITLIFE_OLD_SAVE` and `BITLIFE_NEW_SAVE` are supplied. The normal CI skips that test and uses synthetic fixtures only; this does not prove compatibility with arbitrary BitLife saves.

A byte-for-byte export check proves only that the app wrote the bytes it intended to write. It does not prove that BitLife accepts the save or that the field mapping is semantically correct.

Do not replace the active save. Export to a new filename, keep a separate backup, and test the exported copy in BitLife. Stop if the game reports corruption or the displayed balance is wrong.

## Validation evidence

- GitHub Actions run #83 for commit `3a1041020adffea9a7fdac64e63912799b9755ce` completed successfully on 27 November 2026: https://github.com/Muhtadi112/BitLife-Save-Editor/actions/runs/45944826016
- `testDebugUnitTest`: 41 test cases, 40 passed, 1 skipped, 0 failures, 0 errors. The skipped case is `RealSaveBalanceTest`; no personal save paths were supplied to CI.
- `assembleDebug` completed successfully and uploaded `BitLifeSaveEditor-alpha-0.9.7-debug-apk` (artifact ID `15173983563`).
- Extracted APK: 16,375,684 bytes; SHA-256 `3fa634763bba190cacd1932cbd60b08c359951e570024593af91f71c5863e7db`.
- The Kotlin NRBF locator tests use synthetic streams only. No real BitLife save was included in CI.
- The APK manifest declares target SDK 35 / min SDK 26 and contains no `android.permission.INTERNET` request.
- Android 15 device installation, SAF provider behavior on a physical phone, and in-game save acceptance have not been verified.

The current workflow also uploads JUnit XML reports so future runs can distinguish passed synthetic tests from skipped opt-in real-save tests. Alpha 0.9.7 must pass a fresh CI run before its APK is considered buildable.

## Features

- Money Editor: experimental fixed-layout candidate patch only.
- Backup export: implemented with read-back byte verification.
- Dedicated Restore workflow: not implemented.
- Age Editor: not implemented.
- Character Stats: not implemented.
- Family & Relationships: not implemented.
- Career & Education: not implemented.
- Assets: not implemented.

## Read-only NRBF investigation tool

tools/NrbfProbe uses Microsoft's System.Formats.Nrbf reader to inspect two local save files without invoking BinaryFormatter object deserialization. Run it only on local copies:

    dotnet run --project tools/NrbfProbe -- /path/to/old-copy.data /path/to/new-copy.data

For the optional Android regression test, set BITLIFE_OLD_SAVE and BITLIFE_NEW_SAVE to the two local file paths before running testDebugUnitTest. Do not commit personal save files or upload them as CI artifacts.

## Privacy

Personal BitLife save files must never be committed to this repository. The repository's historical Git objects may still contain an earlier save upload; deleting a current working-tree copy or adding .gitignore does not purge Git history. Repository visibility and historical cleanup must be handled separately by the repository owner.

# BitLife Save Editor — Android Edition by ADI

Offline Android application built with Kotlin and Jetpack Compose. Package: `com.adi.bitlifesaveeditor`.

## Alpha 0.9.2 status

This is an experimental build. It is not a general-purpose .NET BinaryFormatter/NRBF editor.

Implemented in the current source:
- Import a save through Android's Storage Access Framework (no root and no broad storage permission).
- Keep source bytes separate from the edited byte array.
- Enforce a 64 MiB input limit.
- Export an unchanged backup and compare every byte after reading it back.
- Experimental fixed-layout Money Editor candidate for a Float64/Double balance field.
- Reject missing source URI and reject export when the destination URI is exactly the imported source URI.
- Reopen exported files and verify exact bytes, file length, the expected candidate value, and that bytes outside the 8-byte patch range are unchanged.
- SHA-256 fingerprints for imported, edited, and exported copies.
- JVM unit tests for the export-target guard and byte verifier.

## Critical Money Editor limitation

The current Android code is a **fixed-layout patcher**, not a complete NRBF parser or serializer. It checks an NRBF header and expected metadata strings, then reads a Double at a fixed offset from a metadata anchor. That is a conservative heuristic, not structural proof that every future BitLife save uses the same layout.

Earlier development notes report that a read-only NRBF decoder matched the candidate field against two save revisions. Those original save files are not present in this repository or this build workspace, and this CI workflow does not rerun that real-save analysis. The synthetic unit tests only validate the code against synthetic fixtures.

A byte-for-byte export check proves that the destination contains the bytes the app intended to write. It **does not** prove that BitLife accepts the save or that the edited field is semantically correct.

**Do not replace the active save.** Export under a new filename, preserve a separate backup, and test the exported copy in BitLife first. Stop if BitLife reports a corrupt save or the displayed balance does not match.

## Implemented vs. not implemented

- Money Editor: experimental fixed-layout candidate patch only.
- Backup export: implemented with read-back byte verification.
- Restore: not implemented as a dedicated workflow.
- Age, Character Stats, Family & Relationships, Career & Education, and Assets: not implemented.

## Offline/runtime permissions

The Android manifest requests no Internet permission. File selection and export use Android's Storage Access Framework. GitHub Actions is only used to build/test the APK; it is not needed when the app runs.

## CI

The Alpha 0.9.2 workflow runs JVM unit tests and builds a debug APK using JDK 17 and Gradle 8.9. It does not access personal save files and does not upload save files as artifacts.

## Save privacy

Never commit personal `savedLife.data` files to this repository. Earlier repository history reportedly included a save file; removing the latest copy alone does not remove historical copies. Keep the repository private and purge exposed history if that save is personal.

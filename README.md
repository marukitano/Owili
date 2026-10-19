# Owili

A gesture-driven list app for Android, inspired by Clear, with future Pebble smartwatch integration.

## First Android prototype (0.1.0)

- Kotlin + Jetpack Compose, native Android UI
- Ocean-inspired gradient and animated perspective cards
- Add items via keyboard or plus button
- Swipe right to mark complete (swipe again to undo); swipe left to delete
- Local persistence using Android SharedPreferences (temporary prototype storage)
- No account, network access, or permissions needed

**Current scope:** one list; no undo for deletion, drag-to-reorder, Room database, Pebble sync, or iOS build yet.

## Build

Use Android Studio with Android SDK 37 and JDK 17. Open the repository as a Gradle project.

The checked-in Gradle distribution is specified in `gradle/wrapper/gradle-wrapper.properties`; a Gradle wrapper JAR/script is **not yet included**. You can build with an installed Gradle 9.4.1:

```sh
gradle :app:assembleDebug
```

Or download the debug APK produced by the GitHub Actions **Android debug build** workflow (Actions → latest successful run → Artifacts).

## Licensing

Owili's source code is publicly available under the [PolyForm Noncommercial License 1.0.0](LICENSE.md).

You can download the source, build it, modify it, and use or share it for **noncommercial purposes** under the license terms. Commercial exploitation, including selling builds or publishing paid versions, requires separate permission from the rights holder(s).

Official app-store releases may be sold by the project owner under separately held rights. Owili is **source-available**, not OSI-approved open source.

**Commercial licensing enquiries:** Contact the repository owner through GitHub.

**Contributions:** Before accepting external code contributions, we will establish terms that allow official commercial distribution.

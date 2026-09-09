# LiteralShield

![LiteralShield banner](docs/images/literalshield-banner.png)

LiteralShield is an Android Gradle plugin that obfuscates selected string literals in your own Android module bytecode.

## Requirements

- JDK 17
- Android Gradle Plugin 8.13.2
- Android module with `minSdk` 23 or higher

## Install

LiteralShield is designed to be built by JitPack from a Git tag, so maintainers do not need to upload artifacts to GitHub Packages. Add JitPack to the consuming project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Use a Git tag, commit hash, or branch snapshot as `<version>`. JitPack builds the repository on demand; no package-upload workflow is required.

Apply the plugin to each Android application, library, or dynamic-feature module that you want to protect:

```kotlin
plugins {
    id("com.android.application")
    id("io.github.jevhee.literalshield") version "<version>"
}
```

For JitPack, configure Gradle plugin resolution to use the plugin module shown by the JitPack build page for the selected tag. The same page lists the runtime module coordinate. This avoids credentials and keeps the project compatible with other Maven repositories.

## Configure protection

```kotlin
literalShield {
    enabled.set(true)
    mode.set("PROTECT")
    buildTypes.set(setOf("release"))
    minimumLength.set(4)
    excludePackages.add("com.example.generated")
    excludeClasses.add("**/BuildConfig")
    ignoredValues.add("success")
    includePatterns.add("^custom:.*")
    buildSeed.set("release-001")

    // Set these when the runtime is served by JitPack or another Maven host.
    runtimeCoordinates.set("<runtime-group>:literalshield-runtime")
    runtimeVersion.set("<version>")
}
```

All configuration properties use Gradle `Property`, `SetProperty`, or `ListProperty` APIs. Use `set(...)` to replace a value and `add(...)` to append one value.

| Property | Default | Use |
| --- | --- | --- |
| `enabled` | `true` | Enables or disables LiteralShield for the module. |
| `mode` | `"PROTECT"` | Use `"PROTECT"` to instrument bytecode or `"REPORT"` to scan only. |
| `buildTypes` | `setOf("release")` | Selects Android build types to process. |
| `minimumLength` | `4` | Skips literals shorter than this value. |
| `excludePackages` | empty | Skips Java/Kotlin package prefixes. |
| `excludeClasses` | empty | Skips class paths using `*` and `**` glob patterns. |
| `ignoredValues` | empty | Skips exact string values. |
| `includePatterns` | empty | Includes values matched by a Kotlin regular expression. |
| `buildSeed` | generated | Sets a stable seed for reproducible protected builds. |
| `runtimeCoordinates` | `io.github.jevhee.literalshield:literalshield-runtime` | Sets the Maven group and artifact for the runtime dependency. |
| `runtimeVersion` | `0.1.0-SNAPSHOT` | Sets the runtime dependency version. Match it to the plugin release or JitPack tag. |

## Scan before protecting

Use `REPORT` mode to inspect eligible literals without modifying an artifact:

```kotlin
literalShield { mode.set("REPORT") }
```

Then run:

```sh
./gradlew :your-module:literalShieldReport --no-configuration-cache
```

The task writes `build/reports/literalshield/<variant>/scan.json` with class/member locations, length, risk level, decision, and reason. It does not write literal plaintext.

## Behaviour and limitations

- Apply the plugin to every internal module you want to protect. It does not instrument dependencies or unrelated modules.
- Android resources, annotations and metadata, exported constants, excluded values, and unsupported cases can still contain the original value.
- `REPORT` mode does not add the runtime dependency and never transforms classes.
- Configuration-cache reuse is not supported with the current AGP integration. Build with `--no-configuration-cache`.

## License

Copyright 2026 jevhee.

LiteralShield is available under the [Apache License 2.0](LICENSE).

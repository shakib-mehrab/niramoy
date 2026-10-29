pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // Tesseract4Android (on-device OCR, Apache-2.0) is published only on JitPack.
    maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.adaptech-cz.*") } }
  }
}

rootProject.name = "Niramoy"

include(":app")

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.spotless) apply true
}

spotless {
  kotlin {
    target("**/*.kt")
    ktfmt("0.63").googleStyle().configure {
      it.setMaxWidth(80)
      it.setBlockIndent(2)
      it.setContinuationIndent(2)
      it.setRemoveUnusedImports(true)
      it.setManageTrailingCommas(false)
    }
  }
  
  kotlinGradle {
    target("*.gradle.kts")
    ktlint("1.8.0")
      .setEditorConfigPath("$projectDir/.editorconfig")
      .editorConfigOverride(
        mapOf(
          "ktlint_code_style" to "intellij_idea"
        )
      )
    }
  }

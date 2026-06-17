// Top-level build file where you can add configuration options common to all sub-projects/modules.
import com.diffplug.spotless.kotlin.KtfmtStep.TrailingCommaManagementStrategy

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.spotless) apply true
}

spotless {
  kotlin {
    target("**/*.kt")
    ktfmt().googleStyle().configure {
      it.setMaxWidth(80)
      it.setBlockIndent(2)
      it.setContinuationIndent(2)
      it.setRemoveUnusedImports(true)
      it.setTrailingCommaManagementStrategy(
        TrailingCommaManagementStrategy.NONE,
      )
    }
  }

  kotlinGradle {
    target("*.gradle.kts")
    ktlint()
      .setEditorConfigPath("$projectDir/.editorconfig")
      .editorConfigOverride(
        mapOf(
          "ktlint_code_style" to "intellij_idea",
        ),
      )
  }
}

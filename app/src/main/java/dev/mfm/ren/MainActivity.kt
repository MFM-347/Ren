package dev.mfm.ren

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.mfm.ren.ui.RenScreen
import dev.mfm.ren.ui.theme.AppTheme
import dev.mfm.ren.viewmodel.RenViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: RenViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          RenScreen(viewModel = viewModel)
        }
      }
    }
  }
}

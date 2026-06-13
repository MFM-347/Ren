package com.example.renpy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.renpy.ui.RenPyScreen
import com.example.renpy.viewmodel.RenPyViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: RenPyViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          RenPyScreen(viewModel = viewModel)
        }
      }
    }
  }
}

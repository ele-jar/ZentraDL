package com.elejar.ZentraDL

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

// Phase 0 skeleton activity (real nav + theme land in Phase 2).
// Exists so the CI skeleton compiles and the manifest resolves.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("ZentraDL")
            }
        }
    }
}

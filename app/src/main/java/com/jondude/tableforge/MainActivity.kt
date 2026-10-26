package com.jondude.tableforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jondude.tableforge.ui.AppShell
import com.jondude.tableforge.ui.theme.TableForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TableForgeTheme { AppShell() }
        }
    }
}

package com.uttt.gogirls

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.uttt.gogirls.navigation.AppNavigation
import com.uttt.gogirls.ui.LoginScreen
import com.uttt.gogirls.ui.RegisterScreen
import com.uttt.gogirls.ui.theme.GoGirlsTheme
import com.uttt.gogirls.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val authViewModel = remember { AuthViewModel() }

            MaterialTheme {
                AppNavigation(navController, authViewModel)
            }
        }

    }
}


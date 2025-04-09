package com.uttt.gogirls.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.uttt.gogirls.ui.*
import com.uttt.gogirls.ui.home.HistorialConductoraScreen
import com.uttt.gogirls.ui.home.HistorialPasajeraScreen
import com.uttt.gogirls.ui.home.HomeConductoraScreen
import com.uttt.gogirls.ui.home.HomePasajeraScreen
import com.uttt.gogirls.viewmodel.AuthViewModel

@Composable
fun AppNavigation(navController: NavHostController, authViewModel: AuthViewModel) {
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { doc ->
                                val role = doc.getString("role")
                                when (role) {
                                    "pasajera" -> navController.navigate("home_pasajera") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                    "conductora" -> navController.navigate("home_conductora") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                    else -> Log.e("Navigation", "Rol desconocido")
                                }
                            }
                            .addOnFailureListener {
                                Log.e("Navigation", "Error leyendo Firestore: ${it.message}")
                            }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                authViewModel = authViewModel,
                onRegisterSuccess = {
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onNavigateToLogin = { // ✅ Agregado para permitir regresar al login
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                }
            )
        }

        composable("home_pasajera") {
            HomePasajeraScreen(navController)
        }

        composable("home_conductora") {
            HomeConductoraScreen(navController)
        }
        composable("historial_pasajera") {
            HistorialPasajeraScreen(navController)
        }
        composable("historial_conductora") {
            HistorialConductoraScreen(navController)
        }


    }
}

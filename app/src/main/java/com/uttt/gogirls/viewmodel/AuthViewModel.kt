package com.uttt.gogirls.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.uttt.gogirls.model.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun registerUser(
        name: String,
        email: String,
        password: String,
        role: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 🔐 Crea el usuario en Auth y espera la respuesta
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid ?: throw Exception("UID nulo")

                // 📝 Guarda en Firestore y espera que se guarde
                val newUser = User(
                    uid = uid,
                    name = name,
                    email = email,
                    role = role,
                    available = role == "conductora",
                    location = GeoPoint(0.0, 0.0)
                )

                db.collection("users").document(uid).set(newUser).await()
                onSuccess()

            } catch (e: Exception) {
                onError("Error: ${e.message}")
            }
        }
    }
    fun loginUser(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError("Error al iniciar sesión: ${e.message}")
            }
    }

}

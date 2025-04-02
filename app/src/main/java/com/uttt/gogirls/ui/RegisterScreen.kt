package com.uttt.gogirls.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uttt.gogirls.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("pasajera") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Registro - Go Girls", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Rol:")
            Spacer(modifier = Modifier.width(8.dp))
            listOf("pasajera", "conductora").forEach {
                RadioButton(
                    selected = role == it,
                    onClick = { role = it }
                )
                Text(it.replaceFirstChar { char -> char.uppercaseChar() })
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    errorMessage = "Correo inválido"
                    return@Button
                }

                if (password.length < 6) {
                    errorMessage = "La contraseña debe tener al menos 6 caracteres"
                    return@Button
                }

                if (name.isBlank()) {
                    errorMessage = "El nombre no puede estar vacío"
                    return@Button
                }

                authViewModel.registerUser(
                    name, email, password, role,
                    onSuccess = {
                        errorMessage = null
                        Toast.makeText(context, "¡Registro exitoso!", Toast.LENGTH_SHORT).show()

                        // Le damos un mini delay para que se vea el Toast antes de cambiar pantalla
                        coroutineScope.launch {
                            delay(500)
                            onRegisterSuccess()
                        }
                    },
                    onError = {
                        errorMessage = it
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Registrarse")
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

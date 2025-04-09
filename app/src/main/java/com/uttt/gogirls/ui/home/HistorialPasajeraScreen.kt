package com.uttt.gogirls.ui.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SimpleDateFormat")
@Composable
fun HistorialPasajeraScreen(navController: NavHostController) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var viajes by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val pinkColor = Color(0xFFFF6B98)

    LaunchedEffect(uid) {
        uid?.let {
            firestore.collection("viajes")
                .whereEqualTo("pasajeraUid", it)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { result ->
                    val lista = mutableListOf<Map<String, Any>>()

                    result.documents.forEach { doc ->
                        val data = doc.data?.toMutableMap() ?: return@forEach
                        data["id"] = doc.id
                        val conductoraUid = data["conductoraUid"] as? String
                        if (conductoraUid != null) {
                            firestore.collection("users").document(conductoraUid).get()
                                .addOnSuccessListener { userDoc ->
                                    data["nombreConductora"] = userDoc.getString("name") ?: "Sin nombre"
                                    lista.add(data)
                                    viajes = lista.sortedByDescending { (it["timestamp"] as Timestamp).toDate() }
                                }
                        } else {
                            data["nombreConductora"] = "Sin asignar"
                            lista.add(data)
                            viajes = lista.sortedByDescending { (it["timestamp"] as Timestamp).toDate() }
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error cargando historial", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Historial de viajes", fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pinkColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (viajes.isEmpty()) {
                Text("No hay viajes en tu historial", color = Color.Gray)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(viajes) { viaje ->
                        val fecha = (viaje["timestamp"] as? Timestamp)?.toDate()
                        val fechaFormateada = fecha?.let {
                            SimpleDateFormat("dd/MM/yyyy HH:mm").format(it)
                        } ?: "--"

                        val estado = (viaje["estado"] as? String)?.replaceFirstChar { it.uppercase() } ?: "Desconocido"
                        val conductora = viaje["nombreConductora"] as? String ?: "Sin asignar"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Fecha: $fechaFormateada", fontWeight = FontWeight.SemiBold)
                                Text("Estado: $estado", color = Color.DarkGray)
                                Text("Conductora: $conductora", color = Color.DarkGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

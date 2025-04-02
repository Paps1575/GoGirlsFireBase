package com.uttt.gogirls.ui.home

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeConductoraScreen(navController: NavHostController) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid

    var viajeActual by remember { mutableStateOf<Map<String, Any>?>(null) }
    var viajesPendientes by remember { mutableStateOf(listOf<Map<String, Any>>()) }

    // Escuchar viaje actual aceptado
    DisposableEffect(uid) {
        val listener = firestore.collection("viajes")
            .whereEqualTo("estado", "aceptado")
            .whereEqualTo("conductoraUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    viajeActual = null
                    return@addSnapshotListener
                }

                val aceptado = snapshot.documents
                    .firstOrNull { it.getString("estado") == "aceptado" }

                if (aceptado != null) {
                    val data = aceptado.data?.toMutableMap() ?: return@addSnapshotListener
                    data["id"] = aceptado.id
                    val pasajeraUid = data["pasajeraUid"] as? String ?: return@addSnapshotListener

                    firestore.collection("users").document(pasajeraUid).get()
                        .addOnSuccessListener { userDoc ->
                            val nombre = userDoc.getString("name") ?: "Desconocida"
                            data["nombrePasajera"] = nombre
                            viajeActual = data
                        }
                } else {
                    viajeActual = null
                }
            }

        onDispose { listener.remove() }
    }


    // Escuchar viajes pendientes solo si no hay viaje en curso
    DisposableEffect(viajeActual) {
        if (viajeActual == null) {
            val listener = firestore.collection("viajes")
                .whereEqualTo("estado", "pendiente")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    val viajesTemp = mutableListOf<Map<String, Any>>()

                    snapshot.documents.forEach { doc ->
                        val data = doc.data?.toMutableMap() ?: return@forEach
                        data["id"] = doc.id

                        val pasajeraUid = data["pasajeraUid"] as? String ?: return@forEach

                        firestore.collection("users").document(pasajeraUid).get()
                            .addOnSuccessListener { userDoc ->
                                data["nombrePasajera"] = userDoc.getString("name") ?: "Desconocida"
                                viajesTemp.add(data)

                                viajesPendientes = viajesTemp.sortedByDescending {
                                    (it["timestamp"] as? Timestamp)?.toDate()
                                }
                            }
                    }
                }

            onDispose { listener.remove() }
        } else {
            onDispose { /* listener no registrado */ }
        }
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bienvenida conductora 🚗", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Mostrar solo si el viaje actual existe y no ha sido finalizado
        if (viajeActual != null && viajeActual?.get("estado") != "finalizado") {
            val viaje = viajeActual!!
            val nombre = viaje["nombrePasajera"] as? String ?: "Pasajera"
            val fecha = (viaje["timestamp"] as? Timestamp)?.toDate()
            val id = viaje["id"] as String
            val geo = viaje["ubicacion"] as? GeoPoint
            val fechaFormateada = fecha?.let {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
            } ?: "--"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Viaje en curso", style = MaterialTheme.typography.titleMedium)
                    Text("Pasajera: $nombre")
                    Text("Fecha: $fechaFormateada")

                    geo?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        val cameraPositionState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(
                                LatLng(geo.latitude, geo.longitude), 15f
                            )
                        }

                        GoogleMap(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            cameraPositionState = cameraPositionState
                        ) {
                            Marker(
                                state = MarkerState(position = LatLng(geo.latitude, geo.longitude)),
                                title = "Ubicación de la pasajera"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        firestore.collection("viajes").document(id)
                            .update("estado", "finalizado")
                            .addOnSuccessListener {
                                Log.d("ViajeFinalizado", "Viaje $id actualizado a finalizado")
                                Toast.makeText(context, "Viaje finalizado", Toast.LENGTH_SHORT).show()
                                viajeActual = null
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Error al finalizar viaje", Toast.LENGTH_SHORT).show()
                            }
                    }) {
                        Text("Finalizar viaje")
                    }
                }
            }
        } else {
            // Mostrar lista de viajes pendientes
            if (viajesPendientes.isEmpty()) {
                Text("No hay viajes pendientes")
            } else {
                LazyColumn {
                    items(viajesPendientes) { viaje ->
                        val nombre = viaje["nombrePasajera"] as? String ?: "Pasajera"
                        val fecha = (viaje["timestamp"] as? Timestamp)?.toDate()
                        val id = viaje["id"] as? String ?: return@items
                        val fechaFormateada = fecha?.let {
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
                        } ?: "--"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Pasajera: $nombre")
                                Text("Fecha: $fechaFormateada")
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    firestore.collection("viajes").document(id)
                                        .update(
                                            mapOf(
                                                "estado" to "aceptado",
                                                "conductoraUid" to uid
                                            )
                                        )
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "¡Viaje aceptado!", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(context, "Error al aceptar viaje", Toast.LENGTH_SHORT).show()
                                        }
                                }) {
                                    Text("Aceptar viaje")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
            navController.navigate("login") {
                popUpTo("home_conductora") { inclusive = true }
            }
        }) {
            Text("Cerrar sesión")
        }
    }
}

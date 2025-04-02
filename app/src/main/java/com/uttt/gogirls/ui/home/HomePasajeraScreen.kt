package com.uttt.gogirls.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import com.google.accompanist.permissions.isGranted
import com.google.android.gms.location.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomePasajeraScreen(navController: NavHostController) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid

    var viajeId by remember { mutableStateOf<String?>(null) }
    var viajeEstado by remember { mutableStateOf<String?>(null) }
    var conductoraNombre by remember { mutableStateOf<String?>(null) }

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Buscar viaje más reciente
    LaunchedEffect(uid) {
        uid?.let {
            firestore.collection("viajes")
                .whereEqualTo("pasajeraUid", it)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { docs ->
                    val doc = docs.firstOrNull()
                    if (doc != null) {
                        viajeId = doc.id
                        viajeEstado = doc.getString("estado")
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error buscando el último viaje: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Escuchar cambios en el viaje
    DisposableEffect(viajeId) {
        val listener = viajeId?.let { id ->
            firestore.collection("viajes").document(id)
                .addSnapshotListener { doc, error ->
                    if (error == null && doc != null && doc.exists()) {
                        val estado = doc.getString("estado")
                        viajeEstado = estado

                        if (estado == "aceptado") {
                            val conductoraUid = doc.getString("conductoraUid")
                            if (conductoraUid != null) {
                                firestore.collection("users").document(conductoraUid).get()
                                    .addOnSuccessListener { userDoc ->
                                        conductoraNombre = userDoc.getString("name")
                                    }
                            }
                        }
                    }
                }
        }
        onDispose {
            listener?.remove()
        }
    }

    @SuppressLint("MissingPermission")
    fun solicitarNuevoViajeConUbicacion() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            val geoPoint = if (location != null) GeoPoint(location.latitude, location.longitude) else GeoPoint(0.0, 0.0)

            val viaje = hashMapOf(
                "pasajeraUid" to uid,
                "estado" to "pendiente",
                "timestamp" to Timestamp.now(),
                "ubicacion" to geoPoint
            )

            firestore.collection("viajes").add(viaje)
                .addOnSuccessListener { doc ->
                    viajeId = doc.id
                    Toast.makeText(context, "¡Nuevo viaje solicitado!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error al solicitar viaje", Toast.LENGTH_LONG).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bienvenida pasajera 👧", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        Text("Estado actual: ${viajeEstado ?: "null"}")
        Spacer(modifier = Modifier.height(8.dp))

        when (viajeEstado) {
            null -> {
                Text("Presiona el botón para solicitar tu primer viaje.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    if (!locationPermissionState.status.isGranted) {
                        locationPermissionState.launchPermissionRequest()
                    } else {
                        solicitarNuevoViajeConUbicacion()
                    }
                }) {
                    Text("Solicitar viaje")
                }
            }
            "pendiente" -> Text("Tu viaje está pendiente, esperando una conductora...")
            "aceptado" -> Text("Tu viaje fue aceptado por: $conductoraNombre")
            "finalizado" -> {
                Text("Tu viaje fue finalizado. ¿Quieres pedir otro?")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    if (!locationPermissionState.status.isGranted) {
                        locationPermissionState.launchPermissionRequest()
                    } else {
                        solicitarNuevoViajeConUbicacion()
                    }
                }) {
                    Text("Solicitar nuevo viaje")
                }
            }
            else -> Text("Estado del viaje: $viajeEstado")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
            navController.navigate("login") {
                popUpTo("home_pasajera") { inclusive = true }
            }
        }) {
            Text("Cerrar sesión")
        }
    }
}

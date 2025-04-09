package com.uttt.gogirls.ui.home

import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.compose.*
import com.uttt.gogirls.network.DirectionsClient
import com.uttt.gogirls.utils.PolylineDecoder
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeConductoraScreen(navController: NavHostController) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid

    var viajeActual by remember { mutableStateOf<Map<String, Any>?>(null) }
    var viajesPendientes by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    // al inicio del composable
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var polylinePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var origen by remember { mutableStateOf<LatLng?>(null) }
    var destino by remember { mutableStateOf<LatLng?>(null) }

    var duracionEstimada by remember { mutableStateOf("--") }
    var distanciaEstimada by remember { mutableStateOf("--") }


    DisposableEffect(viajeActual) {
        if (viajeActual == null) {
            val listener = firestore.collection("viajes")
                .whereEqualTo("estado", "pendiente")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    val documentos = snapshot.documents
                    if (documentos.isEmpty()) {
                        Log.d("ViajesPendientes", "No hay viajes pendientes.")
                        viajesPendientes = emptyList()
                        return@addSnapshotListener
                    }

                    val viajesTemp = mutableListOf<Map<String, Any>>()
                    var completados = 0

                    documentos.forEach { doc ->
                        Log.d("ViajesPendientes", "Viaje detectado con ID: ${doc.id}")
                        val data = doc.data?.toMutableMap() ?: return@forEach
                        data["id"] = doc.id
                        val pasajeraUid = data["pasajeraUid"] as? String ?: return@forEach

                        firestore.collection("users").document(pasajeraUid).get()
                            .addOnSuccessListener { userDoc ->
                                data["nombrePasajera"] = userDoc.getString("name") ?: "Desconocida"
                                viajesTemp.add(data)
                            }
                            .addOnCompleteListener {
                                completados++
                                if (completados == documentos.size) {
                                    viajesPendientes = viajesTemp.sortedByDescending {
                                        (it["timestamp"] as? Timestamp)?.toDate()
                                    }
                                    Log.d("ViajesPendientes", "Total viajes cargados: ${viajesTemp.size}")
                                }
                            }
                    }
                }

            onDispose { listener.remove() }
        } else {
            onDispose { }
        }
    }


    // Escucha del viaje aceptado actual
    DisposableEffect(uid) {
        val listener = firestore.collection("viajes")
            .whereEqualTo("estado", "aceptado")
            .whereEqualTo("conductoraUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    viajeActual = null
                    return@addSnapshotListener
                }


                val aceptado = snapshot.documents.firstOrNull { it.getString("estado") == "aceptado" }

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

    // Escucha de viajes pendientes (corregido)
// 🔁 Obtener y trazar ruta cuando cambia el viaje
    LaunchedEffect(viajeActual) {
        val geo = viajeActual?.get("ubicacion") as? GeoPoint ?: return@LaunchedEffect
        destino = LatLng(geo.latitude, geo.longitude)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    origen = LatLng(it.latitude, it.longitude)

                    // 🔁 Lanzamos una corrutina para usar la función suspend
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val response = DirectionsClient.getRoute(origen!!, destino!!)
                            val route = response.routes.firstOrNull()
                            val leg = route?.legs?.firstOrNull()

                            duracionEstimada = leg?.duration?.text ?: "--"
                            distanciaEstimada = leg?.distance?.text ?: "--"

                            val poly = route?.overviewPolyline?.points
                            if (!poly.isNullOrEmpty()) {
                                polylinePoints = PolylineDecoder.decode(poly)
                            }
                        } catch (e: Exception) {
                            Log.e("RUTA", "Error al trazar ruta: ${e.message}")
                        }
                    }
                }
            }
        }
    }


    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Go Girls In Drive",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    )
                    Text(
                        text = "Viajes seguros para mujeres",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = {
                        FirebaseAuth.getInstance().signOut()
                        Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                        navController.navigate("login") {
                            popUpTo("home_conductora") { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                        contentDescription = "Cerrar sesión",
                        tint = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // VIAJE ACTUAL
            AnimatedVisibility(
                visible = viajeActual != null && viajeActual?.get("estado") != "finalizado",
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                viajeActual?.let { viaje ->
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
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Viaje en curso",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            )
                            Text("Duración estimada: $duracionEstimada", style = MaterialTheme.typography.bodyLarge)
                            Text("Distancia: $distanciaEstimada", style = MaterialTheme.typography.bodyLarge)

                            Text("Pasajera: $nombre", style = MaterialTheme.typography.bodyLarge)
                            Text("Fecha: $fechaFormateada", style = MaterialTheme.typography.bodyLarge)

                            geo?.let {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    val cameraPositionState = rememberCameraPositionState {
                                        position = CameraPosition.fromLatLngZoom(
                                            LatLng(geo.latitude, geo.longitude), 15f
                                        )
                                    }

                                    GoogleMap(
                                        modifier = Modifier.fillMaxSize(),
                                        cameraPositionState = cameraPositionState
                                    ) {
                                        origen?.let {
                                            Marker(state = MarkerState(position = it), title = "Tú")
                                        }
                                        destino?.let {
                                            Marker(state = MarkerState(position = it), title = "Pasajera")
                                        }
                                        if (polylinePoints.isNotEmpty()) {
                                            Polyline(
                                                points = polylinePoints,
                                                color = MaterialTheme.colorScheme.primary,
                                                width = 6f
                                            )
                                        }
                                    }

                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
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
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(25.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = secondaryColor)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Finalizar viaje"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Finalizar viaje", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }

            // VIAJES PENDIENTES
            AnimatedVisibility(
                visible = viajeActual == null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.List,
                            contentDescription = "Viajes pendientes",
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Solicitudes pendientes",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (viajesPendientes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay solicitudes pendientes",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                        .animateItemPlacement(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(40.dp),
                                                shape = CircleShape,
                                                color = primaryColor.copy(alpha = 0.1f)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = nombre.first().toString(),
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = primaryColor
                                                        )
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = nombre,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = fechaFormateada,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            FilledTonalButton(
                                                onClick = {
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
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = secondaryColor.copy(alpha = 0.1f),
                                                    contentColor = secondaryColor
                                                )
                                            ) {
                                                Text(
                                                    text = "Aceptar",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

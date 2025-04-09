package com.uttt.gogirls.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomePasajeraScreen(navController: NavHostController) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid
    val username = remember { mutableStateOf("") }

    var viajeId by remember { mutableStateOf<String?>(null) }
    var viajeEstado by remember { mutableStateOf<String?>(null) }
    var conductoraNombre by remember { mutableStateOf<String?>(null) }
    var conductoraTelefono by remember { mutableStateOf<String?>(null) }
    var conductoraVehiculo by remember { mutableStateOf<String?>(null) }

    var isRequesting by remember { mutableStateOf(false) }

    val pinkColor = Color(0xFFFF6B98)
    val lightPinkColor = Color(0xFFFFD9E3)
    val purpleColor = Color(0xFF9B4F96)

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Obtener el nombre de la usuaria
    LaunchedEffect(uid) {
        uid?.let {
            firestore.collection("users").document(it).get()
                .addOnSuccessListener { userDoc ->
                    username.value = userDoc.getString("name") ?: ""
                }
        }
    }

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
                                        conductoraTelefono = userDoc.getString("phone")
                                        conductoraVehiculo = userDoc.getString("vehiculo") ?: "No especificado"
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

        if (isRequesting) return
        isRequesting = true

        uid?.let { pasajeraUid ->
            firestore.collection("viajes")
                .whereEqualTo("pasajeraUid", pasajeraUid)
                .whereIn("estado", listOf("pendiente", "aceptado"))
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        Toast.makeText(context, "Ya tienes un viaje activo", Toast.LENGTH_SHORT).show()
                        isRequesting = false
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                            val geoPoint = if (location != null) GeoPoint(location.latitude, location.longitude) else GeoPoint(0.0, 0.0)

                            val viaje = hashMapOf(
                                "pasajeraUid" to pasajeraUid,
                                "estado" to "pendiente",
                                "timestamp" to Timestamp.now(),
                                "ubicacion" to geoPoint
                            )

                            firestore.collection("viajes").add(viaje)
                                .addOnSuccessListener { doc ->
                                    viajeId = doc.id
                                    Toast.makeText(context, "¡Nuevo viaje solicitado!", Toast.LENGTH_SHORT).show()
                                    isRequesting = false
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Error al solicitar viaje", Toast.LENGTH_LONG).show()
                                    isRequesting = false
                                }
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error verificando viajes existentes", Toast.LENGTH_SHORT).show()
                    isRequesting = false
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "GoGirls",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pinkColor
                ),
                actions = {
                    IconButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                        navController.navigate("login") {
                            popUpTo("home_pasajera") { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Cerrar sesión",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = lightPinkColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Usuario",
                            tint = purpleColor,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("¡Hola ${username.value}! 👋", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = purpleColor)
                            Text("Bienvenida a GoGirls", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                        }
                    }
                }
                Button(
                    onClick = { navController.navigate("historial_pasajera") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("Ver historial de viajes")
                }


                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Estado de tu viaje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(16.dp))

                        when (viajeEstado) {
                            null -> {
                                Text("Aún no has solicitado ningún viaje", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Color.DarkGray)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        if (!locationPermissionState.status.isGranted) {
                                            locationPermissionState.launchPermissionRequest()
                                        } else {
                                            solicitarNuevoViajeConUbicacion()
                                        }
                                    },
                                    enabled = !isRequesting,
                                    colors = ButtonDefaults.buttonColors(containerColor = pinkColor),
                                    modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = "Ubicación")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Solicitar viaje", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            "pendiente" -> {
                                StatusCard(Icons.Default.DateRange, "Viaje pendiente", "Estamos buscando una conductora cercana para ti...", Color(0xFFFFB74D))
                                Spacer(modifier = Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(4.dp)),
                                    color = pinkColor
                                )
                            }
                            "aceptado" -> {
                                StatusCard(Icons.Default.Check, "¡Viaje aceptado!", "Tu conductora está en camino", Color(0xFF4CAF50))
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Tu conductora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        InfoRow("Nombre:", conductoraNombre ?: "Cargando...", purpleColor)
                                        InfoRow("Teléfono:", conductoraTelefono ?: "Cargando...", purpleColor)
                                        InfoRow("Vehículo:", conductoraVehiculo ?: "Cargando...", purpleColor)
                                    }
                                }
                            }
                            "finalizado" -> {
                                StatusCard(Icons.Default.Done, "Viaje finalizado", "¡Gracias por viajar con GoGirls!", Color(0xFF673AB7))
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        if (!locationPermissionState.status.isGranted) {
                                            locationPermissionState.launchPermissionRequest()
                                        } else {
                                            solicitarNuevoViajeConUbicacion()
                                        }
                                    },
                                    enabled = !isRequesting,
                                    colors = ButtonDefaults.buttonColors(containerColor = pinkColor),
                                    modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Nuevo viaje")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Solicitar nuevo viaje", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            else -> Text("Estado del viaje: $viajeEstado", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Color.DarkGray)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, iconColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Info, contentDescription = label, tint = iconColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text("$label $value", style = MaterialTheme.typography.bodyLarge)
    }
}

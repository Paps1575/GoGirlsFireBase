package com.uttt.gogirls.model

import com.google.firebase.firestore.GeoPoint

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "", // "pasajera" o "conductora"
    val available: Boolean = false,
    val location: GeoPoint = GeoPoint(0.0, 0.0)
)
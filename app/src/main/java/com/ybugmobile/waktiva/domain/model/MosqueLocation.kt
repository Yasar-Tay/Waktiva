package com.ybugmobile.waktiva.domain.model

data class MosqueLocation(
    val id: Long,
    val name: String?,
    val lat: Double,
    val lng: Double,
    val address: String? = null
)

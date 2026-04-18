package com.ybugmobile.waktiva.domain.manager

import android.app.Activity
import kotlinx.coroutines.flow.Flow

interface BillingManager {
    val donationProducts: Flow<List<DonationProduct>>
    val purchaseEvents: Flow<PurchaseResult>

    suspend fun startConnection()
    suspend fun queryProducts()
    suspend fun purchaseProduct(activity: Activity, product: DonationProduct)
}

data class DonationProduct(
    val id: String,
    val title: String,
    val price: String,
    val description: String
)

sealed class PurchaseResult {
    object Success : PurchaseResult()
    data class Error(val message: String) : PurchaseResult()
    object UserCancelled : PurchaseResult()
}

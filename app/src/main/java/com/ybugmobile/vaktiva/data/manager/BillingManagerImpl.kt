package com.ybugmobile.vaktiva.data.manager

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.ybugmobile.vaktiva.domain.manager.BillingManager
import com.ybugmobile.vaktiva.domain.manager.DonationProduct
import com.ybugmobile.vaktiva.domain.manager.PurchaseResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BillingManager, PurchasesUpdatedListener {

    private val _donationProducts = MutableStateFlow<List<DonationProduct>>(emptyList())
    override val donationProducts = _donationProducts.asStateFlow()

    private val _purchaseEvents = MutableSharedFlow<PurchaseResult>()
    override val purchaseEvents = _purchaseEvents.asSharedFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override suspend fun startConnection() {
        if (billingClient.isReady) return

        val connectDeferred = CompletableDeferred<Unit>()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    connectDeferred.complete(Unit)
                } else {
                    connectDeferred.completeExceptionally(Exception("Billing setup failed: ${billingResult.debugMessage} (Code: ${billingResult.responseCode})"))
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnection logic can be added here
            }
        })
        
        try {
            connectDeferred.await()
            queryProducts()
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("donation_small")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("donation_medium")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("donation_large")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params)
        }

        if (productDetailsResult.billingResult.responseCode == BillingResponseCode.OK) {
            val productDetails = productDetailsResult.productDetailsList
            if (productDetails.isNullOrEmpty()) {
                // If the list is empty, it means IDs were not found in Play Console
                throw Exception("No products found in Play Store. Ensure IDs match Play Console.")
            }
            
            val products = productDetails.map {
                DonationProduct(
                    id = it.productId,
                    title = it.name,
                    price = it.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A",
                    description = it.description
                )
            }
            _donationProducts.value = products
        } else {
            throw Exception("Query failed: ${productDetailsResult.billingResult.debugMessage}")
        }
    }

    override suspend fun purchaseProduct(product: DonationProduct) {
        // Find the activity from context or use a better approach for production
        val activity = context as? Activity 
            ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity

        if (activity == null) {
             _purchaseEvents.emit(PurchaseResult.Error("Activity context not found"))
             return
        }

        val productDetailsList = withContext(Dispatchers.IO) {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(product.id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                ))
                .build()
            billingClient.queryProductDetails(params).productDetailsList
        }

        val productDetails = productDetailsList?.find { it.productId == product.id } ?: return

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            ))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
            CoroutineScope(Dispatchers.Main).launch {
                _purchaseEvents.emit(PurchaseResult.UserCancelled)
            }
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                _purchaseEvents.emit(PurchaseResult.Error(billingResult.debugMessage))
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Donations are non-consumable usually, but for "Buy me a coffee" style, 
            // we consume them so they can be bought again.
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            
            billingClient.consumeAsync(consumeParams) { billingResult, _ ->
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    CoroutineScope(Dispatchers.Main).launch {
                        _purchaseEvents.emit(PurchaseResult.Success)
                    }
                }
            }
        }
    }
}

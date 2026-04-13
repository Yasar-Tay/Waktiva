package com.ybugmobile.waktiva.data.manager

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.ybugmobile.waktiva.domain.manager.BillingManager
import com.ybugmobile.waktiva.domain.manager.DonationProduct
import com.ybugmobile.waktiva.domain.manager.PurchaseResult
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
                // Reconnection logic: You might want to call startConnection again with a backoff strategy
            }
        })
        
        try {
            connectDeferred.await()
            queryProducts()
            queryPurchases() // Uygulama açıldığında işlenmemiş satın alımları kontrol et
        } catch (e: Exception) {
            throw e
        }
    }

    private fun queryPurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
    }

    override suspend fun queryProducts() {
        // ... mevcut kodun devamı (donation_small vb.)
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
            
            val products = productDetails?.map {
                DonationProduct(
                    id = it.productId,
                    title = it.name,
                    price = it.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A",
                    description = it.description
                )
            } ?: emptyList()
            _donationProducts.value = products
        }
    }

    override suspend fun purchaseProduct(product: DonationProduct) {
        val activity = context as? Activity 
            ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity

        if (activity == null) {
             _purchaseEvents.emit(PurchaseResult.Error("Activity context not found"))
             return
        }

        // ProductDetails'i tekrar sorgulamak yerine daha önce saklanan listeden bulmak daha hızlı olabilir
        // Ancak güvenlik ve güncellik için kısa bir sorgu yapıyoruz
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(product.id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ))
            .build()

        val productDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params)
        }

        val productDetails = productDetailsResult.productDetailsList?.firstOrNull()
        
        if (productDetails != null) {
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                ))
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        } else {
            _purchaseEvents.emit(PurchaseResult.Error("Ürün detayları alınamadı."))
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingResponseCode.USER_CANCELED -> {
                CoroutineScope(Dispatchers.Main).launch {
                    _purchaseEvents.emit(PurchaseResult.UserCancelled)
                }
            }
            else -> {
                CoroutineScope(Dispatchers.Main).launch {
                    _purchaseEvents.emit(PurchaseResult.Error(billingResult.debugMessage))
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Eğer ürün bir bağış ise (tüketilebilir), tekrar alınabilmesi için consume ediyoruz.
            // Eğer "Reklamları Kaldır" gibi kalıcı bir ürünse acknowledge edilmelidir.
            
            // Mevcut ID'lerin hepsi bağış olduğu için consume ediyoruz:
            val isConsumable = true // İleride kalıcı ürün gelirse burayı kontrol ederiz

            if (isConsumable) {
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
            } else if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                
                billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
                    if (billingResult.responseCode == BillingResponseCode.OK) {
                        CoroutineScope(Dispatchers.Main).launch {
                            _purchaseEvents.emit(PurchaseResult.Success)
                        }
                    }
                }
            }
        }
    }
}

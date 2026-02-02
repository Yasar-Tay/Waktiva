package com.ybugmobile.vaktiva.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.manager.BillingManager
import com.ybugmobile.vaktiva.domain.manager.DonationProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DonateViewModel @Inject constructor(
    private val billingManager: BillingManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val donationProducts = billingManager.donationProducts
    val purchaseEvents = billingManager.purchaseEvents

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                billingManager.startConnection()
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError(e.message ?: context.getString(R.string.donate_billing_error)))
            }
        }
    }

    fun onDonateClick(product: DonationProduct) {
        viewModelScope.launch {
            billingManager.purchaseProduct(product)
        }
    }

    fun onRateClick() {
        val packageName = context.packageName
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    sealed class UiEvent {
        data class ShowError(val message: String) : UiEvent()
        object ShowSuccess : UiEvent()
    }
}

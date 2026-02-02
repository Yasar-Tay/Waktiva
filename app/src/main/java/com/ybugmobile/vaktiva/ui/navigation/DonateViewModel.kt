package com.ybugmobile.vaktiva.ui.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.manager.BillingManager
import com.ybugmobile.vaktiva.domain.manager.DonationProduct
import com.ybugmobile.vaktiva.domain.manager.PurchaseResult
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

    sealed class UiEvent {
        data class ShowError(val message: String) : UiEvent()
        object ShowSuccess : UiEvent()
    }
}

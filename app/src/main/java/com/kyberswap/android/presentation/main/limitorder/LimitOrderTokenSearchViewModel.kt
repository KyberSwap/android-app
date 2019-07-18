package com.kyberswap.android.presentation.main.limitorder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kyberswap.android.domain.model.PendingBalances
import com.kyberswap.android.domain.model.Token
import com.kyberswap.android.domain.model.Wallet
import com.kyberswap.android.domain.usecase.limitorder.GetPendingBalancesUseCase
import com.kyberswap.android.domain.usecase.limitorder.SaveLimitOrderTokenUseCase
import com.kyberswap.android.domain.usecase.token.GetTokenUseCase
import com.kyberswap.android.domain.usecase.wallet.GetWalletByAddressUseCase
import com.kyberswap.android.presentation.common.Event
import com.kyberswap.android.presentation.main.balance.GetBalanceState
import com.kyberswap.android.presentation.main.swap.SaveSwapDataState
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import java.math.BigDecimal
import javax.inject.Inject

class LimitOrderTokenSearchViewModel @Inject constructor(
    private val getTokenListUseCase: GetTokenUseCase,
    private val getWalletByAddressUseCase: GetWalletByAddressUseCase,
    private val saveLimitOrderTokenUseCase: SaveLimitOrderTokenUseCase,
    private val pendingBalancesUseCase: GetPendingBalancesUseCase
) : ViewModel() {

    private val _getTokenListCallback = MutableLiveData<Event<GetBalanceState>>()
    val getTokenListCallback: LiveData<Event<GetBalanceState>>
        get() = _getTokenListCallback

    private val _saveLimitOrderCallback = MutableLiveData<Event<SaveSwapDataState>>()
    val saveLimitOrderCallback: LiveData<Event<SaveSwapDataState>>
        get() = _saveLimitOrderCallback


    val compositeDisposable by lazy {
        CompositeDisposable()
    }

    fun getTokenList(address: String, pendingBalances: PendingBalances) {
        getTokenListUseCase.execute(
            Consumer { tokens ->
                _getTokenListCallback.value = Event(
                    GetBalanceState.Success(
                        tokens
                            .filter { it.spLimitOrder }.map {
                                val pendingAmount =
                                    pendingBalances.data[it.tokenSymbol] ?: BigDecimal.ZERO
                                if (pendingAmount > BigDecimal.ZERO) {
                                    val availableAmount = it.currentBalance - pendingAmount
                                    it.copy(limitOrderBalance = if (availableAmount > BigDecimal.ZERO) availableAmount else BigDecimal.ZERO)
                                } else {
                                    it.copy(limitOrderBalance = it.currentBalance)
                                }

                            }
                    )
                )
            },
            Consumer {
                it.printStackTrace()
                _getTokenListCallback.value =
                    Event(
                        GetBalanceState.ShowError(
                            it.localizedMessage
                        )
                    )
            },
            address
        )
    }

    fun getPendingBalances(wallet: Wallet) {
        pendingBalancesUseCase.execute(
            Consumer {
                getTokenList(wallet.address, it)
            },
            Consumer {
                it.printStackTrace()
                _getTokenListCallback.value =
                    Event(
                        GetBalanceState.ShowError(
                            it.localizedMessage
                        )
                    )
            },
            GetPendingBalancesUseCase.Param(wallet)
        )
    }

    override fun onCleared() {
        getWalletByAddressUseCase.dispose()
        saveLimitOrderTokenUseCase.dispose()
        getTokenListUseCase.dispose()
        compositeDisposable.dispose()
        super.onCleared()
    }

    fun saveTokenSelection(walletAddress: String, token: Token, sourceToken: Boolean) {
        saveLimitOrderTokenUseCase.execute(
            Action {
                _saveLimitOrderCallback.value = Event(SaveSwapDataState.Success())
            },
            Consumer {
                it.printStackTrace()
                _saveLimitOrderCallback.value =
                    Event(
                        SaveSwapDataState.ShowError(
                            it.localizedMessage
                        )
                    )
            },
            SaveLimitOrderTokenUseCase.Param(walletAddress, token, sourceToken)
        )
    }

}
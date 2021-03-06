package com.kyberswap.android.presentation.main.swap

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kyberswap.android.data.repository.SwapDataRepository
import com.kyberswap.android.domain.model.NotificationAlert
import com.kyberswap.android.domain.model.NotificationExt
import com.kyberswap.android.domain.model.Swap
import com.kyberswap.android.domain.model.Wallet
import com.kyberswap.android.domain.usecase.alert.GetAlertUseCase
import com.kyberswap.android.domain.usecase.swap.EstimateAmountUseCase
import com.kyberswap.android.domain.usecase.swap.EstimateGasUseCase
import com.kyberswap.android.domain.usecase.swap.GetCombinedCapUseCase
import com.kyberswap.android.domain.usecase.swap.GetExpectedRateSequentialUseCase
import com.kyberswap.android.domain.usecase.swap.GetExpectedRateUseCase
import com.kyberswap.android.domain.usecase.swap.GetGasPriceUseCase
import com.kyberswap.android.domain.usecase.swap.GetHintUseCase
import com.kyberswap.android.domain.usecase.swap.GetKyberNetworkStatusCase
import com.kyberswap.android.domain.usecase.swap.GetMarketRateUseCase
import com.kyberswap.android.domain.usecase.swap.GetPlatformFeeUseCase
import com.kyberswap.android.domain.usecase.swap.GetSwapDataUseCase
import com.kyberswap.android.domain.usecase.swap.ResetSwapDataUseCase
import com.kyberswap.android.domain.usecase.swap.SaveSwapUseCase
import com.kyberswap.android.domain.usecase.wallet.CheckEligibleWalletUseCase
import com.kyberswap.android.domain.usecase.wallet.GetSelectedWalletUseCase
import com.kyberswap.android.domain.usecase.wallet.GetWalletByAddressUseCase
import com.kyberswap.android.presentation.common.Event
import com.kyberswap.android.presentation.common.MIN_SUPPORT_AMOUNT
import com.kyberswap.android.presentation.common.MIN_SUPPORT_SWAP_SOURCE_AMOUNT
import com.kyberswap.android.presentation.common.calculateDefaultGasLimit
import com.kyberswap.android.presentation.common.isKatalyst
import com.kyberswap.android.presentation.common.specialGasLimitDefault
import com.kyberswap.android.presentation.main.SelectedWalletViewModel
import com.kyberswap.android.presentation.main.alert.GetAlertState
import com.kyberswap.android.presentation.main.balance.CheckEligibleWalletState
import com.kyberswap.android.util.ErrorHandler
import com.kyberswap.android.util.ext.toBigDecimalOrDefaultZero
import com.kyberswap.android.util.ext.toDisplayNumber
import com.kyberswap.android.util.ext.toLongSafe
import io.reactivex.disposables.CompositeDisposable
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.net.UnknownHostException
import javax.inject.Inject

class SwapViewModel @Inject constructor(
    private val getWalletByAddressUseCase: GetWalletByAddressUseCase,
    private val getExpectedRateUseCase: GetExpectedRateUseCase,
    private val getExpectedRateSequentialUseCase: GetExpectedRateSequentialUseCase,
    private val getSwapDataUseCase: GetSwapDataUseCase,
    private val getMarketRate: GetMarketRateUseCase,
    private val saveSwapUseCase: SaveSwapUseCase,
    private val getGasPriceUseCase: GetGasPriceUseCase,
    private val getPlatformFeeUseCase: GetPlatformFeeUseCase,
    private val estimateGasUseCase: EstimateGasUseCase,
    private val getAlertUseCase: GetAlertUseCase,
    private val estimateAmountUseCase: EstimateAmountUseCase,
    private val getCombinedCapUseCase: GetCombinedCapUseCase,
    private val resetSwapUserCase: ResetSwapDataUseCase,
    private val kyberNetworkStatusCase: GetKyberNetworkStatusCase,
    private val checkEligibleWalletUseCase: CheckEligibleWalletUseCase,
    private val getHintUseCase: GetHintUseCase,
    getWalletUseCase: GetSelectedWalletUseCase,
    private val errorHandler: ErrorHandler
) : SelectedWalletViewModel(getWalletUseCase, errorHandler) {

    private val _getSwapCallback = MutableLiveData<Event<GetSwapState>>()
    val getSwapDataCallback: LiveData<Event<GetSwapState>>
        get() = _getSwapCallback

    private val _getGetGasLimitCallback = MutableLiveData<Event<GetGasLimitState>>()
    val getGetGasLimitCallback: LiveData<Event<GetGasLimitState>>
        get() = _getGetGasLimitCallback

    private val _getGetGasPriceCallback = MutableLiveData<Event<GetGasPriceState>>()
    val getGetGasPriceCallback: LiveData<Event<GetGasPriceState>>
        get() = _getGetGasPriceCallback

    private val _getHintCallback = MutableLiveData<Event<GetHintState>>()
    val getHintCallback: LiveData<Event<GetHintState>>
        get() = _getHintCallback

    private val _getPlatformFeeCallback = MutableLiveData<Event<GetPlatformFeeState>>()
    val getPlatformFeeCallback: LiveData<Event<GetPlatformFeeState>>
        get() = _getPlatformFeeCallback

//    private val _getCapCallback = MutableLiveData<Event<GetCapState>>()
//    val getCapCallback: LiveData<Event<GetCapState>>
//        get() = _getCapCallback

    private val _getKyberStatusback = MutableLiveData<Event<GetKyberStatusState>>()
    val getKyberStatusback: LiveData<Event<GetKyberStatusState>>
        get() = _getKyberStatusback

    private val _getAlertState = MutableLiveData<Event<GetAlertState>>()
    val getAlertState: LiveData<Event<GetAlertState>>
        get() = _getAlertState

    private val _estimateAmountState = MutableLiveData<Event<EstimateAmountState>>()
    val estimateAmountCallback: LiveData<Event<EstimateAmountState>>
        get() = _estimateAmountState

    val compositeDisposable by lazy {
        CompositeDisposable()
    }

    private val _getGetMarketRateCallback = MutableLiveData<Event<GetMarketRateState>>()
    val getGetMarketRateCallback: LiveData<Event<GetMarketRateState>>
        get() = _getGetMarketRateCallback

    private val _getExpectedRateCallback = MutableLiveData<Event<GetExpectedRateState>>()
    val getExpectedRateCallback: LiveData<Event<GetExpectedRateState>>
        get() = _getExpectedRateCallback

    private val _checkMaintenanceCallback =
        MutableLiveData<Event<CheckMaintenanceState>>()
    val checkMaintenanceCallback: LiveData<Event<CheckMaintenanceState>>
        get() = _checkMaintenanceCallback

    private val _saveSwapCallback = MutableLiveData<Event<SaveSwapState>>()
    val saveSwapDataCallback: LiveData<Event<SaveSwapState>>
        get() = _saveSwapCallback

    private val _checkEligibleWalletCallback = MutableLiveData<Event<CheckEligibleWalletState>>()
    val checkEligibleWalletCallback: LiveData<Event<CheckEligibleWalletState>>
        get() = _checkEligibleWalletCallback

    fun getMarketRate(swap: Swap) {

        if (swap.hasSamePair) {
            _getGetMarketRateCallback.value =
                Event(GetMarketRateState.Success(BigDecimal.ONE.toDisplayNumber()))
            return
        }

        getMarketRate.dispose()
        if (swap.hasTokenPair) {
            getMarketRate.execute(
                {
                    _getGetMarketRateCallback.value = Event(GetMarketRateState.Success(it))
                },
                {
                    it.printStackTrace()

                    _getGetMarketRateCallback.value =
                        Event(
                            GetMarketRateState.ShowError(
                                it.localizedMessage,
                                it is UnknownHostException
                            )
                        )
                },
                GetMarketRateUseCase.Param(swap.tokenSource.tokenSymbol, swap.tokenDest.tokenSymbol)
            )
        }
    }

    fun disposeCurrentSwap() {
        getSwapDataUseCase.dispose()
    }

    fun getSwapData(
        wallet: Wallet,
        alert: NotificationAlert? = null,
        notificationExt: NotificationExt? = null
    ) {
        getSwapDataUseCase.dispose()
        getSwapDataUseCase.execute(
            {
                it.gasLimit = calculateGasLimit(it).toString()
                _getSwapCallback.value = Event(GetSwapState.Success(it))
            },
            {
                it.printStackTrace()
                _getSwapCallback.value = Event(GetSwapState.ShowError(errorHandler.getError(it)))
            },
            GetSwapDataUseCase.Param(wallet, alert, notificationExt)
        )
    }

    private fun calculateGasLimit(swap: Swap): BigInteger {
        return calculateDefaultGasLimit(swap.tokenSource, swap.tokenDest)
    }

    fun getGasPrice() {
        getGasPriceUseCase.dispose()
        getGasPriceUseCase.execute(
            {
                _getGetGasPriceCallback.value = Event(GetGasPriceState.Success(it))
            },
            {
                it.printStackTrace()
                _getGetGasPriceCallback.value =
                    Event(GetGasPriceState.ShowError(errorHandler.getError(it)))
            },
            null
        )
    }

    fun getPlatformFee(swap: Swap?) {
        if (swap == null) return
        getPlatformFeeUseCase.dispose()
        getPlatformFeeUseCase.execute(
            {
                _getPlatformFeeCallback.value = Event(GetPlatformFeeState.Success(it))
            },
            {
                it.printStackTrace()
                _getPlatformFeeCallback.value =
                    Event(GetPlatformFeeState.ShowError(it.localizedMessage))
            },
            GetPlatformFeeUseCase.Param(swap.tokenSource.tokenAddress, swap.tokenDest.tokenAddress)
        )
    }

    fun disposeGetExpectedRate() {
        getExpectedRateUseCase.dispose()
    }

    fun getHint(swap: Swap, srcAmount: String) {
        getHintUseCase.dispose()
        getHintUseCase.execute(
            {
                _getHintCallback.value = Event(
                    GetHintState.Success(
                        !it.equals(SwapDataRepository.DEFAULT_HINT, true)
                    )
                )
            },
            {
                it.printStackTrace()
            },
            GetHintUseCase.Param(
                swap.sourceAddress, swap.destAddress, srcAmount, true
            )
        )
    }

    fun getExpectedRate(
        swap: Swap,
        srcAmount: String,
        platformFee: Int,
        isReserveRouting: Boolean
    ) {
        if (swap.hasSamePair) {
            _getExpectedRateCallback.value =
                Event(GetExpectedRateState.Success(listOf(BigDecimal.ONE.toDisplayNumber())))
            return
        }

        getExpectedRateUseCase.dispose()
        getExpectedRateUseCase.execute(
            {
                if (it.isNotEmpty()) {
                    _getExpectedRateCallback.value = Event(GetExpectedRateState.Success(it))
                }

            },
            {
                it.printStackTrace()
                _getExpectedRateCallback.value =
                    Event(GetExpectedRateState.ShowError(it.localizedMessage))
            },
            GetExpectedRateUseCase.Param(
                swap.tokenSource,
                swap.tokenDest,
                srcAmount,
                platformFee,
                isReserveRouting
            )
        )

        checkMaintenance(swap, platformFee, isReserveRouting)
    }

    private fun checkMaintenance(
        swap: Swap,
        platformFee: Int,
        isReserveRouting: Boolean
    ) {
        getExpectedRateSequentialUseCase.dispose()
        getExpectedRateSequentialUseCase.execute(
            {
                _checkMaintenanceCallback.value = Event(
                    CheckMaintenanceState.Success(
                        it.isNotEmpty() && it.first()
                            .toBigDecimalOrDefaultZero() <= BigDecimal.ZERO
                    )
                )

            },
            {

            },
            GetExpectedRateSequentialUseCase.Param(
                swap.tokenSource,
                swap.tokenDest,
                MIN_SUPPORT_SWAP_SOURCE_AMOUNT.toString(),
                platformFee,
                isReserveRouting,
                true
            )
        )
    }

    fun verifySwap(wallet: Wallet, swap: Swap, platformFee: Int, isReserveRouting: Boolean) {
        checkEligibleWalletUseCase.dispose()
        _checkEligibleWalletCallback.postValue(Event(CheckEligibleWalletState.Loading))
        getExpectedRateSequentialUseCase.dispose()
        getExpectedRateSequentialUseCase.execute(
            {
                val sw =
                    if (it.isNotEmpty() && it.first()
                            .toBigDecimalOrDefaultZero() > BigDecimal.ZERO
                    ) {
                        swap.copy(expectedRate = it[0])
                    } else {
                        swap.copy(expectedRate = swap.marketRate)
                    }
                checkEligibleWalletUseCase.execute(
                    { eligibleWallet ->
                        _checkEligibleWalletCallback.value =
                            Event(CheckEligibleWalletState.Success(eligibleWallet, sw))
                    },
                    { error ->
                        _checkEligibleWalletCallback.value =
                            Event(
                                CheckEligibleWalletState.ShowError(
                                    errorHandler.getError(error),
                                    sw
                                )
                            )
                    },
                    CheckEligibleWalletUseCase.Param(wallet)
                )

            },
            {
                val sw = if (swap.expectedRate.toBigDecimalOrDefaultZero() == BigDecimal.ZERO) {
                    swap.copy(expectedRate = swap.marketRate)
                } else {
                    swap
                }
                checkEligibleWalletUseCase.execute(
                    { eligibleWallet ->
                        _checkEligibleWalletCallback.value =
                            Event(
                                CheckEligibleWalletState.Success(
                                    eligibleWallet,
                                    sw
                                )
                            )
                    },
                    { error ->
                        _checkEligibleWalletCallback.value =
                            Event(
                                CheckEligibleWalletState.ShowError(
                                    errorHandler.getError(error),
                                    sw
                                )
                            )
                    },
                    CheckEligibleWalletUseCase.Param(wallet)
                )
            },
            GetExpectedRateSequentialUseCase.Param(
                swap.tokenSource,
                swap.tokenDest,
                swap.sourceAmount,
                platformFee,
                isReserveRouting
            )
        )
    }

    fun estimateAmount(source: String, dest: String, destAmount: String, platformFee: Int) {
        if (destAmount.isBlank()) return
        estimateAmountUseCase.execute(
            {
                if (it.error) {
                    _estimateAmountState.value =
                        Event(EstimateAmountState.ShowError(it.additionalData))
                } else {
                    _estimateAmountState.value = Event(
                        EstimateAmountState.Success(
                            calcAmountWithPlatformBps(
                                it.data,
                                platformFee
                            )
                        )
                    )
                }
            },
            {
                it.printStackTrace()
                _estimateAmountState.value =
                    Event(EstimateAmountState.ShowError(errorHandler.getError(it)))
            },
            EstimateAmountUseCase.Param(
                source,
                dest,
                destAmount
            )
        )
    }

    private fun calcAmountWithPlatformBps(amount: String, bps: Int): String {
        return if (isKatalyst) {
            amount.toBigDecimalOrDefaultZero().multiply(
                BigDecimal.ONE + bps.toBigDecimal()
                    .divide(10000.toBigDecimal(), 18, RoundingMode.UP)
            ).toDisplayNumber()
        } else {
            amount
        }
    }

    fun getGasLimit(wallet: Wallet?, swap: Swap?, platformFee: Int, isReserveRouting: Boolean) {
        if (wallet == null || swap == null) return
        if (swap.sourceAmount.isEmpty() || (swap.sourceAmount.toBigDecimalOrDefaultZero() == BigDecimal.ZERO)) return
        if (swap.sourceAmount.toBigDecimalOrDefaultZero() > swap.tokenSource.currentBalance) return
        if (swap.ethToken.currentBalance <= MIN_SUPPORT_AMOUNT) return
        estimateGasUseCase.dispose()
        estimateGasUseCase.execute(
            {

                val gasLimit = it.toBigInteger()
                val specialGasLimit = specialGasLimitDefault(swap.tokenSource, swap.tokenDest)
                _getGetGasLimitCallback.value = Event(
                    GetGasLimitState.Success(
                        if (specialGasLimit != null && !isReserveRouting) {
                            specialGasLimit.max(gasLimit)
                        } else {
                            gasLimit
                        }
                    )
                )
            },
            {
                it.printStackTrace()
                Event(GetGasLimitState.ShowError(errorHandler.getError(it)))
            },
            EstimateGasUseCase.Param(
                wallet,
                swap.tokenSource,
                swap.tokenDest,
                swap.sourceAmount,
                BigInteger.ZERO,
                platformFee,
                isReserveRouting
            )
        )
    }

    fun saveSwap(swap: Swap?, fromContinue: Boolean = false) {
        if (swap == null) return
        saveSwapUseCase.dispose()
        saveSwapUseCase.execute(
            {
                if (fromContinue) {
                    _saveSwapCallback.value =
                        Event(SaveSwapState.Success(swap.isExpectedRateEmptyOrZero))
                }
            },
            {
                it.printStackTrace()
                _saveSwapCallback.value = Event(SaveSwapState.ShowError(errorHandler.getError(it)))
            },
            SaveSwapUseCase.Param(swap)
        )
    }

    fun getAlert(alertNotification: NotificationAlert) {
        getAlertUseCase.execute(
            {
                _getAlertState.value = Event(GetAlertState.Success(it))
            },
            {
                it.printStackTrace()
                _getAlertState.value = Event(GetAlertState.ShowError(errorHandler.getError(it)))
            },
            GetAlertUseCase.Param(
                if (alertNotification.alertId > 0) alertNotification.alertId else alertNotification.testAlertId.toLongSafe()

            )
        )
    }

    override fun onCleared() {
        getWalletByAddressUseCase.dispose()
        getExpectedRateUseCase.dispose()
        getSwapDataUseCase.dispose()
        getMarketRate.dispose()
        saveSwapUseCase.dispose()
        getGasPriceUseCase.dispose()
        estimateGasUseCase.dispose()
        getAlertUseCase.dispose()
        compositeDisposable.dispose()
        estimateAmountUseCase.dispose()
        getCombinedCapUseCase.dispose()
        resetSwapUserCase.dispose()
        kyberNetworkStatusCase.dispose()
        checkEligibleWalletUseCase.dispose()
        getExpectedRateSequentialUseCase.dispose()
        getPlatformFeeUseCase.dispose()
        getHintUseCase.dispose()
        super.onCleared()
    }

//    fun getCap(wallet: Wallet, swap: Swap) {
//        _getCapCallback.postValue(Event(GetCapState.Loading))
//        getCombinedCapUseCase.execute(
//            Consumer {
//                _getCapCallback.value = Event(GetCapState.Success(it, swap))
//            },
//            Consumer {
//                it.printStackTrace()
//                _getCapCallback.value = Event(GetCapState.ShowError(errorHandler.getError(it)))
//            },
//            GetCombinedCapUseCase.Param(wallet)
//        )
//    }

    fun getKyberStatus() {
        kyberNetworkStatusCase.dispose()
        kyberNetworkStatusCase.execute(
            {
                _getKyberStatusback.value = Event(GetKyberStatusState.Success(it))
            },
            {
                _getKyberStatusback.value =
                    Event(GetKyberStatusState.ShowError(errorHandler.getError(it)))
            },
            null
        )
    }
}
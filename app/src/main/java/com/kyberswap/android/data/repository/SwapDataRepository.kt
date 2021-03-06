package com.kyberswap.android.data.repository

import android.content.Context
import android.util.Base64
import com.kyberswap.android.KyberSwapApplication
import com.kyberswap.android.R
import com.kyberswap.android.data.api.home.KyberSwapApi
import com.kyberswap.android.data.api.home.SwapApi
import com.kyberswap.android.data.api.home.UserApi
import com.kyberswap.android.data.api.home.UtilitiesApi
import com.kyberswap.android.data.api.user.ResponseStatusEntity
import com.kyberswap.android.data.db.ContactDao
import com.kyberswap.android.data.db.SendDao
import com.kyberswap.android.data.db.SwapDao
import com.kyberswap.android.data.db.TokenDao
import com.kyberswap.android.data.db.TokenExtDao
import com.kyberswap.android.data.db.TransactionDao
import com.kyberswap.android.data.db.UserDao
import com.kyberswap.android.data.mapper.CapMapper
import com.kyberswap.android.data.mapper.GasMapper
import com.kyberswap.android.data.mapper.UserMapper
import com.kyberswap.android.domain.model.Alert
import com.kyberswap.android.domain.model.Cap
import com.kyberswap.android.domain.model.Contact
import com.kyberswap.android.domain.model.Gas
import com.kyberswap.android.domain.model.GasLimit
import com.kyberswap.android.domain.model.KyberEnabled
import com.kyberswap.android.domain.model.MaxGasPrice
import com.kyberswap.android.domain.model.PlatformFee
import com.kyberswap.android.domain.model.Promo
import com.kyberswap.android.domain.model.QuoteAmount
import com.kyberswap.android.domain.model.ResponseStatus
import com.kyberswap.android.domain.model.Send
import com.kyberswap.android.domain.model.Swap
import com.kyberswap.android.domain.model.Token
import com.kyberswap.android.domain.model.Transaction
import com.kyberswap.android.domain.model.UserInfo
import com.kyberswap.android.domain.repository.SwapRepository
import com.kyberswap.android.domain.usecase.send.ENSResolveUseCase
import com.kyberswap.android.domain.usecase.send.ENSRevertResolveUseCase
import com.kyberswap.android.domain.usecase.send.GetSendTokenUseCase
import com.kyberswap.android.domain.usecase.send.SaveSendTokenUseCase
import com.kyberswap.android.domain.usecase.send.SaveSendUseCase
import com.kyberswap.android.domain.usecase.send.TransferTokenUseCase
import com.kyberswap.android.domain.usecase.swap.EstimateAmountUseCase
import com.kyberswap.android.domain.usecase.swap.EstimateGasUseCase
import com.kyberswap.android.domain.usecase.swap.EstimateTransferGasUseCase
import com.kyberswap.android.domain.usecase.swap.GetCapUseCase
import com.kyberswap.android.domain.usecase.swap.GetCombinedCapUseCase
import com.kyberswap.android.domain.usecase.swap.GetPlatformFeeUseCase
import com.kyberswap.android.domain.usecase.swap.GetSwapDataUseCase
import com.kyberswap.android.domain.usecase.swap.ResetSwapDataUseCase
import com.kyberswap.android.domain.usecase.swap.SaveSwapDataTokenUseCase
import com.kyberswap.android.domain.usecase.swap.SaveSwapUseCase
import com.kyberswap.android.domain.usecase.swap.SwapTokenUseCase
import com.kyberswap.android.presentation.common.DEFAULT_NAME
import com.kyberswap.android.presentation.common.PLATFORM_FEE_BPS
import com.kyberswap.android.presentation.common.calculateDefaultGasLimit
import com.kyberswap.android.presentation.common.isKatalyst
import com.kyberswap.android.util.TokenClient
import com.kyberswap.android.util.ext.toBigDecimalOrDefaultZero
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import org.consenlabs.tokencore.wallet.WalletManager
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.core.methods.response.EthEstimateGas
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.pow

class SwapDataRepository @Inject constructor(
    private val context: Context,
    private val swapDao: SwapDao,
    private val tokenDao: TokenDao,
    private val sendTokenDao: SendDao,
    private val contactDao: ContactDao,
    private val api: SwapApi,
    private val mapper: GasMapper,
    private val capMapper: CapMapper,
    private val tokenClient: TokenClient,
    private val transactionDao: TransactionDao,
    private val userDao: UserDao,
    private val userApi: UserApi,
    private val userMapper: UserMapper,
    private val utilitiesApi: UtilitiesApi,
    private val kyberSwapApi: KyberSwapApi,
    private val tokenExtDao: TokenExtDao
) : SwapRepository {

    private var currentHintParams: String? = null
    private var currentHintPair: Pair<Long, String?>? = null

    override fun getCap(param: GetCombinedCapUseCase.Param): Single<Cap> {
        return Single.fromCallable {
            userDao.getUser() ?: UserInfo()
        }.flatMap {
            if (it.uid > 0) {
                userApi.getUserStats(param.wallet.address)
            } else {
                api.getCap(param.wallet.address)
            }
        }.map { capMapper.transform(it) }
    }

    override fun estimateAmount(param: EstimateAmountUseCase.Param): Single<QuoteAmount> {
        return utilitiesApi.sourceAmount(
            param.source,
            param.dest,
            param.destAmount,
            BUY_TYPE
        ).map {
            userMapper.transform(it)
        }
    }

    override fun saveSend(param: SaveSendUseCase.Param): Completable {
        return Completable.fromCallable {
            val send = if (param.address.isNotBlank()) {
                val findContactByAddress = contactDao.findContactByAddress(param.address)
                val contact = findContactByAddress?.copy(
                    walletAddress = param.send.walletAddress,
                    address = param.address
                ) ?: Contact(
                    param.send.walletAddress,
                    param.address,
                    if (param.send.contact.name.isNotEmpty()) param.send.contact.name else DEFAULT_NAME
                )
                param.send.copy(contact = contact)
            } else {
                param.send
            }
            val ethToken = tokenDao.getTokenBySymbol(Token.ETH_SYMBOL) ?: Token()
            sendTokenDao.updateSend(send.copy(ethToken = ethToken))
        }
    }

    override fun swapToken(param: SwapTokenUseCase.Param): Single<ResponseStatus> {
        return getHint(
            param.swap.tokenSource.tokenAddress,
            param.swap.tokenDest.tokenAddress,
            param.swap.sourceAmount,
            param.isReserveRouting
        ).flatMap { hint ->
            Single.fromCallable {
                var password = ""
                if (context is KyberSwapApplication) {
                    password = String(
                        context.aead.decrypt(
                            Base64.decode(param.wallet.cipher, Base64.DEFAULT), ByteArray(0)
                        ), Charsets.UTF_8
                    )
                }
                val credentials = WalletUtils.loadCredentials(
                    password,
                    WalletManager.storage.keystoreDir.toString() + "/wallets/" + param.wallet.walletId + ".json"
                )

                val (hash, nonce) = tokenClient.doSwap(
                    param,
                    credentials,
                    context.getString(R.string.kyber_address),

                    if (param.swap.isETHWETHPair) BigInteger.ZERO else param.platformFee.toBigInteger(),
                    hint
                )
                hash?.let {
                    val swap = param.swap
                    transactionDao.insertTransaction(
                        Transaction(
                            hash = it.toLowerCase(Locale.getDefault()),
                            transactionStatus = Transaction.PENDING_TRANSACTION_STATUS,
                            timeStamp = System.currentTimeMillis() / 1000L,
                            from = param.wallet.address,
                            gas = swap.gasLimit,
                            gasUsed = swap.gasLimit,
                            gasPrice = Convert.toWei(
                                swap.gasPrice.toBigDecimalOrDefaultZero(),
                                Convert.Unit.GWEI
                            ).toString(),
                            nonce = nonce.toString(),
                            to = param.wallet.address,
                            tokenSource = swap.tokenSource.tokenSymbol,
                            tokenDest = swap.tokenDest.tokenSymbol,
                            sourceAmount = swap.sourceAmount,
                            destAmount = swap.destAmount,
                            walletAddress = swap.walletAddress,
                            type = Transaction.TransactionType.SWAP,
                            hint = hint
                        )
                    )
                }

                hash ?: ""
            }.flatMap { hash ->
                val userInfo = userDao.getUser()
                val isLogin = userInfo != null && userInfo.uid > 0
                if (isLogin) {
                    userApi.submitTx(hash).map {
                        it.copy(hash = hash)
                    }
                } else {
                    Single.fromCallable {
                        ResponseStatusEntity().copy(hash = hash, success = true)
                    }
                }
            }.map {
                userMapper.transform(it)
            }
        }
    }

    override fun resetSwapData(param: ResetSwapDataUseCase.Param): Completable {
        return Completable.fromCallable {
            val swap = param.swap
            swap.reset()
            swapDao.updateSwap(swap)
        }
    }

    override fun transferToken(param: TransferTokenUseCase.Param): Single<ResponseStatus> {
        return Single.fromCallable {
            var password = ""
            if (context is KyberSwapApplication) {
                password = String(
                    context.aead.decrypt(
                        Base64.decode(param.wallet.cipher, Base64.DEFAULT), ByteArray(0)
                    ), Charsets.UTF_8
                )
            }
            val credentials = WalletUtils.loadCredentials(
                password,
                WalletManager.storage.keystoreDir.toString() + "/wallets/" + param.wallet.walletId + ".json"
            )

            val (hash, nonce) = tokenClient.doTransferTransaction(
                param,
                credentials
            )
            hash?.let {
                val transfer = param.send
                transactionDao.insertTransaction(
                    Transaction(
                        hash = it.toLowerCase(Locale.getDefault()),
                        transactionStatus = Transaction.PENDING_TRANSACTION_STATUS,
                        timeStamp = System.currentTimeMillis() / 1000L,
                        from = param.wallet.address,
                        gas = transfer.gasLimit,
                        gasUsed = transfer.gasLimit,
                        nonce = nonce.toString(),
                        gasPrice = Convert.toWei(
                            transfer.gasPrice.toBigDecimalOrDefaultZero(),
                            Convert.Unit.GWEI
                        ).toString(),
                        to = transfer.contact.address,
                        value = transfer.amountUnit.toString(),
                        tokenDecimal = transfer.tokenSource.tokenDecimal.toString(),
                        tokenSymbol = transfer.tokenSource.tokenSymbol,
                        walletAddress = param.send.walletAddress,
                        type = Transaction.TransactionType.SEND
                    )
                )
            }

            hash ?: ""

        }.map {
            userMapper.transform(ResponseStatusEntity().copy(hash = it, success = true))
        }
    }

    private fun needUpdateHint(): Boolean {
        if (currentHintPair == null) return true
        val lastRequestTime = currentHintPair!!.first
        val currentTimeStamp = System.currentTimeMillis() / 1000L
        return (currentTimeStamp - lastRequestTime) > 30
    }

    override fun getHint(
        srcAddress: String,
        destAddress: String,
        amount: String,
        isReserveRouting: Boolean
    ): Single<String?> {
        if (!isReserveRouting) return Single.fromCallable {
            DEFAULT_HINT
        }
        val hintParams = srcAddress + destAddress + amount
        val isHintParamsChanged = hintParams != currentHintParams
        currentHintParams = hintParams
        return if (isHintParamsChanged || needUpdateHint()) {
            userApi.getHint(srcAddress, destAddress, amount).map {
                if (it.success) {
                    currentHintPair = System.currentTimeMillis() / 1000L to it.hint
                    currentHintPair?.second
                } else {
                    DEFAULT_HINT
                }
            }.onErrorReturnItem(DEFAULT_HINT)
        } else {
            Single.fromCallable {
                currentHintPair?.second
            }
        }
    }

    override fun estimateGas(param: EstimateGasUseCase.Param): Single<BigDecimal> {

        return Singles.zip(
            utilitiesApi.estimateGas(
                param.tokenSource.tokenAddress,
                param.tokenDest.tokenAddress,
                param.sourceAmount
            ).map {
                mapper.transform(it)
            }.onErrorReturnItem(
                GasLimit(
                    calculateDefaultGasLimit(
                        param.tokenSource,
                        param.tokenDest
                    ).toBigDecimal(), false
                )
            ),
            getHint(
                param.tokenSource.tokenAddress,
                param.tokenDest.tokenAddress,
                param.sourceAmount,
                param.isReserveRouting
            ).flatMap {
                Single.fromCallable {
                    try {
                        tokenClient.estimateGas(
                            param.wallet.address,
                            context.getString(R.string.kyber_address),
                            param.tokenSource.tokenAddress,
                            param.tokenDest.tokenAddress,
                            param.sourceAmount.toBigDecimalOrDefaultZero().times(
                                10.0.pow(param.tokenSource.tokenDecimal)
                                    .toBigDecimal()
                            ).toBigInteger(),
                            param.minConversionRate,
                            param.tokenSource.isETH,
                            param.platformFee.toBigInteger(),
                            it
                        )
                    } catch (ex: Exception) {
                        throw RuntimeException("error: " + ex.localizedMessage)
                    }
                }
            }

        ) { gasLimitEntity, ethEstimateGas ->

            val srcTokenExt = tokenExtDao.getTokenExtByAddress(param.tokenSource.tokenAddress)
            val destTokenExt = tokenExtDao.getTokenExtByAddress(param.tokenDest.tokenAddress)

            val calcGasLimitFromNode = ((ethEstimateGas?.amountUsed
                ?: BigInteger.ZERO).multiply(120.toBigInteger())
                .divide(100.toBigInteger())).toBigDecimal()

            val calcGasLimitFromApi = gasLimitEntity.data

            val defaultValue = calculateDefaultGasLimit(
                param.tokenSource,
                param.tokenDest
            ).toBigDecimal()

            if (srcTokenExt?.isGasFixed == true && !param.isReserveRouting) {
                val gasLimitFromApiWithFallback = if (!gasLimitEntity.error) {
                    calcGasLimitFromApi
                } else {
                    srcTokenExt.gasLimit.toBigDecimalOrDefaultZero()
                }
                gasLimitFromApiWithFallback.max(calcGasLimitFromNode)
            } else if (destTokenExt?.isGasFixed == true && !param.isReserveRouting) {
                val gasLimitFromApiWithFallback = if (!gasLimitEntity.error) {
                    calcGasLimitFromApi
                } else {
                    destTokenExt.gasLimit.toBigDecimalOrDefaultZero()
                }
                gasLimitFromApiWithFallback.max(calcGasLimitFromNode)
            } else if (gasLimitEntity.error && ethEstimateGas?.error != null) {
                defaultValue
            } else if (gasLimitEntity.error) {
                calcGasLimitFromNode
            } else if (ethEstimateGas?.error != null) {
                calcGasLimitFromApi
            } else {
                calcGasLimitFromNode.min(calcGasLimitFromApi)
            }

        }
    }

    override fun estimateGas(param: EstimateTransferGasUseCase.Param): Single<EthEstimateGas> {
        return Single.fromCallable {
            tokenClient.estimateGasForTransfer(
                param.wallet.address,
                param.send.tokenSource.tokenAddress,
                param.send.contact.address,
                param.send.estimateSource.toBigDecimalOrDefaultZero().times(
                    BigDecimal.TEN.pow(param.send.tokenSource.tokenDecimal)
                ).toBigInteger().toString(),
                param.send.tokenSource.isETH
            )
        }
    }

    override fun getCap(param: GetCapUseCase.Param): Single<Cap> {
        return api.getCap(param.walletAddress).map { capMapper.transform(it) }
    }

    override fun getGasPrice(): Flowable<Gas> {
        return api.getGasPrice().map {
            if (it.success) {
                it.data
            } else {
                tokenClient.getGasPrice()
            }
        }
            .map { mapper.transform(it) }
            .repeatWhen {
                it.delay(30, TimeUnit.SECONDS)
            }
            .onErrorResumeNext(Flowable.fromCallable {
                mapper.transform(tokenClient.getGasPrice())
            })
//            .retryWhen { throwable ->
//                throwable.compose(zipWithFlatMap())
//            }
    }

    override fun saveSwap(param: SaveSwapUseCase.Param): Completable {
        return Completable.fromCallable {
            val swap = param.swap
            if (swap.gasLimit.isEmpty()) {
                val sourceToken = tokenDao.getTokenByAddress(swap.tokenSource.tokenAddress)
                swap.gasLimit = sourceToken?.gasLimit ?: swap.gasLimit
            }
            swapDao.insertSwap(swap)
        }
    }

    override fun saveSend(param: SaveSendTokenUseCase.Param): Completable {
        return Completable.fromCallable {
            val address = param.walletAddress
            val token = param.token
            sendTokenDao.add(Send(address, token))

        }
    }

    override fun saveSwapData(param: SaveSwapDataTokenUseCase.Param): Completable {
        return Completable.fromCallable {
            val swapByWalletAddress =
                swapDao.findSwapByAddressFlowable(param.walletAddress).blockingFirst()
            val tokenByAddress = tokenDao.getTokenByAddress(param.token.tokenAddress)
            val swap = if (param.isSourceToken) {
                swapByWalletAddress.copy(tokenSource = tokenByAddress ?: Token())
            } else {
                swapByWalletAddress.copy(tokenDest = tokenByAddress ?: Token())
            }
            swapDao.updateSwap(swap)
        }
    }

    private fun getPairFromPromoWallet(promo: Promo?): Pair<String, String> {
        val sourceToken: String = context.getString(R.string.promo_source_token)
        val destToken =
            if (promo?.destinationToken?.isNotBlank() == true) promo.destinationToken else Token.KNC

        return Pair(sourceToken, destToken)
    }

    override fun getSwapData(param: GetSwapDataUseCase.Param): Flowable<Swap> {
        return Flowable.fromCallable {
            val wallet = param.wallet
            val alert = param.alert
            val notification = param.notificationExt
            val localSwap = swapDao.findSwapByAddress(wallet.address)
            val defaultSwap =
                when {
                    alert != null -> {
                        val sourceToken: String
                        val destToken: String
                        if (wallet.isPromo) {
                            val pair = getPairFromPromoWallet(wallet.promo)
                            sourceToken = pair.first
                            destToken = pair.second
                        } else {
                            sourceToken =
                                if (alert.base == Alert.BASE_USD) Token.ETH else alert.token
                            destToken = if (alert.base == Alert.BASE_USD) Token.KNC else Token.ETH
                        }

                        val defaultSourceToken = tokenDao.getTokenBySymbol(sourceToken) ?: Token()
                        val defaultDestToken = tokenDao.getTokenBySymbol(destToken) ?: Token()
                        val ethToken = tokenDao.getTokenBySymbol(Token.ETH) ?: Token()
                        Swap(
                            wallet.address,
                            defaultSourceToken,
                            defaultDestToken,
                            ethToken = ethToken
                        )
                    }

                    notification != null -> {
                        val sourceToken: String
                        var destToken: String
                        if (wallet.isPromo) {
                            val pair = getPairFromPromoWallet(wallet.promo)
                            sourceToken = pair.first
                            destToken = pair.second
                        } else {
                            sourceToken = Token.ETH
                            destToken =
                                if (notification.token.isEmpty()) Token.KNC else notification.token

                            if (destToken == sourceToken) {
                                destToken = Token.DAI
                            }
                        }

                        val defaultSourceToken = tokenDao.getTokenBySymbol(sourceToken) ?: Token()
                        val defaultDestToken = tokenDao.getTokenBySymbol(destToken) ?: Token()
                        val ethToken = tokenDao.getTokenBySymbol(Token.ETH) ?: Token()
                        Swap(
                            wallet.address,
                            defaultSourceToken,
                            defaultDestToken,
                            ethToken = ethToken
                        )
                    }

                    localSwap == null -> {
                        val sourceToken: String
                        val destToken: String
                        if (wallet.isPromo) {
                            val pair = getPairFromPromoWallet(wallet.promo)
                            sourceToken = pair.first
                            destToken = pair.second
                        } else {
                            sourceToken = Token.ETH
                            destToken = Token.KNC
                        }

                        val defaultSourceToken = tokenDao.getTokenBySymbol(sourceToken) ?: Token()
                        val defaultDestToken = tokenDao.getTokenBySymbol(destToken) ?: Token()
                        val ethToken = tokenDao.getTokenBySymbol(Token.ETH) ?: Token()

                        Swap(
                            wallet.address,
                            defaultSourceToken,
                            defaultDestToken,
                            ethToken = ethToken
                        )
                    }
                    else -> {
                        var tokenSource =
                            tokenDao.getTokenByAddress(localSwap.tokenSource.tokenAddress)
                                ?: Token()
                        var tokenDest =
                            tokenDao.getTokenByAddress(localSwap.tokenDest.tokenAddress)
                                ?: Token()

                        if (tokenSource.isOther) {
                            val listedSource =
                                tokenDao.getAllTokenBySymbol(tokenSource.tokenSymbol)
                                    .firstOrNull { !it.isOther }
                            if (listedSource != null) {
                                tokenSource = listedSource
                            }
                        }

                        if (tokenDest.isOther) {
                            val listedDest =
                                tokenDao.getAllTokenBySymbol(tokenDest.tokenSymbol)
                                    .firstOrNull { !it.isOther }
                            if (listedDest != null) {
                                tokenDest = listedDest
                            }
                        }

                        val ethToken = tokenDao.getTokenBySymbol(Token.ETH) ?: Token()
                        localSwap.copy(
                            tokenSource = tokenSource,
                            tokenDest = tokenDest,
                            ethToken = ethToken
                        )
                    }
                }

            val swap = defaultSwap.copy(
                tokenSource =
                if (wallet.address != defaultSwap.tokenSource.selectedWalletAddress) {
                    defaultSwap.tokenSource.updateSelectedWallet(wallet)
                } else {
                    defaultSwap.tokenSource
                },
                tokenDest =
                if (wallet.address != defaultSwap.tokenDest.selectedWalletAddress) {
                    defaultSwap.tokenDest.updateSelectedWallet(wallet)
                } else {
                    defaultSwap.tokenDest
                },
                ethToken = if (wallet.address != defaultSwap.ethToken.selectedWalletAddress) {
                    defaultSwap.ethToken.updateSelectedWallet(wallet)
                } else {
                    defaultSwap.ethToken
                }

            )
            swapDao.insertSwap(swap)
            swap
        }.flatMap { swap ->
            swapDao.findSwapByAddressFlowable(param.wallet.address).defaultIfEmpty(
                swap
            ).map {
                it.ethToken = swap.ethToken
                it
            }
        }
    }

    override fun getSendData(param: GetSendTokenUseCase.Param): Flowable<Send> {
        return Flowable.fromCallable {
            val wallet = param.wallet
            val send = sendTokenDao.findSendByAddress(wallet.address)
            val defaultSend = if (send == null) {
                val defaultSourceToken = tokenDao.getTokenBySymbol(Token.ETH) ?: Token()
                val ethToken = tokenDao.getTokenBySymbol(Token.ETH) ?: Token()
                Send(
                    wallet.address,
                    defaultSourceToken,
                    ethToken = ethToken
                )
            } else {
                val tokenSource =
                    tokenDao.getTokenByAddress(send.tokenSource.tokenAddress)
                        ?: Token()

                val ethToken = tokenDao.getTokenBySymbol(Token.ETH) ?: Token()
                send.copy(tokenSource = tokenSource, ethToken = ethToken)
            }

            val updatedSend =
                defaultSend.copy(
                    tokenSource = if (defaultSend.tokenSource.selectedWalletAddress != wallet.address) {
                        defaultSend.tokenSource.updateSelectedWallet(wallet)
                    } else {
                        defaultSend.tokenSource
                    },
                    ethToken = if (defaultSend.ethToken.selectedWalletAddress != wallet.address) {
                        defaultSend.ethToken.updateSelectedWallet(wallet)
                    } else {
                        defaultSend.ethToken
                    }
                )

            sendTokenDao.insertSend(updatedSend)
            updatedSend
        }.flatMap { updatedSend ->
            sendTokenDao.findSendByAddressFlowable(param.wallet.address).defaultIfEmpty(
                updatedSend
            ).map {
                it.copy(ethToken = updatedSend.ethToken)
            }
        }
    }

    override fun getKyberNetworkStatus(): Single<KyberEnabled> {
        return api.getKyberEnabled().map {
            userMapper.transform(it)
        }
    }

    override fun getPlatformFee(param: GetPlatformFeeUseCase.Param): Single<PlatformFee> {
        return if (isKatalyst) {
            kyberSwapApi.swapFee(param.source, param.dest).map {
                PlatformFee(it)
            }
        } else {
            Single.fromCallable {
                PlatformFee(true, PLATFORM_FEE_BPS)
            }
        }
    }

    override fun getMaxGasPrice(): Single<MaxGasPrice> {
        return api.getMaxGasPrice().map {
            MaxGasPrice(it)
        }
    }

    override fun ensResolve(param: ENSResolveUseCase.Param): Single<String> {
        return Single.fromCallable {
            tokenClient.resolve(param.name)

        }
    }

    override fun ensRevertResolve(param: ENSRevertResolveUseCase.Param): Single<String> {
        return Single.fromCallable {
            tokenClient.revertResolve(param.address)
        }
    }

    companion object {
        const val BUY_TYPE = "buy"
        const val DEFAULT_HINT = "0x"
    }
}
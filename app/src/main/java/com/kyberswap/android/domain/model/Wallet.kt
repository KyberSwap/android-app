package com.kyberswap.android.domain.model

import android.os.Parcelable
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kyberswap.android.util.ext.shortenValue
import com.kyberswap.android.util.ext.toBigDecimalOrDefaultZero
import com.kyberswap.android.util.ext.toDisplayNumber
import com.kyberswap.android.util.ext.toWalletAddress
import com.kyberswap.android.util.views.DateTimeHelper
import kotlinx.android.parcel.Parcelize
import org.consenlabs.tokencore.wallet.Wallet
import org.consenlabs.tokencore.wallet.WalletManager
import org.jetbrains.annotations.NotNull
import java.math.BigDecimal

@Parcelize
@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey
    @NotNull
    val address: String = "",
    val walletId: String = "",
    val name: String = "",
    val cipher: String = "",
    var isSelected: Boolean = false,
    val mnemonicAvailable: Boolean = false,
    var unit: String = "USD",
    var balance: String = "0",
    @Embedded
    var promo: Promo? = Promo(),
    val createAt: Long = 0,
    val hasBackup: Boolean = false
) :
    Parcelable {
    constructor(wallet: Wallet) : this(
        wallet.address.toWalletAddress(),
        wallet.id,
        wallet.metadata.name
    )

    val displayWalletAddress: String
        get() = address.shortenValue()

    val walletPath: String
        get() = WalletManager.storage.keystoreDir.toString() + "/wallets/" + walletId + ".json"

    val walletAddress: String
        get() = if (isPromoPayment) promo?.receiveAddress
            ?: address else
            address

    fun isSameWallet(other: com.kyberswap.android.domain.model.Wallet?): Boolean {
        return this.address == other?.address &&
            this.isSelected == other.isSelected &&
            this.unit == other.unit &&
            this.isPromo == other.isPromo
    }

    fun display(): String {
        val displayBuilder = StringBuilder()
        if (name.isNotEmpty()) {
            displayBuilder.append(name).append(" - ")
        }
        displayBuilder.append(address.substring(0, 5))
            .append("...")
            .append(
                address.substring(
                    if (address.length > 6) {
                        address.length - 6
                    } else address.length
                )
            )
        return displayBuilder.toString()
    }

    val displayBalance: String
        get() = if (balance.toBigDecimalOrDefaultZero() > BigDecimal(1E-18)) balance.toBigDecimalOrDefaultZero()
            .toDisplayNumber() else BigDecimal.ZERO.toDisplayNumber()

    val isPromo: Boolean
        get() = promo != null && promo!!.type.isNotEmpty()

    val isPromoPayment: Boolean
        get() = isPromo && Promo.PAYMENT == promo?.type

    val expiredDatePromoCode: String
        get() = DateTimeHelper.displayDate(promo?.expiredDate)
}
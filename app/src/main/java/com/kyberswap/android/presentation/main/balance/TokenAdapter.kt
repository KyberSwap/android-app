package com.kyberswap.android.presentation.main.balance

import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.daimajia.swipe.SimpleSwipeListener
import com.daimajia.swipe.SwipeLayout
import com.kyberswap.android.AppExecutors
import com.kyberswap.android.BR
import com.kyberswap.android.R
import com.kyberswap.android.databinding.ItemTokenBinding
import com.kyberswap.android.domain.model.Token
import com.kyberswap.android.presentation.base.DataBoundListSwipeAdapter
import com.kyberswap.android.presentation.base.DataBoundViewHolder
import com.kyberswap.android.presentation.common.MIN_SUPPORT_AMOUNT
import java.math.RoundingMode

class TokenAdapter(
    appExecutors: AppExecutors,
    private val handler: Handler,
    private val onTokenClick: ((Token) -> Unit)?,
    private val onBuyClick: ((Token) -> Unit)?,
    private val onSellClick: ((Token) -> Unit)?,
    private val onSendClick: ((Token) -> Unit)?,
    private val onFavClick: ((Token) -> Unit)?

) : DataBoundListSwipeAdapter<Token, ItemTokenBinding>(
    appExecutors,
    diffCallback = object : DiffUtil.ItemCallback<Token>() {
        override fun areItemsTheSame(oldItem: Token, newItem: Token): Boolean {
            return oldItem.tokenAddress.equals(newItem.tokenAddress, true)
        }

        override fun areContentsTheSame(oldItem: Token, newItem: Token): Boolean {
            return oldItem.areContentsTheSame(newItem)
        }
    }
) {
    private var isEth = false

    val showEth: Boolean
        get() = isEth

    private var orderType: OrderType = OrderType.DEFAULT

    private var isHide = false

    val hideBalance: Boolean
        get() = isHide

    private var tokenType: TokenType = TokenType.LISTED

    private var tokenList = mutableListOf<Token>()

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return R.id.swipe
    }

    fun hideBalance(isHide: Boolean) {
        this.isHide = isHide
        notifyDataSetChanged()
    }

    fun showEth(boolean: Boolean) {
        isEth = boolean
        notifyDataSetChanged()
    }

    fun setFullTokenList(tokenList: List<Token>) {
        this.tokenList.clear()
        this.tokenList.addAll(tokenList)
    }

    fun getFullTokenList(): List<Token> {
        return tokenList
    }

    fun submitFilterList(tokens: List<Token>, forceUpdate: Boolean = false) {
        val orderList = when (orderType) {
            OrderType.NAME -> tokens.sortedBy { it.tokenSymbol }
            OrderType.BALANCE -> tokens.sortedByDescending { it.currentBalance }
                .sortedByDescending { it.currentBalance * it.rateEthNowOrDefaultValue }
            OrderType.ETH_ASC -> tokens.sortedBy {
                it.rateEthNowOrDefaultValue
            }
            OrderType.ETH_DESC -> tokens.sortedByDescending {
                it.rateEthNowOrDefaultValue
            }
            OrderType.USD_ASC -> tokens.sortedBy { it.rateUsdNow }
            OrderType.USD_DESC -> tokens.sortedByDescending {
                it.rateUsdNow
            }
            OrderType.CHANGE_24H_ASC -> tokens.sortedBy {
                it.change24hValue(isEth)
            }
            OrderType.CHANGE24H_DESC -> tokens.sortedByDescending {
                it.change24hValue(isEth)
            }
            OrderType.DEFAULT -> {
                val newTokens = tokens.filter { it.shouldShowAsNew }
                val mutableTokens = tokens.toMutableList()
                if (newTokens.isNotEmpty()) {
                    mutableTokens.removeAll(newTokens)
                    newTokens.union(mutableTokens.sortedByDescending { it.currentBalance }
                        .sortedByDescending { it.currentBalance * it.rateEthNowOrDefaultValue })
                } else {
                    mutableTokens.sortedByDescending { it.currentBalance }
                        .sortedByDescending { it.currentBalance * it.rateEthNowOrDefaultValue }
                }
            }
        }

        val filterList = when (tokenType) {
            TokenType.LISTED -> orderList.filter { it.isListed && !it.isOther }
            TokenType.FAVOURITE -> orderList.filter {
                it.fav
            }
            TokenType.OTHER -> orderList.filter {
                it.isOther && it.currentBalance >= MIN_SUPPORT_AMOUNT
            }
        }.mapIndexed { index, token ->
            token.apply {
                isEven = index % 2 == 0
            }
        }

        if (forceUpdate) {
            submitList(null)
        } else {
            submitList(listOf())
        }

        submitList(filterList)
    }

    fun toggleEth(): OrderType {
        return when (orderType) {
            OrderType.ETH_ASC -> OrderType.ETH_DESC
            OrderType.ETH_DESC -> OrderType.ETH_ASC
            else -> OrderType.ETH_DESC
        }
    }

    fun toggleUsd(): OrderType {
        return when (orderType) {
            OrderType.USD_ASC -> OrderType.USD_DESC
            OrderType.USD_DESC -> OrderType.USD_ASC
            else -> OrderType.USD_DESC
        }
    }

    fun toggleChange24h(): OrderType {
        return when (orderType) {
            OrderType.CHANGE24H_DESC -> OrderType.CHANGE_24H_ASC
            OrderType.CHANGE_24H_ASC -> OrderType.CHANGE24H_DESC
            else -> OrderType.CHANGE24H_DESC
        }
    }

    fun toggleNameBal(): OrderType {
        return when (orderType) {
            OrderType.NAME -> OrderType.BALANCE
            OrderType.BALANCE -> OrderType.NAME
            else -> OrderType.NAME
        }
    }

    val isNameBalOrder: Boolean
        get() = orderType == OrderType.BALANCE || orderType == OrderType.NAME

    val isAsc: Boolean
        get() = orderType == OrderType.ETH_ASC ||
            orderType == OrderType.USD_ASC ||
            orderType == OrderType.CHANGE_24H_ASC


    override fun onBindViewHolder(
        holder: DataBoundViewHolder<ItemTokenBinding>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        mItemManger.bindView(holder.itemView, position)
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun bind(binding: ItemTokenBinding, item: Token) {
        item.isHide = isHide
        binding.setVariable(BR.token, item)
        binding.lnItem.setOnClickListener {
            onTokenClick?.invoke(item)
        }
        binding.btnBuy.setOnClickListener {

            binding.swipe.close(true)
            handler.postDelayed({
                onBuyClick?.invoke(item)
            }, 250)

        }
        binding.btnSell.setOnClickListener {
            binding.swipe.close(true)
            handler.postDelayed({
                onSellClick?.invoke(item)
            }, 250)
        }
        binding.btnSend.setOnClickListener {
            binding.swipe.close(true)
            handler.postDelayed(
                {
                    onSendClick?.invoke(item)
                }, 250
            )

        }

        binding.imgFav.setOnClickListener {
            it.isSelected = !item.fav
            item.fav = !item.fav
            onFavClick?.invoke(item)
        }

        binding.swipe.addSwipeListener(object : SimpleSwipeListener() {
            override fun onStartOpen(layout: SwipeLayout?) {
                mItemManger.closeAllExcept(layout)
            }
        })

        val drawable = when (item.change24hStatus(isEth)) {
            Token.UP -> R.drawable.ic_arrow_up
            Token.DOWN -> R.drawable.ic_arrow_down
            else -> null
        }

        val color = when (item.change24hStatus(isEth)) {
            Token.UP -> R.color.token_change24h_up
            Token.DOWN -> R.color.token_change24h_down
            else -> R.color.token_change24h_same
        }


        Glide.with(binding.imgState)
            .load(drawable)
            .into(binding.imgState)

        binding.imgState.visibility = if (drawable == null) View.GONE else View.VISIBLE

        binding.tvChange24h.setTextColor(ContextCompat.getColor(binding.root.context, color))
        binding.setVariable(BR.showEth, isEth)

        val rate24h = StringBuilder().append(
            if (isEth) item.changeEth24h.abs().setScale(2, RoundingMode.UP).toPlainString() else
                item.changeUsd24h.abs().setScale(2, RoundingMode.UP).toPlainString()
        ).append("%").toString()
        binding.tvChange24h.text = rate24h

        binding.executePendingBindings()

        val background = if (item.isEven) {
            R.drawable.item_even_background
        } else {
            R.drawable.item_odd_background
        }
        binding.lnItem.setBackgroundResource(background)
    }

//    override fun onBindViewHolder(holder: DataBoundViewHolder<ItemTokenBinding>, position: Int) {
//        val binding = holder.binding
//        val background =
//            if (position % 2 == 0) R.drawable.item_even_background else R.drawable.item_odd_background
//        binding.lnItem.setBackgroundResource(background)
//        super.onBindViewHolder(holder, position)
//    }

    override fun createBinding(parent: ViewGroup, viewType: Int): ItemTokenBinding =
        DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.item_token,
            parent,
            false
        )


    fun setOrderBy(type: OrderType, tokenList: List<Token>, forceUpdate: Boolean = true) {
        this.orderType = type
        submitFilterList(tokenList, forceUpdate)
    }

    fun setTokenType(tokenType: TokenType, tokenList: List<Token>) {
        this.tokenType = tokenType
        submitFilterList(tokenList, true)
    }
}

enum class OrderType {
    NAME, BALANCE, ETH_ASC, ETH_DESC, USD_ASC, USD_DESC, CHANGE_24H_ASC, CHANGE24H_DESC, DEFAULT
}

enum class TokenType {
    LISTED, FAVOURITE, OTHER
}
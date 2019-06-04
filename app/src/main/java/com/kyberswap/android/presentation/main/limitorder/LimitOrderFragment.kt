package com.kyberswap.android.presentation.main.limitorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.util.Attributes
import com.kyberswap.android.AppExecutors
import com.kyberswap.android.databinding.FragmentLimitOrderBinding
import com.kyberswap.android.domain.SchedulerProvider
import com.kyberswap.android.domain.model.Wallet
import com.kyberswap.android.presentation.base.BaseFragment
import com.kyberswap.android.presentation.helper.Navigator
import com.kyberswap.android.presentation.main.MainActivity
import com.kyberswap.android.presentation.main.swap.SwapViewModel
import com.kyberswap.android.util.di.ViewModelFactory
import com.kyberswap.android.util.ext.setAllOnClickListener
import com.kyberswap.android.util.ext.showDrawer
import kotlinx.android.synthetic.main.fragment_swap.*
import javax.inject.Inject


class LimitOrderFragment : BaseFragment() {

    private lateinit var binding: FragmentLimitOrderBinding

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var appExecutors: AppExecutors

    private var wallet: Wallet? = null

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(SwapViewModel::class.java)
    }

    @Inject
    lateinit var schedulerProvider: SchedulerProvider


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wallet = arguments!!.getParcelable(WALLET_PARAM)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentLimitOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.walletName = wallet?.name

        wallet?.let {
            viewModel.getSwapData(it.address)
        }
        grTokenSource.setAllOnClickListener(View.OnClickListener {
            navigator.navigateToTokenSearchFromSwapTokenScreen(
                (activity as MainActivity).getCurrentFragment(),
                wallet,
                true
            )
        })

        grTokenDest.setAllOnClickListener(View.OnClickListener {
            navigator.navigateToTokenSearchFromSwapTokenScreen(
                (activity as MainActivity).getCurrentFragment(),
                wallet,
                false
            )
        })

        imgMenu.setOnClickListener {
            showDrawer(true)
        }

        imgSwap.setOnClickListener {


        }

        binding.tvSubmitOrder.setOnClickListener {
            navigator.navigateToLimitOrderSuggestionScreen(
                (activity as MainActivity).getCurrentFragment(),
                wallet
            )
        }

        binding.rvRelatedOrder.layoutManager = LinearLayoutManager(
            activity,
            RecyclerView.VERTICAL,
            false
        )
        val tokenAdapter =
            OrderAdapter(
                appExecutors
            ) {

            }
        tokenAdapter.mode = Attributes.Mode.Single
        binding.rvRelatedOrder.adapter = tokenAdapter

        binding.tvManageOrder.setOnClickListener {
            navigator.navigateToManageOrder(
                (activity as MainActivity).getCurrentFragment(),
                wallet
            )
        }

    }

    companion object {
        private const val WALLET_PARAM = "wallet_param"
        fun newInstance(wallet: Wallet?) =
            LimitOrderFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(WALLET_PARAM, wallet)
                }
            }
    }
}

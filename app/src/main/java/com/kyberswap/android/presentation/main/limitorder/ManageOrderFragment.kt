package com.kyberswap.android.presentation.main.limitorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.util.Attributes
import com.google.firebase.analytics.FirebaseAnalytics
import com.kyberswap.android.AppExecutors
import com.kyberswap.android.R
import com.kyberswap.android.databinding.FragmentManageOrderBinding
import com.kyberswap.android.domain.SchedulerProvider
import com.kyberswap.android.domain.model.Wallet
import com.kyberswap.android.presentation.base.BaseFragment
import com.kyberswap.android.presentation.common.LoginState
import com.kyberswap.android.presentation.helper.DialogHelper
import com.kyberswap.android.presentation.helper.Navigator
import com.kyberswap.android.presentation.main.profile.UserInfoState
import com.kyberswap.android.util.LO_MANAGER_CANCEL
import com.kyberswap.android.util.LO_MANAGER_CLOSE_ORDER_TAPPED
import com.kyberswap.android.util.LO_MANAGER_FILTER
import com.kyberswap.android.util.LO_MANAGER_OPEN_ORDER_TAPPED
import com.kyberswap.android.util.di.ViewModelFactory
import com.kyberswap.android.util.ext.createEvent
import com.kyberswap.android.util.ext.openUrl
import javax.inject.Inject

class ManageOrderFragment : BaseFragment(), LoginState {

    private lateinit var binding: FragmentManageOrderBinding

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var dialogHelper: DialogHelper

    @Inject
    lateinit var appExecutors: AppExecutors

    private var wallet: Wallet? = null

    private var isHideFaq: Boolean = false

    private var isHideInstruction: Boolean = false

    @Inject
    lateinit var analytics: FirebaseAnalytics

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(ManageOrderViewModel::class.java)
    }

    private var currentSelectedView: TextView? = null

    @Inject
    lateinit var schedulerProvider: SchedulerProvider

    var orders: List<OrderItem> = mutableListOf()

    var orderAdapter: OrderAdapter? = null

    var error: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wallet = arguments?.getParcelable(WALLET_PARAM)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentManageOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.rvOrder.layoutManager = LinearLayoutManager(
            activity,
            RecyclerView.VERTICAL,
            false
        )

        if (orderAdapter == null) {
            orderAdapter =
                OrderAdapter(
                    appExecutors
                    , {
                        dialogHelper.showCancelOrder(it) {
                            viewModel.cancelOrder(it)
                            analytics.logEvent(
                                LO_MANAGER_CANCEL,
                                Bundle().createEvent()
                            )
                        }
                    }, {
                        dialogHelper.showBottomSheetExtraDialog(it)

                    }, {
                        openUrl(getString(R.string.transaction_etherscan_endpoint_url) + it.txHash)
                    }, {
                        dialogHelper.showInvalidatedDialog(it)
                    })
        }
        orderAdapter?.mode = Attributes.Mode.Single
        binding.rvOrder.adapter = orderAdapter

        binding.imgBack.setOnClickListener {
            activity?.onBackPressed()
        }

        binding.imgFilter.setOnClickListener {
            navigator.navigateToLimitOrderFilterScreen(
                currentFragment,
                wallet
            )
            analytics.logEvent(LO_MANAGER_FILTER, Bundle().createEvent())
        }

        if (currentSelectedView != null) {
            if (currentSelectedView?.text == binding.tvOpenOrder.text) {
                binding.tvOpenOrder.isSelected = true
                currentSelectedView = binding.tvOpenOrder
            } else {
                binding.tvOrderHistory.isSelected = true
                currentSelectedView = binding.tvOrderHistory
            }
        } else {

            binding.tvOpenOrder.isSelected = true
            currentSelectedView = binding.tvOpenOrder
        }

        listOf(binding.tvOpenOrder, binding.tvOrderHistory)
            .forEach { tv ->
                tv.setOnClickListener {
                    if (currentSelectedView != it) {
                        currentSelectedView?.isSelected = false
                        currentSelectedView = tv
                    }
                    it.isSelected = true
                    orderAdapter?.submitList(null)
                    filterByTab(tv == binding.tvOpenOrder)
                    showFaq(!isHideFaq)
                    showInstruction(!isHideInstruction)

                }
            }

        wallet?.let {
            viewModel.getAllOrders()
        }


        viewModel.getOrdersCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                showProgress(state == GetRelatedOrdersState.Loading)
                when (state) {
                    is GetRelatedOrdersState.Success -> {
                        orders = state.orders
                        orderAdapter?.submitList(null)
                        filterByTab(currentSelectedView == binding.tvOpenOrder)
                    }
                    is GetRelatedOrdersState.ShowError -> {
                        val msg = state.message ?: getString(R.string.something_wrong)
                        if (error != msg) {
                            error = msg
                            showError(msg)
                        }
                    }
                }
            }
        })


        viewModel.cancelOrderCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                showProgress(state == CancelOrdersState.Loading)
                when (state) {
                    is CancelOrdersState.Success -> {
                        viewModel.getAllOrders()
                    }
                    is CancelOrdersState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })

        binding.imgFaq.setOnClickListener {
            isHideFaq = true
            showFaq(false)
        }

        binding.imgInstruction.setOnClickListener {
            isHideInstruction = true
            showInstruction(false)
        }

        binding.tvFaq.setOnClickListener {
            openUrl(getString(R.string.order_why_order_not_filled))
        }
    }

    override fun showProgress(showProgress: Boolean) {
        binding.progressBar.visibility = if (showProgress) View.VISIBLE else View.GONE
    }

    private fun showFaq(isShow: Boolean) {
        binding.tvFaq.visibility = if (isShow) View.VISIBLE else View.GONE
        binding.imgFaq.visibility = if (isShow) View.VISIBLE else View.GONE
    }

    private fun showInstruction(isShow: Boolean) {
        binding.tvInstruction.visibility = if (isShow) View.VISIBLE else View.GONE
        binding.imgInstruction.visibility = if (isShow) View.VISIBLE else View.GONE
    }

    override fun getLoginStatus() {
        viewModel.getLoginStatus()
        viewModel.getLoginStatusCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is UserInfoState.Success -> {
                        if (!(state.userInfo != null && state.userInfo.uid > 0)) {
                            activity?.onBackPressed()
                        }
                    }
                    is UserInfoState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })
    }

    private fun filterByTab(isOpenTab: Boolean) {

        val orders = viewModel.ordersWrapper?.orders?.filter {
            it.isOpen == isOpenTab
        } ?: listOf()

        val items = viewModel.toOrderItems(
            orders,
            viewModel.ordersWrapper?.asc == true
        )

        orderAdapter?.submitOrdersList(items)

        binding.isEmpty = items.isEmpty()
        binding.executePendingBindings()

        analytics.logEvent(
            if (isOpenTab) LO_MANAGER_OPEN_ORDER_TAPPED else LO_MANAGER_CLOSE_ORDER_TAPPED,
            Bundle().createEvent()
        )
    }

    companion object {
        private const val WALLET_PARAM = "wallet_param"
        fun newInstance(wallet: Wallet?) =
            ManageOrderFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(WALLET_PARAM, wallet)
                }
            }
    }
}

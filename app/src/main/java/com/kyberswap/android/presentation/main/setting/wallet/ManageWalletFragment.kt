package com.kyberswap.android.presentation.main.setting.wallet


import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.util.Attributes
import com.kyberswap.android.AppExecutors
import com.kyberswap.android.R
import com.kyberswap.android.databinding.FragmentManageWalletBinding
import com.kyberswap.android.domain.model.Wallet
import com.kyberswap.android.domain.model.WalletChangeEvent
import com.kyberswap.android.presentation.base.BaseFragment
import com.kyberswap.android.presentation.helper.DialogHelper
import com.kyberswap.android.presentation.helper.Navigator
import com.kyberswap.android.presentation.landing.CreateWalletState
import com.kyberswap.android.presentation.main.MainActivity
import com.kyberswap.android.presentation.main.balance.GetAllWalletState
import com.kyberswap.android.presentation.setting.PassCodeLockActivity
import com.kyberswap.android.presentation.wallet.UpdateWalletState
import com.kyberswap.android.util.di.ViewModelFactory
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject


class ManageWalletFragment : BaseFragment() {

    private lateinit var binding: FragmentManageWalletBinding

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var appExecutors: AppExecutors

    @Inject
    lateinit var dialogHelper: DialogHelper

    private lateinit var walletAdapter: ManageWalletAdapter

    private var selectedWallet: Wallet? = null

    private val handler by lazy {
        Handler()
    }

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(ManageWalletViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentManageWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        walletAdapter =
            ManageWalletAdapter(appExecutors, handler,
                {
                    dialogHelper.showBottomSheetManageWalletDialog(
                        walletAdapter.getData().size == 1 || it.isSelected,
                        {
                            (activity as MainActivity).isCreateWallet = true
                            viewModel.updateSelectedWallet(it.copy(isSelected = true))
                        }, {
                            navigator.navigateToEditWallet(currentFragment, it)
                        }, {

                            selectedWallet = it
                            showPassCodeLock(DELETE_WALLET)

                        })
                },
                {

                    (activity as MainActivity).isCreateWallet = true
                    viewModel.updateSelectedWallet(it.copy(isSelected = true))
                },
                {
//                    navigator.navigateToEditWallet(currentFragment, it)
                    navigator.navigateToEditWallet(currentFragment, it)
                },
                {
                    selectedWallet = it
                    showPassCodeLock(DELETE_WALLET)
                })



        binding.rvWallet.layoutManager = LinearLayoutManager(
            activity,
            RecyclerView.VERTICAL,
            false
        )
        walletAdapter.mode = Attributes.Mode.Single
        binding.rvWallet.adapter = walletAdapter

        viewModel.getWallets()
        viewModel.getAllWalletStateCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetAllWalletState.Success -> {
                        walletAdapter.submitList(listOf())
                        walletAdapter.submitList(state.wallets)
                    }
                    is GetAllWalletState.ShowError -> {

                    }
                }
            }
        })

        binding.imgAddWallet.setOnClickListener {
            dialogHelper.showBottomSheetDialog(
                {
                    dialogHelper.showConfirmation {
                        (activity as MainActivity).isCreateWallet = true
                        viewModel.createWallet()
                    }
                },
                {
                    navigator.navigateToImportWalletPage()

                }
            )
        }

        viewModel.createWalletCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                showProgress(state == CreateWalletState.Loading)
                when (state) {
                    is CreateWalletState.Success -> {
                        showAlert(getString(R.string.create_wallet_success)) {
                            navigator.navigateToBackupWalletPage(state.words, state.wallet, true)
                        }
                    }
                    is CreateWalletState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })

        viewModel.deleteWalletCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                showProgress(state == DeleteWalletState.Loading)
                when (state) {
                    is DeleteWalletState.Success -> {
                        showAlert(getString(R.string.delete_wallet_success))
                        if (state.verifyStatus.isEmptyWallet) {
                            navigator.navigateToLandingPage()
                            activity?.finishAffinity()
                        }
                    }
                    is DeleteWalletState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })

        viewModel.updateWalletStateCallback.observe(viewLifecycleOwner, Observer { event ->
            event?.getContentIfNotHandled()?.let { state ->
                showProgress(state == UpdateWalletState.Loading)
                when (state) {
                    is UpdateWalletState.Success -> {
                        EventBus.getDefault().post(WalletChangeEvent(state.wallet.address))
                    }
                    is UpdateWalletState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })


        binding.imgBack.setOnClickListener {
            activity?.onBackPressed()
        }
    }

    private fun showPassCodeLock(requestCode: Int) {
        if (activity != null) {
            startActivityForResult(
                PassCodeLockActivity.newIntent(
                    activity!!,
                    PassCodeLockActivity.PASS_CODE_LOCK_TYPE_MANAGE_WALLET
                ), requestCode
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DELETE_WALLET) {
            if (resultCode == Activity.RESULT_OK) {
                selectedWallet?.let {
                    dialogHelper.showConfirmation(
                        getString(R.string.title_delete),
                        getString(R.string.delete_wallet_confirmation),
                        {
                            viewModel.deleteWallet(it)
                        })
                }
            }
        }
    }


    companion object {
        fun newInstance() =
            ManageWalletFragment()
    }
}

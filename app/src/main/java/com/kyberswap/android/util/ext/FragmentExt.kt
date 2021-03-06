package com.kyberswap.android.util.ext

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import androidx.fragment.app.Fragment
import com.kyberswap.android.R
import com.kyberswap.android.presentation.main.MainActivity


fun Fragment.showDrawer(boolean: Boolean) {
    if (activity != null && activity is MainActivity) {
        (activity as MainActivity).showDrawer(boolean)
    }
}

fun Fragment.openUrl(url: String?) {
    if (url.isNullOrEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(url)

    val packageManager = activity?.packageManager
    if (packageManager != null && intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    }

}

fun Fragment.isNetworkAvailable(): Boolean {
    val connectivityManager =
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    val activeNetworkInfo = connectivityManager?.activeNetworkInfo
    return activeNetworkInfo != null && activeNetworkInfo.isConnected
}

fun Fragment.isSomethingWrongError(err: String): Boolean {
    return getString(R.string.something_wrong) == err
}

fun Fragment.shareUrl(text: String?) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    startActivity(sendIntent)

}




package com.kyberswap.android.util.views

import android.text.Spannable
import android.text.SpannableString
import android.widget.RadioButton
import android.widget.TextView
import androidx.databinding.BindingAdapter
import io.github.inflationx.calligraphy3.CalligraphyTypefaceSpan
import io.github.inflationx.calligraphy3.TypefaceUtils

object TextViewBindingAdapter {
    @BindingAdapter("app:resourceId")
    @JvmStatic
    fun setText(view: TextView, resourceId: Int) {
        view.text = view.context.resources.getString(resourceId)
    }

    @BindingAdapter("app:data", "app:font")
    @JvmStatic
    fun styleText(view: TextView, words: Array<String>, font: String) {

        val spannableString = SpannableString(view.text.toString())
        val typeface = TypefaceUtils.load(
            view.context.assets,
            font
        )

        words.forEach {
            val calligraphyTypeface = CalligraphyTypefaceSpan(
                typeface
            )

            spannableString.setSpan(
                calligraphyTypeface,
                spannableString.indexOf(it),
                spannableString.indexOf(it) + it.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        view.setText(spannableString, TextView.BufferType.SPANNABLE)
    }


    @BindingAdapter("app:styleBold", "app:font")
    @JvmStatic
    fun styleRadioButtonText(view: RadioButton, bold: String, font: String) {

        val spannableString = SpannableString(view.text.toString())
        val typeface = TypefaceUtils.load(
            view.context.assets,
            font
        )

        val calligraphyTypeface = CalligraphyTypefaceSpan(
            typeface
        )

        spannableString.setSpan(
            calligraphyTypeface,
            spannableString.indexOf(bold),
            spannableString.indexOf(bold) + bold.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        view.setText(spannableString, TextView.BufferType.SPANNABLE)
    }
}
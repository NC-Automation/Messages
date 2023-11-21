package com.ncautomation.commons.compose.extensions

import android.app.Activity
import android.content.Context
import com.ncautomation.commons.R
import com.ncautomation.commons.extensions.baseConfig
import com.ncautomation.commons.extensions.redirectToRateUs
import com.ncautomation.commons.extensions.toast
import com.ncautomation.commons.helpers.BaseConfig

val Context.config: BaseConfig get() = BaseConfig.newInstance(applicationContext)

fun Activity.rateStarsRedirectAndThankYou(stars: Int) {
    if (stars == 5) {
        redirectToRateUs()
    }
    toast(R.string.thank_you)
    baseConfig.wasAppRated = true
}

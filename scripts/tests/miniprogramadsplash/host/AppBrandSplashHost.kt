package com.tencent.mm.plugin.appbrand.fake

/**
 * Synthetic appbrand host for the splash-ad selector: the parameter type lives in
 * com.tencent.mm.plugin.appbrand.* and the method is a one-argument boolean, which
 * is exactly the descriptor the production selector accepts. Not the WeChat class.
 */
class AppBrandContact {
    var appId: String = "wx0000000000000000"
}

class SplashAdHost {
    fun canShowAd(contact: AppBrandContact): Boolean = true
}

/** Same descriptor shape in another class: used to assert ambiguous candidates are refused. */
class SecondSplashAdHost {
    fun canShowAd(contact: AppBrandContact): Boolean = false
}
package com.tencent.mm.plugin.appbrand.jsapi

import com.tencent.mm.plugin.appbrand.config.AppBrandLaunchFromNotifyReferrer
import com.tencent.mm.plugin.appbrand.report.quality.QualitySession
import org.json.JSONObject

/** Matches the 17-parameter CgiLaunchWxaApp request constructor and the private open URL method. */
class CgiLaunchHost(
    a0: String,
    a1: Boolean,
    a2: Any?,
    a3: Any?,
    a4: Any?,
    a5: String,
    a6: Int,
    a7: QualitySession,
    a8: String,
    a9: AppBrandLaunchFromNotifyReferrer,
    a10: String,
    a11: Any?,
    a12: Int,
    a13: Boolean,
    a14: String,
    a15: Boolean,
    a16: Boolean
) {
    fun privateOpenUrl(key: String, request: JSONObject, flag: Int) {
        calls++
        val probe = observer
        observer = null
        probe?.invoke(request)
    }

    companion object {
        var calls = 0
        var observer: ((JSONObject) -> Unit)? = null
    }
}
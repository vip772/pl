package t.mma.hooks.api.core

import t.mma.hooks.api.model.WeChatVersionInfo

/** Synthetic version feed: one host version per test, re-read on every call. */
class VersionApi(private val info: WeChatVersionInfo) {
    fun current(): WeChatVersionInfo = info
}

object WeChatApis {
    @JvmStatic
    var versionApi: VersionApi? = null

    @JvmStatic
    fun version(): VersionApi? = versionApi

    fun reset() {
        versionApi = null
    }
}

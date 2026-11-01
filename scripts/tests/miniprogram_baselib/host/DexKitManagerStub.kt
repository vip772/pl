package t.mma.dexkit

import org.luckypray.dexkit.BridgeState
import org.luckypray.dexkit.DexKitBridge

/** Synthetic bridge manager: exposes one bridge fed by BridgeState (mirrors the real ABI). */
class DexKitBridgeManager {
    fun <T> withCurrentBridge(action: (DexKitBridge) -> T): T = action(BridgeState.bridge)
}
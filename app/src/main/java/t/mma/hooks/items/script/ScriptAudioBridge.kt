package t.mma.hooks.items.script

import t.mma.media.AudioTransformBridge

class ScriptAudioBridge internal constructor(
    bridge: ScriptPluginBridge
) : AudioTransformBridge({ message -> bridge.log(message) })

"""Source contracts only: no host execution, APK build or device acceptance."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]/'app/src/main/java/t/mma'
f=(r/'hooks/items/antirecall/AntiRecallFeature.kt').read_text()
v=(r/'hooks/items/antirecall/DynamicRecallRuntime.kt').read_text()
h=(r/'hooks/items/hchatextra/HchatExtraFeature.kt').read_text()
def section(text,a,b):return text[text.index(a):text.index(b,text.index(a))]
lookup=section(f,'    private fun resolveRecalledMessage(', '    private fun normalizeMillis(')
assert 'getMessageById' not in lookup and 'getCreateTimeBySvrId' not in lookup
assert 'getMessageBySvrId(talker, id)' in lookup
assert 'it.talker == talker && it.msgSvrId == id && !it.isRecalled()' in lookup
payload=section(f,'    private fun parseRevokePayload(', '    private fun revokeNameFromText(')
assert 'readField(addMsg, "r")' not in payload and 'xmlLong(commandContent, "msgid")' not in payload
assert 'xmlLong(commandContent, "newmsgid")' in payload
callback=section(f,'                            val payloadInfo = runCatching', '    private abstract class RecallHook')
assert callback.index('param.setResult(null)')<callback.index('publishRecallEvent(context')<callback.index('handleRecall(context')
assert 'callback.beforeHookedMethod(param) } catch' in f
assert 'if (method in installedMethods) return' in f and 'installedMethods.add(method)' in f
for token in ['installation?.close()', 'ownedHooks.close()', 'installedMethods.clear()',
              'noticeHandler.removeCallbacksAndMessages(null)', 'messageCache.clear()',
              'selfRecallMsgIds.clear()', 'rawMessageRows.clear()', 'if (!owner.isClosed()) insertLocalNotice']:
 assert token in f,token
assert 'var installed = revokeEntries.isNotEmpty()' in f
assert f.count('var installed = methods.isNotEmpty()')==3
assert 'HookRegistry.get().hook' not in f
assert 'putBoolean' not in f
entry=section(h,'    override fun onFeatureInstall(', '    companion object {')
assert 'recallPrefs.getBoolean(t.mma.hooks.items.antirecall.AntiRecallSettings.KEY_ENABLE, false)' in entry
assert 'recallPrefs.registerOnSharedPreferenceChangeListener(recallListener)' in entry
assert 'recallPrefs.unregisterOnSharedPreferenceChangeListener(recallListener)' in entry
binding=section(h,'    private fun bindDynamicRecall(', '    private fun captureMessageDetailsBindState(')
assert binding.index('findHolderView(holder, "clickArea"')<binding.index('holderCardContainer(holder, parent)')
assert 'hasMessageSizedBounds(it, parent)' in binding
marker=section(v,'    private inner class RecallMarker', 'private inner class RecallStroke')
draw=marker[marker.index('        override fun draw('):]
assert 'updatePosition(current, outgoing)' in draw
assert 'root.getLocationOnScreen(rootLocation)' in marker
assert 'path.reset()' not in marker
assert 'view.alpha = 0.5f' in v and 'fadedContent?.alpha = originalAlpha' in v
assert 'dynamicRecall.clear(root)' in h
print('PASS static contracts: server IDs/talker validation, payload identity, interception isolation, per-method retry, owned teardown, independent recall enable, native anchor and live marker coordinates')
print('Static evidence only; no behavior or device acceptance claimed')
"""Cross-check production integration and low-cost path structure without Gradle."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
def read(path):return (r/path).read_text()
base='app/src/main/java/t/mma/'
feature=read(base+'hooks/items/groupbar/GroupBarFeature.kt')
store=read(base+'hooks/items/groupbar/GroupBarStore.kt')
runtime=read(base+'hooks/items/groupbar/GroupBarRuntime.kt')
page=read(base+'ui/miuix/GroupBarMiuixPage.kt')
assert '.register(t.mma.hooks.items.groupbar.GroupBarFeature())' in read(base+'hooks/core/FeatureRegistry.kt')
assert '"conversation_group_bar" to Toggle("Hchat_conversation_group_bar_config", "enable")' in read(base+'ui/FeatureToggleRegistry.kt')
assert 'GroupBarStore.ID -> GroupBarMiuixPage(context, provider, onBack)' in read(base+'ui/miuix/MiuixSettingsPagePart03.kt')
listing=read(base+'ui/miuix/MiuixSettingsPagePart02.kt')
assert 'setOf(ConversationGroupFeature.ID, "conversation_group_bar")' in listing
assert 'FeatureSettingsProvider.CATEGORY_ENHANCE, true' in feature
assert 'prefs.getBoolean(GroupBarStore.ENABLE, false)' in runtime
assert 'queryScope.get()' in runtime and 'queryScope.remove()' in runtime
assert 'database.subscribeQuery' in runtime
assert 'findMethodRecursive(adapter.javaClass, "S2")' in runtime and 'reset.invoke(adapter)' in runtime
assert '"jo5.y", "po5.l"' in runtime
assert 'discoverHomepage' in runtime and 'HomeExtensionSurface.Root' in runtime
assert 'priority=title_first' in runtime and 'bar.bringToFront()' in runtime
assert 'filter.accepts' in runtime and 'getDeclaredMethod("l", row)' in runtime
assert 'onBindViewHolder' not in runtime and 'getView' not in runtime and 'stateFields' not in runtime
assert 'afterDisabledRestore' in runtime and 'enabled && ready' in runtime
assert 'if (changed && (enabled || wasEnabled)' in runtime
assert 'worker.execute' in runtime and 'getPickerContacts' in runtime
for banned in ('OnDrawListener','OnScrollListener','postOnAnimation','Timer('):assert banned not in runtime
assert 'ConversationGroupingExclusion.bind' in read(base+'preferences/HchatStorage.kt')
restore=read(base+'hooks/items/conversationgroup/ConversationGroupRuntime.kt')
assert 'syncDatabase(app, requireRestored = true, allowUnresolved = true)' in restore and 'restored.containsAll(plan.updates.keys)' in restore
assert 'override fun onViewDetachedFromWindow' in runtime and 'removeOnAttachStateChangeListener' in runtime
d=Path('/sdcard/Download/wechat8078_decoded')
source=(d/'smali_classes11/jo5/e.smali').read_text()
assert '.method public final g(Ljo5/m0;)Ljo5/n0;' in source
assert '.method public final l(Ljo5/z;)Z' in source
for f in ['jo5/y0','po5/u']:
 text=(d/f'smali_classes11/{f}.smali').read_text();reset=text.split('.method public S2()V',1)[1].split('.end method',1)[0]
 assert 'forceResetAllData' in reset
 assert ('Ljo5/r;->b()V' in reset) if f=='jo5/y0' else ('ConcurrentMvvmList;->f(' in reset)
for f in ['jo5/m','po5/l']:assert 'Ljo5/e;->g(Ljo5/m0;)' in (d/f'smali_classes11/{f}.smali').read_text()
assert 'Ljo5/e;->l(Ljo5/z;)Z' in (d/'smali_classes11/po5/l.smali').read_text()
print('PASS production integration and actual smali: both native loaders, S2 resets, scoped SQL, incremental membership, mutual exclusion/restore guards and detach cleanup')

assert 'RelativeLayout.BELOW, bar.id' not in runtime
assert 'lp.topMargin = 0' not in runtime
assert '.setPadding(' not in runtime and 'restoreAvoidance' in runtime
assert 'scrollField.setInt(list, base + reserve)' in runtime
assert 'main.post { if (!v.isAttachedToWindow)' in runtime
assert 'close.invoke(list, 0L, 0, true)' in runtime
beauty=read(base+'hooks/items/backgroundbeauty/BackgroundBeautyFeature.kt')
assert 'HomeExtensionSurface.ownsGeometry(view)' not in beauty
print('PASS native viewport/rules/padding retained; native list/taskbar offsets coordinated; deferred detach')

listSource=(d/'smali_classes16/com/tencent/mm/ui/conversation/ConversationListView.smali').read_text()
for contract in ['.method public final C()V','.method public o()V','.method public q(II)V','->s:I','->n:I','computeActionBarTranslation %d']:
 assert contract in listSource
recyclerSource=(d/'smali_classes16/com/tencent/mm/ui/conversation/recycler/ConversationRecyclerView.smali').read_text()
for contract in ['.method public final y1()V','.method public final u1()V','.method public o()V','.method public q(II)V','->v2:I','->n:I']:
 assert contract in recyclerSource
assert 'titleTranslation(anchor)' in runtime
assert 'openField.getBoolean(list)' in runtime and 'View.INVISIBLE' in runtime
print('PASS 8.0.78 ListView/RecyclerView offset and taskbar contracts, animation translation excluded, native header visibility gates')

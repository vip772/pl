"""Check actual feature startup ordering; BaseFeature failure behavior tested separately."""
from pathlib import Path
import re
r=Path(__file__).resolve().parents[1]
b=r/'app/src/main/java/t/mma/hooks/items'
files=['transparentavatar/UploadTransparentAvatarFeature.kt','roundavatar/RoundAvatarFeature.kt','emojiat/EmojiAtFeature.kt','hiddenat/HiddenAtFeature.kt','conversationanalysis/ConversationAnalysisFeature.kt','backgroundbeauty/MeEntryVisibilityFeature.kt','homeextension/HomeExtensionFeature.kt','moments/MomentsAutoLikeFeature.kt','moments/MomentsAutoForwardFeature.kt','moments/MomentsAutoCommentFeature.kt','customfriendavatar/CustomFriendAvatarFeature.kt']
for name in files:
 s=(b/name).read_text()
 a=s.index('override fun onFeatureInstall(');z=s.index('override fun onFeatureDestroy(',a)
 block=s[a:z]
 assert not re.search(r'runtime = [^\n]+\.also',block),name
 assert block.index('val installed =') < block.index('runtime = installed') < re.search(r'installed\.(install|start)',block).start(),name
 assert 'runtime?.destroy()' in s[z:],name
base=(r/'app/src/main/java/t/mma/hooks/core/BaseFeature.kt').read_text()
assert 'try { onFeatureInstall(context) } catch (error: Throwable)' in base
assert 'onDestroy(context)' in base
print('PASS 11 production feature runtime owners assigned before fallible installation; existing BaseFeature failure cleanup retained')
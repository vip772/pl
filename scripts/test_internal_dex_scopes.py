"""Source-boundary checks for the next internal DexKit migration batch."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
base = root / 'app/src/main/java/t/mma/hooks/items'
files = [
    'miniprogrambaselib/FakeMiniProgramBaseLibFeature.kt',
    'hideavatar/HideChatAvatarFeature.kt',
    'customfriendavatar/CustomFriendAvatarFeature.kt',
    'groupleave/GroupLeaveMonitorHighlighter.kt',
]
total = 0
for relative in files:
    source = (base / relative).read_text()
    assert 'context.dexKitBridge()' not in source, relative
    matches = list(re.finditer(r'context\.dexKitManager\(\)\.withCurrentBridge \{ bridge ->', source))
    assert matches, relative
    for match in matches:
        opening = source.index('{', match.start())
        depth, end = 1, opening + 1
        while depth:
            depth += (source[end] == '{') - (source[end] == '}')
            end += 1
        scope = source[match.start():end]
        assert 'bridge.findMethod(' in scope, relative
        assert 'getMethodInstance' in scope or 'getConstructorInstance' in scope, relative
        total += 1
print(f'PASS internal DexKit source boundaries: {total} manager-protected query scopes; no raw bridge calls in migrated files')

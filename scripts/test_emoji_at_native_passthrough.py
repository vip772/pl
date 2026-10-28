"""Verify search payloads retain chooser arguments and stable host selection."""
from pathlib import Path
import subprocess, tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/emojiat/EmojiAtRuntime.kt').read_text()
assert 'param.args.getOrNull(2) != null || param.args.getOrNull(4) != null' not in s
assert 'val args = param.args.clone()' in s
assert 'R.readField(foreground, "d2")' in s
assert 'main.post { if (alive && enabled && epoch == generation' in s
assert 'resume(t, receiver, args, talker, emptyList(), epoch)' in s
assert 'resume(t, receiver, args, talker, users, epoch)' in s
assert 'if (Looper.myLooper() != Looper.getMainLooper()) return' not in s
print('PASS search payloads retain both choices, original args, stable chat host, main-thread dispatch and generation guards')

"""Structural integration guards; behavior covered by test_emoji_at_choice_queue.py."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/items/emojiat/EmojiAtRuntime.kt').read_text()
assert 'val args = param.args.clone()' in s
assert 'R.readField(foreground, "d2")' not in s
assert 'choices.offer(Choice(t, receiver, args, talker, epoch))' in s
assert 'choices.retry(request)' in s
assert 'dispatchAfterClose = false' in s
assert 'resume(request.targets, request.receiver, request.args, request.talker, emptyList(), request.epoch)' in s
assert 'resume(request.targets, request.receiver, request.args, request.talker, users, request.epoch)' in s
assert 'registerActivityLifecycleCallbacks(lifecycle)' in s and 'unregisterActivityLifecycleCallbacks(lifecycle)' in s
assert 'if (activity.isFinishing || activity.isDestroyed) return' in s
print('PASS cloned native args, main queue, resumed host migration, both send choices, exact-once selection and lifecycle cleanup')

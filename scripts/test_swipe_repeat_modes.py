"""Static interaction/scope contract; real Android drawing still requires device acceptance."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/t/mma/hooks/items/swipequote'
s=(p/'SwipeRepeatPresentation.kt').read_text()
f=(p/'SwipeQuoteFeature.kt').read_text()
block=f[f.index('    private fun confirmSwipeRepeat'):f.index('    private fun repeatNativeMessage')]
assert 'nextInt(Mode.entries.size)' in s
assert 'ORIGINAL, GENTLE, FINGERPRINT' in s
assert '[害羞]' in s and 'com.tencent.mm.smiley.x1' in s
assert 'getDeclaredMethod("fj", Context::class.java, CharSequence::class.java)' in s
assert 'root.setOnClickListener { dialog.cancel() }' in s
assert 'dialog.setCancelable(true)' in s and 'dialog.setCanceledOnTouchOutside(true)' in s
assert 'dialog.dismiss()\n                confirm()' in s
assert '64 * context.resources.displayMetrics.density' in s
assert 'Color.rgb(250, 246, 235)' in s
assert 'setPositiveButton' not in s and 'setMessage' not in s and 'setTitle' not in s
assert 'FingerprintManager' not in s and 'BiometricPrompt' not in s
assert 'if (confirmed) return@confirm' in block
assert 'currentTalker() == pendingTarget.talker' in block
assert 'row.isAttachedToWindow' in block and 'resources.isClosed()' in block
assert 'row.removeOnAttachStateChangeListener(detachListener)' in block
assert 'SwipeRepeatPresentation.fingerprint(activity, confirm)' in block
assert '.setPositiveButton("真的") { _, _ -> confirm() }' in block
print('PASS three independent random modes, verified native emoji entry, fingerprint-only surface, cancel/confirm contract, frozen talker, single-send guard and detach cleanup (static)')
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
assert 'Color.rgb(255, 152, 0)' in s and 'color = Color.WHITE' in s
assert 'Color.rgb(91, 138, 206)' in s
assert 'anchorTop - gap - button.measuredHeight' in s
assert '100 * resources.displayMetrics.density' in s
assert 'setPositiveButton' not in s and 'setMessage' not in s and 'setTitle' not in s
assert 'FingerprintManager' not in s and 'BiometricPrompt' not in s
assert 'if (confirmed) return@confirm' in block
assert 'currentTalker() == pendingTarget.talker' in block
assert 'row.isAttachedToWindow' in block and 'resources.isClosed()' in block
assert 'row.removeOnAttachStateChangeListener(detachListener)' in block
assert 'SwipeRepeatPresentation.fingerprint(activity, confirm)' in block
assert 'SwipeRepeatDialogStyle.confirmation(activity, message, confirm)' in block
style = (p/'SwipeRepeatDialogStyle.kt').read_text()
assert 'translationY' not in style and 'alertTitle' not in style
assert 'bodyHeight = maxOf(dp(104), message.measuredHeight)' in style
assert 'message.layout(0, 0, width, bodyHeight)' in style
assert 'gravity = Gravity.CENTER' in style
print('PASS swipe confirmation scope, adaptive text region and fingerprint appearance/anchor contracts')
print('PASS three independent random modes, verified native emoji entry, fingerprint-only surface, cancel/confirm contract, frozen talker, single-send guard and detach cleanup (static)')
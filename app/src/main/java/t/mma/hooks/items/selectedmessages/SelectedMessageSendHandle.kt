package t.mma.hooks.items.selectedmessages

class SelectedMessageSendHandle internal constructor(
    private val cancelAction: () -> Unit
) {
    private var stopPendingAction: () -> Unit = cancelAction
    internal fun withStopPending(action: () -> Unit): SelectedMessageSendHandle {
        stopPendingAction = action
        return this
    }
    fun stopPending() { stopPendingAction() }
    fun cancel() {
        cancelAction()
    }
}

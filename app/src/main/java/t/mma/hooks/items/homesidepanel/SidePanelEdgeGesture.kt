package t.mma.hooks.items.homesidepanel

import kotlin.math.abs

internal class SidePanelEdgeGesture(private val edgeWidth: Float, private val touchSlop: Float) {
    private var candidate = false
    private var active = false
    private var downX = 0f
    private var downY = 0f
    fun down(x: Float, y: Float, allowed: Boolean) {
        downX = x
        downY = y
        candidate = allowed && x >= 0f && x <= edgeWidth
        active = false
    }
    fun move(x: Float, y: Float): Boolean {
        if (active) return true
        if (!candidate) return false
        val dx = x - downX
        val dy = abs(y - downY)
        if (dy > touchSlop && dy >= abs(dx) || dx < -touchSlop) candidate = false
        if (candidate && dx > touchSlop && dx > dy * 1.5f) active = true
        return active
    }
    fun reset() { candidate = false; active = false }
}
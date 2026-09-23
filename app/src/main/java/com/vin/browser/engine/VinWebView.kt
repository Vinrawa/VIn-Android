package com.vin.browser.engine

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Custom WebView that intercepts Android OS window visibility callbacks
 * and maintains View.VISIBLE to Chromium's native AwContents when Background Play is ON.
 * Does not intercept onTouchEvent to allow full DOM touch dispatch (player controls, gears, quality sheets).
 */
class VinWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var isBackgroundPlayActive: Boolean = true

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /** True when the page is scrolled to (or above) its top edge -- gates pull-to-refresh. */
    fun isAtTop(): Boolean = scrollY <= 0

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (isBackgroundPlayActive && visibility != View.VISIBLE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}
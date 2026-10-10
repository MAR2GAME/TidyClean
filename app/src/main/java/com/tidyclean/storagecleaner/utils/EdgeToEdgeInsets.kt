package com.tidyclean.storagecleaner.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object EdgeToEdgeInsets {
    fun applyNavigationBarPadding(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val navigationBottom = insets
                .getInsets(WindowInsetsCompat.Type.navigationBars())
                .bottom

            target.setPadding(
                initialLeft,
                initialTop,
                initialRight,
                initialBottom + navigationBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}

package dev.indla.island

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView

/**
 * Debug builds only: an immersive screen, like full-screen video, for checking that the island
 * hides. `adb shell am start -n dev.indla.island.debug/dev.indla.island.FullscreenTestActivity`
 */
class FullscreenTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Full screen test: the island should be hidden"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            textSize = 20f
            setPadding(48, 300, 48, 48)
        })
        window.insetsController?.run {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

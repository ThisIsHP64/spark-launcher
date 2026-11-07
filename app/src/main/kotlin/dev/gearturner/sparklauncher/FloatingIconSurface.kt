package dev.gearturner.sparklauncher

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * a copy of an icon in its own surface, handed to the system during the swipe home animation so
 * the closing window can crossfade into it. the real icon is hidden meanwhile so only one shows.
 * it removes itself when the system reports the animation finished, or after a timeout in case
 * that message never comes.
 */
class FloatingIconSurface(
    private val activity: Activity,
    private val bounds: RectF,
    private val icon: Bitmap,
    private val onReady: (surface: SurfaceControl, onFinished: Message) -> Unit,
    private val onDismissed: () -> Unit,
) : SurfaceHolder.Callback {

    // the finish message crosses into the system process, so it can only carry a code, never a runnable
    private val handler = Handler(Looper.getMainLooper()) { message ->
        if (message.what == MSG_ANIMATION_FINISHED) {
            dismiss()
            true
        } else {
            false
        }
    }
    private val surfaceView = SurfaceView(activity)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var sent = false
    private var dismissed = false

    private val timeout = Runnable { dismiss() }

    fun show() {
        val width = bounds.width().roundToInt().coerceAtLeast(1)
        val height = bounds.height().roundToInt().coerceAtLeast(1)
        surfaceView.apply {
            // above the launcher window so the system can fade between it and the closing app
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.addCallback(this@FloatingIconSurface)
            x = bounds.left
            y = bounds.top
        }
        val decor = activity.window.decorView as? ViewGroup ?: return dismiss()
        decor.addView(surfaceView, FrameLayout.LayoutParams(width, height))
        handler.postDelayed(timeout, TIMEOUT_MS)
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        handler.removeCallbacks(timeout)
        surfaceView.holder.removeCallback(this)
        (surfaceView.parent as? ViewGroup)?.removeView(surfaceView)
        onDismissed()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        draw(holder)
        if (sent || dismissed) return
        sent = true
        // the system replies to this message on our main thread once the window has landed
        val finished = Message.obtain(handler, MSG_ANIMATION_FINISHED).apply { replyTo = Messenger(handler) }
        onReady(surfaceView.surfaceControl, finished)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = draw(holder)

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    private fun draw(holder: SurfaceHolder) {
        val canvas = try {
            holder.lockHardwareCanvas()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "floating icon surface not drawable", e)
            null
        } ?: return
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(icon, null, Rect(0, 0, canvas.width, canvas.height), paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private companion object {
        const val TAG = "FloatingIconSurface"
        const val TIMEOUT_MS = 2_000L
        const val MSG_ANIMATION_FINISHED = 1
    }
}

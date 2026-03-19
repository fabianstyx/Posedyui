// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.stream

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.metallic.chiaki.R
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.common.ext.viewModelFactory
import com.metallic.chiaki.databinding.ActivityStreamBinding
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.ConnectVideoProfile
import com.metallic.chiaki.session.*
import com.metallic.chiaki.touchcontrols.DefaultTouchControlsFragment
import com.metallic.chiaki.touchcontrols.TouchControlsFragment
import com.metallic.chiaki.touchcontrols.TouchpadOnlyFragment
import com.metallic.chiaki.posetracker.PoseTrackerManager
import com.metallic.chiaki.posetracker.PoseTrackerOverlayView
import android.graphics.RectF
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlin.math.min
import kotlin.math.abs

private sealed class DialogContents
private object StreamQuitDialog: DialogContents()
private object CreateErrorDialog: DialogContents()
private object PinRequestDialog: DialogContents()

class StreamActivity : AppCompatActivity(), View.OnSystemUiVisibilityChangeListener
{
        companion object
        {
                const val EXTRA_CONNECT_INFO = "connect_info"
                private const val HIDE_UI_TIMEOUT_MS = 2000L
                private const val FRAME_CAPTURE_INTERVAL_MS = 100L
        }

        private lateinit var viewModel: StreamViewModel
        private lateinit var binding: ActivityStreamBinding
        private lateinit var preferences: Preferences
        private var poseTrackerManager: PoseTrackerManager? = null
        
        private var poseTrackerHandlerThread: HandlerThread? = null
        private var poseTrackerHandler: Handler? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile private var isCapturingFrames = false
        @Volatile private var isPendingCapture = false
        
        private var buttonDragStartX = 0f
        private var buttonDragStartY = 0f
        private var buttonInitialX = 0f
        private var buttonInitialY = 0f
        private var isDragging = false
        private val dragThreshold = 10f
        
        private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

        private val uiVisibilityHandler = Handler()

        override fun onCreate(savedInstanceState: Bundle?)
        {
                super.onCreate(savedInstanceState)

                val connectInfo = intent.getParcelableExtra<ConnectInfo>(EXTRA_CONNECT_INFO)
                if(connectInfo == null)
                {
                        finish()
                        return
                }

                viewModel = ViewModelProvider(this, viewModelFactory {
                        StreamViewModel(application, connectInfo)
                })[StreamViewModel::class.java]

                viewModel.input.observe(this)

                binding = ActivityStreamBinding.inflate(layoutInflater)
                setContentView(binding.root)
                window.decorView.setOnSystemUiVisibilityChangeListener(this)

                viewModel.onScreenControlsEnabled.observe(this, Observer {
                        if(binding.onScreenControlsSwitch.isChecked != it)
                                binding.onScreenControlsSwitch.isChecked = it
                        if(binding.onScreenControlsSwitch.isChecked)
                                binding.touchpadOnlySwitch.isChecked = false
                })
                binding.onScreenControlsSwitch.setOnCheckedChangeListener { _, isChecked ->
                        viewModel.setOnScreenControlsEnabled(isChecked)
                        showOverlay()
                }

                viewModel.touchpadOnlyEnabled.observe(this, Observer {
                        if(binding.touchpadOnlySwitch.isChecked != it)
                                binding.touchpadOnlySwitch.isChecked = it
                        if(binding.touchpadOnlySwitch.isChecked)
                                binding.onScreenControlsSwitch.isChecked = false
                })
                binding.touchpadOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
                        viewModel.setTouchpadOnlyEnabled(isChecked)
                        showOverlay()
                }

                binding.displayModeToggle.addOnButtonCheckedListener { _, _, _ ->
                        adjustStreamViewAspect()
                        showOverlay()
                }

                //viewModel.session.attachToTextureView(textureView)
                viewModel.session.attachToSurfaceView(binding.surfaceView)
                viewModel.session.state.observe(this, Observer { this.stateChanged(it) })
                adjustStreamViewAspect()

                if(Preferences(this).rumbleEnabled)
                {
                        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                        viewModel.session.rumbleState.observe(this, Observer {
                                val amplitude = min(255, (it.left.toInt() + it.right.toInt()) / 2)
                                vibrator.cancel()
                                if(amplitude == 0)
                                        return@Observer
                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        vibrator.vibrate(VibrationEffect.createOneShot(1000, amplitude))
                                else
                                        vibrator.vibrate(1000)
                        })
                }

                initializePoseTracker()
        }

        private fun initializePoseTracker()
        {
                preferences = Preferences(this)
                val overlayView = binding.poseTrackerOverlay
                
                poseTrackerHandlerThread = HandlerThread("PoseTrackerThread").apply { start() }
                poseTrackerHandler = Handler(poseTrackerHandlerThread!!.looper)
                
                poseTrackerManager = PoseTrackerManager(
                        context = this,
                        overlayView = overlayView,
                        onCursorMove = { movementX, movementY ->
                                viewModel.input.injectPoseTrackerMovement(movementX, movementY)
                        },
                        onTriggerBot = { isFiring ->
                                viewModel.input.injectTriggerBot(isFiring)
                        },
                        // FIX: tell StreamInput to zero and deactivate the right stick whenever
                        // the pose tracker loses its target, so the camera stops moving.
                        onResetMovement = {
                                viewModel.input.resetPoseTrackerMovement()
                        }
                )
                poseTrackerManager?.initialize()

                binding.poseTrackerToggleButton.visibility = View.VISIBLE
                setupDraggableButton()
                
                if(preferences.poseTrackerEnabled)
                {
                        poseTrackerManager?.setTrackingEnabled(true)
                        binding.poseTrackerToggleButton.alpha = 1.0f
                        startFrameCapture()
                }
                
                layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                        updateVideoRect()
                }
                binding.surfaceView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        
        @Suppress("ClickableViewAccessibility")
        private fun setupDraggableButton()
        {
                val button = binding.poseTrackerToggleButton
                
                button.setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                        buttonDragStartX = event.rawX
                                        buttonDragStartY = event.rawY
                                        buttonInitialX = view.x
                                        buttonInitialY = view.y
                                        isDragging = false
                                        true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                        val deltaX = event.rawX - buttonDragStartX
                                        val deltaY = event.rawY - buttonDragStartY
                                        
                                        if (abs(deltaX) > dragThreshold || abs(deltaY) > dragThreshold) {
                                                isDragging = true
                                        }
                                        
                                        if (isDragging) {
                                                val parent = view.parent as View
                                                val newX = (buttonInitialX + deltaX).coerceIn(0f, (parent.width - view.width).toFloat())
                                                val newY = (buttonInitialY + deltaY).coerceIn(0f, (parent.height - view.height).toFloat())
                                                view.x = newX
                                                view.y = newY
                                                true
                                        } else {
                                                false
                                        }
                                }
                                MotionEvent.ACTION_UP -> {
                                        if (!isDragging) {
                                                view.performClick()
                                        }
                                        isDragging = false
                                        true
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                        isDragging = false
                                        true
                                }
                                else -> false
                        }
                }
                
                button.setOnClickListener {
                        togglePoseTracker()
                }
        }
        
        private fun togglePoseTracker()
        {
                val isActive = poseTrackerManager?.toggleTracking() ?: false
                binding.poseTrackerToggleButton.alpha = if(isActive) 1.0f else 0.6f
                preferences.poseTrackerEnabled = isActive
                
                if(isActive) {
                        startFrameCapture()
                } else {
                        stopFrameCapture()
                        viewModel.input.resetPoseTrackerMovement()
                }
        }
        
        private fun updateVideoRect()
        {
                val surfaceView = binding.surfaceView
                val overlayView = binding.poseTrackerOverlay
                
                val surfaceLocation = IntArray(2)
                val overlayLocation = IntArray(2)
                surfaceView.getLocationInWindow(surfaceLocation)
                overlayView.getLocationInWindow(overlayLocation)
                
                val videoRect = RectF(
                        (surfaceLocation[0] - overlayLocation[0]).toFloat(),
                        (surfaceLocation[1] - overlayLocation[1]).toFloat(),
                        (surfaceLocation[0] - overlayLocation[0] + surfaceView.width).toFloat(),
                        (surfaceLocation[1] - overlayLocation[1] + surfaceView.height).toFloat()
                )
                poseTrackerManager?.setVideoRect(videoRect)
        }
        
        private fun startFrameCapture()
        {
                if (isCapturingFrames) return
                isCapturingFrames = true
                scheduleNextCapture()
        }
        
        private fun stopFrameCapture()
        {
                isCapturingFrames = false
        }
        
        private fun scheduleNextCapture()
        {
                if (!isCapturingFrames) return
                poseTrackerHandler?.postDelayed({
                        if (isCapturingFrames && !isPendingCapture) {
                                // FIX: post the actual capture to the main thread so that
                                // surfaceView.width/height are read on the UI thread.
                                // Previously this was called directly from poseTrackerHandler,
                                // causing silent PixelCopy failures due to off-thread View access.
                                mainHandler.post { captureFrame() }
                        }
                        scheduleNextCapture()
                }, FRAME_CAPTURE_INTERVAL_MS)
        }
        
        // Must be called on the main thread (accesses View dimensions and PixelCopy).
        private fun captureFrame()
        {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
                if (isPendingCapture) return
                
                val surfaceView = binding.surfaceView
                val w = surfaceView.width
                val h = surfaceView.height
                if (w <= 0 || h <= 0) return
                
                isPendingCapture = true
                
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                
                try {
                        PixelCopy.request(
                                surfaceView,
                                bitmap,
                                { copyResult ->
                                        isPendingCapture = false
                                        if (copyResult == PixelCopy.SUCCESS && isCapturingFrames) {
                                                poseTrackerManager?.processFrame(bitmap)
                                        } else {
                                                bitmap.recycle()
                                        }
                                },
                                mainHandler
                        )
                } catch (e: Exception) {
                        isPendingCapture = false
                        bitmap.recycle()
                        android.util.Log.e("StreamActivity", "PixelCopy failed: ${e.message}")
                }
        }

        private val controlsDisposable = CompositeDisposable()

        override fun onAttachFragment(fragment: Fragment)
        {
                super.onAttachFragment(fragment)
                if(fragment is TouchControlsFragment)
                {
                        fragment.controllerState
                                .subscribe { viewModel.input.touchControllerState = it }
                                .addTo(controlsDisposable)
                        fragment.onScreenControlsEnabled = viewModel.onScreenControlsEnabled
                        if(fragment is TouchpadOnlyFragment)
                                fragment.touchpadOnlyEnabled = viewModel.touchpadOnlyEnabled
                }
        }

        override fun onResume()
        {
                super.onResume()
                hideSystemUI()
                viewModel.session.resume()
                if (poseTrackerManager?.isTrackingActive() == true) {
                        startFrameCapture()
                }
        }

        override fun onPause()
        {
                super.onPause()
                stopFrameCapture()
                viewModel.session.pause()
        }

        override fun onDestroy()
        {
                super.onDestroy()
                stopFrameCapture()
                
                layoutListener?.let {
                        binding.surfaceView.viewTreeObserver.removeOnGlobalLayoutListener(it)
                }
                layoutListener = null
                
                poseTrackerHandler?.removeCallbacksAndMessages(null)
                poseTrackerHandlerThread?.quitSafely()
                poseTrackerHandlerThread = null
                poseTrackerHandler = null
                
                controlsDisposable.dispose()
                poseTrackerManager?.release()
                poseTrackerManager = null
        }

        private fun reconnect()
        {
                viewModel.session.shutdown()
                viewModel.session.resume()
        }

        private val hideSystemUIRunnable = Runnable { hideSystemUI() }

        override fun onSystemUiVisibilityChange(visibility: Int)
        {
                if(visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                        showOverlay()
                else
                        hideOverlay()
        }

        private fun showOverlay()
        {
                binding.overlay.isVisible = true
                binding.overlay.animate()
                        .alpha(1.0f)
                        .setListener(object: AnimatorListenerAdapter()
                        {
                                override fun onAnimationEnd(animation: Animator)
                                {
                                        binding.overlay.alpha = 1.0f
                                }
                        })
                uiVisibilityHandler.removeCallbacks(hideSystemUIRunnable)
                uiVisibilityHandler.postDelayed(hideSystemUIRunnable, HIDE_UI_TIMEOUT_MS)
        }

        private fun hideOverlay()
        {
                binding.overlay.animate()
                        .alpha(0.0f)
                        .setListener(object: AnimatorListenerAdapter()
                        {
                                override fun onAnimationEnd(animation: Animator)
                                {
                                        binding.overlay.isGone = true
                                }
                        })
        }

        override fun onWindowFocusChanged(hasFocus: Boolean)
        {
                super.onWindowFocusChanged(hasFocus)
                if(hasFocus)
                        hideSystemUI()
        }

        private fun hideSystemUI()
        {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }

        private var dialogContents: DialogContents? = null
        private var dialog: AlertDialog? = null
                set(value)
                {
                        field = value
                        if(value == null)
                                dialogContents = null
                }

        private fun stateChanged(state: StreamState)
        {
                binding.progressBar.visibility = if(state == StreamStateConnecting) View.VISIBLE else View.GONE

                when(state)
                {
                        is StreamStateQuit ->
                        {
                                if(dialogContents != StreamQuitDialog)
                                {
                                        if(state.reason.isError)
                                        {
                                                dialog?.dismiss()
                                                val reasonStr = state.reasonString
                                                val dialog = MaterialAlertDialogBuilder(this)
                                                        .setMessage(getString(R.string.alert_message_session_quit, state.reason.toString())
                                                                        + (if(reasonStr != null) "\n$reasonStr" else ""))
                                                        .setPositiveButton(R.string.action_reconnect) { _, _ ->
                                                                dialog = null
                                                                reconnect()
                                                        }
                                                        .setOnCancelListener {
                                                                dialog = null
                                                                finish()
                                                        }
                                                        .setNegativeButton(R.string.action_quit_session) { _, _ ->
                                                                dialog = null
                                                                finish()
                                                        }
                                                        .create()
                                                dialogContents = StreamQuitDialog
                                                dialog.show()
                                        }
                                        else
                                                finish()
                                }
                        }

                        is StreamStateCreateError ->
                        {
                                if(dialogContents != CreateErrorDialog)
                                {
                                        dialog?.dismiss()
                                        val dialog = MaterialAlertDialogBuilder(this)
                                                .setMessage(getString(R.string.alert_message_session_create_error, state.error.errorCode.toString()))
                                                .setOnDismissListener {
                                                        dialog = null
                                                        finish()
                                                }
                                                .setNegativeButton(R.string.action_quit_session) { _, _ -> }
                                                .create()
                                        dialogContents = CreateErrorDialog
                                        dialog.show()
                                }
                        }

                        is StreamStateLoginPinRequest ->
                        {
                                if(dialogContents != PinRequestDialog)
                                {
                                        dialog?.dismiss()

                                        val view = layoutInflater.inflate(R.layout.dialog_login_pin, null)
                                        val pinEditText = view.findViewById<EditText>(R.id.pinEditText)

                                        val dialog = MaterialAlertDialogBuilder(this)
                                                .setMessage(
                                                        if(state.pinIncorrect)
                                                                R.string.alert_message_login_pin_request_incorrect
                                                        else
                                                                R.string.alert_message_login_pin_request)
                                                .setView(view)
                                                .setPositiveButton(R.string.action_login_pin_connect) { _, _ ->
                                                        dialog = null
                                                        viewModel.session.setLoginPin(pinEditText.text.toString())
                                                }
                                                .setOnCancelListener {
                                                        dialog = null
                                                        finish()
                                                }
                                                .setNegativeButton(R.string.action_quit_session) { _, _ ->
                                                        dialog = null
                                                        finish()
                                                }
                                                .create()
                                        dialogContents = PinRequestDialog
                                        dialog.show()
                                }
                        }

                        else -> {}
                }
        }

        private fun adjustTextureViewAspect(textureView: TextureView)
        {
                val trans = TextureViewTransform(viewModel.session.connectInfo.videoProfile, textureView)
                val resolution = trans.resolutionFor(TransformMode.fromButton(binding.displayModeToggle.checkedButtonId))
                Matrix().also {
                        textureView.getTransform(it)
                        it.setScale(resolution.width / trans.viewWidth, resolution.height / trans.viewHeight)
                        it.postTranslate((trans.viewWidth - resolution.width) * 0.5f, (trans.viewHeight - resolution.height) * 0.5f)
                        textureView.setTransform(it)
                }
        }

        private fun adjustSurfaceViewAspect()
        {
                val videoProfile = viewModel.session.connectInfo.videoProfile
                binding.aspectRatioLayout.aspectRatio = videoProfile.width.toFloat() / videoProfile.height.toFloat()
                binding.aspectRatioLayout.mode = TransformMode.fromButton(binding.displayModeToggle.checkedButtonId)
        }

        private fun adjustStreamViewAspect() = adjustSurfaceViewAspect()

        override fun dispatchKeyEvent(event: KeyEvent) = viewModel.input.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
        override fun onGenericMotionEvent(event: MotionEvent) = viewModel.input.onGenericMotionEvent(event) || super.onGenericMotionEvent(event)
}

enum class TransformMode
{
        FIT,
        STRETCH,
        ZOOM;

        companion object
        {
                fun fromButton(displayModeButtonId: Int)
                        = when (displayModeButtonId)
                        {
                                R.id.display_mode_stretch_button -> STRETCH
                                R.id.display_mode_zoom_button -> ZOOM
                                else -> FIT
                        }
        }
}

class TextureViewTransform(private val videoProfile: ConnectVideoProfile, private val textureView: TextureView)
{
        private val contentWidth : Float get() = videoProfile.width.toFloat()
        private val contentHeight : Float get() = videoProfile.height.toFloat()
        val viewWidth : Float get() = textureView.width.toFloat()
        val viewHeight : Float get() = textureView.height.toFloat()
        private val contentAspect : Float get() =  contentHeight / contentWidth

        fun resolutionFor(mode: TransformMode): Resolution
                = when(mode)
                {
                        TransformMode.STRETCH -> strechedResolution
                        TransformMode.ZOOM -> zoomedResolution
                        TransformMode.FIT -> normalResolution
                }

        private val strechedResolution get() = Resolution(viewWidth, viewHeight)

        private val zoomedResolution get() =
                if(viewHeight > viewWidth * contentAspect)
                {
                        val zoomFactor = viewHeight / contentHeight
                        Resolution(contentWidth * zoomFactor, viewHeight)
                }
                else
                {
                        val zoomFactor = viewWidth / contentWidth
                        Resolution(viewWidth, contentHeight * zoomFactor)
                }

        private val normalResolution get() =
                if(viewHeight > viewWidth * contentAspect)
                        Resolution(viewWidth, viewWidth * contentAspect)
                else
                        Resolution(viewHeight / contentAspect, viewHeight)
}


data class Resolution(val width: Float, val height: Float)

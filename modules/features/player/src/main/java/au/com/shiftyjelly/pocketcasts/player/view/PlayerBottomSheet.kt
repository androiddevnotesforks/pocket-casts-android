package au.com.shiftyjelly.pocketcasts.player.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsEvent
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.player.R
import au.com.shiftyjelly.pocketcasts.player.databinding.ViewPlayerBottomSheetBinding
import au.com.shiftyjelly.pocketcasts.player.helper.BottomSheetAnimation
import au.com.shiftyjelly.pocketcasts.player.helper.BottomSheetAnimation.Companion.SCALE
import au.com.shiftyjelly.pocketcasts.player.helper.BottomSheetAnimation.Companion.SCALE_NORMAL
import au.com.shiftyjelly.pocketcasts.player.helper.BottomSheetAnimation.Companion.TRANSLATE_Y
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.playback.PlaybackState
import au.com.shiftyjelly.pocketcasts.repositories.playback.UpNextQueue
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.utils.extensions.dpToPx
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@AndroidEntryPoint
class PlayerBottomSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs),
    CoroutineScope {

    @Inject lateinit var analyticsTracker: AnalyticsTracker

    @Inject lateinit var settings: Settings
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val binding = ViewPlayerBottomSheetBinding.inflate(inflater, this)
    private var animations: Array<BottomSheetAnimation>? = null

    var sheetBehavior: BottomSheetBehavior<PlayerBottomSheet>? = null
        private set
    val isPlayerOpen get() = sheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED
    var listener: PlayerBottomSheetListener? = null
    var isDragEnabled: Boolean
        get() = sheetBehavior?.isDraggable == true
        set(value) {
            sheetBehavior?.isDraggable = value
        }

    private var hasLoadedFirstTime = false

    init {
        settings.updatePlayerOrUpNextBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED)
        elevation = 8.dpToPx(context).toFloat()

        binding.miniPlayer.clickListener = object : MiniPlayer.OnMiniPlayerClicked {
            override fun onPlayClicked() {
                listener?.onPlayClicked()
            }

            override fun onPauseClicked() {
                listener?.onPauseClicked()
            }

            override fun onSkipBackwardClicked() {
                listener?.onSkipBackwardClicked()
            }

            override fun onSkipForwardClicked() {
                listener?.onSkipForwardClicked()
            }

            override fun onPlayerClicked() {
                openPlayer()
            }

            override fun onUpNextClicked() {
                listener?.onUpNextClicked()
            }

            override fun onLongClick() {
                listener?.onMiniPlayerLongClick()
            }
        }
    }

    fun initializeBottomSheetBehavior() {
        sheetBehavior = BottomSheetBehavior.from(this).apply {
            val callback = createBottomSheetCallback(rootView = parent as CoordinatorLayout)
            addBottomSheetCallback(callback)

            doOnLayout {
                if (state == BottomSheetBehavior.STATE_EXPANDED) {
                    callback.onSlide(this@PlayerBottomSheet, 1f)
                    listener?.onPlayerBottomSheetSlide(this@PlayerBottomSheet, 1f)
                }
            }
        }
    }

    interface PlayerBottomSheetListener {
        fun onMiniPlayerHidden()
        fun onMiniPlayerVisible()
        fun onPlayerOpen()
        fun onPlayerBottomSheetSlide(bottomSheetView: View, slideOffset: Float)
        fun onPlayerClosed()
        fun onPlayClicked()
        fun onPauseClicked()
        fun onSkipBackwardClicked()
        fun onSkipForwardClicked()
        fun onUpNextClicked()
        fun onMiniPlayerLongClick()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.player.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun setPlaybackState(playbackState: PlaybackState) {
        binding.miniPlayer.setPlaybackState(playbackState)
    }

    fun setUpNext(upNext: UpNextQueue.State, theme: Theme, shouldAnimateOnAttach: Boolean, useEpisodeArtwork: Boolean) {
        binding.miniPlayer.setUpNext(upNext, theme, useEpisodeArtwork)

        // only show the mini player when an episode is loaded
        if (upNext is UpNextQueue.State.Loaded) {
            if (isInvisible || !hasLoadedFirstTime) {
                isVisible = true
                if (shouldAnimateOnAttach) {
                    translationY = 68.dpToPx(context).toFloat()
                    animate().translationY(0f).setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            listener?.onMiniPlayerVisible()
                            hasLoadedFirstTime = true
                        }
                    })
                } else {
                    translationY = 0f
                    if (!shouldAnimateOnAttach) {
                        listener?.onMiniPlayerVisible()
                        hasLoadedFirstTime = true
                    }
                }
            }
        } else if (isVisible) {
            isInvisible = true
            closePlayer()
            listener?.onMiniPlayerHidden()
        }
    }

    private fun createBottomSheetCallback(
        rootView: CoordinatorLayout,
    ): BottomSheetBehavior.BottomSheetCallback {
        val miniPlayButtonScale = BottomSheetAnimation(
            viewId = R.id.miniPlayButton,
            rootView = rootView,
            effect = SCALE,
            slideOffsetFrom = 0.2f,
            slideOffsetTo = 0.5f,
            valueFrom = SCALE_NORMAL,
            valueTo = 0.6f,
            closeStartDelay = 200,
            closeInterpolator = OvershootInterpolator(),
            disabled = false,
        )
        val backgroundScale = BottomSheetAnimation(
            viewId = R.id.container,
            rootView = rootView,
            effect = SCALE,
            slideOffsetFrom = 0f,
            slideOffsetTo = 1f,
            valueFrom = SCALE_NORMAL,
            valueTo = 0.9f,
            disabled = false,
        )
        val playerTranslateY = BottomSheetAnimation(
            viewId = R.id.player,
            rootView = rootView,
            effect = TRANSLATE_Y,
            slideOffsetFrom = 0f,
            slideOffsetTo = 1f,
            valueFrom = resources.getDimension(R.dimen.player_fragment_start_y),
            valueTo = 0f,
            disabled = false,
        )
        animations = arrayOf(miniPlayButtonScale, backgroundScale, playerTranslateY)

        return object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // remove bottom navigation view
                listener?.onPlayerBottomSheetSlide(bottomSheet, slideOffset)

                animations?.forEach { it.onSlide(slideOffset) }
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                settings.updatePlayerOrUpNextBottomSheetState(newState)
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> onCollapsed()
                    BottomSheetBehavior.STATE_DRAGGING -> onDragging()
                    BottomSheetBehavior.STATE_SETTLING -> onSettling()
                    BottomSheetBehavior.STATE_EXPANDED -> onExpanded()
                }
            }
        }
    }

    private fun onCollapsed() {
        listener?.onPlayerClosed()
        animations?.forEach { it.onCollapsed() }
        analyticsTracker.track(AnalyticsEvent.PLAYER_DISMISSED)

        binding.player.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        binding.miniPlayer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun onDragging() {
        animations?.forEach { it.onDragging() }
    }

    private fun onSettling() {
        animations?.forEach { it.onSettling() }
    }

    private fun onExpanded() {
        listener?.onPlayerOpen()
        animations?.forEach { it.onExpanded() }
        analyticsTracker.track(AnalyticsEvent.PLAYER_SHOWN)

        binding.player.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        binding.miniPlayer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun openPlayer() {
        doOnLayout {
            sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun closePlayer() {
        sheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
    }
}

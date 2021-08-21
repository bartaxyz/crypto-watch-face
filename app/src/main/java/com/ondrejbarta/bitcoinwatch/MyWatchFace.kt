package com.ondrejbarta.bitcoinwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlin.math.abs


/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

private const val SECOND_TICK_STROKE_WIDTH = 4f

/**
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace: CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar
        private lateinit var tradingLineChartService: TradingLineChartService

        private var watchFaceDataService = WatchFaceDataService()

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F

        private var mSecondHandLength: Float = 0F
        private var sMinuteHandLength: Float = 0F
        private var sHourHandLength: Float = 0F

        private var currentVsCurrency = WatchFaceDataService.VsCurrency.USD
        private var currentCurrency = WatchFaceDataService.Currency.bitcoin
        private var nextCurrency = currentCurrency

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private var mWatchHandColor: Int = 0
        private var mWatchHandHighlightColor: Int = 0
        private var mWatchCircleColor: Int = 0

        private lateinit var mCirclePaint: Paint
        private lateinit var mTickPaint: Paint
        private lateinit var mSmallTickPaint: Paint

        private lateinit var timePaint: TextPaint
        private lateinit var syncMessagePaint: TextPaint
        private lateinit var currencyLabelPaint: TextPaint
        private lateinit var currencyPricePaint: TextPaint
        private lateinit var currencyDeltaPaint: TextPaint

        private lateinit var mBackgroundPaint: Paint

        private var mAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        private lateinit var displayMetrics: DisplayMetrics
        private var displayWidth: Int = 0
        private var displayHeight: Int = 0

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            tradingLineChartService = TradingLineChartService(resources, theme)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            displayMetrics = DisplayMetrics()
            displayWidth = displayMetrics.widthPixels
            displayHeight = displayMetrics.heightPixels

            initializeBackground()
            initializeWatchFace()

            updateTimer()
        }

        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
        }

        private fun initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = resources.getColor(R.color.watchHandColor, theme)
            mWatchHandHighlightColor = resources.getColor(R.color.watchSecondHandColor, theme)
            mWatchCircleColor = resources.getColor(R.color.watchHandColor, theme)

            mCirclePaint = Paint().apply {
                color = mWatchCircleColor
                strokeWidth = (SECOND_TICK_STROKE_WIDTH / 1.5).toFloat()
                isAntiAlias = true
                style = Paint.Style.FILL_AND_STROKE
                color
            }
            mTickPaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = (SECOND_TICK_STROKE_WIDTH / 1.5).toFloat()
                isAntiAlias = true
                style = Paint.Style.STROKE
            }
            mSmallTickPaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH / 2
                isAntiAlias = true
                style = Paint.Style.STROKE
            }
            mSmallTickPaint.setARGB(255, 51, 51, 51)

            timePaint = TextPaint().apply {
                color = Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
                textSize = 64f
            }
            syncMessagePaint = TextPaint().apply {
                color = Color.argb(0.5f, 1f, 1f, 1f)
                typeface = resources.getFont(R.font.roboto_condensed)
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
            val currencyTextSize = 24f
            currencyLabelPaint = TextPaint().apply {
                color = Color.WHITE
                typeface = resources.getFont(R.font.roboto_condensed_bold)
                textSize = currencyTextSize
            }
            currencyPricePaint = TextPaint().apply {
                color = Color.WHITE
                typeface = resources.getFont(R.font.roboto_condensed_bold)
                textSize = currencyTextSize
            }
            currencyDeltaPaint = TextPaint().apply {
                color = Color.argb(0.5f, 1f, 1f, 1f)
                typeface = resources.getFont(R.font.roboto_condensed_bold)
                textSize = currencyTextSize
            }
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        private fun updateWatchHandStyle() {
            mTickPaint.color = resources.getColor(R.color.watchTickColor, theme)
            mSmallTickPaint.color = resources.getColor(R.color.watchTickColor, theme)

            tradingLineChartService.update(mAmbient);

            if (mAmbient) {
                mTickPaint.isAntiAlias = false
                mSmallTickPaint.isAntiAlias = false
                timePaint.isAntiAlias = false
                syncMessagePaint.isAntiAlias = false

                currencyLabelPaint.isAntiAlias = false
                currencyDeltaPaint.isAntiAlias = false
                currencyPricePaint.isAntiAlias = false

            } else {
                mTickPaint.isAntiAlias = true
                mSmallTickPaint.isAntiAlias = true
                timePaint.isAntiAlias = true
                syncMessagePaint.isAntiAlias = true

                currencyLabelPaint.isAntiAlias = true
                currencyDeltaPaint.isAntiAlias = true
                currencyPricePaint.isAntiAlias = true
            }

            if (mAmbient) {
                timePaint.style = Paint.Style.STROKE
                timePaint.strokeWidth = 2f
                currencyLabelPaint.color = Color.argb(0.5f, 1f, 1f, 1f)
            } else {
                timePaint.style = Paint.Style.FILL
                timePaint.strokeWidth = 0f
                currencyLabelPaint.color = Color.argb(1f, 1f, 1f, 1f)
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (mCenterX * 0.875).toFloat()
            sMinuteHandLength = (mCenterX * 0.75).toFloat()
            sHourHandLength = (mCenterX * 0.5).toFloat()
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    // The user has completed the tap gesture.

                    /*
                    if (currentCurrency === WatchFaceDataService.Currency.ethereum) {
                        nextCurrency = WatchFaceDataService.Currency.bitcoin;
                    } else {
                        nextCurrency = WatchFaceDataService.Currency.ethereum
                    }

                    updateTimer()
                     */
                }
            }
            invalidate()
        }


        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawBackground(canvas)

            tradingLineChartService.prepare(canvas, bounds)
            tradingLineChartService.setData(watchFaceDataService.chartDataReduced)
            tradingLineChartService.draw(mAmbient)

            drawWatchTimeAndPriceChange(canvas, bounds)

            if (
                resources.configuration.isScreenRound &&
                resources.displayMetrics.heightPixels == resources.displayMetrics.widthPixels
            ) {
                drawSyncMessage(canvas, bounds)
            }
        }


        private fun drawBackground(canvas: Canvas) { canvas.drawColor(Color.BLACK) }

        private fun convertDate(): String {
            val currentDateTime = LocalDateTime.now()
            val time = currentDateTime.format(
                DateTimeFormatter.ofLocalizedTime(
                    FormatStyle.SHORT
                )
            ).removeSuffix(" AM").removeSuffix(" PM")
            return time;
        }
        private var watchTimeX: Int = 0
        private var watchTimeY: Float = 0f
        private var watchTimeTextBounds = Rect()
        private fun drawWatchTime(canvas: Canvas, bounds: Rect) {
            val text = convertDate()
            timePaint.getTextBounds(text, 0, text.length, watchTimeTextBounds)
            watchTimeX = abs(bounds.centerX() - watchTimeTextBounds.centerX())
            watchTimeY = 64f + watchTimeTextBounds.height()

            canvas.drawText(text, watchTimeX.toFloat(), watchTimeY, timePaint)
        }
        private fun drawPriceAndDelta(canvas: Canvas, bounds: Rect) {
            val currencyLabel = watchFaceDataService.getCurrencyLabel()
            val currencyPrice = watchFaceDataService.getFormattedVsCurrencyPrice()
            val currencyDelta = watchFaceDataService.getFormattedVsCurrencyDelta()

            val chartColor = tradingLineChartService.getTrendingColor()

            val currencyLabelBounds = Rect()
            val currencyPriceBounds = Rect()
            val currencyDeltaBounds = Rect()

            currencyLabelPaint.getTextBounds(
                currencyLabel,
                0,
                currencyLabel.length,
                currencyLabelBounds
            )
            currencyPricePaint.getTextBounds(
                currencyPrice,
                0,
                currencyPrice.length,
                currencyPriceBounds
            )
            currencyDeltaPaint.getTextBounds(
                currencyDelta,
                0,
                currencyDelta.length,
                currencyDeltaBounds
            )

            currencyPricePaint.color = chartColor


            val gap = 12

            val totalLabelWidth = currencyLabelBounds.width() + gap + currencyPriceBounds.width() + gap + currencyDeltaBounds.width();
            val labelY = watchTimeY + 24f + currencyLabelBounds.height()
            val currencyLabelX = (bounds.width() - totalLabelWidth) / 2f
            val currencyPriceX = currencyLabelX + currencyLabelBounds.width() + gap;
            val currencyDeltaX = currencyPriceX + currencyPriceBounds.width() + gap;

            canvas.drawText(currencyLabel, currencyLabelX, labelY, currencyLabelPaint)
            canvas.drawText(currencyPrice, currencyPriceX, labelY, currencyPricePaint)
            canvas.drawText(currencyDelta, currencyDeltaX, labelY, currencyDeltaPaint)

        }
        private fun drawWatchTimeAndPriceChange(canvas: Canvas, bounds: Rect) {
            drawWatchTime(canvas, bounds)
            drawPriceAndDelta(canvas, bounds)
        }

        private var syncMessageX: Int = 0;
        private var syncMessageY: Float = 0f;
        private var syncMessageTextBounds = Rect()

        private fun drawSyncMessage(canvas: Canvas, bounds: Rect) {
            // val text = "Last sync at " + convertDate();
            val text = watchFaceDataService.getLastSyncMessage();
            syncMessagePaint.getTextBounds(text, 0, text.length, syncMessageTextBounds)
            syncMessageX = abs(bounds.centerX() - syncMessageTextBounds.centerX())
            syncMessageY = bounds.bottom - 32f - syncMessageTextBounds.height()

            val offset = 8f;
            val textArc = Path();
            textArc.addArc(
                offset,
                offset,
                bounds.width() - offset,
                bounds.height() - offset,
                -180f,
                -180f
            );

            canvas.drawTextOnPath(text, textArc, 0f, 0f, syncMessagePaint);
            // canvas.drawText(text, syncMessageX.toFloat(), syncMessageY, syncMessagePaint)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        var timeSinceLastFetch: Long = 0;
        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the .
         */
        private fun updateTimer() {
            val timeDifference = System.currentTimeMillis() - timeSinceLastFetch;
            if (
                currentCurrency !== nextCurrency ||
                // Fetch every 30 minutes (or more)
                (timeDifference > watchFaceDataService.cacheInvalidationTime) ||
                // Fetch every 1 minutes (or more) in case of an error
                watchFaceDataService.fetchFailed && timeDifference > (1 * 60 * 1000).toLong()
            ) {
                currentCurrency = nextCurrency

                Log.i("UPDATE_TIMER", "Just triggered fetching")

                GlobalScope.launch(Dispatchers.Main) {
                    watchFaceDataService.fetchChartData(
                        currentCurrency,
                        currentVsCurrency,
                        1
                    )

                    updateTimer()
                }
            }

            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}




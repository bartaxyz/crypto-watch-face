package com.ondrejbarta.bitcoinwatch

import android.content.res.Resources
import android.graphics.*
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

private const val LINE_STROKE_WIDTH = 4f

class TradingLineChartService {
    private lateinit var canvas: Canvas;
    private lateinit var bounds: Rect;
    private var canvasWidth: Float = 0f;
    private var canvasHeight: Float = 0f;

    var maxDataValue: Float = -1f;
    var minDataValue: Float = -1f;
    var firstDataValue: Float = 0f;
    var lastDataValue: Float = 0f;

    private var chartLinePaint: Paint
    private var chartAreaPaint: Paint
    private var chartBaselinePaint: Paint
    private var chartOverlayPaint: Paint
    private var chartAreaOverlayPaint: Paint

    private lateinit var data: MutableList<Float>;
    
    private var mChartTrendingUpColor: Int = 0
    private var mChartTrendingDownColor: Int = 0
    private var mChartTrendingIndeterminateColor: Int = 0

    constructor(resources: Resources, theme: Resources.Theme) {
        mChartTrendingUpColor = resources.getColor(R.color.chartTrendingUp, theme)
        mChartTrendingDownColor = resources.getColor(R.color.chartTrendingDown, theme)
        mChartTrendingIndeterminateColor = resources.getColor(R.color.chartTrendingIndeterminate, theme)

        chartLinePaint = Paint().apply {
            strokeWidth = LINE_STROKE_WIDTH
            isAntiAlias = true
            style = Paint.Style.STROKE
            pathEffect = CornerPathEffect(8f)
        }
        chartAreaPaint = Paint().apply{
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        chartBaselinePaint = Paint().apply {
            strokeWidth = 2f
            isAntiAlias = true
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(10f, 15f), 0f)
        }
        chartOverlayPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        chartAreaOverlayPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
    }

    fun prepare(canvas: Canvas, bounds: Rect) {
        this.canvas = canvas;
        this.bounds = bounds;

        canvasWidth = bounds.width().toFloat()
        canvasHeight = bounds.height().toFloat()
    }

    fun update(ambient: Boolean) {
        if (ambient) {
            chartLinePaint.isAntiAlias = false;
            chartAreaPaint.isAntiAlias = false;
        } else {
            chartLinePaint.isAntiAlias = true;
            chartAreaPaint.isAntiAlias = true;
        }
    }

    fun clearData() {
        data = mutableListOf()
        maxDataValue = -1f
        minDataValue = -1f
        firstDataValue = 0f
        lastDataValue = 0f
    }

    fun setData(data: MutableList<Float>) {
        clearData()

        this.data = data;

        if (data.isEmpty()) {
            return
        }

        for ((index, dataPoint) in data.withIndex()) {
            val dataPointValue = dataPoint;

            if (maxDataValue == -1f) {
                maxDataValue = dataPointValue;
            }
            if (minDataValue == -1f) {
                minDataValue = dataPointValue;
            }
            if (maxDataValue < dataPointValue) {
                maxDataValue = dataPointValue;
            }
            if (minDataValue > dataPointValue) {
                minDataValue = dataPointValue;
            }
        }
    }

    fun getTrendingColor(): Int {
        if (data.isEmpty()) {
            return mChartTrendingIndeterminateColor;
        }

        val firstChartValue = data[0];
        val lastChartValue = data[data.lastIndex];

        if (firstChartValue > lastChartValue) {
            return mChartTrendingDownColor;
        } else {
            return mChartTrendingUpColor;
        }
    }
    
    private fun domainRangeMap(
        value: Float,
        in_min: Float,
        in_max: Float,
        out_min: Float,
        out_max: Float
    ): Float {
        return (value - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    fun draw(ambient: Boolean) {
        val linePath = Path()
        val areaPath = Path()
        val baselinePath = Path()
        val dataPointCount = data.size

        var chartColor = getTrendingColor()

        if (data.isEmpty()) {
            // Draw empty chart (flat line)
            var emptyLineData = listOf(1f, 1f);
            for ((index, dataPoint) in emptyLineData.withIndex()) {
                val y = domainRangeMap(
                    dataPoint,
                    2f,
                    0f,
                    bounds.height().toFloat() * 0.5f,
                    bounds.height().toFloat() * 0.75f
                )
                val x = domainRangeMap(
                    index.toFloat(),
                    0f,
                    1f,
                    16f, canvasWidth - 16f
                )

                if (index == 0) {
                    baselinePath.moveTo(x, y);
                    baselinePath.lineTo(canvasWidth, y);

                    linePath.moveTo(x, y)
                }

                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        } else {
            for ((index, dataPoint) in data.withIndex()) {
                val y = domainRangeMap(
                    dataPoint,
                    maxDataValue,
                    minDataValue,
                    bounds.height().toFloat() * 0.5f,
                    bounds.height().toFloat() * 0.75f
                )
                val x = domainRangeMap(
                    index.toFloat(),
                    0f,
                    dataPointCount.toFloat(),
                    16f, canvasWidth - 16f
                )

                if (index == 0) {
                    baselinePath.moveTo(x, y);
                    baselinePath.lineTo(canvasWidth, y);

                    linePath.moveTo(x, y)
                }

                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }

        /* Draw chart area shape */
        if (!ambient) {
            areaPath.lineTo(canvasWidth, canvasHeight)
            areaPath.lineTo(0f, canvasHeight)
            chartAreaPaint.setARGB(
                51,
                chartColor.red,
                chartColor.green,
                chartColor.blue
            )
            canvas.drawPath(areaPath, chartAreaPaint);
        }

        /* Draw chart area overlay */
        chartAreaOverlayPaint.setShader(
            RadialGradient(
                canvasWidth / 2,
                canvasHeight / 2,
                canvasWidth / 2,
                intArrayOf(
                    Color.TRANSPARENT, Color.argb(
                        255 / 100 * 70,
                        0, 0, 0
                    ), Color.BLACK
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        );
        canvas.drawRect(0f, 0f, canvasWidth, canvasHeight, chartAreaOverlayPaint);

        /* Draw base line */
        val baselinePathOpacity: Int
        if (ambient) {
            baselinePathOpacity = 255 / 100 * 80 // 50% alpha
        } else {
            baselinePathOpacity = 255 / 100 * 50 // 50% alpha
        }
        chartBaselinePaint.setARGB(
            baselinePathOpacity,
            chartColor.red,
            chartColor.green,
            chartColor.blue
        )

        canvas.drawPath(baselinePath, chartBaselinePaint);
        /* Draw chart line */
        chartLinePaint.color = chartColor;
        canvas.drawPath(linePath, chartLinePaint);

        /* Draw chart overlay */
        chartOverlayPaint.setShader(
            RadialGradient(
                canvasWidth / 2,
                canvasHeight / 2,
                canvasWidth / 2,
                intArrayOf(
                    Color.TRANSPARENT, Color.argb(
                        255 / 100 * 50,
                        0, 0, 0
                    ), Color.BLACK
                ),
                floatArrayOf(0f, 0.85f, 1f),
                Shader.TileMode.CLAMP
            )
        );
        canvas.drawRect(0f, 0f, canvasWidth, canvasHeight, chartOverlayPaint);
    }
}
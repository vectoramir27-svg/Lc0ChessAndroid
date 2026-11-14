package com.lc0.chess.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class OverlayCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var fromSquare: String? = null
    private var toSquare: String? = null
    var isFlipped: Boolean = false

    var boardRect: RectF = RectF()

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88")
        strokeWidth = 14f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(16f, 0f, 0f, Color.parseColor("#00FF88"))
    }

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88")
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, 0f, Color.parseColor("#00FF88"))
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setMoveArrow(from: String, to: String, flipped: Boolean = false) {
        this.fromSquare = from.lowercase()
        this.toSquare = to.lowercase()
        this.isFlipped = flipped
        invalidate()
    }

    fun clearArrow() {
        this.fromSquare = null
        this.toSquare = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val from = fromSquare ?: return
        val to = toSquare ?: return
        if (from.length != 2 || to.length != 2) return

        if (boardRect.width() <= 0 || boardRect.height() <= 0) {
            // По умолчанию берем квадрат по ширине экрана, выровненный по центру
            val size = width.toFloat()
            val top = (height - size) / 2f
            boardRect.set(0f, top, size, top + size)
        }

        val sqW = boardRect.width() / 8f
        val sqH = boardRect.height() / 8f

        fun getCenter(sq: String): PointF {
            val f = sq[0] - 'a'
            val r = sq[1] - '1'

            val col = if (isFlipped) (7 - f) else f
            val row = if (isFlipped) r else (7 - r)

            val x = boardRect.left + (col + 0.5f) * sqW
            val y = boardRect.top + (row + 0.5f) * sqH
            return PointF(x, y)
        }

        val p1 = getCenter(from)
        val p2 = getCenter(to)

        // Рисуем тело стрелки
        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, arrowPaint)

        // Рисуем наконечник стрелки
        val angle = atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
        val headLength = sqW * 0.40f
        val headAngle = Math.toRadians(30.0)

        val path = Path().apply {
            moveTo(p2.x, p2.y)
            lineTo(
                (p2.x - headLength * cos(angle - headAngle)).toFloat(),
                (p2.y - headLength * sin(angle - headAngle)).toFloat()
            )
            lineTo(
                (p2.x - (headLength * 0.7f) * cos(angle)).toFloat(),
                (p2.y - (headLength * 0.7f) * sin(angle)).toFloat()
            )
            lineTo(
                (p2.x - headLength * cos(angle + headAngle)).toFloat(),
                (p2.y - headLength * sin(angle + headAngle)).toFloat()
            )
            close()
        }
        canvas.drawPath(path, headPaint)
    }
}

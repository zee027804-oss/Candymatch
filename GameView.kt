package com.example.candymatch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Match-3 board. Rules: swipe a candy to an adjacent cell. If the swap makes a line of
 * 3+ same candies (row or column) they pop, candies above fall, new ones drop in, and
 * chains score more. A swap that makes no match is reverted and costs no move.
 */
class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onStats(score: Int, moves: Int)
        fun onWon()
        fun onLost()
    }

    var listener: Listener? = null
    var score = 0; private set
    var moves = 0; private set
    var target = 0; private set

    private val rows = 8
    private val cols = 8
    private var kinds = 5
    private val grid = Array(rows) { IntArray(cols) { -1 } }
    private val offX = Array(rows) { FloatArray(cols) }
    private val offY = Array(rows) { FloatArray(cols) }
    private val scale = Array(rows) { FloatArray(cols) { 1f } }
    private val fallStart = Array(rows) { FloatArray(cols) }

    private var busy = true
    private var cell = 0f

    private val colors = intArrayOf(
        0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(),
        0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt()
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val pi = Math.PI.toFloat()

    // ---------- level setup ----------

    fun startLevel(level: Int) {
        kinds = if (level < 3) 5 else 6
        target = 500 + 300 * (level - 1)
        score = 0
        moves = 25
        for (r in 0 until rows) for (c in 0 until cols) {
            scale[r][c] = 1f; offX[r][c] = 0f; offY[r][c] = 0f
        }
        fillBoard()
        busy = false
        listener?.onStats(score, moves)
        invalidate()
    }

    private fun fillBoard() {
        do {
            for (r in 0 until rows) for (c in 0 until cols) {
                var t: Int
                do {
                    t = Random.nextInt(kinds)
                } while (
                    (c >= 2 && grid[r][c - 1] == t && grid[r][c - 2] == t) ||
                    (r >= 2 && grid[r - 1][c] == t && grid[r - 2][c] == t)
                )
                grid[r][c] = t
            }
        } while (!hasMoves())
    }

    // ---------- match logic ----------

    private fun findMatches(): Array<BooleanArray> {
        val m = Array(rows) { BooleanArray(cols) }
        for (r in 0 until rows) {
            var c = 0
            while (c < cols) {
                val t = grid[r][c]
                var e = c + 1
                while (t != -1 && e < cols && grid[r][e] == t) e++
                if (t != -1 && e - c >= 3) for (k in c until e) m[r][k] = true
                c = e
            }
        }
        for (c in 0 until cols) {
            var r = 0
            while (r < rows) {
                val t = grid[r][c]
                var e = r + 1
                while (t != -1 && e < rows && grid[e][c] == t) e++
                if (t != -1 && e - r >= 3) for (k in r until e) m[k][c] = true
                r = e
            }
        }
        return m
    }

    private fun count(m: Array<BooleanArray>) = m.sumOf { row -> row.count { it } }

    private fun swapData(r1: Int, c1: Int, r2: Int, c2: Int) {
        val tmp = grid[r1][c1]; grid[r1][c1] = grid[r2][c2]; grid[r2][c2] = tmp
    }

    private fun hasMoves(): Boolean {
        for (r in 0 until rows) for (c in 0 until cols) {
            for ((dr, dc) in listOf(0 to 1, 1 to 0)) {
                val r2 = r + dr; val c2 = c + dc
                if (r2 >= rows || c2 >= cols) continue
                swapData(r, c, r2, c2)
                val ok = count(findMatches()) > 0
                swapData(r, c, r2, c2)
                if (ok) return true
            }
        }
        return false
    }

    // ---------- animation helpers ----------

    private fun anim(
        ms: Long, interp: TimeInterpolator = LinearInterpolator(),
        update: (Float) -> Unit, end: () -> Unit
    ) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = interp
            addUpdateListener { update(it.animatedValue as Float); invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { end() }
            })
            start()
        }
    }

    private fun animateSwap(r1: Int, c1: Int, r2: Int, c2: Int, done: () -> Unit) {
        anim(180, update = { t ->
            offX[r1][c1] = (c2 - c1) * t; offY[r1][c1] = (r2 - r1) * t
            offX[r2][c2] = (c1 - c2) * t; offY[r2][c2] = (r1 - r2) * t
        }, end = {
            swapData(r1, c1, r2, c2)
            offX[r1][c1] = 0f; offY[r1][c1] = 0f
            offX[r2][c2] = 0f; offY[r2][c2] = 0f
            done()
        })
    }

    private fun trySwap(r1: Int, c1: Int, r2: Int, c2: Int) {
        if (r2 !in 0 until rows || c2 !in 0 until cols) return
        busy = true
        animateSwap(r1, c1, r2, c2) {
            if (count(findMatches()) > 0) {
                moves--
                listener?.onStats(score, moves)
                resolve(1)
            } else {
                animateSwap(r1, c1, r2, c2) { busy = false } // swap back, no move used
            }
        }
    }

    private fun resolve(chain: Int) {
        val m = findMatches()
        val n = count(m)
        if (n == 0) { afterResolve(); return }
        score += n * 10 * chain
        listener?.onStats(score, moves)
        anim(220, update = { t ->
            for (r in 0 until rows) for (c in 0 until cols) if (m[r][c]) scale[r][c] = 1f - t
        }, end = {
            for (r in 0 until rows) for (c in 0 until cols) if (m[r][c]) {
                grid[r][c] = -1; scale[r][c] = 1f
            }
            collapse(chain)
        })
    }

    private fun collapse(chain: Int) {
        for (r in 0 until rows) for (c in 0 until cols) fallStart[r][c] = 0f
        var maxDist = 1f
        for (c in 0 until cols) {
            var write = rows - 1
            for (r in rows - 1 downTo 0) {
                if (grid[r][c] != -1) {
                    if (write != r) {
                        grid[write][c] = grid[r][c]
                        grid[r][c] = -1
                        fallStart[write][c] = -(write - r).toFloat()
                        maxDist = max(maxDist, (write - r).toFloat())
                    }
                    write--
                }
            }
            val empties = write + 1
            for (i in 0..write) {
                grid[i][c] = Random.nextInt(kinds)
                fallStart[i][c] = -empties.toFloat()
                maxDist = max(maxDist, empties.toFloat())
            }
        }
        anim((120 + 70 * maxDist).toLong(), AccelerateInterpolator(), update = { t ->
            for (r in 0 until rows) for (c in 0 until cols) offY[r][c] = fallStart[r][c] * (1f - t)
        }, end = {
            for (r in 0 until rows) for (c in 0 until cols) offY[r][c] = 0f
            resolve(chain + 1)
        })
    }

    private fun afterResolve() {
        if (score >= target) { busy = true; listener?.onWon(); return }
        if (moves <= 0) { busy = true; listener?.onLost(); return }
        if (!hasMoves()) { fillBoard(); invalidate() } // no moves left: reshuffle
        busy = false
    }

    // ---------- input ----------

    private var downX = 0f
    private var downY = 0f
    private var startR = 0
    private var startC = 0
    private var tracking = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                startR = (e.y / cell).toInt(); startC = (e.x / cell).toInt()
                tracking = !busy && startR in 0 until rows && startC in 0 until cols
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = e.x - downX
                val dy = e.y - downY
                if (max(abs(dx), abs(dy)) > cell * 0.35f) {
                    tracking = false
                    val dr: Int; val dc: Int
                    if (abs(dx) > abs(dy)) { dr = 0; dc = if (dx > 0) 1 else -1 }
                    else { dc = 0; dr = if (dy > 0) 1 else -1 }
                    trySwap(startR, startC, startR + dr, startC + dc)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }

    // ---------- drawing ----------

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, w)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cell = w / cols.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        rect.set(0f, 0f, width.toFloat(), width.toFloat())
        canvas.drawRoundRect(rect, cell * 0.3f, cell * 0.3f, paint)
        for (r in 0 until rows) for (c in 0 until cols) {
            if ((r + c) % 2 == 0) {
                paint.color = 0x22FFFFFF
                canvas.drawRect(c * cell, r * cell, (c + 1) * cell, (r + 1) * cell, paint)
            }
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            val t = grid[r][c]
            if (t < 0) continue
            val cx = (c + offX[r][c] + 0.5f) * cell
            val cy = (r + offY[r][c] + 0.5f) * cell
            drawCandy(canvas, t, cx, cy, cell * 0.38f * scale[r][c])
        }
    }

    private fun drawCandy(canvas: Canvas, t: Int, cx: Float, cy: Float, r: Float) {
        if (r <= 0.5f) return
        paint.style = Paint.Style.FILL
        paint.color = colors[t]
        drawShape(canvas, t, cx, cy, r)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = cell * 0.03f
        paint.color = 0x66000000
        drawShape(canvas, t, cx, cy, r)
        paint.style = Paint.Style.FILL
        paint.color = 0x80FFFFFF.toInt()
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.35f, r * 0.22f, paint)
    }

    // Different shapes per colour so the game is playable for colour-blind users too.
    private fun drawShape(canvas: Canvas, t: Int, cx: Float, cy: Float, r: Float) {
        when (t) {
            0 -> canvas.drawCircle(cx, cy, r, paint)
            1 -> {
                rect.set(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f)
                canvas.drawRoundRect(rect, r * 0.3f, r * 0.3f, paint)
            }
            2 -> polygon(canvas, cx, cy, r * 1.15f, 4, -pi / 2)
            3 -> polygon(canvas, cx, cy, r * 1.15f, 3, -pi / 2)
            4 -> polygon(canvas, cx, cy, r, 6, 0f)
            else -> polygon(canvas, cx, cy, r, 8, pi / 8)
        }
    }

    private fun polygon(canvas: Canvas, cx: Float, cy: Float, r: Float, sides: Int, rot: Float) {
        path.reset()
        for (i in 0 until sides) {
            val a = rot + i * 2f * pi / sides
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}

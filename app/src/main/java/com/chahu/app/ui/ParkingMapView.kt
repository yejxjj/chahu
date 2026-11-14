package com.chahu.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.chahu.app.engine.ParkingLot
import com.chahu.app.engine.Shadows
import com.chahu.app.engine.Vec
import kotlin.math.min

/**
 * 주차장 평면도. 지도 SDK 없이 Canvas로 직접 그린다.
 * - 칸 색: 현재 시각 그늘 비율 (주황 = 햇볕, 파랑 = 그늘)
 * - 반투명 남색: 건물·나무 그림자
 * - 굵은 테두리: 추천 칸
 */
class ParkingMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    var lot: ParkingLot? = null
        set(value) { field = value; computeBounds(); invalidate() }
    var shadows: Shadows? = null
        set(value) { field = value; invalidate() }
    var shadeBySpace: Map<String, Double> = emptyMap()
        set(value) { field = value; invalidate() }
    var recommendedId: String? = null
        set(value) { field = value; invalidate() }

    private var minX = 0.0; private var maxX = 1.0; private var minY = 0.0; private var maxY = 1.0
    private var scale = 1f; private var ox = 0f; private var oy = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }

    private fun computeBounds() {
        val l = lot ?: return
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        l.spaces.forEach { xs += it.x; xs += it.x + it.w; ys += it.y; ys += it.y + it.h }
        l.buildings.flatMap { it.footprint }.forEach { xs += it.x; ys += it.y }
        l.trees.forEach { xs += it.center.x - it.radiusM; xs += it.center.x + it.radiusM; ys += it.center.y - it.radiusM; ys += it.center.y + it.radiusM }
        xs += l.entrance.x; ys += l.entrance.y
        val pad = 3.0
        minX = xs.min() - pad; maxX = xs.max() + pad; minY = ys.min() - pad; maxY = ys.max() + pad
        requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutTransform()
    }

    private fun layoutTransform() {
        val ww = (maxX - minX).toFloat(); val hh = (maxY - minY).toFloat()
        scale = min(width / ww, height / hh)
        ox = (width - ww * scale) / 2f
        oy = (height - hh * scale) / 2f
    }

    // 로컬 미터 → 화면 픽셀 (y축 뒤집기)
    private fun sx(x: Double) = ox + ((x - minX) * scale).toFloat()
    private fun sy(y: Double) = oy + ((maxY - y) * scale).toFloat()

    private fun path(poly: List<Vec>) = Path().apply {
        poly.forEachIndexed { i, p -> if (i == 0) moveTo(sx(p.x), sy(p.y)) else lineTo(sx(p.x), sy(p.y)) }
        close()
    }

    private fun lerpColor(a: Int, b: Int, t: Double): Int {
        val k = t.coerceIn(0.0, 1.0).toFloat()
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = lot ?: return
        layoutTransform()
        canvas.drawColor(Color.rgb(18, 19, 23))

        // 주차장 바닥 (그림자가 잘 보이도록 밝은 회색)
        val sxs = l.spaces.flatMap { listOf(it.x, it.x + it.w) }
        val sys = l.spaces.flatMap { listOf(it.y, it.y + it.h) }
        fill.color = Color.rgb(58, 61, 68)
        canvas.drawRoundRect(
            RectF(sx(sxs.min() - 2), sy(sys.max() + 2), sx(maxOf(sxs.max() + 2, l.entrance.x)), sy(sys.min() - 2)),
            12f, 12f, fill,
        )

        // 칸: 현재 시각 그늘 비율 (주황 = 햇볕, 파랑 = 그늘)
        val sun = Color.rgb(255, 150, 60)
        val shade = Color.rgb(70, 140, 255)
        l.spaces.forEach { sp ->
            val r = RectF(sx(sp.x) + 2, sy(sp.y + sp.h) + 2, sx(sp.x + sp.w) - 2, sy(sp.y) - 2)
            fill.color = lerpColor(sun, shade, shadeBySpace[sp.id] ?: 0.0)
            fill.alpha = 230
            canvas.drawRoundRect(r, 6f, 6f, fill)
            stroke.pathEffect = null
            stroke.color = Color.argb(140, 255, 255, 255); stroke.strokeWidth = 2f
            canvas.drawRect(sx(sp.x), sy(sp.y + sp.h), sx(sp.x + sp.w), sy(sp.y), stroke)
        }

        // 그림자: 반투명 검정으로 덮고, 경계선을 점선으로
        shadows?.let { s ->
            fill.color = Color.argb(150, 0, 0, 10)
            s.polygons.forEach { canvas.drawPath(path(it), fill) }
            s.circles.forEach { c -> canvas.drawCircle(sx(c.center.x), sy(c.center.y), (c.radius * scale).toFloat(), fill) }
            stroke.color = Color.argb(220, 150, 200, 255); stroke.strokeWidth = 3f
            stroke.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
            s.polygons.forEach { canvas.drawPath(path(it), stroke) }
            stroke.pathEffect = null
        }

        // 건물
        l.buildings.forEach { b ->
            fill.color = Color.rgb(70, 72, 80); fill.alpha = 255
            canvas.drawPath(path(b.footprint), fill)
            val cx = b.footprint.map { it.x }.average(); val cy = b.footprint.map { it.y }.average()
            text.textSize = 13f * resources.displayMetrics.density
            canvas.drawText("${b.heightM.toInt()}m", sx(cx), sy(cy), text)
        }
        // 나무
        l.trees.forEach { t ->
            fill.color = Color.rgb(60, 150, 90)
            canvas.drawCircle(sx(t.center.x), sy(t.center.y), (t.radiusM * scale).toFloat(), fill)
        }
        // 입구
        text.textSize = 12f * resources.displayMetrics.density
        canvas.drawText("입구", sx(l.entrance.x), sy(l.entrance.y), text)

        // 추천 칸
        recommendedId?.let { id ->
            l.spaces.find { it.id == id }?.let { sp ->
                stroke.color = Color.rgb(120, 190, 255); stroke.strokeWidth = 6f
                canvas.drawRoundRect(RectF(sx(sp.x) - 3, sy(sp.y + sp.h) - 3, sx(sp.x + sp.w) + 3, sy(sp.y) + 3), 8f, 8f, stroke)
                text.textSize = 12f * resources.displayMetrics.density
                canvas.drawText(sp.id, sx(sp.center.x), sy(sp.y + sp.h) - 10, text)
            }
        }

        // 범례
        val d = resources.displayMetrics.density
        val lx = 12 * d
        val ly = height - 14 * d
        text.textAlign = Paint.Align.LEFT
        text.textSize = 12f * d
        fill.color = sun; canvas.drawRect(lx, ly - 10 * d, lx + 10 * d, ly, fill)
        canvas.drawText("햇볕", lx + 14 * d, ly, text)
        fill.color = shade; canvas.drawRect(lx + 56 * d, ly - 10 * d, lx + 66 * d, ly, fill)
        canvas.drawText("그늘", lx + 70 * d, ly, text)
        stroke.color = Color.argb(220, 150, 200, 255); stroke.strokeWidth = 3f
        stroke.pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        canvas.drawRect(lx + 112 * d, ly - 10 * d, lx + 122 * d, ly, stroke)
        stroke.pathEffect = null
        canvas.drawText("건물·나무 그림자", lx + 126 * d, ly, text)
        text.textAlign = Paint.Align.CENTER
    }
}

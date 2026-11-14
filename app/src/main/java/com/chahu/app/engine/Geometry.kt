package com.chahu.app.engine

/** 주차장 로컬 좌표계 (단위 m, x = 동쪽, y = 북쪽) */
data class Vec(val x: Double, val y: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun times(k: Double) = Vec(x * k, y * k)
}

object Geometry {

    /** Andrew monotone chain 볼록 껍질 (반시계 방향) */
    fun convexHull(points: List<Vec>): List<Vec> {
        val pts = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (pts.size < 3) return pts
        fun cross(o: Vec, a: Vec, b: Vec) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Vec>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
            lower.add(p)
        }
        val upper = ArrayList<Vec>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
            upper.add(p)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    /** 반시계 방향 볼록 다각형 내부 판정 */
    fun insideConvex(poly: List<Vec>, p: Vec): Boolean {
        if (poly.size < 3) return false
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            if ((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x) < 0) return false
        }
        return true
    }
}

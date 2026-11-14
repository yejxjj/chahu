package com.chahu.app.engine

import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 그늘 예측 엔진.
 *
 * 1) 태양 방위각·고도각 계산
 * 2) 건물 그림자 = 바닥면 ∪ (바닥면을 태양 반대 방향으로 높이/tan(고도)만큼 이동) 의 볼록 껍질
 * 3) 칸별 그늘 비율 = 칸 안 샘플 점 중 그림자에 들어간 비율
 * 4) 그늘 유지율 = 주차 시간 동안 일정 간격(기본 15분) 그늘 비율의 평균
 */
class ShadeEngine(
    private val lot: ParkingLot,
    private val stepMinutes: Long = 15,
    private val samplesX: Int = 4,
    private val samplesY: Int = 6,
) {

    fun sunAt(time: ZonedDateTime): SunPosition =
        SunPosition.at(time.toInstant().toEpochMilli(), lot.latDeg, lot.lonDeg)

    fun shadowsAt(time: ZonedDateTime): Shadows = shadowsFor(sunAt(time))

    fun shadowsFor(sun: SunPosition): Shadows {
        if (!sun.isUp) return Shadows(emptyList(), emptyList(), night = true)
        val rad = Math.toRadians(sun.azimuthDeg)
        // 그림자는 태양 반대 방향으로 뻗는다
        val dir = Vec(-sin(rad), -cos(rad))
        val cot = 1.0 / tan(Math.toRadians(sun.altitudeDeg))

        val polys = lot.buildings.map { b ->
            val len = b.heightM * cot
            Geometry.convexHull(b.footprint + b.footprint.map { it + dir * len })
        }
        val circles = lot.trees.map { t ->
            // 수관 중심 높이를 나무 높이의 약 55%로 근사
            Circle(t.center + dir * (t.heightM * 0.55 * cot), t.radiusM * 1.05)
        }
        return Shadows(polys, circles, night = false)
    }

    fun shadeRatio(space: Space, shadows: Shadows): Double {
        if (shadows.night) return 1.0
        var hit = 0
        val total = samplesX * samplesY
        for (i in 0 until samplesX) for (j in 0 until samplesY) {
            val p = Vec(space.x + space.w * (i + 0.5) / samplesX, space.y + space.h * (j + 0.5) / samplesY)
            val inPoly = shadows.polygons.any { Geometry.insideConvex(it, p) }
            val inCircle = shadows.circles.any { c ->
                val dx = p.x - c.center.x
                val dy = p.y - c.center.y
                dx * dx + dy * dy < c.radius * c.radius
            }
            if (inPoly || inCircle) hit++
        }
        return hit.toDouble() / total
    }

    /** 주차 시작 시각부터 duration 동안의 칸별 그늘 유지율, 높은 순 */
    fun rank(start: ZonedDateTime, duration: Duration): List<SpaceScore> {
        val steps = (duration.toMinutes() / stepMinutes).coerceAtLeast(0)
        val times = (0..steps).map { start.plusMinutes(it * stepMinutes) }
        val shadowList = times.map { shadowsAt(it) }
        return lot.spaces.map { sp ->
            SpaceScore(sp, shadowList.map { shadeRatio(sp, it) }.average())
        }.sortedWith(compareByDescending<SpaceScore> { it.shadeRatio }.thenBy { it.space.id })
    }

    /**
     * 확인 기록(출발 시 실내 온도)을 반영한 최종 순위.
     * history: 칸 id → 그 칸에서 기록된 출발 시 실내 온도 평균(℃). 낮을수록 가점.
     */
    fun rankWithHistory(
        start: ZonedDateTime,
        duration: Duration,
        history: Map<String, Double>,
        weight: Double = 0.3,
    ): List<SpaceScore> {
        val base = rank(start, duration)
        if (history.isEmpty()) return base
        val min = history.values.min()
        val max = history.values.max()
        val span = (max - min).takeIf { it > 0.1 } ?: return base
        return base.map { s ->
            val t = history[s.space.id] ?: return@map s
            val bonus = (max - t) / span // 0..1, 가장 시원했던 칸 = 1
            s.copy(shadeRatio = ((1 - weight) * s.shadeRatio + weight * bonus).coerceIn(0.0, 1.0))
        }.sortedWith(compareByDescending<SpaceScore> { it.shadeRatio }.thenBy { it.space.id })
    }
}

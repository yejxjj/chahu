package com.chahu.app.engine

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 태양 위치 계산 (SunCalc 알고리즘 포팅, 외부 의존성 없음).
 *
 * azimuthDeg  : 북쪽 기준 시계 방향 방위각 (0 = 북, 90 = 동, 180 = 남, 270 = 서)
 * altitudeDeg : 지평선 기준 고도각 (0 이하이면 해가 진 상태)
 */
data class SunPosition(val azimuthDeg: Double, val altitudeDeg: Double) {
    val isUp: Boolean get() = altitudeDeg > 0.5

    companion object {
        private const val RAD = PI / 180.0
        private const val DAY_MS = 1000.0 * 60 * 60 * 24
        private const val J1970 = 2440588.0
        private const val J2000 = 2451545.0
        private const val OBLIQUITY = RAD * 23.4397

        private fun toDays(epochMillis: Long): Double =
            epochMillis / DAY_MS - 0.5 + J1970 - J2000

        private fun rightAscension(l: Double, b: Double) =
            atan2(sin(l) * cos(OBLIQUITY) - tan(b) * sin(OBLIQUITY), cos(l))

        private fun declination(l: Double, b: Double) =
            asin(sin(b) * cos(OBLIQUITY) + cos(b) * sin(OBLIQUITY) * sin(l))

        private fun solarMeanAnomaly(d: Double) = RAD * (357.5291 + 0.98560028 * d)

        private fun eclipticLongitude(m: Double): Double {
            val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
            val p = RAD * 102.9372
            return m + c + p + PI
        }

        private fun siderealTime(d: Double, lw: Double) = RAD * (280.16 + 360.9856235 * d) - lw

        fun at(epochMillis: Long, latDeg: Double, lonDeg: Double): SunPosition {
            val lw = RAD * -lonDeg
            val phi = RAD * latDeg
            val d = toDays(epochMillis)
            val m = solarMeanAnomaly(d)
            val l = eclipticLongitude(m)
            val dec = declination(l, 0.0)
            val ra = rightAscension(l, 0.0)
            val h = siderealTime(d, lw) - ra

            // SunCalc 방위각은 남쪽 기준 서쪽 방향(+) → 북쪽 기준 시계 방향으로 변환
            val azSouth = atan2(sin(h), cos(h) * sin(phi) - tan(dec) * cos(phi))
            val alt = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
            val azNorth = ((azSouth / RAD) + 180.0 + 360.0) % 360.0
            return SunPosition(azNorth, alt / RAD)
        }
    }
}

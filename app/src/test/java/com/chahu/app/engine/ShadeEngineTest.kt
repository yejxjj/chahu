package com.chahu.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ShadeEngineTest {

    private val kst = ZoneId.of("Asia/Seoul")

    /** assets/lots/sample_lot.json 과 같은 배치 */
    private fun sampleLot(): ParkingLot {
        val spaces = buildList {
            for ((row, y0) in listOf("A" to 17.0, "B" to 6.0)) for (i in 0 until 10) {
                add(Space("%s-%02d".format(row, i + 1), 2.0 + i * 2.6, y0, 2.5, 5.0))
            }
        }
        fun rect(x0: Double, y0: Double, x1: Double, y1: Double) =
            listOf(Vec(x0, y0), Vec(x1, y0), Vec(x1, y1), Vec(x0, y1))
        return ParkingLot(
            name = "test", latDeg = 36.6284, lonDeg = 127.4562, entrance = Vec(50.0, 14.0),
            spaces = spaces,
            buildings = listOf(
                Building("west", rect(-15.0, -2.0, -3.0, 12.0), 15.0),
                Building("south", rect(6.0, -13.0, 30.0, -4.0), 12.0),
            ),
            trees = listOf(Tree(Vec(38.0, 3.0), 2.6, 8.0), Tree(Vec(44.0, 3.0), 2.4, 7.0), Tree(Vec(41.0, 26.0), 2.6, 8.0)),
        )
    }

    @Test
    fun sunAtSeoulSummerSolsticeNoon() {
        val t = ZonedDateTime.of(2026, 6, 21, 12, 33, 0, 0, kst)
        val s = SunPosition.at(t.toInstant().toEpochMilli(), 37.57, 126.98)
        assertEquals(75.9, s.altitudeDeg, 0.5)   // 90 - 37.57 + 23.44
        assertEquals(180.0, s.azimuthDeg, 3.0)   // 정남
    }

    @Test
    fun sunIsDownAtNight() {
        val t = ZonedDateTime.of(2026, 8, 14, 22, 0, 0, 0, kst)
        val s = SunPosition.at(t.toInstant().toEpochMilli(), 37.57, 126.98)
        assertTrue(!s.isUp)
    }

    @Test
    fun shadowPointsAwayFromSun() {
        val lot = sampleLot()
        val engine = ShadeEngine(lot)
        // 해가 정남쪽에 있으면 그림자는 북쪽으로 뻗는다
        val sh = engine.shadowsFor(SunPosition(azimuthDeg = 180.0, altitudeDeg = 45.0))
        val south = sh.polygons[1]
        assertTrue(south.maxOf { it.y } > -4.0 + 11.0) // 높이 12m, 45° → 약 12m 북쪽까지
    }

    @Test
    fun nightCountsAsFullShade() {
        val lot = sampleLot()
        val engine = ShadeEngine(lot)
        val sh = engine.shadowsFor(SunPosition(0.0, -10.0))
        assertEquals(1.0, engine.shadeRatio(lot.spaces.first(), sh), 1e-9)
    }

    @Test
    fun recommendsSpaceNextToWestBuildingOnSummerAfternoon() {
        val engine = ShadeEngine(sampleLot())
        val start = ZonedDateTime.of(2026, 8, 14, 14, 0, 0, 0, kst)
        val ranked = engine.rank(start, Duration.ofHours(2))
        assertEquals("B-01", ranked.first().space.id)
        assertTrue(ranked.first().shadeRatio > 0.8)
        // 내림차순 정렬
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.shadeRatio >= b.shadeRatio })
    }

    @Test
    fun historyCanPromoteACoolerSpace() {
        val engine = ShadeEngine(sampleLot())
        val start = ZonedDateTime.of(2026, 8, 14, 14, 0, 0, 0, kst)
        val base = engine.rank(start, Duration.ofHours(2))
        val second = base[1].space.id
        val history = mapOf(base[0].space.id to 45.0, second to 33.0)
        val ranked = engine.rankWithHistory(start, Duration.ofHours(2), history, weight = 0.6)
        assertEquals(second, ranked.first().space.id)
    }
}

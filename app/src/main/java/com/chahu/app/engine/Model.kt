package com.chahu.app.engine

/** 주차 칸: 축 정렬 직사각형 (좌하단 x, y / 폭 w / 깊이 h, 단위 m) */
data class Space(val id: String, val x: Double, val y: Double, val w: Double, val h: Double) {
    val center: Vec get() = Vec(x + w / 2, y + h / 2)
}

/** 건물: 볼록 다각형 바닥면 + 높이(m). 오목한 건물은 볼록 조각으로 나눠 입력한다. */
data class Building(val name: String, val footprint: List<Vec>, val heightM: Double)

/** 나무: 수관 중심, 반지름, 높이(m) */
data class Tree(val center: Vec, val radiusM: Double, val heightM: Double)

data class ParkingLot(
    val name: String,
    val latDeg: Double,
    val lonDeg: Double,
    val entrance: Vec,
    val spaces: List<Space>,
    val buildings: List<Building>,
    val trees: List<Tree>,
)

/** 한 시각의 그림자: 건물 그림자 다각형 + 나무 그림자 원 */
data class Shadows(
    val polygons: List<List<Vec>>,
    val circles: List<Circle>,
    val night: Boolean,
)

data class Circle(val center: Vec, val radius: Double)

/** 추천 결과 한 칸 */
data class SpaceScore(val space: Space, val shadeRatio: Double) {
    val percent: Int get() = Math.round(shadeRatio * 100).toInt()
}

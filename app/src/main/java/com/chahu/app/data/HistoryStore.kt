package com.chahu.app.data

import android.content.Context

/**
 * 확인 기록 저장소: 칸별 "출발 시 실내 온도" 평균.
 * 개인정보는 저장하지 않고 (주차장, 칸, 온도)만 차량 안에 둔다.
 * 2차 PT에서는 Room DB로 옮기고 요일·시간대 축을 추가한다.
 */
class HistoryStore(context: Context, private val lotName: String) {
    private val prefs = context.getSharedPreferences("shade_history", Context.MODE_PRIVATE)

    fun record(spaceId: String, cabinTempC: Double) {
        val sumKey = "$lotName|$spaceId|sum"
        val cntKey = "$lotName|$spaceId|cnt"
        prefs.edit()
            .putFloat(sumKey, prefs.getFloat(sumKey, 0f) + cabinTempC.toFloat())
            .putInt(cntKey, prefs.getInt(cntKey, 0) + 1)
            .apply()
    }

    fun averages(): Map<String, Double> = prefs.all.keys
        .filter { it.startsWith("$lotName|") && it.endsWith("|cnt") }
        .associate { key ->
            val id = key.removePrefix("$lotName|").removeSuffix("|cnt")
            val cnt = prefs.getInt(key, 0).coerceAtLeast(1)
            id to prefs.getFloat("$lotName|$id|sum", 0f).toDouble() / cnt
        }

    fun clear() = prefs.edit().clear().apply()
}

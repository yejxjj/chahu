package com.chahu.app.data

import android.content.Context
import com.chahu.app.engine.Building
import com.chahu.app.engine.ParkingLot
import com.chahu.app.engine.Space
import com.chahu.app.engine.Tree
import com.chahu.app.engine.Vec
import org.json.JSONArray
import org.json.JSONObject

/**
 * assets/lots 폴더의 JSON 파일에서 주차장 데이터를 읽는다.
 * 좌표는 주차장 기준점(latDeg, lonDeg)으로부터의 로컬 미터 좌표(x = 동, y = 북).
 */
object LotRepository {

    fun load(context: Context, assetPath: String = "lots/sample_lot.json"): ParkingLot {
        val text = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(JSONObject(text))
    }

    fun parse(o: JSONObject): ParkingLot = ParkingLot(
        name = o.getString("name"),
        latDeg = o.getDouble("lat"),
        lonDeg = o.getDouble("lon"),
        entrance = vec(o.getJSONArray("entrance")),
        spaces = o.getJSONArray("spaces").objects().map {
            Space(it.getString("id"), it.getDouble("x"), it.getDouble("y"), it.getDouble("w"), it.getDouble("h"))
        },
        buildings = o.getJSONArray("buildings").objects().map {
            val floors = it.optInt("floors", 0)
            val height = if (it.has("height")) it.getDouble("height") else floors * 3.0
            Building(it.getString("name"), it.getJSONArray("footprint").arrays().map(::vec), height)
        },
        trees = o.optJSONArray("trees")?.objects()?.map {
            Tree(vec(it.getJSONArray("center")), it.getDouble("radius"), it.getDouble("height"))
        } ?: emptyList(),
    )

    private fun vec(a: JSONArray) = Vec(a.getDouble(0), a.getDouble(1))
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.arrays() = (0 until length()).map { getJSONArray(it) }
}

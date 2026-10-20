package com.vincenthzr.locationspoofer.data.model

import org.json.JSONArray
import org.json.JSONObject

/** 路线点 JSON 的唯一编解码入口，避免各模块各自实现时漏掉 waitSec。 */
object RoutePointCodec {

    fun encode(points: List<RoutePoint>): String {
        val array = JSONArray()
        points.forEach { point ->
            array.put(JSONObject().apply {
                put("lat", point.lat)
                put("lng", point.lng)
                if (point.waitSec > 0.0) put("waitSec", point.waitSec)
            })
        }
        return array.toString()
    }

    fun decode(json: String?): List<RoutePoint> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val lat = obj.optDouble("lat", Double.NaN)
                    val lng = obj.optDouble("lng", Double.NaN)
                    if (!lat.isFinite() || !lng.isFinite()) continue
                    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) continue
                    add(
                        RoutePoint(
                            lat = lat,
                            lng = lng,
                            waitSec = obj.optDouble("waitSec", 0.0).takeIf { it.isFinite() && it > 0.0 } ?: 0.0
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

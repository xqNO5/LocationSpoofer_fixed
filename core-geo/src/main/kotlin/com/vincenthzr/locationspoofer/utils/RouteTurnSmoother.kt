package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * 只对真实道路折线的"急转弯"做局部圆角，不动直线段。
 *
 * 高德返回的道路折线已经贴合路网，不能整体再用 Catmull-Rom 平滑（会把直路也拉弯）。
 * 这里在航向变化超过阈值的拐点处，用一小段二次贝塞尔替换尖角：
 * 拐点前后各取不超过 [radiusMeters] 的切线长度，曲线中点只会内切几米，视觉上更自然。
 */
object RouteTurnSmoother {

    private const val DEDUPE_TOLERANCE_M = 0.3
    private const val MIN_RADIUS_M = 1.0

    fun smooth(
        points: List<LatLng>,
        minTurnDegrees: Double = 25.0,
        radiusMeters: Double = 8.0,
        sampleSpacingMeters: Double = 2.0
    ): List<LatLng> {
        val clean = dedupe(points)
        if (clean.size < 3) return clean

        val out = ArrayList<LatLng>(clean.size + 16)
        out += clean.first()

        for (i in 1 until clean.lastIndex) {
            val prev = clean[i - 1]
            val curr = clean[i]
            val next = clean[i + 1]
            val prevLen = GeoMath.distance(prev, curr)
            val nextLen = GeoMath.distance(curr, next)
            if (prevLen < MIN_RADIUS_M || nextLen < MIN_RADIUS_M) {
                out += curr
                continue
            }

            val inBearing = GeoMath.bearing(prev, curr).toDouble()
            val outBearing = GeoMath.bearing(curr, next).toDouble()
            val turn = abs(((outBearing - inBearing + 540.0) % 360.0) - 180.0)
            if (turn < minTurnDegrees || turn > 165.0) {
                out += curr
                continue
            }

            val radius = min(radiusMeters, min(prevLen, nextLen) * 0.45)
            if (radius < MIN_RADIUS_M) {
                out += curr
                continue
            }

            val entry = GeoMath.destination(curr, GeoMath.bearing(curr, prev).toDouble(), radius)
            val exit = GeoMath.destination(curr, outBearing, radius)
            out += entry

            // 二次贝塞尔 entry -> curr -> exit，用 curr 作为控制点，曲线内切但不穿过尖角。
            val approxLength = GeoMath.distance(entry, curr) + GeoMath.distance(curr, exit)
            val steps = ceil(approxLength / sampleSpacingMeters.coerceAtLeast(0.5))
                .toInt()
                .coerceIn(3, 24)
            for (step in 1 until steps) {
                val t = step.toDouble() / steps
                val it = 1.0 - t
                out += LatLng(
                    it * it * entry.lat + 2.0 * it * t * curr.lat + t * t * exit.lat,
                    it * it * entry.lng + 2.0 * it * t * curr.lng + t * t * exit.lng
                )
            }
            out += exit
        }

        out += clean.last()
        return dedupe(out).let { if (it.size < 2) clean else it }
    }

    private fun dedupe(points: List<LatLng>): List<LatLng> {
        val out = ArrayList<LatLng>(points.size)
        for (point in points) {
            if (!point.lat.isFinite() || !point.lng.isFinite()) continue
            if (point.lat !in -90.0..90.0 || point.lng !in -180.0..180.0) continue
            val last = out.lastOrNull()
            if (last == null || GeoMath.distance(last, point) > DEDUPE_TOLERANCE_M) {
                out += point
            }
        }
        return out
    }
}

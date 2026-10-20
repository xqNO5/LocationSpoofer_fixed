package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import kotlin.math.ceil
import kotlin.math.pow

/**
 * 把用户点选的折线路点变成一条经过每个路点的平滑曲线。
 *
 * 使用向心 Catmull-Rom（centripetal Catmull-Rom）插值：
 * - 曲线严格经过原始路点，不会像普通样条那样把用户选的拐点"抹掉"；
 * - 向心参数化可以避免均匀 Catmull-Rom 在急转弯处出现的尖点和自交；
 * - 首尾路点原样保留，闭环路线会补回起点，保证 [RoutePath] 仍能识别为闭环。
 *
 * 真实道路匹配成功时不应调用本方法，直接使用高德返回的道路折线。
 */
object RouteSmoother {

    private const val DEFAULT_SPACING_M = 5.0
    private const val DEFAULT_MAX_POINTS = 5_000
    private const val CLOSED_LOOP_TOLERANCE_M = 5.0
    private const val DEDUPE_TOLERANCE_M = 0.5
    private const val ALPHA = 0.5

    /**
     * @param points 原始路点（首尾相距 5 米以内视为闭环）
     * @param spacingMeters 目标采样间距，越小曲线越圆滑、点也越多
     * @param maxPoints 输出点数上限，长路线会自动放大采样间距
     */
    fun smooth(
        points: List<LatLng>,
        spacingMeters: Double = DEFAULT_SPACING_M,
        maxPoints: Int = DEFAULT_MAX_POINTS
    ): List<LatLng> {
        val clean = dedupe(points)
        if (clean.size < 2) return clean
        if (clean.size == 2) {
            // 两个点之间不存在可用于定义曲率的第三点，只能保持直线。
            return clean
        }

        val closed = clean.size >= 3 &&
            GeoMath.distance(clean.first(), clean.last()) <= CLOSED_LOOP_TOLERANCE_M
        val source = if (closed) clean.dropLast(1) else clean
        if (source.size < 2) return clean

        val segmentCount = if (closed) source.size else source.size - 1
        val lengths = DoubleArray(segmentCount) { i ->
            GeoMath.distance(source[i], source[(i + 1) % source.size])
        }
        val total = lengths.sum()
        if (!total.isFinite() || total <= 0.0) return listOf(clean.first())

        val cappedMax = maxPoints.coerceAtLeast(2)
        val spacing = spacingMeters.coerceAtLeast(0.5)
            .coerceAtLeast(total / cappedMax)

        val result = ArrayList<LatLng>(cappedMax.coerceAtMost(512))
        result += source.first()

        for (i in 0 until segmentCount) {
            val p1 = source[i]
            val p2 = source[(i + 1) % source.size]
            val p0 = if (closed) {
                source[(i - 1 + source.size) % source.size]
            } else {
                if (i == 0) reflect(p1, p2) else source[i - 1]
            }
            val p3 = if (closed) {
                source[(i + 2) % source.size]
            } else {
                if (i + 2 >= source.size) reflect(p2, p1) else source[i + 2]
            }

            val steps = ceil(lengths[i] / spacing).toInt().coerceIn(1, 500)
            for (step in 0 until steps) {
                val t = step.toDouble() / steps
                result += catmullRom(p0, p1, p2, p3, t)
            }
        }

        result += if (closed) source.first() else source.last()
        return dedupe(result).let { if (it.size < 2) clean else it }
    }

    private fun dedupe(points: List<LatLng>): List<LatLng> {
        if (points.isEmpty()) return emptyList()
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

    /** 以 p 为中心、沿 p->other 反方向镜像一个控制点，用于开放路线的端点切线。 */
    private fun reflect(p: LatLng, other: LatLng): LatLng =
        LatLng(2.0 * p.lat - other.lat, 2.0 * p.lng - other.lng)

    private fun catmullRom(p0: LatLng, p1: LatLng, p2: LatLng, p3: LatLng, t: Double): LatLng {
        val t0 = 0.0
        val t1 = t0 + GeoMath.distance(p0, p1).pow(ALPHA)
        val t2 = t1 + GeoMath.distance(p1, p2).pow(ALPHA)
        val t3 = t2 + GeoMath.distance(p2, p3).pow(ALPHA)
        if (t1 <= t0 || t2 <= t1 || t3 <= t2) return lerp(p1, p2, t)

        val tt = t1 + (t2 - t1) * t

        val a1 = blend(p0, p1, t0, t1, tt)
        val a2 = blend(p1, p2, t1, t2, tt)
        val a3 = blend(p2, p3, t2, t3, tt)
        val b1 = blend(a1, a2, t0, t2, tt)
        val b2 = blend(a2, a3, t1, t3, tt)
        return blend(b1, b2, t1, t2, tt)
    }

    private fun blend(a: LatLng, b: LatLng, ta: Double, tb: Double, t: Double): LatLng {
        val span = tb - ta
        if (span <= 0.0) return b
        val wa = (tb - t) / span
        val wb = (t - ta) / span
        return LatLng(a.lat * wa + b.lat * wb, a.lng * wa + b.lng * wb)
    }

    private fun lerp(a: LatLng, b: LatLng, t: Double): LatLng =
        LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
}

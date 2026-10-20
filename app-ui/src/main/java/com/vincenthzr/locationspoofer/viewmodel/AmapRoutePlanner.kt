package com.vincenthzr.locationspoofer.viewmodel

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.DrivePath
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.WalkPath
import com.vincenthzr.locationspoofer.data.model.RealRouteMode
import com.vincenthzr.locationspoofer.data.model.RouteBatchPlanner
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import com.vincenthzr.locationspoofer.utils.GeoMath

/**
 * 高德真实道路匹配。
 *
 * 路网约束（来自 app/drive.txt、app/walk.txt 与高德 Android SDK）：
 * - 驾车 [RouteSearch.DriveRouteQuery] 单次最多 6 个途经点；
 * - 步行 [RouteSearch.WalkRouteQuery] 只有起终点，不支持途经点。
 *
 * 因此驾车按 6 个途经点分批请求，步行按相邻点分段请求。两者都不是"一次请求塞进所有点"。
 * 驾车批次失败时会退化为逐段请求，避免某一个批次因为路口/收费/距离限制拖垮整条路线。
 */
internal object AmapRoutePlanner {

    fun fetch(
        context: Context,
        waypoints: List<RoutePoint>,
        profile: RealRouteMode,
        allowDriveFallback: Boolean = false
    ): List<RoutePoint> {
        require(profile.concrete) { "AUTO must be resolved before calling the AMap route planner" }
        if (waypoints.size < 2) return waypoints

        com.amap.api.services.core.ServiceSettings.updatePrivacyShow(context, true, true)
        com.amap.api.services.core.ServiceSettings.updatePrivacyAgree(context, true)
        val routeSearch = RouteSearch(context)

        val result = ArrayList<RoutePoint>()
        if (profile == RealRouteMode.WALKING) {
            // 步行 API 没有途经点参数，只能逐段请求；这也是它比驾车 API 更容易走出内部路的原因。
            for (i in 0 until waypoints.size - 1) {
                val segment = try {
                    fetchWalkSegment(routeSearch, waypoints[i], waypoints[i + 1])
                } catch (walkError: Exception) {
                    // AUTO 模式下步行算不出来时退回驾车；用户显式选择步行则不偷偷换路网。
                    if (!allowDriveFallback) throw walkError
                    fetchDriveChunk(routeSearch, waypoints, i, i + 1)
                }
                appendSegment(result, segment)
            }
        } else {
            for (batch in RouteBatchPlanner.drivingBatches(waypoints.size)) {
                val segment = fetchDriveChunkWithFallback(routeSearch, waypoints, batch.first, batch.last)
                appendSegment(result, segment)
            }
        }
        return result
    }

    private fun fetchWalkSegment(routeSearch: RouteSearch, start: RoutePoint, end: RoutePoint): List<RoutePoint> {
        val fromAndTo = RouteSearch.FromAndTo(toLatLon(start), toLatLon(end))
        val query = RouteSearch.WalkRouteQuery(fromAndTo, RouteSearch.WALK_DEFAULT)
        val result = routeSearch.calculateWalkRoute(query)
        val path = result?.paths?.firstOrNull()
            ?: throw IllegalStateException("AMap returned no walking path")
        return walkPathPoints(path)
    }

    private fun fetchDriveChunkWithFallback(
        routeSearch: RouteSearch,
        points: List<RoutePoint>,
        startIndex: Int,
        endIndex: Int
    ): List<RoutePoint> {
        return try {
            fetchDriveChunk(routeSearch, points, startIndex, endIndex)
        } catch (first: Exception) {
            if (endIndex <= startIndex + 1) throw first
            // 批次太大或中间某个途经点不可达时，退回逐段请求，至少保留能算出来的部分。
            val fallback = ArrayList<RoutePoint>()
            for (i in startIndex until endIndex) {
                appendSegment(fallback, fetchDriveChunk(routeSearch, points, i, i + 1))
            }
            fallback
        }
    }

    private fun fetchDriveChunk(
        routeSearch: RouteSearch,
        points: List<RoutePoint>,
        startIndex: Int,
        endIndex: Int
    ): List<RoutePoint> {
        val fromAndTo = RouteSearch.FromAndTo(toLatLon(points[startIndex]), toLatLon(points[endIndex]))
        val via = (startIndex + 1 until endIndex).map { toLatLon(points[it]) }
        val query = RouteSearch.DriveRouteQuery(
            fromAndTo,
            RouteSearch.DrivingDefault,
            via.ifEmpty { null },
            null,
            ""
        )
        val result = routeSearch.calculateDriveRoute(query)
        val path = result?.paths?.firstOrNull()
            ?: throw IllegalStateException("AMap returned no driving path")
        return drivePathPoints(path)
    }

    private fun walkPathPoints(path: WalkPath): List<RoutePoint> {
        val points = ArrayList<RoutePoint>()
        for (step in path.steps.orEmpty()) {
            for (polyline in step.polyline.orEmpty()) {
                points.add(RoutePoint(polyline.latitude, polyline.longitude))
            }
        }
        return points
    }

    private fun drivePathPoints(path: DrivePath): List<RoutePoint> {
        val points = ArrayList<RoutePoint>()
        val stepEndIndices = ArrayList<Int>()
        for (step in path.steps.orEmpty()) {
            for (polyline in step.polyline.orEmpty()) {
                points.add(RoutePoint(polyline.latitude, polyline.longitude))
            }
            if (points.isNotEmpty()) stepEndIndices.add(points.lastIndex)
        }

        // 高德只返回红绿灯数量，不返回精确坐标；沿用原有策略，把等待点均匀随机放到各段末尾。
        val trafficLights = path.totalTrafficlights
        if (trafficLights > 0 && stepEndIndices.isNotEmpty()) {
            stepEndIndices.shuffled().take(trafficLights).forEach { index ->
                points[index] = points[index].copy(waitSec = 15.0)
            }
        }
        return points
    }

    private fun appendSegment(target: MutableList<RoutePoint>, segment: List<RoutePoint>) {
        if (segment.isEmpty()) return
        if (target.isEmpty()) {
            target.addAll(segment)
            return
        }
        val last = target.last()
        val first = segment.first()
        val samePoint = GeoMath.distance(LatLng(last.lat, last.lng), LatLng(first.lat, first.lng)) < 1.0
        target.addAll(if (samePoint) segment.drop(1) else segment)
    }

    private fun toLatLon(point: RoutePoint): LatLonPoint = LatLonPoint(point.lat, point.lng)
}
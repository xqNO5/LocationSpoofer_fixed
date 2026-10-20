package com.vincenthzr.locationspoofer.data.model

/**
 * 真实道路匹配使用的路网类型。
 *
 * - [AUTO]：步行/跑步用步行路网，骑行/驾车用驾车路网；
 * - [WALKING]：强制步行路网。高德步行 API 不支持途经点，内部会按相邻点分段请求；
 * - [DRIVING]：强制驾车路网。高德驾车 API 单次最多支持 6 个途经点，内部会按 6 个一批分段请求。
 */
enum class RealRouteMode {
    AUTO,
    WALKING,
    DRIVING;

    /** 实际请求高德时使用的路网，调用方负责把 AUTO 根据速度档位解析成具体值。 */
    val concrete: Boolean get() = this != AUTO
}

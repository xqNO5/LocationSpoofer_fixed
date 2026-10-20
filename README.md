<div align="center">

<h1>LocationSpoofer</h1>

<p>基于 KernelSU + LSPosed 的 Android 系统级虚拟定位与无线环境伪装框架</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)](https://kotlinlang.org)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20101%2B-purple.svg)](https://github.com/LSPosed/LSPosed)
[![Release](https://img.shields.io/github/v/release/xqNO5/LocationSpoofer_fixed)](https://github.com/xqNO5/LocationSpoofer_fixed/releases)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

## 简介

LocationSpoofer 通过 **KernelSU / APatch / Magisk** 获取 Root 权限，并使用 **LSPosed（libxposed API 101+）** 在目标进程中拦截定位、GNSS、Wi-Fi、基站、蓝牙和传感器接口，提供一套自洽的系统级虚拟定位环境。

项目支持两种方案：

- **scoped**：只对 LSPosed 作用域内勾选的目标 App 生效。
- **global**：Hook system_server 等系统进程，对全设备生效。

> 当前版本：**3.0.2** · [下载 Release](https://github.com/xqNO5/LocationSpoofer_fixed/releases/tag/v3.0.2)

## v3.0.2 更新

- **修复旧路线残留**：从路线库载入或手动规划新路线后，不再沿用之前的采集路线、地图叠加层和环境匹配。
- **真实道路结果持久化**：匹配成功后写入 Room 缓存，再次使用同一路线时不重复调用高德 API。
- **非真实道路平滑**：手动路线、路线库路线和采集路线统一使用 Catmull-Rom 平滑曲线；真实道路匹配成功时直接使用高德折线。
- **统一驾车路网**：真实道路匹配全部使用高德驾车 API，单次最多 6 个途经点，超出后自动分批请求，确保经过每个用户点选的途经点。
- **移除步行路网**：高德步行 API 不支持途经点，无法满足路线必须经过指定点的要求。

## 主要功能

| 模块 | 能力 |
|---|---|
| 定位模拟 | WGS-84 / GCJ-02 / BD-09 自适应，静止、路线、摇杆三种移动方式 |
| 路线规划 | 手动选点、路线库、采集路线、真实道路匹配、平滑曲线、路线缓存 |
| 无线电环境 | Wi-Fi、2G–5G 基站、BLE 蓝牙扫描与连接态模拟，WiGLE / OpenCellID 导入 |
| 卫星与传感器 | GNSS 卫星矩阵、NMEA、海拔漂移、步频与计步模拟 |
| 反检测 | Mock 标记抹除、AppOps / Settings 隐藏、堆栈清洗、ClassLoader 探测隔离 |
| 地图与 UI | 高德 / 百度 / Google 三地图引擎，Jetpack Compose 界面，悬浮摇杆 |

## 路线规划说明

- **真实道路匹配**只使用高德驾车 API。每批最多 6 个途经点，路线超过 7 个点时自动拆成多批，相邻批次共享端点。
- 匹配成功后的道路折线会持久化到本地缓存，后续相同途经点直接复用。
- 未开启真实匹配，或匹配失败时，路线会按控制点生成平滑曲线，不再使用两点之间的硬直线。
- 真实道路折线不做整体平滑，只对急转弯做几米的局部圆角，直线段保持高德原样。
- 从路线库或采集路线切换到手动规划时，旧的路线来源会被清空，避免环境数据继续匹配旧路线。
- 开始路线模拟后立即显示橙色当前位置标记；如开启“开始模拟时重启目标 App”，路线模式也会和定点模式一样重启作用域应用，确保旧进程重新加载 Hook。

## 项目结构

| 模块 | 职责 |
|---|---|
| `app` | 宿主壳工程：Application、MainActivity、签名与打包 |
| `app-ui` | Compose UI、页面、弹窗、ViewModel |
| `service` | 前台保活、悬浮摇杆、开机自启 |
| `xposed` | LSPosed 注入入口与定位 / 网络 / 传感器 Hook |
| `core-data` | Room 数据库、Repository、配置与环境扫描 |
| `core-geo` | 纯 Kotlin 坐标换算、路线几何、运动真实度引擎 |

## 环境要求

- Android 8.0（API 26）及以上
- KernelSU / APatch / Magisk
- LSPosed（libxposed API 101+）
- 构建使用 JDK 17、Android SDK API 37；Windows 本地环境见 [docs/development-windows.md](docs/development-windows.md)

## 构建

`scoped` 和 `global` 必须分两次构建：

```bash
./gradlew :app:assembleScopedDebug
./gradlew :app:assembleGlobalDebug
```

产物：

```text
app/build/outputs/apk/scoped/debug/
app/build/outputs/apk/global/debug/
```

每次 push 由 GitHub Actions 执行单元测试和 Debug 构建；`main` 的版本号变化时自动创建 Tag 并发布 Release。发布流程见 [docs/releasing.md](docs/releasing.md)。

## 安装

1. 在 Release 中下载 **scoped** 或 **global** 其中一个方案，不要同时安装。
2. 在 LSPosed 中启用模块；scoped 勾选目标 App，global 勾选系统框架、电话和蓝牙。
3. 强制停止目标 App 或重启设备，使 Hook 生效。
4. 打开 LocationSpoofer，选择位置或路线后开始模拟。

> 两个方案使用相同包名，但用途不同。切换方案前先停用并卸载旧方案。

## 签名

- 包名：`com.vincenthzr.locationspoofer`
- v3.0.2 发布签名 SHA1：`07:75:AC:B5:92:B2:E4:64:40:93:4A:DB:F7:E2:1D:F6:9D:BF:B8:35`

本仓库从 v3.0.2 起使用独立的发布签名。此前使用其他签名安装的同包名版本无法直接覆盖升级，请先导出数据、卸载旧版，再安装新版本并导入数据。

## 使用提示

- 首次使用前在 LSPosed 中确认模块已激活，并勾选需要模拟的目标 App。
- Wi-Fi、基站、蓝牙模拟需要先采集本地环境数据，或配置 WiGLE / OpenCellID Token。
- 真实道路匹配依赖网络和高德 API 配额；复杂路线建议先在路线库中保存控制点。
- 悬浮摇杆需要“显示在其他应用上层”权限。

## 文档

- [系统适配进度](ADAPTATION_PROGRESS.md)
- [Windows 本地开发](docs/development-windows.md)
- [发布与签名](docs/releasing.md)
- [Xposed 适配指南](xposed/ADAPTATION_GUIDE.md)
- [厂商适配框架](xposed/src/main/java/com/vincenthzr/locationspoofer/xposed/hooks/vendor/README.md)
- [贡献指南](CONTRIBUTING.md)

## 免责声明

本项目仅供学习研究、技术交流和合法的开发测试使用。请勿用于虚假打卡、考试作弊、商业欺诈或其他违法违规用途。使用本项目造成的账号封禁、数据丢失、法律纠纷等后果由使用者自行承担。

## License

[GNU General Public License v3.0](LICENSE)

Copyright (C) 2026 SuseOAA
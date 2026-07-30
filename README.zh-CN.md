# KioskRelay Android 版

[English](./README.md) | 简体中文

KioskRelay 是一款面向工业平板、数字标牌、手机和基础 Android TV
场景的开源 WebView 网页大屏终端。`v0.4.0` 普通模式 MVP 已实现为可构建的
单模块 Android 应用，可在 App 内配置网页、品牌和运行策略，无需重新打包。

## 当前状态

- 应用标识：`io.github.kioskrelay`
- 版本：`0.4.0`
- 最低系统：Android 7.0 / API 24
- `targetSdk=36`、`compileSdk=36.1`
- 工具链：JDK 17、Gradle 9.4.1、AGP 9.2.1
- 技术栈：Jetpack Compose、Proto DataStore、AndroidX WebKit
- 语言：跟随系统、简体中文、英文

当前工程已通过 JVM 单元测试、Android Lint、Debug/混淆 Release 构建和
Android 仪器测试 APK 编译。Debug APK 已在 Android 7.0 / API 24 x86_64 AVD
完成安装和核心流程实测。Chrome/WebView 119 下测试页面完整渲染；该 AVD 镜像自带
WebView 53 虽能加载主文档，但无法解析 Vite 8 客户端代码并报
`SyntaxError: Unexpected token .`，最终白屏。

当前状态是“API 24 Debug 核心流程已在模拟器验证”，不是“Android 7 生产发布已全面
验收”。API 24 connected 仪器测试已最终全量 `13/13` 通过，0 skipped、0 failed；
签名 Release 安装、真实硬件和其余 Android 版本矩阵仍是发布门禁。

## 已实现的普通模式 MVP

- 四步首次配置：欢迎/语言、产品品牌、网页/显示、管理员/开机配置
- 动态产品名称、品牌色、Logo、启动背景和状态文案
- HTTPS 默认策略；HTTP 需管理员显式开启并二次确认
- 首次 URL 自动生成精确 Origin 白名单，高级设置可增加精确 Origin
- 默认开启 JavaScript、DOM Storage 和第一方 Cookie
- 默认关闭 mixed content、第三方 Cookie、文件/内容访问、JS Interface、
  弹窗、下载、上传、定位、摄像头和麦克风
- SSL 错误始终拒绝；WebView 调试只在 Debug 构建开启
- 离线提示、`5/10/20/40/60/60` 秒退避、网络恢复和 API 26+ Renderer 重建
- 沉浸式全屏、屏幕常亮、方向选择和返回键保护
- 隐藏管理入口：3 秒内点击左上角 5 次；TV 使用
  `上 上 下 下 左 右 左 右 确认`
- 品牌、网页与显示、运行、安全、维护五组设置
- 重载、清缓存、单独清 Cookie/网页数据、恢复默认和脱敏诊断导出
- `.kioskrelay` ZIP 导入导出，包含大小、版本、图片和路径穿越校验；
  不导出密码、Cookie、缓存或日志，也不覆盖现有管理员密码
- API 24–28 尽力直接处理开机启动；API 29+ 使用通知/下次打开恢复
- Android TV Launcher、D-pad 基础操作、Adaptive Icon、Android 7
  各密度图标和 320×180 TV Banner

视觉稿中的水文业务仪表盘不属于本仓库实现范围，它代表 WebView 加载的客户网页。

## 构建

准备 JDK 17 和 Android SDK 36.1，然后执行：

```bash
./gradlew test lint assembleDebug assembleRelease assembleDebugAndroidTest
```

构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Release APK 当前未签名，交付设备前需使用正式部署证书签名。

## Android 兼容说明

- API 24–25 没有 `WebViewClient.onRenderProcessGone`，只能保证配置持久化，并在
  进程或 Activity 下次启动时恢复。
- API 26+ 支持 Renderer 退出后重建；连续异常达到上限后停止自动循环并等待手动重试。
- API 24 AVD 已实测通过四步首启、HTTP 风险确认、DataStore 进程重启、返回键保护、
  管理员 D-pad 序列、全屏横屏常亮、失败退避、真实网络断开/恢复和
  `BOOT_COMPLETED` 自启动。
- WebView 119 的通过结论仅指页面前端完整渲染。本地 `8071` 后端未启动，Vite 代理
  返回 HTTP 502，因此业务数据为 `--`，未完成端到端业务数据验收。
- API 29+ 受后台 Activity 启动限制，普通模式仅做通知或下次打开恢复，
  不承诺开机后一定自动弹出。
- Android 7 建议只加载可信内网页面，并控制系统 WebView 版本；公网无人值守部署
  推荐使用更高版本 Android。
- API 25、26、29、31、35/36 仍需完成运行矩阵；本轮 API 24 人工验收不宣称已覆盖
  SSL 错误和真实重定向链。

## 当前范围

本版本不包含 Device Owner、默认 Launcher、Lock Task、云端管理、远程升级和
多网页轮播，这些能力不属于普通模式 MVP。

## 文档

- [产品规划与实现状态](./docs/KioskRelay-产品规划.md)
- [Android 7.0 / API 24 验证报告](./docs/Android-7-API24-验证报告-20260730.md)
- [视觉素材说明](./docs/assets/README.md)

## 开源协议

KioskRelay 使用 [MIT License](./LICENSE)。

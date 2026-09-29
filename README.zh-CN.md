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
- 工具链：Gradle 9.5.0、AGP 9.3.1；Wrapper 可由 JDK 17 启动，Gradle Daemon 固定使用 Java 21
- 技术栈：Jetpack Compose、Proto DataStore、AndroidX WebKit
- 语言：跟随系统、简体中文、英文

当前工程已通过 58 个 JVM 单元测试、Android Lint、Debug/混淆 Release 构建，以及
Android 7.0 / API 24 AVD 上 13 个 connected 仪器测试。Debug APK 已使用
`http://192.168.1.11:5173/` 完成首启/品牌、选定安全场景、开启刷新时断网恢复、
开启且 0 秒延迟的开机路径、配置 ZIP 核心路径及部分维护/诊断的严格实测。
Chrome/WebView 119 下目标网页主体和 8 个业务请求最终正常加载；该 AVD 镜像自带
WebView 53 虽能加载主文档，但无法解析 Vite 8 客户端代码并报
`SyntaxError: Unexpected token .`，最终白屏。

当前状态是“API 24 Debug 核心流程已在模拟器验证”，不是“Android 7 生产发布已全面
验收”。严格实测确认了 3 个运行阻断项：关闭网络恢复重载时错误页被误标成在线；
从设置返回或执行维护重载后沉浸式系统栏未恢复；HTTPS 子资源证书失败会错误遮挡整个
已加载大屏。源码审计另发现凭据损坏可能失败开放的高风险项。签名 Release 安装、
真实硬件和其余 Android 版本矩阵也仍是发布门禁。

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
- 首启管理员密码可选；留空时隐藏入口直接进入设置，也可稍后在安全设置中补设
- 品牌、网页与显示、运行、安全、维护五组设置
- 重载、清缓存、单独清 Cookie/网页数据、恢复默认和脱敏诊断导出
- `.kioskrelay` ZIP 导入导出，包含大小、版本、图片和路径穿越校验；
  不导出密码、Cookie、缓存或日志，也不覆盖现有管理员密码
- API 24–28 尽力直接处理开机启动；API 29+（含 Android 11 TV）授权“显示在其他应用上层”后尝试直接启动，否则使用通知恢复
- Android TV Launcher、D-pad 基础操作、Adaptive Icon、Android 7
  各密度图标和 320×180 TV Banner

视觉稿中的水文业务仪表盘不属于本仓库实现范围，它代表 WebView 加载的客户网页。

## 构建

准备 Android SDK 36.1 和 Java 21；也可以使用 JDK 17 启动 Wrapper，并允许 Gradle
根据 `gradle/gradle-daemon-jvm.properties` 自动配置 Java 21。然后执行：

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
  管理员触屏/D-pad 序列、首次进入展示页的全屏横屏常亮、前两档失败退避、
  开启恢复刷新时的真实网络断开/恢复、
  自签名证书拒绝、跨 Origin/`intent://` 拦截和 `BOOT_COMPLETED` 自启动。
- WebView 119 下目标页面及本轮检查的 8 个数据请求最终返回 HTTP 200。测试期间
  `8071` 后端曾短暂不可用并由 Vite 返回 HTTP 502；业务数据准确性和服务 SLA
  不属于 Android 客户端兼容验收。
- 目标页的 Open-Meteo HTTPS 天气请求在 API 24 / WebView 119 上发生证书失败；
  请求被正确取消，但当前 App 会把子资源错误升级为全局 Fatal，遮挡已渲染大屏。
- 当前已知缺陷和未覆盖场景以严格测试报告为准；尤其不能把仪器测试中的合成回调
  等同于真实 302 重定向、Renderer 崩溃或硬件行为。
- API 29+ 受后台 Activity 启动限制。首次配置或“设置 → 运行”中开启开机启动，
  点击“授权开机启动”并在系统中允许本应用“显示在其他应用上层”，返回后确认授权状态并保存。
  应用不绘制悬浮窗；此权限用于系统允许的后台启动例外。未授权时仅尝试发送恢复通知。
  已授权时保留通知作为厂商静默拦截的兜底，应用进入前台后移除通知。
- 海信 55E3NH Pro（用户提供型号，系统按 Android 11 适配）尚未真机验证。
  如电视另有自启动/后台运行管理，需要允许本应用；具体菜单以固件为准。
  如果固件不提供或禁止上述授权，普通 APK 无法保证自动进入大屏，需厂商启动配置等支持。
  安装后必须先手动打开并完成配置；强行停止后应重新打开，再做重启测试。
  验收请分别测试系统重启、断电重启和遥控器待机唤醒；待机唤醒不等于系统开机，
  不保证发送开机广播。检查网页加载、遥控器操作和多次重启结果，不能以构建通过代替真机验收。
- Android 7 建议只加载可信内网页面，并控制系统 WebView 版本；公网无人值守部署
  推荐使用更高版本 Android。
- API 25、26、29、31、35/36 仍需完成运行矩阵；本轮已实测自签名 SSL 错误取消和
  直接跨 Origin 导航，但不宣称已覆盖真实 302 重定向链。

## 当前范围

本版本不包含 Device Owner、默认 Launcher、Lock Task、云端管理、远程升级和
多网页轮播，这些能力不属于普通模式 MVP。

## 文档

- [产品规划与实现状态](./docs/KioskRelay-产品规划.md)
- [严格功能与场景测试报告（2026-07-30）](./docs/KioskRelay-严格功能与场景测试报告-20260730.md)
- [Android 7.0 / API 24 验证报告](./docs/Android-7-API24-验证报告-20260730.md)
- [视觉素材说明](./docs/assets/README.md)

## 开源协议

KioskRelay 使用 [MIT License](./LICENSE)。

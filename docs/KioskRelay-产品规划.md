# KioskRelay 产品规划

## 1. 项目概述

KioskRelay 是一个面向 Android 手机、平板、电视盒子和大屏终端的开源 WebView 展示应用。

用户只需安装一个通用 APK，即可在 App 内动态配置受信任的网页地址、产品名称、Logo 和品牌外观，将设备快速转换为一个具有独立产品形象的网页大屏终端。

后续可在保持统一产品定位和配置格式的基础上，扩展 Windows WebView2 客户端。

当前仓库已完成 `v0.4.0` 普通模式 MVP 的工程与功能实现。本文同时记录产品边界、实际实现状态和仍需在目标设备完成的验收项；“已实现”不等于已经通过全部真机兼容矩阵。

### v0.4.0 实现状态

- 已完成单 `app` 模块、四步首次配置、动态品牌、Proto DataStore 和中英文界面。
- 已完成受限 WebView、精确 Origin 白名单、HTTP 风险确认、网络恢复、退避重试和 API 26+ Renderer 重建；
  API 24 严格实测发现关闭网络恢复重载时存在“伪 Online”缺陷，发布前待修复。
- 已完成管理员密码摘要/锁定、隐藏入口、五组设置、配置导入导出、维护和脱敏诊断。
- 已完成 API 24–28 与 API 29+ 分级开机策略，以及 Android TV Launcher、Banner 和 D-pad 基础操作。
- 已通过 58 个 JVM 单元测试、Lint、Debug/Release 构建；API 24 connected 仪器测试
  已最终全量 `13/13` 通过，0 skipped、0 failed。
- Debug APK 已在 Android 7.0 / API 24 x86_64 AVD 完成安装和核心流程实测。
  WebView 119 下指定页面主体和检查的业务请求最终正常加载；系统 WebView 53 因无法
  解析 Vite 8 客户端代码而白屏。
- 当前 Android 7 状态是“API 24 Debug 核心流程已在模拟器验证，生产发布全面验收待完成”。
  严格实测已确认网络状态、沉浸式恢复和子资源 TLS 处理三处发布阻断项。详细边界和证据见
  [严格功能与场景测试报告](./KioskRelay-严格功能与场景测试报告-20260730.md)。

### 一句话介绍

> 安装一次，自由配置受信任网页、产品名称与品牌 Logo，将 Android 设备快速变成专属网页大屏终端。

### 英文介绍

> KioskRelay is an open-source, dynamically brandable WebView kiosk for unattended web dashboards.

## 2. 项目目标

- 使用一个通用 APK 适配不同项目和客户。
- 在 App 内动态修改产品名称、Logo 和品牌外观。
- 无需重新编译 APK 即可切换受信任的目标网页。
- 支持沉浸式全屏和屏幕常亮，并按部署模式提供不同级别的开机启动能力。
- 在断网、页面加载失败或 WebView 异常时自动恢复。
- 提供隐藏管理入口和可选管理员密码保护；未设置密码时仅提供隐藏性，不提供认证保护。
- 保持项目轻量、易部署、易二次开发。

### 2.1 交付原则

- 文档分别标明“已实现”“构建已验证”和“真机待验证”，不混用三类结论。
- HTTPS 为默认网页协议；HTTP 只在管理员显式开启后使用。
- 普通 Android 设备不承诺绝对可靠的后台自启动。
- 真正不可退出的无人值守终端采用受管专用设备方案。
- 安全能力与基础 WebView 同期建设，不在功能完成后补做。

## 3. 产品边界

### 3.1 第一阶段支持

- Android 手机
- Android 平板
- Android TV 或电视盒子
- 工业 Android 大屏终端
- HTTPS 网页，以及管理员显式允许的 HTTP 网页
- 单网页全屏展示
- App 内动态品牌配置

### 3.2 部署模式

#### 普通模式

适用于手机、平板和普通电视盒子。提供沉浸式全屏、屏幕常亮和尽力而为的开机启动。该模式受 Android 后台启动限制、厂商自启动策略、电池优化和用户强制停止影响，不能承诺设备重启后一定自动弹出。

#### 专用设备模式

适用于可统一配置的工业终端、数字标牌和公共大屏。通过 Device Owner、默认 Launcher 和 Lock Task/Kiosk 模式限制退出并提高开机启动可靠性。该模式需要设备初始化或企业设备管理流程配合。

### 3.3 暂不支持

- 动态修改 Android 系统桌面上的应用名称
- 动态上传并替换 Android 系统桌面图标
- 启动和守护其他 Android 应用
- 网页内容管理和网页编辑
- 多租户云端管理平台
- iOS 客户端

### 3.4 系统层限制

App 内部的产品名称、Logo、启动页和主题可以动态修改，但以下信息由 APK 在编译时确定：

- Android 桌面显示的应用名称
- Android 桌面显示的应用图标
- 系统设置中的应用名称
- Android 应用包名
- APK 签名

因此，系统桌面仍显示 KioskRelay；进入 App 后则可以完全呈现为用户配置的专属产品。

## 4. 核心使用流程

```text
安装 KioskRelay
    ↓
首次启动配置向导
    ↓
选择语言并设置产品名称、Logo 与品牌
    ↓
填写 URL，设置方向、全屏、常亮和网络恢复
    ↓
测试网页（失败可带警告继续）
    ↓
选择是否设置管理员密码，设置开机策略并确认
    ↓
进入全屏展示
```

日常运行流程：

```text
设备开机
    ↓
KioskRelay 接收开机事件
    ↓
读取本地配置
    ↓
显示自定义品牌启动页
    ↓
打开 WebView
    ↓
加载目标网页
    ↓
持续监测网络和页面状态
```

## 5. 功能范围

### 5.1 品牌外观

- 自定义产品名称
- 从相册或文件中选择 Logo
- 自定义启动页背景图
- 自定义品牌主色
- 自定义背景颜色
- 自定义加载提示语
- 自定义离线提示语
- 自定义错误页面文案
- 品牌效果实时预览
- 恢复默认品牌外观

### 5.2 网页大屏

- 配置 HTTPS 网页地址
- 由管理员显式开启 HTTP 兼容模式
- 网页地址合法性检查
- 按已配置的精确 Origin 白名单限制主文档和重定向
- 默认阻止 `file://`、`content://` 和未知协议
- 打开前测试网页连通性
- WebView 全屏展示
- 支持 JavaScript
- 支持 DOM Storage
- 支持 Cookie
- 支持需用户手势触发的网页音视频播放
- 支持页面缩放
- 支持自定义 User-Agent
- 支持横屏、竖屏和跟随系统
- 支持手动重新加载
- 支持清理网页缓存

### 5.3 终端运行

- 开机自动启动
- 沉浸式全屏
- 隐藏状态栏
- 隐藏导航栏
- 保持屏幕常亮
- 禁止屏幕休眠
- 网络状态监听
- 断网提示
- 网络恢复后自动加载
- 页面加载失败自动重试
- WebView 渲染进程异常恢复
- App 返回前台后自动检查页面
- 可配置重试间隔
- 可配置启动延迟

### 5.4 管理与安全

- 管理员密码
- 隐藏设置入口
- 连续点击屏幕指定区域进入设置
- 返回键退出保护
- 防止普通用户误操作
- 配置导出
- 配置导入
- 恢复默认设置
- 清除缓存
- 查看应用版本
- 查看 WebView 版本
- 查看最近运行错误

管理员密码和配置安全要求：

- 首启密码可留空；未设置密码时隐藏管理手势直接进入设置，管理员可稍后补设。
- 只保存加盐密码摘要，不保存或记录明文密码。
- 连续认证失败后采用递增等待，降低暴力尝试风险。
- 配置导出默认不包含管理员密码摘要。
- 配置导入不得静默覆盖现有管理员凭据。
- 运行日志默认脱敏 URL 查询参数、Cookie、令牌和用户输入。

### 5.5 Android TV 适配

- 当前提供基础五向 D-pad 和返回键支持；所有配置功能的完整焦点链仍需 TV 硬件验收。
- 提供清晰的焦点、选中和按下状态。
- 隐藏设置入口支持遥控器按键序列，不依赖触屏连点。
- 提供 Android TV Launcher 图标和 Banner。
- 检查 Overscan、安全区域和横屏显示。
- 明确遥控返回键在网页历史、设置退出和 Kiosk 保护中的优先级。

## 6. 页面规划

### 6.1 首次启动向导

1. 欢迎页与语言选择
2. 产品名称、Logo、品牌色和启动背景
3. URL、屏幕方向、全屏、常亮、网络恢复及网页测试
4. 可选管理员密码、开机启动和配置确认

URL 必填，管理员密码可选；网页测试失败时允许管理员带明确警告继续保存。

### 6.2 展示页面

- 自定义品牌启动画面
- WebView 网页区域
- 页面加载进度
- 断网状态提示
- 页面错误提示
- 自动重试状态
- 隐藏的管理入口

### 6.3 设置页面

```text
设置
├── 品牌外观
│   ├── 产品名称
│   ├── 产品 Logo
│   ├── 启动页背景
│   ├── 品牌颜色
│   └── 提示文案
├── 网页大屏
│   ├── 网页地址
│   ├── 页面缩放
│   ├── User-Agent
│   ├── 屏幕方向
│   ├── Cookie
│   └── 网络恢复刷新
├── 终端运行
│   ├── 开机启动
│   ├── 启动延迟
│   ├── 沉浸式全屏
│   ├── 屏幕常亮
│   └── 异常恢复
├── 安全设置
│   ├── 管理员密码
│   ├── 设置入口
│   └── 返回键保护
└── 系统维护
    ├── 测试网页
    ├── 重新加载
    ├── 清理缓存
    ├── 导入/导出配置
    └── 恢复默认设置
```

## 7. 技术方案

### 7.1 Android 技术栈

- Kotlin
- Android WebView
- Jetpack Compose
- Proto DataStore
- AndroidX WebKit
- Activity Result API
- BroadcastReceiver
- ConnectivityManager
- Kotlin Coroutines
- Material 3

### 7.2 当前工程结构

当前使用单一 `app` Gradle 模块和手工依赖容器，通过包划分职责：

```text
app/src/main/java/io/github/kioskrelay/
├── config/
├── data/
├── feature/
│   ├── onboarding/
│   ├── kiosk/
│   ├── settings/
│   └── diagnostics/
├── diagnostics/
├── web/
├── startup/
├── security/
└── ui/
```

主要职责：

| 包 | 职责 |
| --- | --- |
| `config` | 配置模型、默认值、版本迁移和校验 |
| `data` | Proto DataStore、品牌图片和私有文件管理 |
| `feature` | 首次配置、展示、设置和诊断页面 |
| `diagnostics` | 脱敏事件环形缓冲与导出 |
| `web` | WebView 创建、安全设置、导航和异常恢复 |
| `startup` | 开机启动、启动延迟和部署模式判断 |
| `security` | 管理密码、隐藏入口和退出保护 |
| `ui` | Compose 主题、通用组件和国际化展示 |

### 7.3 配置模型

```json
{
  "schemaVersion": 1,
  "locale": "SYSTEM",
  "onboardingCompleted": true,
  "branding": {
    "productName": "KioskRelay",
    "logoRelativePath": "logo.webp",
    "splashRelativePath": "splash.webp",
    "primaryColorArgb": 4281303277,
    "backgroundColorArgb": 4278655531,
    "loadingMessage": "",
    "offlineMessage": "",
    "errorMessage": ""
  },
  "webView": {
    "initialUrl": "https://example.com/dashboard",
    "allowedOrigins": [
      "https://example.com"
    ],
    "allowHttp": false,
    "orientation": "LANDSCAPE",
    "fullscreen": true,
    "keepScreenOn": true,
    "supportZoom": false,
    "customUserAgent": "",
    "acceptCookies": true,
    "refreshOnNetworkRecovery": true
  },
  "runtime": {
    "retryDelaysSeconds": [5, 10, 20, 40, 60, 60],
    "bootStartEnabled": true,
    "bootDelaySeconds": 10
  }
}
```

示例字段名以当前 `.kioskrelay` 导出格式为准。管理员密码摘要、随机盐值和失败计数属于本机安全数据，不进入可导出的通用配置模型。Logo 和启动页图片复制到 App 私有目录，配置中只保存 `logo.webp` 和 `splash.webp` 两个受控相对路径。

### 7.4 WebView 运行状态

展示页面使用显式状态机管理加载和恢复：

```text
Starting → Loading → Online
                ↘ Offline
                ↘ PageError → BackoffRetry
                ↘ RendererGone → RecreateWebView
                ↘ Fatal

PageError → BackoffRetry → Loading
PageError → RetryLimitReached → Fatal
SslError(MainFrame) → Cancel → Fatal
SslError(Subresource) → CancelResource
```

- 页面失败使用带上限的退避重试，避免固定间隔无限刷新。
- 开启网络恢复刷新时只触发一次受控重载；未开启时不得只凭网络回调进入 Online，
  Online 必须由主文档成功加载确认。当前 API 24 实测发现后一状态不变量尚未满足。
- 所有 SSL 错误都取消；只有主文档证书失败进入 Fatal。当前实现会将子资源证书失败
  也升级为 Fatal，API 24 指定网页已复现。
- 渲染进程退出后销毁旧 WebView 并创建新实例，不复用失效对象。
- 同一页面连续触发渲染崩溃时停止自动恢复并展示诊断入口。
- 页面级秒级重试由前台生命周期内的协程管理，不使用后台任务调度器。

## 8. 动态品牌实现

### 8.1 产品名称

产品名称保存到 DataStore，在以下位置动态展示：

- 启动页
- 加载页
- 离线页
- 错误页
- 设置页面标题
- 关于页面

### 8.2 Logo

用户通过 Android 系统文件选择器选择图片。App 将图片复制到自身私有目录，避免原文件移动或授权失效后无法访问。

建议支持：

- PNG
- JPEG
- WebP
- SVG 可作为后续能力

导入时应完成：

- 文件类型检查
- 文件大小限制
- 图片尺寸检查
- 必要的压缩处理
- 错误文件提示

### 8.3 启动页

启动页根据本地配置动态组合：

- 背景颜色或背景图片
- 产品 Logo
- 产品名称
- 加载动画
- 加载提示语

系统原生 Splash Screen 时间较短，进入主 Activity 后应继续展示自定义品牌启动层，直到 WebView 首屏加载完成。

## 9. 开机启动策略

### 9.1 标准 Android

- 注册 `BOOT_COMPLETED`。
- 收到广播后读取“开机启动”配置。
- 在系统允许的情况下，根据配置延迟进入展示页面。
- 无法从后台拉起页面时保持配置有效，并在用户下次打开 App 时恢复展示。

### 9.2 消费级设备限制

普通消费级手机和平板可能受到以下影响：

- 厂商自启动限制
- 电池优化
- 后台活动启动限制
- 用户强制停止
- 应用长期未使用后被系统限制

首次配置向导应提供对应的权限和厂商设置说明，但不能承诺所有设备均可在完全无人干预的情况下自动弹出页面。

### 9.3 专用终端

对于可控的大屏或工业设备，专用设备模式采用：

- 默认桌面 Launcher 模式
- Device Owner 模式
- Lock Task/Kiosk 模式

专用设备能力属于 `v1.0.0` 的稳定交付目标，但工程设计从首个版本开始保留部署模式边界。

## 10. WebView 安全原则

- 默认只允许 HTTPS；HTTP 需管理员显式开启。
- 主文档地址可配置，但页面跳转和重定向必须匹配已配置的精确 Origin 白名单。
- 默认阻止 `file://` 任意文件访问。
- 默认阻止 `content://` 和未知协议。
- 谨慎开放 JavaScript Interface。
- 外部应用跳转必须经过配置控制。
- SSL 证书错误默认阻止，不直接忽略。
- 下载和文件上传能力应单独配置。
- 摄像头、麦克风和定位权限应按需申请。
- 管理员密码只保存加盐摘要，不以明文持久化或输出日志。
- URL 查询参数、Cookie、令牌和表单内容不得写入普通运行日志。
- 配置导入前必须校验格式和字段。

## 11. MVP 范围

MVP 通过 `v0.1.0` 至 `v0.4.0` 分阶段完成，`v0.4.0` 作为普通模式 MVP 候选版本。MVP 包含：

- 可构建、可安装的 Android 工程和自动化检查
- 首次启动配置和本地配置迁移
- 动态产品名称、Logo、品牌颜色和启动层
- HTTPS 网页配置、安全导航和可选 HTTP 兼容模式
- WebView 全屏展示、横竖屏设置和屏幕常亮
- 网络状态提示、失败退避和渲染进程恢复
- 隐藏设置入口、管理员认证和失败频率限制
- 普通设备尽力而为的开机启动
- Android TV 遥控器基本操作
- 清理缓存、配置导入导出和恢复默认设置

MVP 暂不包含：

- Device Owner 自动配置
- 完整 Lock Task/Kiosk 管理
- 云端管理
- 多网页轮播
- 远程升级
- 远程控制
- 复杂设备管理
- Windows 客户端

## 12. 版本规划

### v0.1.0：工程与基础展示（已纳入 v0.4.0）

- Gradle 工程、Debug/Release 构建和基础 CI
- 首次启动配置
- WebView 页面
- HTTPS 网页地址配置和基础校验
- 全屏模式
- 屏幕常亮
- 横竖屏设置

### v0.2.0：稳定性与安全导航（已纳入 v0.4.0）

- 网络状态监听和恢复加载
- 页面失败退避重试
- WebView 渲染进程重建
- 精确 Origin 白名单
- 协议、SSL 错误和外部跳转控制
- 运行状态和错误页面

### v0.3.0：认证与动态品牌（已纳入 v0.4.0）

- 管理员密码摘要和失败频率限制
- 隐藏设置入口
- 产品名称
- Logo 导入
- 品牌主题
- 自定义启动页
- 自定义加载与离线文案

### v0.4.0：普通模式 MVP（已实现，API 24 核心流程已验证，完整矩阵待验收）

- 普通设备分 API 等级的尽力而为开机恢复
- Android TV 遥控器导航和 Launcher 资源
- 配置导入与导出
- 运行日志
- 页面诊断
- 清理缓存和恢复默认设置
- JVM/UI/WebView 自动化测试基础
- API 24 Debug 核心流程兼容性验证（已在 x86_64 AVD 完成）
- API 24 的 13 项仪器测试最终全量通过（已完成）
- API 24 自签名 TLS 拒绝、跨 Origin/`intent://` 拦截、配置 ZIP 和真实重启自启动
  （已完成设备端补充验证）
- 网络恢复关闭时伪 Online、设置/维护返回后沉浸式系统栏未恢复、子资源 TLS 错误
  升级为全局 Fatal（已复现，待修复）
- 签名 Release、真实硬件，以及 API 25、26、29、31、35/36 兼容性验证（待完成）

### v1.0.0：专用设备与稳定发布

- Device Owner 部署说明
- 默认 Launcher 和 Lock Task/Kiosk 模式
- 常见厂商设备验证
- 完整使用文档
- APK 发布流程
- 自动化测试和回归测试矩阵
- 开源贡献指南

### 后续版本

- 多网页定时轮播
- 远程配置
- 终端在线状态
- 远程刷新和重启
- 应用在线升级
- 多套本地配置切换
- Windows WebView2 客户端

## 13. 开源建议

- 项目名称：KioskRelay
- 仓库名称：`kiosk-relay-android`
- Android 包名：`io.github.kioskrelay`
- 开源协议：MIT License
- 默认应用名称：KioskRelay
- 默认桌面图标：KioskRelay 官方图标
- 默认语言：跟随系统，并支持简体中文和英文

## 14. 验收标准

### 14.1 普通模式 MVP

1. Debug 和 Release 版本可以通过标准 Gradle 命令构建并安装。
2. 首次启动可以完成产品名称、网页地址和方向配置；管理员密码可留空或设置为 6–64 个字符。
3. 用户可以导入并持久保存 Logo，源文件移动或授权失效后仍能显示。
4. 修改品牌配置后无需重装即可生效。
5. App 默认只加载 HTTPS；HTTP 必须由管理员显式开启。
6. 主页面及重定向只能进入配置允许的精确 Origin。
7. `file://`、`content://`、未知协议和 SSL 证书错误默认被阻止。
8. App 可以沉浸式全屏显示网页并保持屏幕常亮。
9. 网络中断时显示离线状态，恢复后只触发一次受控重载。
10. 页面加载失败后按照有上限的退避策略重试。
11. WebView 渲染进程退出后可以重建；连续崩溃不会无限循环。
12. 设置管理员密码后不以明文保存或记录，连续失败会受到频率限制；未设置时隐藏入口可直接打开设置。
13. 配置导出不包含管理员凭据，非法导入文件不会覆盖有效配置。
14. 触屏设备和 Android TV 遥控器均可进入并操作设置。
15. 普通设备开机启动失败时不会被宣称为系统故障，文档明确设备限制。
16. 清除缓存、恢复默认配置和运行诊断可正常使用。

### 14.2 专用设备 v1.0

1. 受管设备可以将 KioskRelay 设置为默认 Launcher。
2. Device Owner 可以将应用加入 Lock Task 白名单。
3. 锁定模式下普通用户无法返回桌面或进入未授权应用。
4. 管理员可以通过受保护流程退出锁定模式。
5. 设备重启后能恢复到展示页面，并在目标硬件上完成实机验证。

### 14.3 发布前目标测试要求

以下是完整发布目标，不代表当前 13 项仪器测试已经用真实网络或硬件覆盖所有条目；
当前实测范围和缺口以 14.4 为准。

- JVM 单元测试覆盖配置默认值/迁移、URL 与 Origin、HTTP 策略、退避状态机、密码摘要/锁定、诊断脱敏、图片编辑事务、配置导入导出和恶意 ZIP。
- Compose/UI 测试覆盖四步首次配置、字段错误、HTTP 确认、设置保存和恢复默认。
- WebView 仪器测试覆盖安全设置、允许/拒绝导航、重定向、SSL、离线恢复、失败上限、Cookie/缓存清理和 Renderer 恢复。
- Debug 构建可使用受 `BuildConfig.DEBUG` 保护的 `chrome://crash` 路径测试 API 26+ Renderer；Release 不提供该入口。
- 使用触屏设备与 Android TV/电视盒子分别验证管理入口、D-pad 焦点、返回键和显示安全区域。
- 发布前记录 Android 系统版本、WebView 版本、设备型号和测试结果。

### 14.4 当前验收记录（2026-07-30）

| 门禁 | 当前结果 |
| --- | --- |
| `test` | 通过，58 个 JVM 用例 |
| `lint` | 干净提交基线通过，0 error、47 warning |
| `assembleDebug` | 通过 |
| `assembleRelease` | 通过，产出未签名 Release APK；尚未在 API 24 安装 |
| `assembleDebugAndroidTest` | 通过，13 个仪器测试用例可编译为测试 APK |
| 完整构建门禁 | `ec3c6b5` 干净副本、Gradle 9.4.1 / AGP 9.2.1 下，58 个 JVM + 13 个 API 24 仪器用例全部通过，`BUILD SUCCESSFUL in 4m 52s` |
| 当前升级工具链 | Gradle 9.5.0 / AGP 9.3.1、JDK 17 Launcher / Java 21 Daemon 下，58 个 JVM + 13 个 API 24 仪器用例通过，Lint 0 error、46 warning，增量门禁 `BUILD SUCCESSFUL in 30s` |
| API 24 仪器测试 | 最终全量运行 `13/13` 通过，0 skipped、0 failed；其中部分 WebView 场景是合成回调 |
| API 24 Debug 首启与管理员 | 四步首启、HTTP 确认、DataStore 重启、返回键、触屏隐藏入口和实际 keyevent TV 序列通过；不等同 TV 硬件验收 |
| API 24 展示策略 | 首次进入的横屏、全屏、常亮通过；从设置返回或维护重载后系统栏未重新隐藏，实测不通过 |
| API 24 失败退避 | 真实拒绝连接已验证 5 秒、10 秒和手动重试归零；完整上限由自动化覆盖 |
| API 24 网络恢复，刷新开启 | 真实断网进入 Offline，恢复后受控重载并回到网页，实测通过 |
| API 24 网络恢复，刷新关闭 | 恢复后错误进入 Online 并露出 `ERR_CONNECTION_REFUSED` 原生页，实测不通过 |
| API 24 安全导航 | 自签名主文档 TLS 被取消并进入 Fatal；直接跨 Origin 和 `intent://` 主文档导航被阻止；真实 HTTP 302 链仍待测 |
| API 24 子资源 TLS | Open-Meteo HTTPS 子资源失败被正确取消，但整个已渲染大屏被升级为 Fatal，实测不通过 |
| API 24 配置与维护 | 配置 ZIP 导出、有效导入、未知 schema 拒绝、旧密码保留和诊断导出通过；Cookie/缓存清理完成性仍待专用页面断言 |
| API 24 开机启动 | 开启、延迟 0 秒执行真实系统重启后自动拉起通过；其余延迟/关闭/强停场景待测 |
| API 24 + WebView 53 | 测试页面不通过；主文档完成，但 Vite 8 客户端报 `SyntaxError: Unexpected token .` 并白屏 |
| API 24 + WebView 119 | 指定页面主体和本轮检查的 8 个业务数据请求最终返回 HTTP 200；外部天气 HTTPS 子资源失败触发全局 Fatal |
| API 25 安装与运行 | 待模拟器或 Android 7.1 真机 |
| API 26/29/31/35/36 运行矩阵 | 待设备验收 |
| Android 7 工业平板/TV 与签名 Release | 待目标硬件验收 |

当前结论：`v0.4.0` 可以标记“API 24 Debug 核心流程已在模拟器验证，API 24 仪器测试
13/13 通过”，但不能标记“Android 7 生产发布已全面验收”。发布前至少需要修复
子资源 TLS 错误升级为全局 Fatal、`refreshOnNetworkRecovery=false` 的伪 Online、
路由返回后沉浸式未恢复，以及凭据损坏失败开放风险；随后安装签名 Release，并完成
真实 302、安全专用页、目标硬件和 Android 版本矩阵。API 24 没有
`onRenderProcessGone` 平台回调，因此 Android 7 不具备 Renderer 自动重建能力。完整证据见
[严格功能与场景测试报告](./KioskRelay-严格功能与场景测试报告-20260730.md)。

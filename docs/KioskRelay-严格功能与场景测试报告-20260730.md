# KioskRelay 严格功能与场景测试报告

## 1. 验收结论

本轮按用户当前指定参数在 Android 7.0 / API 24 x86_64 AVD 上执行：

- 网页：`http://192.168.1.11:5173/`
- 管理员凭据：6 位测试密码（具体值不进入仓库文档）
- 应用：`io.github.kioskrelay.debug` / `0.4.0-debug`
- WebView Provider：Chrome `119.0.6045.193`

最终结论：

> `v0.4.0-debug` 的主要功能可以在 API 24 + WebView 119 上运行，目标网页主体可完整加载。
> 本轮获得了首启/品牌、选定安全场景、开启刷新时网络恢复、开启且 0 秒延迟的开机路径、
> 配置 ZIP 核心路径和部分维护/诊断的真实证据；同时稳定复现 3 个 Kiosk 运行缺陷。
> Release 尚未签名，Android 版本矩阵和目标工业硬件也尚未验收，因此当前不能标记为
> 生产发布通过。

发布判断：

| 判断项 | 结果 |
| --- | --- |
| API 24 Debug 安装和核心功能 | 有条件通过 |
| 指定网页在 WebView 119 上加载 | 通过 |
| 自动化构建门禁 | 通过，58 个 JVM + 13 个仪器用例 |
| API 24 开机启动开启且延迟 0 秒 | 通过 |
| Android 7 生产发布 | 不通过，存在 3 个实测阻断项和 1 个高风险审计阻断项 |
| 签名 Release、目标硬件和版本矩阵 | 待验证 |

本报告严格区分“真实运行”“自动化验证”“源码审计”和“尚未覆盖”，不会将合成回调
或源码存在等同于真实设备场景已通过。

## 2. 测试环境与源码状态

| 项目 | 值 |
| --- | --- |
| 日期 | 2026-07-30 |
| 人工测试时 HEAD | `ec3c6b5 feat(onboarding): make administrator password optional`，工作树非干净 |
| 模拟器 | `emulator-5554`，Android 7.0 / API 24，x86_64，1280 × 800 |
| 应用包 | `io.github.kioskrelay.debug` |
| 应用版本 | `0.4.0-debug`，`versionCode=400` |
| `minSdk` / `targetSdk` / `compileSdk` | 24 / 36 / 36.1 |
| WebView Provider | Chrome `119.0.6045.193` |
| 测试 URL | `http://192.168.1.11:5173/` |
| 屏幕策略 | 横屏、沉浸式全屏、保持常亮 |
| 开机策略 | 开启，延迟 0 秒 |

严格人工测试运行在用户原工作树，其中已有中文文案、测试夹具和 Gradle
9.5.0 / AGP 9.3.1 等未提交调整。为确保结果可从提交基线复现，随后在
`ec3c6b5` 的隔离干净副本中，使用 JDK 17、Gradle 9.4.1、AGP 9.2.1 重新执行了
完整自动化门禁；以下 APK 摘要和自动化数字均来自该干净基线。

测试开始前工作区已有以下非本轮测试报告改动，均被保留且没有纳入提交：

- 三个中文字符串资源；
- 一个配置测试文件；
- Gradle 版本目录、Wrapper 和 Daemon JVM 配置。

## 3. 自动化构建门禁

执行命令：

```bash
./gradlew test lint assembleDebug assembleRelease assembleDebugAndroidTest \
  connectedDebugAndroidTest --no-configuration-cache --max-workers=2
```

结果：

| 门禁 | 结果 |
| --- | --- |
| JVM 单元测试 | 58/58 通过，0 failure，0 error |
| API 24 connected 仪器测试 | 13/13 通过，0 skipped，0 failure，0 error |
| Android Lint | 0 error，47 warning |
| Debug APK | 构建通过，约 15 MiB |
| 混淆 Release APK | 构建通过，约 2.5 MiB，未签名 |
| AndroidTest APK | 构建通过，约 1.1 MiB |
| 总任务 | `BUILD SUCCESSFUL in 4m 52s` |

APK 摘要：

| 产物 | SHA-256 |
| --- | --- |
| `app-debug.apk` | `633cd093ce433169b0334304e013f2e77cf8612eb30e4022d98b0f2b5769e093` |
| `app-release-unsigned.apk` | `1f7c680bbdd047c7610e64657f65c9044b555ba994a3d9688fd01596b5270f23` |
| `app-debug-androidTest.apk` | `271af6e117a43b5919334e371b9a41e3ca54c6c97f6affb155095654d3008d0c` |

Debug APK 已确认使用 Android Debug 证书和 APK Signature Scheme v2。Release 产物未签名，
不能用本轮 Debug 安装结果替代 Release 安装、证书链和升级覆盖验证。

47 项 Lint warning 主要是可升级依赖、Compose `Modifier` 风格、可使用复数资源、
允许明文 HTTP 的已知配置和未使用资源；没有 Lint error。允许明文流量是为了支持管理员
显式开启 HTTP，运行时安全依赖 URL 和 Origin 策略，因此仍需作为安全边界持续测试。

## 4. 指定网页与 WebView 兼容性

### 4.1 目标网页

`http://192.168.1.11:5173/` 的主页面主体可以在 WebView 119 中完整渲染。通过页面
运行时和主机侧复核，主文档及下列业务请求最终均返回 HTTP 200：

- `/data/dashboard.json`
- `/api/jinan/dxs/statistics`
- `/api/jinan/pptn/shangqing`
- `/api/jinan/pptn/zhuriRain`
- `/api/jinan/pptn/zhuyuerain`
- `/api/jinan/pptn/statistics`
- `/api/jinan/dxs/data/month-trend`
- `/api/jinan/dxs/data/year-trend`

测试期间 `8071` 后端曾短暂不可用，Vite 代理出现 HTTP 502，页面部分指标显示 `--`；
服务恢复后接口返回 200。该现象属于目标网页的服务依赖可用性，不是 Android WebView
兼容性缺陷。

干净重装后的最终复核发现，页面请求
`https://api.open-meteo.com/v1/forecast?...` 时，API 24 / WebView 119 报
`SSL_UNTRUSTED (3)`。取消该不受信任请求本身符合失败关闭策略，网页也已经捕获天气
请求失败并保留主体内容；但 KioskRelay 将任意 HTTPS 子资源证书错误升级为整个页面
Fatal，遮挡已经渲染的大屏。该行为属于客户端缺陷 KR-API24-03。

### 4.2 Android 7 WebView 版本边界

同一 AVD 内旧 Android System WebView `53.0.2785.124` 能读取主文档，但不能解析 Vite 8
客户端语法，报 `SyntaxError: Unexpected token .` 并白屏。WebView 119 可以正常执行。

因此 Android 7 部署必须控制 WebView Provider 版本，不能只以“网页 HTTP 200”判断
客户端兼容。WebView 119 的结论也只适用于本次目标网页和测试状态。

## 5. 真实运行功能矩阵

### 5.1 首次配置、品牌和显示

| 场景 | 结果 | 证据/说明 |
| --- | --- | --- |
| 清除数据后进入四步首启 | 通过 | 完成欢迎、品牌、网页/显示、安全确认 |
| 简体中文流程 | 通过 | 中文字段、提示和错误信息正常 |
| 英文界面 | 未人工完整回归 | 资源与构建通过，不宣称本轮人工通过 |
| 默认品牌字段 | 通过 | 未填写项有 KioskRelay 默认值 |
| 自定义产品名和品牌色 | 通过 | 使用紫色 `#FF8C63E8`，启动层实时生效 |
| 导入 Logo | 通过 | 有效 PNG 被复制并转换到私有 `logo.webp` |
| 导入启动背景 | 通过 | 私有 `splash.webp` 为 1280 × 800 WebP |
| Logo/背景预览 | 通过 | 启动品牌层显示正常 |
| 横屏网页测试窗口 | 通过 | 预览 WebView 约 889 × 506，按可用空间缩放 |
| 竖屏网页测试窗口 | 通过 | 预览 WebView 约 288 × 506，随方向重新布局 |
| 跟随系统方向 | 未人工完整回归 | 代码/自动化覆盖，不宣称目标硬件通过 |
| 5 字符密码 | 通过：正确拒绝 | 首启和设置页均不能提交 |
| 6 字符密码下限 | 通过 | 测试密码成功设置并可验证 |
| 留空密码 | 自动化通过 | 本轮真实流程使用非空测试密码 |

私有目录检查确认品牌素材已复制到 App 私有目录。对配置和私有文件进行文本扫描，
没有发现测试密码明文。

未实际导入大于 10 MiB、像素炸弹和带错误 MIME 的图片；这些边界只能记为源码/自动化
已有保护，不能写成真实设备已通过。源码审计还发现普通图片导入未处理 EXIF 旋转。

### 5.2 URL、HTTP 和网页测试

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| HTTP 开关关闭时输入 HTTP URL | 通过：正确拒绝 | 显示明确错误 |
| 开启 HTTP 的风险确认 | 通过 | 取消保持关闭；确认后才允许加载 |
| 指定 URL 网页测试 | 通过 | 成功显示并允许保存 |
| 网页测试失败后继续保存 | 功能存在，未作为最终配置保留 | 错误提示可见 |
| URL 自动生成精确 Origin | 通过 | 为 `http://192.168.1.11:5173` |
| 修改 URL/安全策略后 WebView 重建 | 通过 | 恢复原 URL 后正常加载 |
| 不可达端口 | 通过 | `ERR_CONNECTION_REFUSED` 进入退避状态 |

### 5.3 Kiosk 展示和管理员入口

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| 横屏展示 | 通过 | 1280 × 800 横屏 |
| 沉浸式全屏冷启动 | 通过 | `mSystemUiVisibility=0x1706` |
| 屏幕常亮 | 通过 | Window 持有常亮策略 |
| 展示根页面按返回键 | 通过 | 返回被消费，未退出到桌面/设置 |
| 允许 Origin 内的 WebView 历史 | 基础行为通过 | 未构造复杂 SPA 历史全集 |
| 左上角 3 秒内连续点击 5 次 | 通过 | 打开管理员认证 |
| TV 遥控隐藏序列 | 通过 | `上 上 下 下 左 右 左 右 确认` 打开认证 |
| 真正 Android TV Launcher/D-pad 焦点 | 未完成 | 本轮在平板型 API 24 AVD 注入遥控按键 |
| Release 常驻管理员按钮 | 静态确认不存在 | Release 未签名、未安装 |
| 错误密码 | 通过 | 一次错误显示剩余 4 次 |
| 正确测试密码 | 通过 | 进入设置 |
| 展示页退出保护 | 有条件通过 | 普通模式不是 Device Owner/Lock Task |

### 5.4 设置

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| 品牌、网页与显示、运行、安全、维护五组 | 通过 | 五个分组均可进入 |
| 修改产品名后取消 | 通过 | 草稿没有写入持久配置 |
| 5 字符新密码 | 通过：正确拒绝 | 确认按钮不可用 |
| 设置新密码 | 基础验证通过 | 本轮为保留测试参数未覆盖再次改密全过程 |
| 清除已设置密码 | 当前不支持 | 只能恢复全部默认配置 |
| Activity 重建保留未保存草稿 | 未实测 | 源码审计发现使用 `remember` 的草稿丢失风险 |
| 从设置取消返回后全屏恢复 | **不通过** | 见缺陷 KR-API24-02 |

### 5.5 页面失败、断网和恢复

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| 首次连接被拒绝 | 通过 | 显示 `ERR_CONNECTION_REFUSED` |
| 第 1 次退避 | 通过 | 5 秒 |
| 第 2 次退避 | 通过 | 10 秒 |
| 后续退避 | 完整自动化 | 20/40/60/60 秒余下档位和第 6 次后 Fatal 未逐档人工取证 |
| 手动重试 | 通过 | 计数重置为第 1 次 |
| 6 次后 Fatal | 自动化通过 | 未等待完整人工时间链 |
| 真实默认网络关闭 | 通过 | 显示品牌离线层 |
| 网络恢复且开启自动刷新 | 通过 | 自动返回目标网页 |
| 仅网页服务恢复、网络未切换 | 符合当前设计 | 等计划重试或手动重试，不会立刻获知服务恢复 |
| 网络恢复但关闭自动刷新 | **不通过** | 错误页被误标成 Online，见 KR-API24-01 |
| 进程强停后重启 | 通过 | 配置、密码、素材和网页恢复 |

### 5.6 WebView 安全

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| 跨 Origin 主文档导航到 `https://example.com/` | 通过：正确拦截 | 原目标 URL 保持不变 |
| `intent://` 主文档导航 | 通过：正确拦截 | 没有拉起外部 Activity |
| 自签名 HTTPS | 通过：正确拒绝 | 进入 Fatal，显示 TLS certificate error (3) |
| HTTP 主页面的 HTTPS 子资源证书失败 | **不通过** | 子资源被正确取消，但整个已渲染大屏被错误升级为 Fatal，见 KR-API24-03 |
| SSL 错误继续访问入口 | 通过：不存在 | 测试 Debug WebView 无绕过 |
| mixed content | 静态/自动化通过 | 未用真实混合内容站点逐项抓包 |
| `file://`、`content://` | 策略测试通过 | 本轮未逐项真实导航 |
| 真实同 Origin 302 | 未覆盖 | 不能用合成 `onPageStarted` 回调代替 |
| 真实跨 Origin 302 | 未覆盖 | 直接跨域导航已通过，不等同重定向链 |
| 第三方 Cookie、上传、下载、权限请求 | 配置为禁用 | 未用专用测试页逐项验证 |

Origin 白名单限制的是主文档导航，不是所有子资源。客户页面仍可请求跨域脚本、字体、
图片和 API；它不是数据外发沙箱。

### 5.7 开机启动和生命周期

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| API 24 开机启动开启、延迟 0 秒 | 通过 | 执行真实 `adb reboot`，系统广播后自动聚焦 App |
| 开机启动关闭 | 静态/自动化覆盖 | 本轮未再次真实重启 |
| 1–60 秒启动延迟 | 静态实现 | 本轮只测 0 秒 |
| 强制停止后的开机广播 | 未覆盖 | Android/厂商可能阻止接收 |
| API 29+ 通知降级 | 未覆盖 | 仅 API 24 AVD |
| API 26+ Renderer 重建 | 不适用 | API 24–25 没有平台回调 |

普通模式自启动成功不代表所有工业设备都会成功。API 29+ 后台 Activity 限制、厂商
自启动白名单、电池优化和用户强制停止都可能改变结果。

### 5.8 配置导入导出

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| 导出 `.kioskrelay` | 通过 | 生成约 40 KiB ZIP |
| 导出配置和素材 | 通过 | 只有 `config.json`、`assets/logo.webp`、`assets/splash.webp` |
| 导出密码/Cookie/缓存/日志 | 通过：未发现 | ZIP 内容扫描未发现这些数据 |
| 导入有效配置 | 通过 | 产品名临时改为 `ImportedTest` |
| 导入后管理员密码 | 通过 | 原测试密码仍然有效 |
| 导入未知 `schemaVersion=99` | 通过：正确拒绝 | 原配置保持 KioskRelay |
| 导入未知 ZIP 条目 | 通过：正确拒绝 | 目录条目也按严格白名单拒绝 |
| 文件选择器取消 | 通过 | 返回应用且不修改配置 |
| 路径穿越、重复条目、超限 ZIP | 自动化/审计覆盖 | 未逐个在设备上导入 |
| 伪造 WebP 有效码流验证 | **存在风险** | 当前只验头部和尺寸，没有真实解码 |

### 5.9 维护和诊断

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| 重新加载 | 通过加载；全屏恢复不通过 | 见 KR-API24-02 |
| 清除 WebView 缓存 | 操作可执行 | 诊断记录成功，未从服务端独立验证命中消失 |
| 清除 Cookie | 操作可执行但确认不足 | 异步删除未等待回调 |
| 清除网页数据 | 操作可执行 | 未用专用页逐项核验 localStorage/IndexedDB |
| 诊断设备信息 | 通过 | 显示 App、Android、设备、WebView 版本 |
| 最近事件脱敏展示 | 基础通过 | 没有记录查询参数、Cookie 或表单 |
| 导出诊断 | 通过 | 文本约 430 bytes |
| 恢复默认二次确认 | 通过 | 取消后保持当前配置 |
| 真正确认恢复默认 | 未执行 | 为保留用户指定最终配置 |

## 6. 已确认缺陷

### KR-API24-01：关闭网络恢复重载时误报 Online

**严重度：高，发布阻断。**

复现步骤：

1. 关闭“网络恢复后重新加载”；
2. 将 URL 指向不可达服务，等待进入 `PageError`；
3. 关闭默认网络，进入 `Offline`；
4. 恢复网络，但不恢复目标服务或不触发新页面成功；
5. App 将状态直接改为 `Online`，隐藏品牌错误层并露出 Chromium 原生
   `Webpage not available / ERR_CONNECTION_REFUSED` 页面。

期望：只有主文档确认加载成功后才能进入 `Online`；否则应保持 `Offline`、
`PageError` 或等待手动重试。

代码位置：`app/src/main/java/io/github/kioskrelay/web/WebViewController.kt:120-128`。
`refreshOnNetworkRecovery=false` 分支没有发起加载或等待 `onPageFinished`，却直接写入
`WebRuntimeState.Online`。

证据：

- `/tmp/kiosk-strict-no-refresh-offline.png`
- `/tmp/kiosk-strict-no-refresh-false-online.png`

### KR-API24-02：从设置返回或维护重载后沉浸式系统栏不恢复

**严重度：高，Kiosk 展示发布阻断。**

复现路径 A：

1. 展示页冷启动，系统栏隐藏，`mSystemUiVisibility=0x1706`；
2. 通过隐藏入口进入设置；
3. 取消返回；
4. 系统状态栏/导航栏仍显示，值变为 `0x1700`。

复现路径 B：

1. 进入维护；
2. 执行“重新加载”；
3. 返回展示页后系统栏仍显示，WebView 顶部从 `y=24` 开始。

期望：每次重新进入 Kiosk 路由后立即重新应用方向、全屏和常亮策略。

代码位置：

- `app/src/main/java/io/github/kioskrelay/KioskRelayApp.kt:235-248`：展示策略只随
  WebView 配置/语言变化，不随路由返回触发；
- `app/src/main/java/io/github/kioskrelay/KioskRelayApp.kt:1257-1260`：维护重载弹栈时
  没有重新应用展示策略；
- `app/src/main/java/io/github/kioskrelay/MainActivity.kt:44-80`：主要依赖 Window focus
  回调，路由切换不一定产生新的 focus 事件。

证据：`/tmp/kiosk-strict-immersive-not-restored.png`。

强制停止后重新启动可以恢复全屏，但这不是可接受的日常管理流程。

### KR-API24-03：HTTPS 子资源证书失败导致整个大屏 Fatal

**严重度：高，指定网页发布阻断。**

复现步骤：

1. 使用目标主页面 `http://192.168.1.11:5173/`；
2. 干净启动或点击“立即重试”；
3. 主页面和水文数据已经渲染；
4. 页面发起 Open-Meteo HTTPS 天气 Fetch；
5. WebView 119 报 `SSL_UNTRUSTED (3)`，该 Fetch 被取消；
6. KioskRelay 随即覆盖整个页面并显示 `TLS certificate error (3)` Fatal。

Chrome DevTools 记录的失败请求是
`https://api.open-meteo.com/v1/forecast?...`，类型为 `Fetch`；页面控制台同时输出
“济南天气加载失败，保留上次成功数据”。截图可看到完整水文大屏仍在 Fatal 遮罩下方，
因此该证书错误不是主文档失败。

期望：所有证书错误仍必须 `cancel()`；主文档证书失败进入 Fatal，子资源证书失败应只
取消该资源并记录脱敏诊断，不应把已经成功的主页面切换为 Fatal。

代码位置：

- `app/src/main/java/io/github/kioskrelay/web/SecureWebViewClient.kt:91-99`：
  `onReceivedSslError` 没有区分主文档和子资源；
- `app/src/main/java/io/github/kioskrelay/web/WebViewController.kt:199-207`：
  任意 SSL 回调都会设置 `pageFailed=true` 并进入 `KioskUiState.Fatal`。

证据：

- `/tmp/kiosk-strict-subresource-tls-fatal.png`
- `/tmp/kiosk-subresource-tls-fatal.xml`
- `/tmp/kiosk-cdp-capture.mjs` 的 DevTools Network 结果

## 7. 源码审计风险

以下项目没有全部通过真实故障注入复现，因此和上面的“已确认缺陷”分开列出：

| 严重度 | 风险 | 影响 |
| --- | --- | --- |
| 高 | 管理员凭据文件损坏会被解释为“未设置密码” | 隐藏入口可能直接打开设置，应区分 `ABSENT` 与 `CORRUPT` 并失败关闭 |
| 高 | 恢复默认跨配置、密码和素材不是原子操作 | 任一步 I/O 失败可能留下混合状态或卡在启动层 |
| 中 | Cookie 清除没有等待 `removeAllCookies` 回调 | UI 和诊断可能在实际删除完成前报告成功 |
| 中 | 缓存/网页数据清理异常被吞掉 | 可能清理失败却仍记录成功 |
| 中 | ZIP WebP 只验证 RIFF 头和声明尺寸 | 损坏码流可能导入成功但无法显示 |
| 中 | 诊断脱敏正则遗漏 `access_token`、`refresh_token`、`client_secret` 等复合键 | 特定错误文本可能泄漏敏感值 |
| 中 | 设置草稿使用 `remember` | Activity 重建会丢失未保存修改 |
| 中 | 已设置的管理员密码没有单独清除入口 | 只能恢复全部默认配置 |
| 中 | 首启密码写入和配置保存不是原子操作 | 配置保存失败时密码可能已经改变 |
| 低 | 素材删除忽略 `File.delete()` 返回值 | 恢复默认后可能遗留私有文件 |
| 低 | 同毫秒相同诊断事件可能生成重复 Compose key | 诊断列表存在重复 key 风险 |
| 低 | 图片导入不处理 EXIF 方向 | 手机照片可能旋转错误 |
| 低 | 开机启动异常被吞掉且异步广播无超时 | 启动失败缺少可观测性 |

其中“凭据损坏失败开放”是发布前应优先修复的安全问题，即使本轮正常凭据流程全部通过。

## 8. 密码专项结论

- 本轮 6 位测试密码满足当前 6–64 字符长度规则；
- 使用 PBKDF2-HMAC-SHA1、16 字节随机盐、200,000 次迭代；
- 配置文件、配置导出和诊断导出中未发现明文密码；
- 正确密码通过，错误密码显示剩余次数；
- 自动化覆盖第 5 次失败后 30 秒锁定和后续级别的基础逻辑；
- 本轮没有人工等待完整 `30/60/120/240/300` 秒阶梯；
- 当前只校验长度，正式部署应使用不易猜测且不同于本轮测试值的密码；
- 锁定依赖墙上时钟，设备时间向前调整可能缩短实际锁定。

## 9. 未覆盖与不可外推范围

本轮不能据此宣称以下项目通过：

- API 25、26、29、31、35/36 的运行兼容矩阵；
- 签名 Release 的安装、证书行为、升级覆盖、回滚和长稳运行；
- 真实工业平板、Android TV/盒子的 D-pad 焦点、开机策略和厂商电源管理；
- 真实同 Origin/跨 Origin 302 重定向链；
- API 26+ Debug `chrome://crash` Renderer 重建；
- 第三方 Cookie、mixed content、上传、下载、摄像头、麦克风、定位的专用攻击页验证；
- 60 秒开机延迟、强制停止后重启、自启动关闭的真实多轮重启；
- 完整密码锁定时间阶梯和凭据损坏故障注入；
- 恢复默认“确认”路径中的中途 I/O 故障；
- Cookie、Cache Storage、localStorage、IndexedDB 清除后的逐项独立断言；
- 目标网页业务数据的准确性、业务接口 SLA 和外部天气服务质量；
- Android 7 System WebView 53 对当前 Vite 8 页面的兼容，已明确不通过。

## 10. 修复与复测优先级

发布前建议按以下顺序处理：

1. 修复 KR-API24-03，区分主文档和子资源 SSL 失败，同时保持全部证书错误失败关闭；
2. 修复 KR-API24-01，只有主文档成功回调才能进入 `Online`；
3. 修复 KR-API24-02，在每次进入 Kiosk 路由时恢复全屏、方向和常亮；
4. 将管理员凭据损坏改为失败关闭，并新增真实损坏 SharedPreferences 测试；
5. 让 Cookie/缓存/网页数据清理等待真实完成并验证结果；
6. ZIP 素材导入增加受限真实图片解码；
7. 扩展诊断复合敏感键脱敏；
8. 使用正式签名 Release 重跑 API 24；
9. 完成 API 25/26/29/31/35/36 和目标工业平板/TV 矩阵。

前 4 项修复后，至少重跑本报告第 3 节完整门禁，以及三处实测缺陷的 API 24 真实复现
步骤。

## 11. 证据索引

本轮截图和导出文件保存在本机 `/tmp`，未加入 Git：

| 类别 | 主要证据 |
| --- | --- |
| 首启和品牌 | `kiosk-strict-start.png`、`kiosk-strict-brand-assets.png`、`kiosk-strict-startup-brand.png` |
| 自适应网页测试窗口 | `kiosk-strict-webtest-success.png`、`kiosk-strict-webtest-portrait.png` |
| HTTP 策略 | `kiosk-strict-http-blocked.png`、`kiosk-strict-http-risk.png` |
| 退避和手动重试 | `kiosk-strict-retry1.png`、`kiosk-strict-retry2.png`、`kiosk-strict-manual-retry.png` |
| 断网和恢复 | `kiosk-strict-offline.png`、`kiosk-strict-network-recovered.png` |
| KR-API24-01 | `kiosk-strict-no-refresh-offline.png`、`kiosk-strict-no-refresh-false-online.png` |
| 管理员入口 | `kiosk-strict-admin-wrong.png`、`kiosk-strict-tv-sequence.png` |
| 安全拦截 | `kiosk-strict-cross-origin-blocked.png`、`kiosk-strict-intent-blocked.png`、`kiosk-strict-tls-fatal.png` |
| KR-API24-03 | `kiosk-strict-subresource-tls-fatal.png`、`kiosk-subresource-tls-fatal.xml`、`kiosk-strict-restored-final.png` |
| 开机和进程恢复 | `kiosk-strict-boot-autostart.png`、`kiosk-strict-process-restart-online.png` |
| 导入导出 | `kiosk-strict-valid-import.png`、`kiosk-strict-invalid-import.png`、`kioskrelay-0.4.0-debug.kioskrelay` |
| 维护和诊断 | `kiosk-strict-maintenance.png`、`kiosk-strict-diagnostics.png`、`kioskrelay-diagnostics.txt` |
| KR-API24-02 | `kiosk-strict-immersive-not-restored.png` |
| 初次测试结束在线状态 | `kiosk-strict-final-online.png` |

Gradle 的测试 XML、HTML 报告和 APK 位于忽略的 `app/build` 目录，不随仓库提交。

## 12. 测试结束时恢复状态

测试结束后已恢复：

- 产品名：KioskRelay；
- URL：`http://192.168.1.11:5173/`；
- HTTP：已显式允许；
- 精确 Origin：`http://192.168.1.11:5173`；
- 横屏、沉浸式全屏、保持常亮；
- 网络恢复后重新加载：开启；
- 开机启动：开启，延迟 0 秒；
- 管理员凭据：已恢复为 6 位测试密码，具体值不进入报告；
- 最终网页状态：大屏主体已经渲染，但外部天气 HTTPS 子资源证书错误触发全局 Fatal
  遮罩；保留该状态供复核，见 KR-API24-03。

为避免测试残留，临时自签名 HTTPS 服务、Chrome DevTools 端口转发和 ADB root 均已关闭。

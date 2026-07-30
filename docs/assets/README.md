# KioskRelay 视觉素材

本目录保存 KioskRelay 的品牌与应用状态素材，用于产品文档、UI 设计和 Android 客户端实现参考。

| 文件 | 用途 | 规格 |
| --- | --- | --- |
| `app-icon-master.png` | Android 应用图标母版 | 1254 × 1254，RGB |
| `logo-mark-transparent.png` | 启动页、关于页和文档 Logo | 1254 × 1254，透明背景 |
| `splash-screen.png` | Android 竖屏启动页参考 | 941 × 1672，RGB |
| `offline-state-transparent.png` | 断网状态插图 | 1254 × 1254，透明背景 |
| `load-error-transparent.png` | 网页加载失败状态插图 | 1254 × 1254，透明背景 |

## 视觉规范

- 主背景：`#071A2B`
- 品牌蓝：`#2F80ED`
- 品牌青：`#26D9E8`
- 主文字：`#FFFFFF`
- 成功状态：绿色
- 警示状态：琥珀色

## 使用说明

- `app-icon-master.png` 是设计母版；工程已提供 Adaptive Icon 的前景层、背景层和 monochrome 图层。
- 透明素材已完成背景移除，可直接放在深色或浅色界面中预览。
- 动态品牌模式下，KioskRelay 官方素材只用于首次启动和未配置状态；用户配置产品名称及 Logo 后，应优先展示用户品牌。
- `tools/generate_android_assets.py`（需 Pillow）会确定性生成 API 24 各密度 PNG 图标、320 × 180 TV Banner，以及最长边 512px 的离线/错误 WebP；不要手工编辑生成物。
- 当前母版素材由生成式图像工具制作，正式发布前仍建议进行人工矢量化、像素对齐和小尺寸辨识度检查。

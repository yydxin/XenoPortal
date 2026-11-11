# XenoPortal · 0.0.1 beta

在小米 HyperOS 上选取图片，通过系统实时通知/超级岛保存与分享。Kotlin + Jetpack Compose，Android 13+，主要适配 Android 16 / HyperOS 3。

**本次发布为手动选图测试版。自动长按抖音图片、自动识别长按目标及原视频提取尚未完成。** 系统岛曾由用户在一台 HyperOS 设备上确认可见；本 beta 的完整流程仍需真机试用。

## 下载与安装

[0.0.1-beta.1 发布页](https://github.com/yydxin/XenoPortal/releases/tag/v0.0.1-beta.1) 提供 APK、APK ZIP、完整源码、安装说明、文件大小与 SHA-256。

- [Release APK](https://raw.githubusercontent.com/yydxin/XenoPortal/main/downloads/0.0.1-beta.1/XenoPortal-0.0.1-beta.1-release.apk)
- [APK ZIP](https://raw.githubusercontent.com/yydxin/XenoPortal/main/downloads/0.0.1-beta.1/XenoPortal-0.0.1-beta.1-APK.zip)
- [完整源码 ZIP](https://raw.githubusercontent.com/yydxin/XenoPortal/main/downloads/0.0.1-beta.1/XenoPortal-0.0.1-beta.1-source.zip)
- [SHA-256](https://raw.githubusercontent.com/yydxin/XenoPortal/main/downloads/0.0.1-beta.1/SHA256SUMS.txt)

沿用包名 `com.xeno.portal`、原诊断签名，`versionCode=205`，可覆盖此前最高204的诊断版。版本名称改为0.0.1不会造成安装降级。首次安装见[安装说明](docs/INSTALL.zh-CN.md)。更换自行生成的签名将无法覆盖原签名安装。

## 试用方式

1. 首页启用 XenoPortal，在设置中授权普通通知、系统实时通知；评论取图还需连接无障碍。
2. 首页点“开始选取评论图片”，5秒内切回准备好的抖音评论列表，先关闭菜单、停止滚动，再从临时预览中点选图片。
3. 点选后请求官方系统岛。岛上“保存到相册”直接写入 `Pictures/XenoPortal`，不打开XenoPortal分享页。
4. 也可用“从系统照片选择图片”试用，无需完整相册读取权限；导入为最长边2048像素的JPEG静态图片，动态图仅首帧。点击“显示官方系统岛”后返回桌面观察。
5. 当前图片可在首页直接保存或打开全部分享目标。遇到问题在“诊断”复制/导出S0～S7与日志。

临时选图面板属于用户选图界面；选定后的通知才由系统绘制。截图/节点位置变化、坐标尺寸不匹配、菜单未关或无法确认评论上下文时会阻断，不会随意截取头像等内容。

## 功能边界

| 功能 | 当前状态 |
|---|---|
| 系统照片选择、静态JPEG缓存 | 已接入；本地测试通过，真机需试用 |
| 评论图片/表情包手动选图裁图 | 已接入；设备适配与未压暗裁图需真机验证 |
| Android16 Live Updates系统通知 | 已接入；先前设备显示已有人工反馈，OEM可另设条件 |
| 岛上直接保存、不重复写入 | 已接入；本地测试通过，真机需验收 |
| 微信、QQ及其他目标 | 按设备查询安装情况，ACTION_SEND接收能力须逐项验证 |
| 自动长按、原触点关联、原视频/GIF提取 | 未完成/未启用 |
| 自动点抖音保存、Root、Shizuku、触摸探索、OCR | 本版不提供 |

目标顺序固定：保存到相册、微信、QQ、小米互传、ChatGPT、Claude、豆包、DeepSeek；未安装的隐藏。通知首排最多展示本实现选取的前三个目标，点击通知主体打开全部目标。微信/QQ仍需用户选择聊天；小米互传与AI应用可能降级为系统分享面板，无法保证接收图片。不会宣称能直达指定好友或图片跟手指跨应用移动。

Android16优先试用系统实时通知入口；HyperOS2仍保留开发工具中的小米焦点协议探针，尚未真机验证。系统登记、canPost=true和eligible=true均不能单独证明超级岛实际可见。应用内不出现而离开应用可见，是用户此前设备的观察，原因未确认。

## 隐私与缓存

应用无INTERNET、悬浮窗和前台服务权限，不上传图片或日志。选图预览只在内存；选定/导入的JPEG存私有cache。只在用户点击保存时写相册，分享后给接收方最长10分钟URI读取租期；进程终止后下次启动清扫过期文件。保存期间避免清理正在写入的图片。关闭文字树调试后不继续采集；已经记录的私人文字须手动清除。日志导出只在用户主动操作时发生。

## 开发与验证

JDK17或21，Android SDK36，Gradle Wrapper8.13：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

签名构建见[签名说明](docs/SIGNING.md)。源码不包含密钥、密码或本机SDK路径。GitHub Actions运行Debug构建、单元测试和Lint；CI不持有发布密钥，不能把Debug产物当作签名Release。

- [当前验证记录：已编译 / 已逻辑自查 / 必须真机验证](docs/VALIDATION.md)
- [真机测试清单](docs/TESTING.md)
- [架构与模块](docs/ARCHITECTURE.md)
- [版本记录](CHANGELOG.md)
- [历史研究和限制](research/README.md)

仓库保留历史probe交付目录和研究材料，当前入口以本README和beta发布页为准。许可证：[MIT](LICENSE)。

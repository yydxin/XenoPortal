# XenoPortal · 0.0.2 beta

在小米 HyperOS 上选取图片，通过系统实时通知/超级岛保存与分享。Kotlin + Jetpack Compose，Android 13+，主要适配 Android 16 / HyperOS 3。

**本次新增 Root / LSPosed 长按实验路线：只作用于抖音主进程，真实触点命中评论图片后截图；尚未在 Root 手机验证。原视频提取尚未完成。** 系统岛曾由用户在一台 HyperOS 设备上确认可见；本 beta 的完整流程仍需真机试用。

## 下载与安装

[0.0.2-beta.1 发布页](https://github.com/yydxin/XenoPortal/releases/tag/v0.0.2-beta.1) 提供 APK、APK ZIP、完整源码、安装说明、文件大小与 SHA-256。

- [Release APK](https://raw.githubusercontent.com/yydxin/XenoPortal/main/downloads/0.0.2-beta.1/XenoPortal-0.0.2-beta.1-release.apk)
- [APK ZIP](https://raw.githubusercontent.com/yydxin/XenoPortal/main/downloads/0.0.2-beta.1/XenoPortal-0.0.2-beta.1-APK.zip)
- [完整源码 ZIP](https://raw.githubusercontent.com/yydxin/XenoPortal/main/downloads/0.0.2-beta.1/XenoPortal-0.0.2-beta.1-source.zip)
- [SHA-256](https://raw.githubusercontent.com/yydxin/XenoPortal/main/downloads/0.0.2-beta.1/SHA256SUMS.txt)

沿用包名 `com.xeno.portal`、原诊断签名，`versionCode=206`，可覆盖此前诊断版和205的beta。版本名称改为0.0.2不会造成安装降级。首次安装见[安装说明](docs/INSTALL.zh-CN.md)。更换自行生成的签名将无法覆盖原签名安装。

## Root 试用方式

1. 安装 APK，在 LSPosed 中启用 XenoPortal，作用域**只选抖音 `com.ss.android.ugc.aweme`**；强制停止并重新打开抖音。更新模块后也要重启抖音。不要求勾选系统框架、SystemUI 或米服务框架。
2. XenoPortal 设置中打开总开关、Root 长按截图，连接无障碍服务，允许普通通知与系统实时通知。
3. 切回抖音评论列表，长按一张完整可见的图片或图片表情；阈值默认350ms，可调250–500ms。原手势仍交给抖音，原菜单可能照常出现。
4. S3记录触点；若能确认唯一评论图片，S4裁图，再请求官方系统岛。岛上“保存到相册”直接写入 `Pictures/XenoPortal`，不打开应用页面。其他目标仍需选择接收位置。
5. 若只见“等待模块”，先检查作用域并重启抖音；若S4阻断，导出诊断。已经出现菜单、目标不唯一、跨屏/放大/窗口变化或消息延迟时会停止，不猜测取图。

这不是系统进程全局触摸监听。首版限定 Activity 评论窗口、主显示器、未放大屏幕、图片/表情静态帧；独立 Dialog/PopupWindow、视频原文件与GIF动画不在已实现范围。Hook 安装、触点收到、取图成功、官方岛可见是不同阶段；350ms加查树/截图延迟不保证赶在菜单前完成。**无 Root 时仍可使用手动选图与系统照片选择，不会自动启用触摸探索。**

免 Root：首页“开始选取评论图片”后5秒内切回已关闭菜单的评论列表，再手动选择裁图；也可用系统照片选择器导入一张图片。临时选图面板只是用户选择界面，不是自绘岛。新版移除了自绘岛与自动降级。

## 功能边界

| 功能 | 当前状态 |
|---|---|
| 系统照片选择、静态JPEG缓存 | 已接入；本地测试通过，真机需试用 |
| 评论图片/表情包手动选图裁图 | 已接入；设备适配与未压暗裁图需真机验证 |
| Android16 Live Updates系统通知 | 已接入；先前设备显示已有人工反馈，OEM可另设条件 |
| 岛上直接保存、不重复写入 | 已接入；本地测试通过，真机需验收 |
| 微信、QQ及其他目标 | 按设备查询安装情况，ACTION_SEND接收能力须逐项验证 |
| Root 长按触点 → 评论图片裁图 | 已接入实验路径；Root设备必须实测 |
| 原视频/GIF提取 | 未实现，只提供静态图片 |
| 自动点抖音保存、Shizuku、触摸探索、OCR | 本版不提供 |

目标顺序固定：保存到相册、微信、QQ、小米互传、ChatGPT、Claude、豆包、DeepSeek；未安装的隐藏。通知首排最多展示本实现选取的前三个目标，点击通知主体打开全部目标。微信/QQ仍需用户选择聊天；小米互传与AI应用可能降级为系统分享面板，无法保证接收图片。不会宣称能直达指定好友或图片跟手指跨应用移动。

Android16优先试用系统实时通知入口；HyperOS2仍保留开发工具中的小米焦点协议探针，尚未真机验证。系统登记、canPost=true和eligible=true均不能单独证明超级岛实际可见。应用内不出现而离开应用可见，是用户此前设备的观察，原因未确认。

## 隐私与缓存

应用无INTERNET、悬浮窗和前台服务权限，不上传图片或日志。Root模块只读取触摸并发送有限坐标/时间，不保存原始触摸流、不修改宿主事件。桥接只接受独占抖音UID、开关门控、时效及去重检查；UID验证不能区分抖音进程内不同代码。空握手URI授权仅用于包可见性，不开放文件读取。选图预览只在内存；选定/导入的JPEG存私有cache。只在用户点击保存时写相册，分享后给接收方最长10分钟URI读取租期；进程终止后下次启动清扫过期文件。保存期间避免清理正在写入的图片。关闭文字树调试后不继续采集；已经记录的私人文字须手动清除。日志导出只在用户主动操作时发生。

## 开发与验证

JDK17或21，Android SDK36，Gradle Wrapper8.13：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

签名构建见[签名说明](docs/SIGNING.md)。源码不包含密钥、密码或本机SDK路径。GitHub Actions运行Debug构建、单元测试和Lint；CI不持有发布密钥，不能把Debug产物当作签名Release。

- [当前验证记录：已编译 / 已逻辑自查 / 必须真机验证](docs/VALIDATION.md)
- [真机测试清单](docs/TESTING.md)
- [架构与模块](docs/ARCHITECTURE.md)
- [Root / 无 Root 调研、官方岛边界与来源](docs/ROOT_RESEARCH.md)
- [版本记录](CHANGELOG.md)
- [历史研究和限制](research/README.md)

仓库保留历史probe交付目录和研究材料，当前入口以本README和beta发布页为准。许可证：[MIT](LICENSE)。

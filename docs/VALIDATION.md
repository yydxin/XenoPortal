# 0.0.2-beta.1 验证记录

Root / LSPosed R1实验版，versionCode206，com.xeno.portal，minSdk33 / targetSdk36。同原独立签名，可覆盖旧beta与诊断版。

## 已编译验证

Release/Debug构建、39项本地测试通过，无跳过；Lint 0错误、25项警告。APK签名与前版证书一致。ZIP/APK CRC、包名/版本、xposed_init入口和DEX中的Hook类已核验；Xposed API只用于编译，未打包框架实现。

新增检查：单指时长/滑动/多指/取消/重复/过期规则；桥接拒绝未知或共享UID、无效坐标/时间，开关即时门控和重复保护；唯一评论目标、菜单/不完整树/无效矩形拒绝、显示器/旋转/放大一致性。沿用照片导入、手动裁图、后台保存、分享缓存、官方通知与启动回归测试。

Lint的ExportedContentProvider警告经逻辑审查：Root桥接需跨UID调用，代码对call逐次校验独占抖音UID，所有数据/文件方法不提供读取；只开放空握手路径授权以建立包可见性。其余警告主要是依赖更新、KTX建议及已有中文硬编码。测试不能替代真机IPC和框架安全边界核验。

## 已逻辑自查但未真机验证

同APK模块元数据、仅抖音主进程窗口触摸Hook、异步IPC不等待宿主UI、限定触点的A11y裁图、图片缓存后官方通知、Root开关默认关闭、时效/取消/断开处理。没有注入系统框架或SystemUI，没有全局焦点鉴权绕过，没有自绘岛降级。

仓库更新Root/无Root研究、README、安装、架构、测试清单和版本说明。GitHub CI以实际在线结果为准，旧beta成功记录不能用于新版本。

## 必须真机验证

LSPosed在目标HyperOS的注入与实际callback类；URI授权后宿主是否能解析Provider；抖音评论图片/表情节点祖先关系及坐标；截图是否未压暗；新beta官方岛、直接保存与微信/QQ接收；快速点按/滚动/普通分享不误触发及原手势不受影响。

ScreenshotResult.timestamp在AOSP是结果返回时序，不是精确像素帧时间。350ms判定+节点查询/截图不能保证菜单前成像；650ms是本实现的消息时效预算，不是抖音菜单出现时间。原视频/GIF、任意Activity独立弹窗/多屏、系统进程全局监听与SystemUI定向放行仍未实现。

此前用户确认probe.3在其手机可显示系统岛；不能当作新Root路线全链路验收。当前无连接的真实Root测试设备。

证据：reports/build.txt、TEST-*.xml、lint.txt、apk-signature.txt、apk-badging.txt、module-packaging.txt。文件大小与SHA-256见交付清单。

# XenoPortal Root 路线研究与本版边界

核对日期：2026-11-12。本文区分公开文档/源码证据、实现设计与真机验收；参考项目能运行，不代表 XenoPortal 在所有 HyperOS 版本上能运行。

## 本次采用的路线 R1

本版仅在 LSPosed 作用域中的**抖音进程**观察 `Window.Callback` 分发的触摸事件，保留原调用和返回结果，不消费、延迟或重放原始触摸。根据按下坐标、时长、移动距离及多指/取消状态判断候选长按；随后由 XenoPortal 无障碍服务用屏幕坐标匹配评论图片或表情包节点，裁剪截图并发送系统通知。节点不明确时应阻断并记录原因，不能任选一张图。

显示沿用用户已在该手机上确认可见的 **Android 16 Native Live Updates 官方系统岛**。这与小米 `miui.focus.param` 模板协议是不同接入路径；不能因为模板协议有白名单，就断言所有自签名应用均无法显示官方岛。

- 本版作用域不包含 SystemUI 或 `system_server`，没有全系统输入 Hook，也没有焦点通知白名单或签名绕过。
- 抖音自己的长按菜单仍会出现；截图是否早于遮罩、坐标是否正确，必须在目标设备验证。不能承诺新弹窗口接管本次手势的 MOVE。
- 取得的是**屏幕静态截图**。视频原文件/承载节点尚未接入；手动全屏诊断截图也只能截当前帧。动态表情/GIF/动态照片的截图不会保留动画、音轨或原文件数据。本版不等于获取原视频。
- 无 Root 时保留手动截图和系统 Photo Picker 导入；不把无障碍菜单候选冒充可靠触摸来源，不默认启用触摸探索。没有重新加入文字识别或自绘岛。

## 已文档、源码核对

### 小米正式接入与显示协议

[接入流程（pId=2132）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132)要求在架应用开通服务，提交场景和方案审核，配置签名指纹及 `com.xiaomi.xms.APP_ID`；本地客户端创建通知也在此流程内。测试设备白名单上限为 10 台、有效期 30 天。未找到个人开发者必能申请、未上架应用可正式接入或固定审批周期的保证。文中的约 7～15 天是获准上线后的灰度放量时间。

[开发指南（pId=2131）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)确认 `notification_focus_protocol` 和 `canShowFocus` 查询。`canShowFocus` 表示焦点通知开关状态，不能单独证明签名鉴权或岛渲染成功。`filterWhenNoPermission=false` 默认允许降级普通通知，`true` 则过滤不显示。模板图片通过 `miui.focus.pics`，动作通过 `miui.focus.actions` 中的 `Notification.Action`/`PendingIntent` 传递；取消使用原生通知取消接口。

[版本信息（pId=2141）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2141)区分 OS2 焦点通知与 OS3 岛，并要求选择兼容模板或分别适配。[展开态规范（pId=2142）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2142)不允许任意拼接组件；按钮组件 1 的普通/进度按钮支持 1～3 个，文字按钮仅 1 个且不能混用。没有一排八个任意应用图标的公开依据。

[官方能力页](https://dev.mi.com/xiaomihyperos/ability/XiaomiHyperIsland)描述卡片、文字、链接形式的拖拽分享；[媒体拖拽说明（pId=2162）](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2162)示例分享歌曲链接。未证实可以把任意 FileProvider 图片/视频文件从岛拖进聊天，因此本版使用点击后保存或 `ACTION_SEND` 分享，不承诺文件跟手拖拽。

### Root 显示参考项目

| 项目与核对版本 | 实际做法 | 可借鉴点与限制 |
| --- | --- | --- |
| [HyperCeiler，0830c587](https://github.com/ReChronoRain/HyperCeiler/blob/0830c5875570e66ae576b9276b0e57033cb6601a/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/systemui/statusbar/island/UnlockFocus.kt)，AGPL-3.0 | Hook `com.miui.systemui.notification.NotificationSettingsManager.canShowFocusState` / `canShowFocusStateApp`，选中包名时结果设为整数 `1`。 | 用户选定包名的限制思路可参考；方法返回类型和参数位置属于 OEM 私有实现，不能当作稳定 API。本项目未复制其代码。 |
| [HyperIsland，83aa7e27](https://github.com/1812z/HyperIsland/blob/83aa7e27325cdd73a12c6ed9a3e6a672c8c941e4/android/app/src/main/kotlin/io/github/hyperisland/xposed/hook/SystemUI/extensions/UnlockAllFocusHook.kt)，MIT | 在 SystemUI Hook `miui.systemui.notification.NotificationSettingsManager.canShowFocus/canCustomFocus` 及 `focus.SignatureChecker.checkSignatures` 的布尔方法；兼顾插件 ClassLoader。 | 项目声明适配 HyperOS 3/4、依赖 LSPosed。“全部放行”范围过大；不能直接移植为仅本包授权。 |
| [XBlocker，24ead43a](https://github.com/bileizhen/XBlocker/blob/24ead43a02a0c424c7a10c9b6aef12448e5863fd/app/src/main/java/io/github/xblocker/hook/XiaomiFocusHook.kt)，MIT | 在已知入口识别模块包名或业务 payload，仅命中时放行，未知调用保留 OEM 结果；监听插件加载。 | 可借鉴保留未知结果和记录 Hook 状态的原则。业务字段可以由其他应用填写，不能单独充当 XenoPortal 的包名/UID 身份证据。 |

以上源码中已经出现类名前缀、方法名、返回类型及插件加载位置的差异。未来若需 SystemUI 扩展，必须先采集目标 ROM/SystemUI/插件版本和方法签名，仅放行系统实参可明确归属 `com.xeno.portal` 的请求，并分别记录“Hook 安装”“本包命中”“实际可见”。不能全局返回成功，也不能在无法判明身份时放行。

HyperIsland 另有 [XMSF AuthSession 鉴权 Hook](https://github.com/1812z/HyperIsland/blob/83aa7e27325cdd73a12c6ed9a3e6a672c8c941e4/android/app/src/main/kotlin/io/github/hyperisland/xposed/hook/XMSF/UnlockFocusAuthHook.kt)。本次没有采用：尚无已核验的仅本包判定条件，而且已有该设备可用的官方 Live Updates 路径。

### 免 Root 触摸与显示近况

[NovaText PR #22](https://github.com/CashewTeam/BigBang_NovaText/pull/22)实际标题为“全屏悬浮窗实现压感大爆炸”，核对时已关闭且未合并。它不能作为已验证的透明全局长按实现依据。

NovaText 当前 [ExperimentalTouchController（76d4278a）](https://github.com/CashewTeam/BigBang_NovaText/blob/76d4278a2b6c193572cbe0725182efdf6857dd66/app/src/main/kotlin/com/smartisanos/textboom/service/ExperimentalTouchController.kt)使用 API 33 `TouchInteractionController` 和触摸探索，存在暂扣/消费事件及 `dispatchGesture` 重放短点击；当前模式包括压力、接触面积和多指点击。其许可证为 GPL-3.0，本项目未复制代码。

[Android 官方 TouchInteractionController 文档](https://developer.android.com/reference/android/accessibilityservice/TouchInteractionController)明确：启用触摸探索才可接收相关事件，委托给原输入链路后，本次交互不再向服务提供 MotionEvent。因此它不是既保持原始触摸完整直通、又持续旁观 MOVE 的通用接口。NovaText 的[触控集成说明](https://github.com/CashewTeam/BigBang_NovaText/blob/76d4278a2b6c193572cbe0725182efdf6857dd66/docs/touch-trigger-integration.md)也建议 Root/Xposed 观察输入，避免透明全屏窗口吞掉事件再回放。

另一个免 Root 线索 [Xiaomi-SuperIsland-Playground](https://github.com/HuberHaYu/Xiaomi-SuperIsland-Playground)（Apache-2.0）通过[隐藏的网络规则接口](https://github.com/HuberHaYu/Xiaomi-SuperIsland-Playground/blob/main/island-super-island/src/main/java/com/lab/island/sdk/island/XiaomiAurogonNetworkGate.java)临时阻断 XMSF UID 网络。它不是官方授权接口，影响范围超过本应用，本版未接入，不能把其 README 的能力声明当作所有 ROM 的保证。

## 逻辑自查与真机验证边界

**已逻辑自查的设计边界：**触发与显示分离；只观察指定抖音进程的输入；不改变原触摸结果；只在媒体节点和触点可关联时取图；不伪造官方鉴权成功；保留无 Root 手动入口。本文是研究记录，不替代代码审查、构建或测试报告。

**已获得的用户真机反馈：**此前版本的 Native Live Updates 官方系统岛在用户这台手机上可见。这只证明当时的显示入口，不代表新版 Root 长按流程、所有通知按钮或其他机型已通过。

**必须真机验证：**

1. LSPosed 在指定抖音版本实际加载模块，原有 `Window.Callback` 包装和替换后仍能观察到 DOWN/MOVE/UP/CANCEL。
2. 普通点击、滚动、多指及抖音自己的长按菜单不受干扰；普通分享按钮不触发自动取图或保存。
3. 评论图片和表情包的坐标、窗口、节点边界能够对应；列表滚动、窗口缩放/旋转及遮挡时拒绝过期目标。
4. 截图时序早于菜单遮罩，图片不压暗、不裁错；表情仅截静态帧的限制与实际结果一致。
5. 新捕获内容能显示于官方系统岛；保存直接落入相册而不打开 XenoPortal；微信/QQ 接收、超时清理和重复触发有效。
6. 缺权限、无障碍断开、模块未加载、无节点及通知降级均有可见诊断；不能将 `notify()` 无异常记作“岛已显示”。

本次研究没有连接手机执行这些步骤，也没有编译项目；“已编译验证”应以本次实际构建日志和交付报告为准。

## 本版编译接口与可见性

Xposed API82来自[官方Maven仓库](https://api.xposed.info/de/robv/android/xposed/api/82/api-82.pom)，Apache-2.0，仅compileOnly。Hook业务代码独立编写，没有复制GPL/AGPL实现。

Android11+包可见性会影响跨应用Provider解析。本应用在启用时给抖音授予不含文件数据的handshake URI读取权限，关闭时撤销；[官方自动可见性说明](https://developer.android.com/training/package-visibility/automatic)列出了URI授权关系。Provider仍逐次校验调用UID与开关，不提供查询或文件读取。目标HyperOS是否另有限制需验证。

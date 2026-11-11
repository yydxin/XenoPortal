# XenoPortal 阶段 0 / 1 可行性报告

核验日期：2026-11-10。范围：资料、源码和方案审查；尚未开发应用。没有收到旧版 ZIP，没有连接目标真机。本文不构成真机验收通过记录。

## 结论与阶段门槛

可以研发独立包名 `com.xeno.portal` 的诊断工具和分享工具，但目前证据不足以保证“普通未上架自签名应用、免 Root、不改变触摸体验，在抖音任意图片长按时自动提取内容并显示官方超级岛”。显示权限、触摸观察、内容提取是三个独立门槛。

建议交付顺序：诊断探针 → 真机 S0～S4 → 显示层 → 分享层。没有通过真实菜单识别和误触发反例前，不启用自动保存。T1 先作为默认关闭的实验探针，不作为默认可靠主链路。官方超级岛只作为具备权限时的显示适配器；默认准备无障碍覆盖窗口。

| 汇报分类 | 本次实际状态 |
|---|---|
| 已编译验证 | 无；未创建 Android 工程、未运行 Gradle、没有 APK |
| 已逻辑自查但未真机验证 | 已核对公开官方文档、112 页模板 PDF 中有关组件/协议、列出的源码及权限边界；形成设计和最小验证步骤 |
| 必须真机验证 | 服务连接、抖音窗口可见性、触摸事件与延迟、截图时序、媒体保存关联、上岛鉴权与渲染、按钮回调、目标应用接收及清理 |

资料缺口必须保留：没有找到可独立访问的《交互能力设计》正文，搜索仅找到开发指南对该标题的引用。不能宣称该篇已读；交互结论以开发指南、Q&A 和模板 PDF 为限。完整跨模板机器可用 schema 尚未建立；附录只给本产品所选模板的字段契约和其他组件索引，不伪称完整 JSON Schema。

## 官方资料逐篇结论

1. [业务介绍，2140](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2140)：定位为有限生命周期的服务/重要信息，需具备用户发起、实时交互或重大后果等价值。XenoPortal 的短时分享是否获准属于平台场景审核，不能靠客户端代码决定。
2. [版本信息，2141](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2141)：OS2 展示焦点通知，OS3 增加岛；接口相同但模板并非全部向下兼容。按协议版本选择模板，不通过 Android API 等级推断 HyperOS 能力。
3. [开发指南，2131](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)：支持本地原生通知及 MiPush 两种构建入口。本项目只评估本地方式，不引入上传用户内容的推送链路。`notification_focus_protocol` 与 `canShowFocus` 确实出现在官方查询接口中，并非只有社区依据。
4. 《交互能力设计》：独立正文未取得。现有证据不足以保证通用文件拖拽，不能承诺把 FileProvider 图片/视频直接从岛拖进任意第三方应用。
5. [展开态设计规范，2142](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2142)：模板固定，不能任意拼接。方案须审核。按钮数量依模板限制。
6. [模板库 PDF，官方开发指南内链接](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)：下载版本为《小米超级岛模板库20260129》，112 页，33,996,992 字节；SHA-256：`45a5233ec844b2f7cb9c38c598e4a07adf3ef8efad6d9b63846842c0f4d8cf4f`。核查模板 12、15、17 及资源/Action 字段；发现表格与样例之间有拼写、类型和必填项不一致，详见协议附录。不原样复制整本 PDF 作为本项目文档。
7. [常见 Q&A，2146](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2146)：存在平台权限配置；普通通知授权与焦点开关有关。拖拽描述指向服务信息卡片/文字/链接，列出微信、QQ、短信、浏览器，不能外推到原图/视频流或所有 AI 应用。
8. [接入流程，2132](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132)：本地构建也要开通超级岛服务；涉及在架应用、证书指纹、场景审核、调测白名单及上线验证。不能以“本地发送、不用 MiPush”推导免鉴权。

## 六个官方问题的回答

**1. 谁能发送、申请条件、系统开关？**

第三方可以通过正式流程接入，不要求应用使用小米系统签名；平台配置的是应用自己的签名指纹。流程描述企业认证并选择在架应用。因而“独立签名”和“未经平台审核”必须分开：前者并不天然被排除，后者没有正式权限保证。公开材料未明确保证个人账户、仅侧载未上架应用能够申请，须由平台答复。自签名不是技术上的反证，但证书必须与登记指纹一致。QA 的开关说明不能解释为所有未授权应用均可手动解锁。

账号与应用审核各有 1～3 工作日的说明；这不是超级岛完整接入周期承诺。场景、方案及上线验证的总时长未确认。调测设备白名单有数量、有效期约束；正式签名和 Debug 签名应分别配置。申请资格、后台实际入口及设备白名单的可用性还需要平台账户核验；本轮没有提交申请或联系任何人。

**2. `canShowFocus=false` 怎样处理？**

官方定义它为当前应用焦点权限状态。开发指南另外规定 `filterWhenNoPermission` 控制无权限时的普通通知退化/过滤：默认 false 为保留普通通知，true 为过滤。不能写成“一定抛异常”或“一定彻底没通知”。系统通知总开关、渠道屏蔽仍可影响结果。查询异常、null Bundle、字段缺失与明确返回 false 在诊断中分别记录；都不能作为可用官方岛的证据。

**3. 结构、8 个图标及预览模板？**

`miui.focus.param` 是 JSON 字符串，根含 `param_v2`。大/小岛在 `param_island` 下；展开态由所选组件字段表达，不是可自由插入 Compose 布局的容器。OS3 模板 15 使用 `iconTextInfo + actions`，普通按钮 1～3 个；OS2 选择模板 12 `chatInfo + actions`，仅使用普通按钮。模板 17 的文字按钮只支持 1～2 个。没有从已读模板找到“一排 8 个应用图标”的支持依据。

建议使用预览 + 前三个已安装目标；点击预览/卡片打开本应用“全部目标”页，完整列表保持指定固定顺序。此处是产品设计，卡片点击是否能在所选 ROM/模板保留独立入口需探针验证；若不成立，改为两个目标 + 更多按钮，并如实记录优先展示前三项的需求降级。不要挤入第四个按钮，也不把系统滑动切换其他服务解释为应用自己的目标分页。

**4. 按钮如何回调、能否用目标应用图标？**

官方示例使用 `Notification.Action` 包装 PendingIntent，放在 extras 的 `miui.focus.actions` Bundle，用 JSON 中的 action key 引用。图标可以自定义，目标应用已安装时可将 PackageManager 读取的 Drawable 转换为受控大小位图，放入资源 Bundle；实际圆形裁剪、深浅模式及显示效果必须验证。

分享按钮用 `PendingIntent.getActivity` 进入本应用 ShareActivity，记录 S6 后再执行 ACTION_SEND。不能默认用广播收到点击后直接启动分享页：[Android 12 的通知 trampoline 限制](https://developer.android.com/about/versions/12/behavior-changes-12#notification-trampolines)会影响此类链路。仅保存文件或更新内部状态可使用广播；不据此扩大 exported 组件权限。每个 session/target 使用独立 PendingIntent identity，避免 extras 被覆盖。

**5. 拖拽分享？**

本轮可确认 `shareData` 含展示图、标题、正文与分享文本。没有确认可传递任意原图 URI、ClipData 或视频文件。XenoPortal 第一版不启用官方岛拖拽文件分享，不声称图片能跟随原长按手指移动。自绘层只接受松手后开始的一次新触摸，点选目标为基本交互。

**6. 显示时长、更新、取消、持续性？**

存在非持续性配置，不能说必须启动前台服务。采用同一 notification id 更新并显式取消。`islandFirstFloat` 控制首次展开，`enableFloat` 控制更新展开；`updatable` 表示持续性。`timeout` 单位分钟，`islandTimeout` 单位秒；不能把 8 写到 timeout 来实现 8 秒。

产品设 8 秒无操作关闭，客户端显式 cancel + Android 通知 timeout 兜底；岛单独消失不能视为通知、窗口和缓存全部清理。进程死亡后仍需启动时清扫过期会话。`notify()` 无异常只证明调用没有同步抛错，S5 必须显示“已提交，是否上岛待确认”。

## 四条显示路线比较

没有目标设备与审批结果，不给伪造的成功率百分比。下表为条件判断与研发建议。

| 路线 | 可行性 / 成功条件 | 代价 | 推荐 |
|---|---|---|---|
| 1 官方开通 | 官方支持的正确路径；本项目场景及个人资格未确认；未上架侧载不符合公开常规入口描述 | 平台资质、应用/场景/设计审核、签名维护；周期不确定 | 有上架条件时长期优先，当前先做资格核验 |
| 2 系统手动开关 | 可验证开关是否影响查询值；无证据证明能替代平台授权/签名鉴权 | 最低；一次真机设置与对照通知实验 | 作为低成本探测优先执行，不当作保证可用路线 |
| 3 Root + LSPosed 模块 | 有公开 Hook 证据，版本依赖强；项目作者的兼容声明不等于本机通过 | 解锁/Root/框架维护、SystemUI 崩溃及 OTA 兼容成本 | 仅用户主动选择后评估，应用默认不依赖 |
| 4 无障碍覆盖窗口 | Android 提供该窗口类型；服务连接且厂商允许显示时有实现基础 | 依赖无障碍存活、Insets/旋转/遮挡和交互适配 | 当前独立侧载版本的默认可控显示方案 |

运行时优先顺序：官方协议/权限/通知条件满足 → 选择 OS3 岛或 OS2 焦点适配器；明确不可用或查询失败 → 覆盖窗口；服务未连接 → 明确无法显示覆盖层并提示修复。无公开可靠“实际已经渲染岛”的通用回执，不能在 notify 后自动假定成功。提供一次人工“看到了/没看到”自检和“强制自绘”选项，按 ROM/应用版本使确认失效重测。

最小特权模块设计（仅评估）：只在必要 SystemUI 进程/插件加载器 Hook；方法签名必须匹配已验证 ROM。仅放行可识别的 `com.xeno.portal` 身份，并核对安装包签名摘要；不能仅凭可伪造 business 字符串放行。拿不到明确目标包身份时保留系统原结果。避免全局 `return true`，不放行其他包，不操作抖音输入链路，不改权限数据库。该设计还不能保证已实现或已适配任何 ROM。

## 触发层关键否定与建议

### T1：TouchInteractionController 是触摸探索控制器

[Android 官方 API](https://developer.android.com/reference/android/accessibilityservice/TouchInteractionController)要求 API 33+、触摸探索能力及 flag。委托后本次交互不再发送 MotionEvent 给服务。由此推导：DOWN 立即委托后，仅凭定时器到 350ms 不能证明手指仍按着、没移动、没变成双指；会误把点击或滚动识别为长按。等 350ms 再委托则改变原触摸链路和时序，不能宣传无干扰，也不符合默认不拦截原菜单的约束。

[API 34 的 onMotionEvent](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onMotionEvent(android.view.MotionEvent))也不是普通应用的旁路监听：所订阅来源的事件不再交给系统其余部分，触摸探索开启时又有额外排他条件。本项目不把它作为“只观察、不改变触摸”的替代品。

因此先实现默认关闭的诊断实验，显示“可能改变触摸体验”；必须有快捷停止、重启默认关闭、排除设置/权限/系统/锁屏/自身窗口、旋转及 CANCEL 复位。不能默认启用保持 350ms 的输入控制，更不能用未获证明的长按结果自动保存。350ms 是候选触发阈值，截图回调有额外延迟，500ms 菜单出现时间只是用户提供的机型观察，需测时间分布。

### T2：菜单特征不等于长按来源证据

必须从所有交互窗口采集真实快照，逐个节点核对 text 与 contentDescription。评论图片长按与普通分享面板分别取样。如果它们暴露相同节点组合，不能靠这些文本保证“绝不误保存”。这种情况下自动点击保存必须关闭，改为用户明确确认本次提取；不能为了通过演示而降低门槛。

仅当经真机确认存在排他的长按菜单特征，或有可靠前序触发证据时，才允许执行自动保存。点击返回 true 只代表请求成功，须追踪菜单状态变化及新增媒体结果。节点可能不可点击、父节点承载点击或已过期；最多在当前快照内受限查找可点击父节点，不盲点坐标、不循环点击。

MediaStore：先做基线，再观察本次操作产生的新增 URI；不能简单拿“最新一张”。排除其他应用并发写入、历史文件和预写 pending 项；候选不唯一就失败提示。文件大小连续两次相等只是启发式，视频可能暂停写入，必须检查可读、非空、pending、元数据并设置超时；保持“写入完成尚未确认”状态，不伪称拥有原视频。

快速模式只裁剪有可信边界且命中触点的图片/视频节点。最小面积节点还可能是头像/按钮，需排除，不能把类名包含 Image 视作内容语义证明。找不到可靠节点时记录 NO_MEDIA_NODE，转 T2 的诊断/用户确认，不截全屏充当内容。

截图有安全窗口、硬件缓冲、频率限制与异步时序问题；记录截图请求/返回/菜单首次出现时间和坐标变换。受保护内容不能提取时明确失败，不尝试绕过。快速模式的视频只得当前帧；动态照片不能通过截图保留运动数据。

### T3：Shizuku，仅评估

[Shizuku API](https://github.com/RikkaApps/Shizuku-API)提供 shell/root 身份执行上下文，不会自动把普通应用提升成系统签名。[AOSP InputManagerService](https://android.googlesource.com/platform/frameworks/base/+/271edc361c46561ee15ee45629853619ca30aa30/services/core/java/com/android/server/input/InputManagerService.java)对 monitorGestureInput 检查 MONITOR_INPUT。需要有权限的进程持有 InputChannel/接收器并确认 shell 在该 ROM 确实获准；隐藏 API、接口变化和重启恢复都需验证。不是只在普通应用加一行反射调用。默认不实现、不依赖，也不调用 pilferPointers 抢夺触摸。

## 参考项目审查

以下只研究机制；未移植代码。源码链接指向检索时分支，后续实现前应锁定提交与逐文件许可。

| 项目与证据 | 怎么做 | 可借鉴 | 限制 |
|---|---|---|---|
| [XBlocker / XiaomiFocusHook](https://github.com/bileizhen/XBlocker/blob/main/app/src/main/java/io/github/xblocker/hook/XiaomiFocusHook.kt) 与同目录 fluid payload | SystemUI 的白名单/签名入口 Hook；识别模块包或相关通知负载 | 范围收窄、状态诊断、OS2/OS3 payload 分离 | 需 LSPosed；payload 标签不是身份认证；未在本机测试 |
| [HyperIsland / UnlockAllFocusHook](https://github.com/1812z/HyperIsland/blob/main/android/app/src/main/kotlin/io/github/hyperisland/xposed/hook/SystemUI/extensions/UnlockAllFocusHook.kt) | Hook canShowFocus、canCustomFocus、SignatureChecker，并处理插件加载器 | 证实部分 ROM 除开关还有鉴权入口；注意插件更新 | README 声明 HyperOS3/4，不能保证全部小版本；全应用放行不符合本项目最小特权目标 |
| [codup / HyperOS3_notify.md](https://github.com/peacemo/codup/blob/main/HyperOS3_notify.md) | 普通通知附加 JSON、资源 Bundle 与权限检测 | 探测和构建入口 | 属于方案说明，不是独立签名应用上岛的真机证据；部分大小岛术语不精确，优先官方定义 |
| [NovaText PR #22](https://github.com/CashewTeam/BigBang_NovaText/pull/22) | 全屏 overlay 接收触摸，移除窗口后 dispatchGesture 回放 | 识别这一错误路线并排除 | 作者明确指出滑动降级、关闭困难等问题；不能当作被动监听证据 |
| [NovaText 触控触发说明](https://github.com/CashewTeam/BigBang_NovaText/blob/smartisan-m-onestep_bigboom/docs/touch-trigger-integration.md) | 建议系统观察层采样或合法自身上下文；采集与捕获解耦 | 模块解耦、设备校准、CANCEL 复位 | 明确反对全屏接管后回放；没有证明抖音任意长按可读 |
| [NovaText ExperimentalTouchController](https://github.com/CashewTeam/BigBang_NovaText/blob/smartisan-m-onestep_bigboom/app/src/main/kotlin/com/smartisanos/textboom/service/ExperimentalTouchController.kt) | 后续主线已有 TouchInteractionController；hold/delegate/consume 状态，压力/面积/双指三指等；有短点回放 | 状态日志、移动阈值、排除包、停止入口 | 检索到的枚举没有单指 LONG_PRESS 模式；它依然控制输入，不能直接满足本项目不拦截约束；作者设备测试声明不是我们的验收 |
| [gkd A11yWindowRead](https://github.com/gkd-kit/gkd/blob/main/gkd-app/src/androidMain/kotlin/li/gkd/app/matching/query/A11yWindowRead.kt)、[NodeInfoCapture](https://github.com/gkd-kit/gkd/blob/main/gkd-app/src/androidMain/kotlin/li/gkd/app/data/NodeInfoCapture.kt) | 包装窗口读取错误，迭代遍历、节点上限、属性快照与耗时记录；服务暴露 windows | 有界遍历、保留空窗口/错误、可审查快照 | GPL-3.0 路线只借鉴，不复制；快照不能让宿主未暴露节点凭空可见 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) / Shizuku-API | 独立特权进程通过 Binder 服务提供能力 | 可选输入观察进程与 UI 解耦 | 用户需启动并授权；无线调试/重启、ROM 权限仍有条件 |
| [XGesture](https://github.com/qpst4/XGesture/blob/main/README.md)，额外参考 1 | 屏幕边缘、悬浮球等自有触摸入口 + 无障碍，增强能力有 Shizuku/Root/LSPosed 模式 | 明确区分自身手势入口与全局输入 | README 级核对，未审计整个项目；AGPL-3.0，不拷贝；不移植 OCR 功能 |
| [TextGrab](https://github.com/AhmetCanArslan/TextGrab/blob/main/README.md)，额外参考 2 | 快捷设置/助理手势截图或显式接收分享 | 原图分享接收作为手动验证入口 | 这些入口不等于应用内图片长按；仅借鉴入口设计，不加入文字功能 |
| [Cliptic](https://github.com/yniyniyni/Cliptic/blob/main/README.md)，额外参考 3 | MediaStore 观察与显式分享接收；系统截图工具栏集成为独立特权模块 | URI 缓存、用户同意清理、基础/特权能力拆开 | README 的延时等待不保证文件完成；其前台服务与剪贴板策略不适合原样照搬 |

## 下一阶段边界

先编写可在无 adb 情况下运行的诊断 APK：S0/S1 连接与事件、S2 白名单、窗口树采样、手动截图/内容预览、官方显示测试和自绘显示测试均独立按钮。使用合成测试图片验证显示，不要求先在抖音成功提取。

生产自动化逐项放行：诊断可读 → 抖音评论图 S0～S4 与普通分享反例通过 → 显示策略通过 → 微信/QQ/相册验证 → 连续触发与清理。工程搭建不等于跨过真机门槛；后续可实现验证工具，但不能把尚未通过的路径作为已验收功能发布。

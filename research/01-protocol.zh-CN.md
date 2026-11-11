# 官方通知协议核验附录

状态：文档/逻辑核查，未编译、未发送、未真机验证。来源：[开发指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)、其链接的 20260129 模板库 PDF、[接入流程](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132)。字段名为协议标识，下文是本项目的字段选择和约束，不是对整个系统协议的完整重印。

## 本地实现的外层契约

| 位置 | 类型 | 作用 |
|---|---|---|
| extras `miui.focus.param` | String | JSON 根对象，包含 `param_v2` |
| extras `miui.focus.pics` | Bundle | key 为 `miui.focus.pic_*`，value 使用 Icon；本地内容不上传 HTTPS |
| extras `miui.focus.actions` | Bundle | key 为 `miui.focus.action_*`，value 为 Notification.Action |
| Manifest `com.xiaomi.xms.APP_ID` | meta-data | 平台开通产生的 App Id；不能填伪造值宣称鉴权通过 |
| Manifest `com.xiaomi.xms.BUILD_TYPE_DEBUG` | meta-data Boolean | 与登记的测试/正式签名对应，未配默认为 false |

`miui.focus.pic_xxx` 是图片引用 key，不表示本地必须把每张资源作为裸 extras 根字段发送。MiPush 图片下载规则与客户端 Icon 传输规则不能混用。跨进程 Binder 体积必须受控；不把原图/原视频塞进通知，使用缩略图。

## `param_v2` 外层字段（官方基础表及示例）

| 字段 | 类型 | 本项目处理 |
|---|---|---|
| protocol | int | 官方例子为 1；不是读取到的 OS 协议版本 3 |
| business | String | 业务标识，须对应实际审批场景，不冒充 taxi |
| islandFirstFloat | Boolean | 首次是否展开；文档默认 true |
| enableFloat | Boolean | 更新是否展开；文档默认 false |
| timeout | int | 通知超时分钟；不拿它当秒 |
| cancel | Boolean | 文档针对 MiPush 结束；客户端直接 NotificationManager.cancel |
| updatable | Boolean | 持续性，默认 false；不等于 foreground service |
| reopen | String | reopen / close；与持续更新/取消后重发相关，条件字段拼写有歧义，需探针验证 |
| filterWhenNoPermission | Boolean | true 过滤无焦点权限时的普通退化通知，默认 false |
| orderId / sequence | String / long | MiPush 订单更新相关，本地不伪造订单 |
| param_island | Object | OS3 大小岛与岛属性 |
| extraInfo | Object | 业务额外信息；不用汽车字段伪装分享场景 |
| ticker / tickerPic / tickerPicDark | String | OS2 状态栏文本与资源引用 |
| aodTitle / aodPic | String | 息屏文案及图标引用 |
| chatInfo / iconTextInfo / actions 等 | 组件对象或数组 | 按模板选择，不能任意叠加成自定义卡片 |

其他模板存在 `baseInfo`、`highlightInfo`、`picInfo`、`progressInfo`、`multiProgressInfo`、`hintInfo`、`textButton`、`highlightInfoV3`、`coverInfo`、`bgInfo` 等。它们不是本产品必填字段。完整跨模板字段表尚未审定，尤其计时器、动画与设备专用组件不能由这个附录推导支持。

## `param_island`

| 字段 | 类型 | 约束 |
|---|---|---|
| islandProperty | int | 1 信息为主，2 操作为主；默认 1 |
| islandOrder | Boolean | 摘要隐藏期间更新是否重排，默认 false |
| islandTimeout | int | 岛超时秒，候选为 8；仍须取消底层通知 |
| dismissIsland | Boolean | 摘要态消失，不表示所有内容均清除 |
| highlightColor | String | 强调色 |
| bigIslandArea | Object | 大岛组件容器 |
| smallIslandArea | Object | 小岛组件容器 |
| shareData | Object | pic、title、content、shareContent 均为 String；不外推为文件共享 URI 契约 |

摘要态本项目选择静态组件：

| 路径 | 字段 | 类型 / 要求 |
|---|---|---|
| bigIslandArea.imageTextInfoLeft | type | int，组件 1 使用 1 |
| 同上.picInfo | type / pic | int 1 / String，引用缩略图 |
| 同上.textInfo | title / content | String，简短说明；不暴露识别文字 |
| 同上.textInfo | narrowFont / showHighlightColor | Boolean，可选 |
| bigIslandArea.picInfo | type / pic | int 1 / String，静态图片组件；与左侧图文组合的实际模板须发送验证 |
| smallIslandArea.picInfo | type / pic | int 1 / String，静态图标 |

PDF 的其他摘要组件索引：右侧 imageTextInfoRight type 2/3/6、左侧 type 5、设备充电专用 type 4、textInfo、fixedWidthDigitInfo、sameWidthDigitInfo、progressTextInfo、combinePicInfo。不用设备专用组件实现第三方分享按钮。

## 展开态候选模板的完整静态子集

OS3 模板 15：`iconTextInfo + actions`。

| 路径 | 类型 | 必填/约束 |
|---|---|---|
| iconTextInfo.animIconInfo | Object | 必填 |
| animIconInfo.type | int | 本项目只采用表格中的静态值 0 |
| animIconInfo.src | String | 必填，pics Bundle 的 key |
| animIconInfo.srcDark | String | 深色资源，可选 |
| iconTextInfo.title | String | 必填 |
| iconTextInfo.content | String | 必填 |
| iconTextInfo.subContent | String | 可选 |
| iconTextInfo.colorTitle / colorTitleDark | String | 可选颜色 |
| iconTextInfo.colorContent / colorContentDark | String | 可选颜色 |
| actions | Array<Object> | 普通图标按钮 1～3 个 |
| actions[].type | int | 本项目固定 0，普通按钮 |
| actions[].action | String | 对应 miui.focus.actions 内的 key |
| actions[].clickWithCollapse | Boolean | 点击收起；不替代应用显式取消通知 |

OS2 模板 12：`chatInfo + actions`；actions 仍仅普通按钮。

| 路径 | 类型 | 必填/约束 |
|---|---|---|
| chatInfo.picProfile | String | 必填，预览图资源 key |
| chatInfo.picProfileDark | String | 可选 |
| chatInfo.appIconPkg | String | 可选，自定义应用图标来源包 |
| chatInfo.title | String | 必填 |
| chatInfo.content | String | 本项目传静态补充文本；不使用 timerInfo 替代 |
| chatInfo.colorTitle / colorTitleDark | String | 可选 |
| chatInfo.colorContent / colorContentDark | String | 可选 |

这些组件定义支持传图标，但不保证任何 ROM 都接受从其他包取得的图标或把它按设计显示；需人工核验。未安装目标不生成 action，顺序保持相册、微信、QQ、互传、ChatGPT、Claude、豆包、DeepSeek。

### Action 两种方式

优先 Bundle 引用方式：`actions[].action` 找到 Notification.Action，内含 Icon、title 和 PendingIntent。分享用 Activity PendingIntent，唯一 requestCode/data 区分会话与目标，FLAG_IMMUTABLE，必要时 FLAG_UPDATE_CURRENT，回调验证会话未过期。

PDF 还定义内联 `actionIcon`、`actionIconDark`、`actionTitle`、`actionTitleColor`、`actionTitleColorDark`、`actionBgColor`、`actionIntentType`、`actionIntent` 等；部分组件还定义深色背景与按压色。actionIntentType 1/2/3 表示 Activity/广播/Service。不同组件表有类型差异。本项目不混用两套 Action 定义，不依赖需 exported=true 的内联广播/服务入口来绕过可见性。

卡片整体点击使用原生 contentIntent 是否在模板 15 保留为“全部目标”入口，需要独立验证；不能仅凭 Notification.Builder 提供该 API 保证厂商模板的点击区域行为。

## 文档冲突与待证问题

- 开发指南示例出现 `miui.focus.paramtextInfo`，PDF 组件表使用 `textInfo`。
- 附录示例出现 `smallIsland`，正式岛字段为 `smallIslandArea`；不能拼接两者。
- PDF iconTextInfo 表写静态 type 0，例子却给 3；先验证表格对应的静态子集，不推断动画协议。
- PDF 图文组件 3 的表格 type 3 与示例 type 2 不同。
- `actionIntentType` 在不同组件表里有 String/int 差异；本项目走 Notification.Action 引用规避，仍需实测。
- 部分示例包含缺逗号、变量名不一致或错误资源前缀；不直接粘贴编译。
- Q&A 的 payload 上限说明为 miui.focus.param 不超过 3072 字节；构建时按 UTF-8 测长，超过就失败记录，不让系统静默处理。
- 未取得独立《交互能力设计》正文，不能对所有点击区域、滑动分页、小窗及第三方任意文件拖拽作全面保证。

正式实现应把每个已验证 payload 作为小型 fixture 保存，记录设备/插件版本、模板和实际截图；语法通过与真机渲染通过分别登记。

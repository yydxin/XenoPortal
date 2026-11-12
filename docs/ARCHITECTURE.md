# 0.0.2-beta.1 架构

com.xeno.portal，minSdk33，target/compileSdk36，Kotlin/Compose；同APK兼作legacy Xposed模块，API82仅compileOnly，不打包框架实现。

## Root R1

抖音主进程 → 真实Window.Callback实现的dispatchTouchEvent只读Hook → 单指稳定长按 → 后台线程ContentResolver.call → RootBridgeProvider校验与异步排队 → 无障碍查评论节点/截图 → 唯一触点裁图 → NativeLiveUpdates官方岛 → 用户保存/分享。

- DouyinTouchHook / HoldGesture：只作用于抖音，不改变事件或宿主返回值、不回放。生命周期/焦点/多指/位移/阈值检查。Activity callback之外的独立弹窗不覆盖。
- RootBridgeProvider：只接受getPackagesForUid恰好为抖音的UID；开关、白名单、有限坐标、主屏、时间、重复及频率检查。不暴露文件/Intent执行/命令入口。不能区分同UID内模块与宿主代码。
- RootBridgeAccess：开启时授予抖音空handshake URI读取权以建立Android11+包可见性；只准该路径授权，Provider不实现读取。关闭撤销，应用启动重建。
- RootCommentCapture：只命中已知评论行内desc图片/图片表情，过滤不可见/越界/头像/菜单，限定主屏无放大，截图前后复核窗口与区域。已过时/多个目标拒绝。日志中的ScreenshotResult时间是返回时序诊断，不等于精确像素帧时间。
- 不注入system_server/SystemUI，不修改焦点签名校验；已验证可显示的Android16官方系统入口继续保留。研究理由见ROOT_RESEARCH.md。

## 公共与免 Root

MainActivity提供设置、手动入口和诊断；PortalAccessibilityService负责服务状态和有界白名单节点读取。CommentImagePicker由用户主动截取后选图，PhotoImport处理系统选择器授权URI。NativeLiveUpdates使用Android16公开持续通知提升请求；PortalDisplay保留小米焦点协议探针，移除自绘岛。

AlbumSaveReceiver用非导出广播PendingIntent处理用户保存，不打开Activity；MediaSessions负责私有JPEG、MediaStore pending写入与失败回滚、重复保护和分享租期。Sharing/ShareActivity维护固定目标和ACTION_SEND。Diagnostics保存S0～S7、最近200行、可选树与分组菜单证据。

所有路径都不自动点抖音“保存”。Root截图仅当前帧，不恢复原图/视频/GIF。主应用无网络、悬浮窗、前台服务权限；不上传图片/触摸/日志。缓存最多10分钟读取租期，进程被杀后下次启动清理。取消、替换请求和断开服务使旧任务失效，显示失败只记录原因。

## 未证实假设

当前抖音评论行id/fmu的祖先关系、callback实际实现、LSPosed在目标HyperOS版本的注入、主屏坐标一致性、菜单前成像时序、官方岛在本版本全链路显示均待设备验证。API文档和本地测试只验证可调用结构/规则，不能替代这些事实。

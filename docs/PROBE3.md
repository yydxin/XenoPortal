# Android16 官方实时通知路径

旧版构建只写miui.focus.param且未设ongoing，缺少Android16官方Live Updates请求。本版新增独立NativeLiveUpdates，通过AndroidX1.17公开接口请求提升，声明POST_PROMOTED_NOTIFICATIONS，设置标准样式、ongoing、标题与短文本，不带小米JSON、不自画、不伪造媒体/导航身份、不启动FGS。

直接查询canPostPromotedNotifications；使用SDK36已提供的ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS入口并处理不存在的Activity。POST_PROMOTED_NOTIFICATIONS在36.1才公开，本机是否认识该权限单独记录，不把旧系统未识别当作诊断失败。

FLAG_PROMOTED_ONGOING仅从系统返回的本应用活跃通知读取，不手动设置。500ms和1500ms观察登记及提升，30秒后显式取消。通知由系统绘制；Android提升不保证HyperOS岛的视觉/模板形式。

本入口验证的是用户主动发起的短时系统显示测试，起止明确。生产产品是否符合Live Updates场景规范与小米准入仍需独立评估；不能将截图中的ChatGPT身份/权限移植到自签名应用。

文档依据：
- https://developer.android.com/develop/ui/views/notifications/live-update
- https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder#setRequestPromotedOngoing(boolean)
- https://developer.android.com/reference/android/provider/Settings#ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS
- https://developer.android.com/reference/android/Manifest.permission#POST_PROMOTED_NOTIFICATIONS

保留probe.2的窗口证据修复，未改变免Root菜单验证路线。自动长按目标定位和未压暗裁图仍未实现或真机验证。

版本差异验证：当前在线指南要求colorized=false，但AOSP android16-qpr2-release的hasPromotableCharacteristics要求isColorizedRequested=true。API36框架测试实际复现了该差异。本实现先构造false，只有本机资格检查不通过时再构造true；仅在true候选确实通过同一系统方法时选用，两个都不通过则阻断。不修改已提升标志，不伪造FGS。真机日志NATIVE_BUILD记录最终eligible/colorized。源码依据：https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/app/Notification.java

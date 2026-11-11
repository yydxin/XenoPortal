# 2.0.3-probe.4 验证记录

包名com.xeno.portal，versionCode203，minSdk33 / targetSdk36。同前版诊断签名，可覆盖安装。

## 已编译验证

Release/Debug、17项本地测试通过；Lint 0错误，18项警告。签名与前版一致，APK/ZIP完整性验证通过。

新增API35回归：保存PendingIntent为非导出的广播，快速重复点击只插入一份MediaStore记录，写入字节一致，IS_PENDING最终为0，不启动Activity；失败只删除自身pending插入行，缓存可重试。同窗口菜单规则、普通分享反例、三组报告和无事件超时检查通过。API36通知构造测试确认保存action走广播。

## 已逻辑自查但未真机验证

直接保存取消旧显示超时，保存中的缓存不被普通清理删除，Toast反馈并记录S6/S7。没有后台Activity或FGS。

三组独立窗口证据、同窗口锚点匹配、限频后的补采样、缺权限/服务断开/关闭总开关的停止行为。分组采样不会点击或截图；大报告复制为摘要，完整树用分享导出。

## 必须真机验证

本版岛上保存是否不跳转、相册Pictures/XenoPortal是否可见、连续点击不重复、失败反馈。真实HyperOS后台广播与MediaStore行为不能由Robolectric代替。

用户已确认probe.3在该手机桌面显示XenoPortal系统岛，并提供截图；应用内看不到的原因未确认。本次保留相同公开Live Updates显示路径。不能推广为全部设备成功。

抖音三组真实菜单尚缺节点证据。自动长按判定、准确目标区域、未压暗裁图并上岛仍未完成/验收；普通分享面板不会触发自动保存。

证据见reports/与PROBE4.md。
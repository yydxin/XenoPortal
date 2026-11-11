# 本轮验证记录

日期：2026-11-10T12:15:41.622579+00:00。版本 2.0.0-probe.1，versionCode 200，包名 com.xeno.portal。

## 已编译验证

- 完整 JDK 21.0.13、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21，compile/targetSdk 36、minSdk 33。
- assembleDebug、assembleRelease、testDebugUnitTest、lintDebug 成功。release 使用独立诊断证书，未使用 debug 证书签 release。
- 6 项测试通过、0 失败：保守路由/自动保存禁用/会话标识规则，以及 Robolectric API 35 的页面启动、FileProvider 目录隔离/会话过期、Manifest 权限与触摸探索禁用检查。
- Robolectric 是 JVM 模拟，不能代替 Android 16 或 HyperOS 真机测试，也没有验证实际视觉效果。
- Android Lint：0 errors，14 warnings。剩余警告类别：AndroidGradlePluginVersion, GradleDependency, NewerVersionAvailable, UseKtx。版本更新与代码风格建议保留，未屏蔽整个 Lint。
- release APK 经 apksigner verify 验证，v2 签名有效，1 个签名者，RSA 3072。证书摘要详见 reports/apk-signature.txt。
- Gradle Wrapper 已生成，分发包已与官方 SHA-256 校验值比对并固定校验值。

实际运行构建任务：

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

release 签名需按 SIGNING.md 提供环境变量。云环境实际通过同版本 Gradle 发行包执行，代理与 SDK 路径为环境配置，不写入源码包。

## 已逻辑自查但未真机验证

- S0～S7 阶段状态与最近200行日志分开；窗口树限频/限制节点，导出需用户主动操作。
- 不调用 performAction 点击保存，不注册触摸控制器、不接管触摸、不启动前台服务；无 INTERNET / SYSTEM_ALERT_WINDOW 权限。
- 官方能力查询异常归入未知并降级，notify 只记已提交；8秒取消同一 notification id 和覆盖窗口。
- 覆盖窗口由无障碍服务持有并清理，关闭总开关会使等待中的显示请求失效。
- 会话 id 校验与有限 FileProvider 路径；缓存租期及进程重启清扫；云备份和设备迁移显式排除应用数据。
- 分享目标按候选包名实际查询，未安装隐藏；ACTION_SEND 无匹配接收时使用系统分享面板并提示降级。

## 必须真机验证

- HyperOS 实际服务连接与后台存活、受限制设置、窗口树是否能看到抖音真实菜单。
- 普通分享面板与长按图片的排他特征、触点与未压暗截图时序。当前没有自动长按或自动提取实现，不能算 S0～S4 长按验收通过。
- 官方超级岛/OS2焦点通知鉴权与模板渲染、通知/卡片点击、覆盖窗口可见性和清理。
- 微信、QQ、相册实际接收结果；互传与 AI 应用图片分享契约；视频与动态照片尚未接入。
- 两次连续操作、旋转/锁屏/权限撤销/服务终止、缓存和通知残留。

环境检查 adb devices 为空；没有连接设备，也无 /dev/kvm。没有真机通过项。下一步先在手机验证 S0 与两类抖音菜单快照，未通过前不启用自动保存。

## 证据

- reports/build.txt：最终成功构建日志。
- reports/lint.txt：完整 Lint 文本结果。
- reports/TEST-*.xml：6项测试的机器结果。
- reports/apk-signature.txt：APK签名验证结果。

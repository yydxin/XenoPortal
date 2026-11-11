# 2.0.2-probe.3 验证记录

包名com.xeno.portal，versionCode202，minSdk33 / targetSdk36。与前两版诊断证书相同，可覆盖安装。

## 已编译验证

Release/Debug构建、13项本地测试通过，Lint 0错误、16项警告。签名校验通过，未执行真机安装。

新增API36框架测试：ongoing、公开提升请求、标题/短文本、标准通知结构，未携带miui.focus.param，未手动设置已提升标志，hasPromotableCharacteristics通过。Manifest声明提升权限和普通通知权限，无网络/前台服务权限。API35原有11项测试仍通过。

API36实际资格检查要求colorized=true，区别于目前在线指南；应用先尝试false，只有本机方法拒绝且true候选通过才切换。不会靠假设或改写已提升标志通过检查。见PROBE3.md源码引用。

## 已逻辑自查但未真机验证

直接查询canPostPromotedNotifications，提供系统设置入口与缺失回退；系统返回的通知在500/1500ms检查FLAG_PROMOTED_ONGOING。系统通知独立测试不使用自绘、不依赖无障碍服务、不启动FGS；用户发起，30秒后结束取消。取消与旧焦点通知、自绘窗口互斥。

保留免Root窗口采样、独立菜单快照、空结果保留旧快照、S5查询与发送分离及S6来源记录。没有新增自动保存或触摸探索。

## 必须真机验证

本手机是否支持公开实时通知权限、允许自签名XenoPortal提升、HyperOS是否映射为系统超级岛。canPost=true、eligible=true和active登记都不能独立证明上岛；读取已提升标志也要对照实际视觉。

ChatGPT截图证明系统岛可显示其任务，不能证明其具体协议或XenoPortal获得同等准入。小米官方平台鉴权仍是独立问题。

自动长按评论图片/表情包、定位实际目标区域、未压暗裁图并上岛尚未完成/验收。继续采集长按图片、表情包和普通分享面板的窗口对照；来源或区域不明时不自动保存、不随机裁图。

证据：reports/build.txt、reports/lint.txt、reports/TEST-*.xml、reports/apk-signature.txt。

# 2.0.1-probe.2 验证记录

包名 com.xeno.portal，versionCode 201，minSdk33 / targetSdk36。与上版诊断证书相同，可覆盖安装。细节见 PROBE2.md。

## 已编译验证

- assembleRelease、assembleDebug、testDebugUnitTest、lintDebug 成功，11项本地测试通过；Lint 0错误，14项警告。
- 新增回归检查：空采样不覆盖成功快照、菜单候选另行保留、复制报告包含窗口快照、能力查询不覆盖S5、包名为空时的前台白名单检查。
- Robolectric API35检查静态兼容官方payload可提交到自己的通知列表，500ms登记检查和取消可执行。不能视为HyperOS渲染通过。
- Release签名复核通过，证书与probe.1一致；未执行真机安装。

## 已逻辑自查但未真机验证

- 全窗口、active root和事件source的有界读取；节点含图片候选与bounds，空结果保留旧有效快照。
- 事件触发采样支持包名为空或系统界面派发的相关窗口变化，仍只读取白名单窗口文字。
- S5能力查询与提交状态分开；S6携带来源。官方测试5秒延时、30秒观察、自身通知登记检查与人工形态确认。
- 模板12使用静态chatInfo与普通actions，协议仅按文档构造，没有伪造平台App Id。
- 不调用自动保存节点，不请求触摸探索或Shizuku，不随机裁图；原触点未知时保持未完成状态。

## 必须真机验证

- 用户此前日志能证明服务连接、手动截图和notify调用，但官方显示答复是不确定；未取得真实抖音菜单快照。
- 本修订在HyperOS上的窗口读取、长按图片/表情菜单和普通分享面板差异。
- 官方通知实际出现的位置及形态：活跃通知不等于超级岛；平台签名/App Id准入仍未知。
- 自动长按判定、目标区域确定、未压暗裁图和自动上岛尚未实现/验收。菜单证据不足时不能声明该链路可用。

证据：reports/build.txt、reports/lint.txt、reports/TEST-*.xml、reports/apk-signature.txt。

# 0.0.1-beta.1 架构与边界

com.xeno.portal，minSdk33，target/compileSdk36，Kotlin/Compose。无网络、悬浮窗或前台服务权限；无Root/Shizuku/触摸探索。

| 模块 | 职责 |
|---|---|
| MainActivity | 首页取图/保存/分享、设置、诊断、独立开发工具 |
| PhotoImport | 系统照片选择URI，最多25MiB读取，软件解码至最长边2048，JPEG静态帧缓存 |
| PortalAccessibilityService | 服务状态、全窗口有界读取、限频与补采样、用户主动截图；不点击第三方节点 |
| CommentImagePicker | 已知评论上下文与可见图片候选，前后位置/旋转/尺寸检查，先截图后手动选择 |
| NativeLiveUpdates | Android16公开提升请求，查询权限和系统返回标志，单通知更新/30秒取消 |
| PortalDisplay | 历史小米焦点协议探针、可选自绘测试、统一取消 |
| AlbumSaveReceiver | 非导出不可变PendingIntent，用户点击后直接保存，不启动Activity |
| MediaSessions | 私有JPEG会话、分享租期、保存并发保护、MediaStore pending写入及失败清理 |
| Sharing / ShareActivity | 固定目标、查询安装、包限定ACTION_SEND、FileProvider授权与系统面板降级 |
| Diagnostics / DouyinMenuTrials | 最近200行日志、S0～S7、全受限窗口树、三组独立证据、导出与大报告摘要 |

照片导入不依赖无障碍；评论截图依赖服务和抖音真实节点。两条路径都由用户主动操作，当前不实现自动长按。临时选图窗口只是选择界面，不冒充官方岛。系统是否提升、HyperOS是否显示、接收方是否成功接收，分别记录并以真机为准。

截图、图像解码、缓存写入、分享租期与取消保持分离。只有选中或导入图片才写私有缓存，只有用户点保存才写相册。未共享会话到期清理，已共享会话保留最长10分钟供读取；进程被杀时不能保证销毁回调，下次启动清理过期文件。FileProvider仅暴露sessions和diagnostics子目录。

当前已知评论行id/fmu上下文来自设备日志，图片祖先关系仍需真机验证；不满足则阻断，不泛化为所有ImageView。节点bounds不是原触点。自动长按、自动第三方保存、外部MediaStore原文件关联、视频提取和官方文件拖拽仍未启用。

package com.xeno.portal

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider

class MainActivity : ComponentActivity() {
    var revision by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        PortalDisplay.channel(this)
        setContent { PortalTheme { PortalScreen(this, revision) } }
    }
    override fun onResume() {
        super.onResume(); revision++
        val connected = PortalAccessibilityService.active != null
        Diagnostics.stage(0, if (connected) "成功" else "阻断", if (connected) "当前服务实例连接可用" else "当前进程无连接实例；检查无障碍开关/受限制的设置/后台限制，具体原因待确认")
        MediaSessions.cleanup(this)
        PortalDisplay.query(this)
        NativeLiveUpdates.refresh(this)
    }
    fun openSettings(intent: Intent) {
        try { startActivity(intent) } catch (e: Exception) { Diagnostics.log("SETTINGS", "${e.javaClass.simpleName}:${e.message}") }
    }
}

@Composable
fun PortalTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary=Color(0xff76d4ca)) else lightColorScheme(primary=Color(0xff006b61)), content = content)
}

@Composable
fun PreviewImage(session: MediaSession) {
    val bitmap = remember(session.id) {
        runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(session.file.path, options)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, session.label, Modifier.fillMaxWidth().heightIn(max=280.dp))
    Text(session.label + " · " + session.file.length() + " bytes · image/jpeg", style=MaterialTheme.typography.bodySmall)
}

@Composable
private fun PortalScreen(activity: MainActivity, revision: Int) {
    var tab by remember { mutableIntStateOf(0) }
    var enabled by remember(revision) { mutableStateOf(Preferences.enabled(activity)) }
    var trees by remember(revision) { mutableStateOf(Preferences.treeEnabled(activity)) }
    var overlay by remember(revision) { mutableStateOf(Preferences.forceOverlay(activity)) }
    var whitelist by remember { mutableStateOf(Preferences.whitelistText(activity)) }
    var threshold by remember { mutableFloatStateOf(Preferences.threshold(activity).toFloat()) }
    var treeWarning by remember { mutableStateOf(false) }
    var exportWarning by remember { mutableStateOf(false) }

    var installed by remember(revision) { mutableStateOf(Sharing.installed(activity)) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        Diagnostics.log("PERMISSION", result.entries.joinToString { "${it.key}=${it.value}" }); activity.revision++
    }
    fun granted(p: String) = activity.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Column(Modifier.padding(horizontal=20.dp, vertical=12.dp)) {
                Text("XenoPortal", style=MaterialTheme.typography.headlineLarge)
                Text("诊断版 2.0.3 · 直接保存与抖音分组采样", style=MaterialTheme.typography.bodyMedium)
            }
            PrimaryTabRow(selectedTabIndex=tab) {
                listOf("设置", "验证工具", "诊断").forEachIndexed { index, label -> Tab(selected=tab==index, onClick={tab=index}, text={Text(label)}) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> {
                        Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("连接与显示", style=MaterialTheme.typography.titleLarge)
                            Text(if (PortalAccessibilityService.active != null) "无障碍服务：已连接" else "无障碍服务：未连接")
                            Text(ProbePolicy.displayRoute(PortalDisplay.probe.protocol,PortalDisplay.probe.permission,PortalDisplay.probe.notifications,overlay))
                            Text(PortalDisplay.probe.detail, style=MaterialTheme.typography.bodySmall)
                            Text("当前窗口：${PortalDisplay.visible}")
                            Text("最近请求：${PortalDisplay.lastSubmission}")
                            Text("系统观察：${PortalDisplay.lastObservation}")
                            Text(PortalDisplay.visualConfirmation)
                            Text(NativeLiveUpdates.capability.detail,style=MaterialTheme.typography.bodySmall)
                            Text(NativeLiveUpdates.status)
                            Text(NativeLiveUpdates.observation)
                        } }
                        Toggle("总开关", "开启事件白名单诊断与主动测试；不会自动保存", enabled) {
                            enabled=it; Preferences.set(activity,"enabled",it)
                            if (!it) { DouyinMenuTrials.finish("总开关关闭"); PortalActions.cancel(); PortalDisplay.close(activity); Preferences.set(activity,"tree",false); trees=false; Diagnostics.updateTree("总开关关闭，已清除文字树预览") }
                        }
                        Toggle("强制使用自绘岛", "官方权限查询失败时也会自动降级；不需要悬浮窗权限", overlay) { overlay=it; Preferences.set(activity,"overlay",it) }
                        Text("权限向导", style=MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick={activity.openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}, Modifier.fillMaxWidth()) { Text("打开无障碍设置") }
                        Text("侧载安装后如系统禁止启用：应用信息 → 右上角菜单 → 允许受限制的设置；是否出现该项取决于系统。应用无法可靠判断具体拦截原因。")
                        Button(onClick={permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))}, Modifier.fillMaxWidth()) { Text("通知权限：${if(granted(Manifest.permission.POST_NOTIFICATIONS)) "已授权" else "点击申请"}") }
                        OutlinedButton(onClick={activity.openSettings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,PortalDisplay.CHANNEL))}, Modifier.fillMaxWidth()) { Text("检查通知渠道 / 焦点通知开关") }
                        OutlinedButton(onClick={activity.openSettings(NativeLiveUpdates.settingsIntent(activity))},Modifier.fillMaxWidth()) { Text("打开系统实时通知权限") }
                        Text("Android16独立的实时通知权限不同于普通通知开关。系统没有对应入口时会打开普通通知设置，并在日志注明。")
                        val images = granted(Manifest.permission.READ_MEDIA_IMAGES); val video = granted(Manifest.permission.READ_MEDIA_VIDEO)
                        Text("媒体完整读取：图片=$images，视频=$video。仅所选照片授权不等于完整读取。诊断截图与保存本应用图片不需要相册读取权限。岛上“保存到相册”直接写入 Pictures/XenoPortal，不打开应用页面。")
                        OutlinedButton(onClick={permissions.launch(buildList { add(Manifest.permission.READ_MEDIA_IMAGES); add(Manifest.permission.READ_MEDIA_VIDEO); if(Build.VERSION.SDK_INT>=34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) }.toTypedArray())}, Modifier.fillMaxWidth()) { Text("可选：申请媒体读取供后续原图实验") }
                        OutlinedButton(onClick={activity.openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}")))}, Modifier.fillMaxWidth()) { Text("应用信息 / 自启动 / 后台与省电设置") }
                        Text("HyperOS 中按需要手动检查自启动、后台弹出界面与电池无限制；入口随系统版本变化。服务被终止时，设置页与 S0 会提示未连接。")
                        HorizontalDivider()
                        Text("取图模式：本版为诊断手动模式", style=MaterialTheme.typography.titleLarge)
                        Text("快速模式（触摸触发 + 节点裁剪）：未启用。屏幕分辨率，视频只能取当前帧。\n原图模式（自动点保存 + MediaStore 关联）：未启用。会产生相册副本；须先通过普通分享不误保存测试。\n本版不申请触摸探索，不回放或接管手势。")
                        Text("未来触发阈值：${threshold.toInt()} ms（仅保存配置，本版不启用）")
                        Slider(value=threshold,onValueChange={threshold=it}, valueRange=250f..500f, steps=4,onValueChangeFinished={Preferences.threshold(activity,threshold.toInt())})
                        OutlinedTextField(value=whitelist,onValueChange={whitelist=it},label={Text("事件白名单（每行一个包名）")},modifier=Modifier.fillMaxWidth(),minLines=4)
                        Button(onClick={Preferences.whitelist(activity,whitelist); Diagnostics.log("CONFIG","已保存白名单，共${Preferences.whitelist(activity).size}项")}) { Text("保存白名单") }
                        Text("分享目标固定顺序", style=MaterialTheme.typography.titleLarge)
                        Sharing.targets.forEach { target -> Text("${Sharing.targets.indexOf(target)+1}. ${target.name} · ${if (target in installed) "已安装/本地功能" else "未安装或设备不可见，分享时隐藏"}") }
                        Text("已安装不代表支持图片/视频 ACTION_SEND。微信/QQ 需要用户选择聊天；互传和 AI 接收能力待真机验证。")
                    }
                    1 -> {
                        Text("独立验证显示与分享", style=MaterialTheme.typography.titleLarge)
                        Text("先使用合成图测试岛和分享，避免把显示失败误判成触发失败。所有截图和文件仅留在本机缓存。")
                        Button(onClick={MediaSessions.sample(activity)}, Modifier.fillMaxWidth()) { Text("生成本地合成测试图片") }
                        OutlinedButton(onClick={PortalActions.captureAfterDelay(activity)}, enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text("5秒后截取全屏诊断图") }
                        Text("点击后切到目标应用；这是用户主动的全屏截图，不是自动长按取图，也不等于原图。不要截取不希望保留的敏感界面。")
                        MediaSessions.current?.let { session ->
                            PreviewImage(session)
                            Button(onClick={PortalActions.nativeAfterDelay(activity)},enabled=enabled && Build.VERSION.SDK_INT>=36,modifier=Modifier.fillMaxWidth()) { Text("5秒后请求系统岛（不用自绘）") }
                            Text("直接使用Android16持续通知/Live Updates，不携带小米JSON；返回桌面观察30秒。系统是否映射到你截图中的岛需真机确认。")
                            OutlinedButton(onClick={activity.openSettings(NativeLiveUpdates.settingsIntent(activity))},Modifier.fillMaxWidth()) { Text("检查系统实时通知权限") }
                            Text(NativeLiveUpdates.status)
                            Text(NativeLiveUpdates.observation,style=MaterialTheme.typography.bodySmall)
                            Button(onClick={PortalDisplay.show(activity)}, enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text("原有显示路径（小米焦点协议 / 自绘）") }
                            OutlinedButton(onClick={PortalDisplay.show(activity,officialOnly=true)},enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text("单独发送官方探针（观察是否退化）") }
                            OutlinedButton(onClick={PortalActions.officialAfterDelay(activity)},enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text("5秒后测试官方超级岛（先返回桌面）") }
                            Text("延时官方测试显示30秒，并在发送500ms后检查系统是否登记通知。其他显示测试8秒收起。没有登记平台 App Id，仍可能被系统鉴权拒绝；不会把普通通知当作上岛成功。")
                            Button(onClick={PortalDisplay.open(activity,session,"more")}, Modifier.fillMaxWidth()) { Text("打开全部分享目标（验证 S6 / S7）") }
                        }
                        HorizontalDivider()
                        Text("抖音长按接入 · 分组采样",style=MaterialTheme.typography.titleLarge)
                        Text("先在诊断页开启窗口文字树。每组60秒，切到抖音打开对应菜单并停留约2秒；回到本页点结束。三组一起复制，避免日志被下一组覆盖。此步骤只记录菜单与图片节点，不自动保存或截图。")
                        DouyinMenuTrials.Case.entries.forEach { case ->
                            OutlinedButton(onClick={DouyinMenuTrials.start(activity,case)},enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text("采集：${case.title}") }
                        }
                        Text(DouyinMenuTrials.status)
                        DouyinMenuTrials.Case.entries.forEach { case ->
                            val r=DouyinMenuTrials.results[case]
                            Text("${case.title}：${if(r==null) "未采集" else "${r.samples}次，同窗口候选=${r.candidate}"}",style=MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick={DouyinMenuTrials.finish()},enabled=DouyinMenuTrials.active!=null,modifier=Modifier.fillMaxWidth()) { Text("结束本组并保留证据") }
                        Text(DouyinMenuTrials.comparison(),style=MaterialTheme.typography.bodySmall)
                        Text("请按实际看到的形态确认本次官方测试：")
                        OutlinedButton(onClick={PortalDisplay.confirmVisual("人工确认：看到了官方超级岛")},Modifier.fillMaxWidth()) { Text("看到了官方超级岛") }
                        OutlinedButton(onClick={PortalDisplay.confirmVisual("人工确认：仅看到普通通知，没有官方超级岛")},Modifier.fillMaxWidth()) { Text("仅看到普通通知") }
                        OutlinedButton(onClick={PortalDisplay.confirmVisual("人工确认：没有看到通知或超级岛")},Modifier.fillMaxWidth()) { Text("什么都没出现") }
                        Text(PortalDisplay.visualConfirmation)
                        OutlinedButton(onClick={PortalDisplay.query(activity)},Modifier.fillMaxWidth()) { Text("重新检测官方能力") }
                        OutlinedButton(onClick={PortalActions.cancel(); PortalDisplay.close(activity)},Modifier.fillMaxWidth()) { Text("立即停止测试并收起") }
                        Text("分享缓存租期10分钟，进程存活时清理，进程终止后下次打开清扫；其他未分享显示会话超时清除。对方没有通用完成回执，启动分享不表示已收到。")
                        OutlinedButton(onClick={PortalDisplay.close(activity); MediaSessions.cleanup(activity,all=true);Diagnostics.log("CACHE","用户清除全部缓存；未完成的接收方读取可能失败")},Modifier.fillMaxWidth()) { Text("立即清除全部媒体缓存") }
                    }
                    2 -> {
                        Text("定位停在哪个阶段", style=MaterialTheme.typography.titleLarge)
                        val names = listOf("服务连接","任意事件","白名单","长按 / 菜单候选","获取内容","显示提交","按钮回调","分享启动")
                        (0..7).forEach { index ->
                            val stage=Diagnostics.stages.getValue(index)
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("S$index ${names[index]} · ${stage.state}",style=MaterialTheme.typography.titleMedium)
                                Text(stage.detail); Text(stage.at,style=MaterialTheme.typography.bodySmall)
                            } }
                        }
                        Toggle("调试：记录窗口文字树", "可能包含聊天内容；仅采样白名单窗口，普通事件1.5秒限频、关键窗口变化最快250ms，最多1000节点。关闭后不再采集，历史日志需手动清除。",trees) {
                            if(it) treeWarning=true else {DouyinMenuTrials.finish("窗口调试关闭");trees=false;Preferences.set(activity,"tree",false);Diagnostics.updateTree("文字树采样已关闭")}
                        }
                        OutlinedButton(onClick={PortalAccessibilityService.active?.sampleWindows() ?: Diagnostics.stage(0,"阻断","无障碍未连接")},Modifier.fillMaxWidth()) { Text("立即采样所有窗口（需开启调试）") }
                        Text("菜单只记录候选，不会点击“保存到相册”。请在开启调试后进入抖音，分别采集长按图片/表情包与普通分享面板；切回本页会保留最近成功的白名单窗口快照。验证工具中的三组采样会分别保留，复制日志含三组完整结果。")
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick={try {
                                val text=Diagnostics.clipboardReport();(activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("XenoPortal",text))
                            } catch(e:Exception) { Diagnostics.exception(3,"复制报告失败，请分享导出",e) }}) { Text("复制日志") }
                            OutlinedButton(onClick={exportWarning=true}) { Text("分享导出") }
                            OutlinedButton(onClick={Diagnostics.clear()}) { Text("清除") }
                        }
                        Text("最近200行日志（历史进程日志不代表当前服务在线）")
                        SelectionContainer { Text(Diagnostics.lines.joinToString("\n"),style=MaterialTheme.typography.bodySmall) }
                        Text("最近采样：${Diagnostics.treeAttempt}",style=MaterialTheme.typography.bodySmall)
                        Text("最近成功读取的白名单窗口快照",style=MaterialTheme.typography.titleMedium)
                        SelectionContainer { Text(Diagnostics.tree,style=MaterialTheme.typography.bodySmall) }
                        Text("最近菜单候选快照（切回本页后仍保留）",style=MaterialTheme.typography.titleMedium)
                        SelectionContainer { Text(Diagnostics.menuTree,style=MaterialTheme.typography.bodySmall) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if(treeWarning) AlertDialog(onDismissRequest={treeWarning=false}, title={Text("启用窗口文字树采样")}, text={Text("白名单窗口可能包含私人文字；日志仅保存在本机。开启总开关后才会采样。导出仅在你主动点击时发生。")},confirmButton={TextButton(onClick={trees=true;Preferences.set(activity,"tree",true);treeWarning=false}){Text("开启采样")}},dismissButton={TextButton(onClick={treeWarning=false}){Text("取消")}})
    if(exportWarning) AlertDialog(onDismissRequest={exportWarning=false},title={Text("分享诊断日志")},text={Text("导出包含设备版本、事件、阶段状态及已采集的窗口文字。请选择你信任的接收位置。")},confirmButton={TextButton(onClick={
        exportWarning=false
        try {
            val f=Diagnostics.export(activity)
            val uri=FileProvider.getUriForFile(activity,"${activity.packageName}.files",f)
            activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newUri(activity.contentResolver,"诊断日志",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"导出诊断日志"))
        }catch(e:Exception){Diagnostics.log("EXPORT","${e.javaClass.simpleName}:${e.message}")}
    }){Text("选择导出位置")}},dismissButton={TextButton(onClick={exportWarning=false}){Text("取消")}})
}

@Composable
private fun Toggle(title: String, detail: String, value: Boolean, change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(detail,style=MaterialTheme.typography.bodySmall)}
        Switch(checked=value,onCheckedChange=change)
    }
}

object PortalActions {
    private var pending: Runnable? = null
    fun cancel() { pending?.let(Diagnostics.main::removeCallbacks); pending=null }
    fun nativeAfterDelay(context: android.content.Context) {
        cancel()
        val app=context.applicationContext
        if(!Preferences.enabled(app) || MediaSessions.current==null) {
            Diagnostics.stage(5,"阻断","先打开总开关并生成新的测试图片");return
        }
        Diagnostics.stage(5,"进行中","5秒后请求Android16系统实时通知；请返回桌面，观察30秒；不会使用自绘岛")
        pending=Runnable {pending=null;NativeLiveUpdates.show(app)}.also {Diagnostics.main.postDelayed(it,5000)}
    }
    fun officialAfterDelay(context: android.content.Context) {
        cancel()
        if (!Preferences.enabled(context) || MediaSessions.current == null) {
            Diagnostics.stage(5,"阻断","先打开总开关并生成测试图片"); return
        }
        val app=context.applicationContext
        Diagnostics.stage(5,"进行中","5秒后发送官方通知；请按Home返回桌面；显示30秒供观察")
        pending=Runnable {
            pending=null
            PortalDisplay.show(app,officialOnly=true,observationSeconds=30)
        }.also { Diagnostics.main.postDelayed(it,5000) }
    }
    fun captureAfterDelay(c: android.content.Context) {
        cancel()
        if (PortalAccessibilityService.active==null) {Diagnostics.stage(4,"阻断","先启用无障碍并确认 S0 已连接");return}
        Diagnostics.stage(4,"进行中","5秒后主动截取全屏；请切换到待检查界面")
        pending=Runnable {pending=null;PortalAccessibilityService.active?.captureForPreview() ?: Diagnostics.stage(4,"阻断","截图时服务已断开")}.also {Diagnostics.main.postDelayed(it,5000)}
    }
}

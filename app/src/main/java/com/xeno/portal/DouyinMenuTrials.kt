package com.xeno.portal

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class MenuWindow(val packageName:String, val windowId:Int, val labels:Set<String>, val mediaNodes:List<String>) {
    val candidate get() = ProbePolicy.menuCandidate(labels)
}

/** Explicit, time-bounded evidence collection. It never clicks, captures or shares media. */
object DouyinMenuTrials {
    enum class Case(val title:String) { IMAGE("评论图片"), STICKER("评论表情包"), SHARE("普通分享面板") }
    data class Result(val samples:Int, val candidate:Boolean, val snapshot:String, val before:String,val panel:String="")
    var results by mutableStateOf<Map<Case,Result>>(emptyMap())
        private set
    var active by mutableStateOf<Case?>(null)
        private set
    var status by mutableStateOf("尚未开始抖音分组采样")
        private set
    private var deadline=0L
    private var timeout:Runnable?=null
    private var previous=""
    private var previousAt=0L
    fun start(c:Context,case:Case) {
        if (!Preferences.enabled(c) || !Preferences.treeEnabled(c) || PortalAccessibilityService.active==null) {
            status="请先开启总开关、窗口文字树调试，并连接无障碍服务"
            Diagnostics.stage(3,"阻断",status);return
        }
        finish("切换采样组")
        results=results-case
        active=case;previous="";previousAt=0L
        deadline=SystemClock.elapsedRealtime()+60_000L
        status="正在采集${case.title}：60秒内切到抖音并打开对应菜单，停留约2秒；不自动保存"
        Diagnostics.stage(3,"采样中",status)
        timeout=Runnable { finish("60秒采样到期") }.also { Diagnostics.main.postDelayed(it,60_000L) }
    }
    fun observe(windows:List<MenuWindow>,snapshot:String,now:Long=SystemClock.elapsedRealtime()) {
        val case=active?:return
        if (now>=deadline) { finish("采样到期");return }
        val douyin=windows.filter{it.packageName=="com.ss.android.ugc.aweme"}
        if(douyin.isEmpty()) return
        val candidate=douyin.any{it.candidate}
        val old=results[case]
        val panel=when {
            candidate -> "评论图片菜单候选"
            douyin.any { "添加到表情" in it.labels } -> "表情菜单候选"
            douyin.any { "复制链接" in it.labels && it.labels.any { label -> label in setOf("微信好友","朋友圈","分享到","分享给") } } -> "普通分享菜单候选（待审查）"
            else -> ""
        }
        // Avoid replacing a visible panel with ordinary content after returning to this app.
        val before=if(panel.isNotBlank() && now-previousAt in 0..2000) previous else "无2秒内前置快照；不能推断原触点"
        val summary="case=${case.title} samples=${(old?.samples?:0)+1} sameWindowCandidate=$candidate panel=${panel.ifBlank { "仅内容，非菜单证据" }} windows=${douyin.size} mediaNodes=${douyin.sumOf{it.mediaNodes.size}}；节点不等于长按目标\n"
        results=results+(case to Result((old?.samples?:0)+1,candidate,(summary+snapshot).take(100000),before.take(100000),panel))
        if(panel.isNotBlank()) {
            Diagnostics.stage(3,"候选", "${case.title}：$panel；坐标未知，仅保留证据，不自动截图/保存")
            finish("捕获$panel")
        } else {
            previous=snapshot.take(100000);previousAt=now
            status="${case.title}已读取${(old?.samples?:0)+1}次，尚无同窗口菜单候选；打开菜单后可点“结束本组”保留对照"
        }
    }
    fun finish(reason:String="用户结束本组") {
        timeout?.let(Diagnostics.main::removeCallbacks);timeout=null
        val case=active?:return
        active=null
        val r=results[case]
        status="${case.title}：$reason；采样=${r?.samples?:0}，候选=${r?.candidate?:false}。${comparison()}"
        Diagnostics.log("DOUYIN_TRIAL",status)
        Diagnostics.stage(3,if(r?.panel?.isNotBlank()==true) "待验证" else "证据不足",status)
        previous="";previousAt=0L
    }
    fun comparison():String {
        val normal=results[Case.SHARE]
        if(normal?.candidate==true) return "普通分享也命中：该菜单规则不具排他性，禁止自动触发"
        if(normal==null || results[Case.IMAGE]==null || results[Case.STICKER]==null) return "三组证据尚未齐全"
        if(normal.panel.isBlank()) return "普通分享仅记录到内容页，未取得分享面板；不能视为排他性对照通过"
        if(results[Case.IMAGE]?.panel.isNullOrBlank() || results[Case.STICKER]?.panel.isNullOrBlank()) return "图片/表情菜单证据不足；未启用自动化"
        return "三组面板候选已记录，仍需人工审查；未启用自动化"
    }
    fun report(maxSnapshotChars:Int=Int.MAX_VALUE):String = "抖音分组采样\n$status\n${comparison()}\n"+Case.entries.joinToString("\n\n") { case ->
        val r=results[case]
        "[${case.title}]\n"+(if(r==null) "未采集" else "前置快照（不是原触点证据）\n${r.before.take(maxSnapshotChars)}\n面板快照\n${r.snapshot.take(maxSnapshotChars)}")
    }
    fun clear() { finish("清除记录");results=emptyMap();status="已清除抖音分组采样" }
}

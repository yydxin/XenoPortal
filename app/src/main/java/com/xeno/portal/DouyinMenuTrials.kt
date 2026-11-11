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
    data class Result(val samples:Int, val candidate:Boolean, val snapshot:String, val before:String)
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
        // Avoid replacing a visible panel with ordinary content after returning to this app.
        val before=if(candidate && now-previousAt in 0..2000) previous else "无2秒内前置快照；不能推断原触点"
        val summary="case=${case.title} samples=${(old?.samples?:0)+1} sameWindowCandidate=$candidate windows=${douyin.size} mediaNodes=${douyin.sumOf{it.mediaNodes.size}}；节点不等于长按目标\n"
        results=results+(case to Result((old?.samples?:0)+1,candidate,(summary+snapshot).take(100000),before.take(100000)))
        if(candidate) {
            Diagnostics.stage(3,"候选", "${case.title}：同一抖音窗口含“分享评论给”和“保存到相册”；坐标未知，仅保留证据，不自动截图/保存")
            finish("捕获同窗口菜单候选")
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
        previous="";previousAt=0L
    }
    fun comparison():String {
        val normal=results[Case.SHARE]
        if(normal?.candidate==true) return "普通分享也命中：该菜单规则不具排他性，禁止自动触发"
        if(normal==null || results[Case.IMAGE]==null || results[Case.STICKER]==null) return "三组证据尚未齐全"
        return "三组已记录，需人工审查真实菜单与图片区域；未启用自动化"
    }
    fun report(maxSnapshotChars:Int=Int.MAX_VALUE):String = "抖音分组采样\n$status\n${comparison()}\n"+Case.entries.joinToString("\n\n") { case ->
        val r=results[case]
        "[${case.title}]\n"+(if(r==null) "未采集" else "前置快照（不是原触点证据）\n${r.before.take(maxSnapshotChars)}\n面板快照\n${r.snapshot.take(maxSnapshotChars)}")
    }
    fun clear() { finish("清除记录");results=emptyMap();status="已清除抖音分组采样" }
}

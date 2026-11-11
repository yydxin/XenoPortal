package com.xeno.portal

/** Pure decision rules; menu text alone never authorizes an automatic save. */
object ProbePolicy {
    fun menuCandidate(labels: Set<String>): Boolean =
        labels.contains("分享评论给") && labels.contains("保存到相册")
    fun automaticSaveAllowed(@Suppress("UNUSED_PARAMETER") labels: Set<String>) = false
    fun validSessionId(id: String) = id.matches(Regex("[a-f0-9]{32}"))
    fun displayRoute(protocol: Int, permission: Boolean?, notifications: Boolean, forceOverlay: Boolean): String = when {
        forceOverlay -> "自绘岛：用户强制选择"
        !notifications -> "自绘岛：通知总开关或渠道未授权"
        permission == null -> "自绘岛：焦点权限查询失败或未知"
        !permission -> "自绘岛：canShowFocus=false"
        protocol == 2 -> "官方焦点通知候选：OS2；实际显示待人工确认"
        protocol >= 3 -> "官方超级岛候选：OS3+；签名鉴权及实际显示待人工确认"
        else -> "自绘岛：未检出兼容协议"
    }
}

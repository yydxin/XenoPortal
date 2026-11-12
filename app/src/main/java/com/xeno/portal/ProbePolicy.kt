package com.xeno.portal

/** Pure decision rules; menu text alone never authorizes an automatic save. */
object ProbePolicy {
    fun menuCandidate(labels: Set<String>): Boolean =
        labels.contains("分享评论给") && labels.contains("保存到相册")
    fun shouldInspectEvent(eventPackage: String, foregroundPackage: String, whitelist: Set<String>): Boolean =
        eventPackage in whitelist || ((eventPackage.isBlank() || eventPackage == "com.android.systemui") && foregroundPackage in whitelist)
    fun automaticSaveAllowed(@Suppress("UNUSED_PARAMETER") labels: Set<String>) = false
    fun validSessionId(id: String) = id.matches(Regex("[a-f0-9]{32}"))
    fun displayRoute(protocol: Int, permission: Boolean?, notifications: Boolean): String = when {
        !notifications -> "官方显示阻断：通知总开关或渠道未授权"
        permission == null -> "小米焦点权限查询失败或未知；可独立验证 Android16 实时通知"
        !permission -> "小米焦点通知 canShowFocus=false；可独立验证 Android16 实时通知"
        protocol == 2 -> "官方焦点通知候选：OS2；实际显示待人工确认"
        protocol >= 3 -> "官方超级岛候选：OS3+；签名鉴权及实际显示待人工确认"
        else -> "未检出兼容小米协议；仅提供官方显示，不启用自绘岛"
    }
}

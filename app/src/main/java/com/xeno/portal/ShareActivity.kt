package com.xeno.portal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("session").orEmpty()
        val selected = intent.getStringExtra("target")
        val source = intent.getStringExtra("source") ?: "unknown"
        val session = MediaSessions.find(this, id)
        PortalDisplay.close(this)
        Diagnostics.stage(6, if (session == null) "阻断" else "成功", "Activity 回调 source=$source session=$id target=$selected；${if (session == null) "文件过期/不存在" else "会话有效"}")
        setContent {
            PortalTheme {
                var busy by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("分享图片", style = MaterialTheme.typography.headlineMedium)
                        Text("微信/QQ 通常进入选择聊天页，不能直达指定好友。AI 应用和互传的接收能力需要逐项验证。")
                        if (session == null) Text("会话已过期或文件不存在，请返回重新生成测试内容。")
                        else {
                            PreviewImage(session)
                            val targets = Sharing.installed(this@ShareActivity).let { list -> if (selected == null || selected == "more") list else list.filter { it.id == selected } }
                            targets.forEach { target ->
                                Button(enabled = !busy, onClick = {
                                    busy = true
                                    if (target.id == "album") MediaSessions.saveToAlbum(this@ShareActivity, session) { ok ->
                                        busy = false
                                        if (ok) { PortalDisplay.close(this@ShareActivity); MediaSessions.discardIfUnshared(this@ShareActivity, id); finish() }
                                    } else {
                                        val ok = Sharing.send(this@ShareActivity, session, target)
                                        busy = false
                                        if (ok) { PortalDisplay.close(this@ShareActivity); finish() }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text(target.name) }
                            }
                            if (targets.isEmpty()) Text("目标已卸载，请返回选择其他目标。")
                        }
                        OutlinedButton(onClick = { finish() }) { Text("返回") }
                    }
                }
            }
        }
    }
}

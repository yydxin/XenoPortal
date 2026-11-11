package com.xeno.portal

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class PortalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(filesDir, "last-crash.txt").writeText(error.stackTraceToString().take(12000)) }
            previous?.uncaughtException(thread, error)
        }
        MediaSessions.cleanup(this)
    }
}

data class Stage(val state: String = "未运行", val detail: String = "尚无结果", val at: String = "—")

object Diagnostics {
    val main = Handler(Looper.getMainLooper())
    val io = Executors.newSingleThreadExecutor()
    private lateinit var journal: File
    var lines by mutableStateOf<List<String>>(emptyList())
        private set
    var stages by mutableStateOf((0..7).associateWith { Stage() })
        private set
    var tree by mutableStateOf("尚未采样；调试文字树默认关闭")
        private set
    var menuTree by mutableStateOf("尚未捕获菜单候选快照")
        private set
    var treeAttempt by mutableStateOf("尚未尝试窗口采样")
        private set
    private fun time() = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
    fun init(context: Context) {
        journal = File(context.filesDir, "diagnostics.txt")
        val old = runCatching { journal.readLines().takeLast(200) }.getOrDefault(emptyList())
        lines = old + "${time()} PROCESS 新进程启动；旧日志仅供参考，阶段状态已重置"
        File(context.filesDir, "last-crash.txt").takeIf { it.exists() }?.let {
            log("CRASH", runCatching { it.readText() }.getOrDefault("读取失败")); it.delete()
        }
    }
    fun log(tag: String, message: String) {
        val entry = "${time()} +${SystemClock.elapsedRealtime()} $tag ${message.replace('\n', ' ').take(2200)}"
        main.post {
            lines = (lines + entry).takeLast(200)
            val snapshot = lines.joinToString("\n")
            io.execute { runCatching { journal.writeText(snapshot) } }
        }
    }
    fun batch(tag: String, messages: List<String>) {
        val entries = messages.map { "${time()} +${SystemClock.elapsedRealtime()} $tag ${it.replace('\n', ' ').take(2200)}" }
        main.post {
            lines = (lines + entries).takeLast(200)
            val snapshot = lines.joinToString("\n")
            io.execute { runCatching { journal.writeText(snapshot) } }
        }
    }
    fun stage(index: Int, state: String, detail: String, record: Boolean = true) {
        main.post { stages = stages + (index to Stage(state, detail.take(1200), time())) }
        if (record) log("S$index/$state", detail)
    }
    fun exception(index: Int, operation: String, error: Throwable) =
        stage(index, "失败", "$operation ${error.javaClass.simpleName}: ${error.message}")
    fun updateTree(value: String) { main.post { tree = value } }
    fun windowScan(attempt: String, snapshot: String, visited: Int, menuCandidate: Boolean = false) {
        main.post {
            treeAttempt = attempt
            if (visited > 0) tree = snapshot
            if (menuCandidate) menuTree = snapshot
        }
    }
    fun textReport(): String = stages.entries.joinToString("\n") { "S${it.key} ${it.value}" } +
        "\n\n" + lines.joinToString("\n") +
        "\n\n最近窗口采样尝试\n" + treeAttempt +
        "\n\n最近成功读取的白名单窗口快照（以快照时间为准）\n" + tree +
        "\n\n最近菜单候选快照（不能证明长按来源）\n" + menuTree
    fun clear() {
        main.post {
            lines = emptyList(); tree = "已清除窗口树"; treeAttempt = "已清除采样结果"; menuTree = "已清除菜单候选快照"; stages = (0..7).associateWith { Stage() }
            io.execute { runCatching { journal.writeText("") } }
        }
    }
    fun export(context: Context): File {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "xeno-diagnostics.txt").apply {
            writeText("XenoPortal 2.0.2-probe.3\n${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n${android.os.Build.DISPLAY} API ${android.os.Build.VERSION.SDK_INT}\n\n" +
                textReport())
        }
    }
}

object Preferences {
    private fun prefs(c: Context) = c.getSharedPreferences("portal", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun treeEnabled(c: Context) = prefs(c).getBoolean("tree", false)
    fun forceOverlay(c: Context) = prefs(c).getBoolean("overlay", false)
    fun set(c: Context, key: String, value: Boolean) { prefs(c).edit().putBoolean(key, value).apply() }
    fun whitelistText(c: Context) = prefs(c).getString("whitelist", "com.ss.android.ugc.aweme\ncom.xingin.xhs\ncom.sina.weibo\ncom.tencent.mm")!!
    fun whitelist(c: Context) = whitelistText(c).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun whitelist(c: Context, text: String) { prefs(c).edit().putString("whitelist", text).apply() }
    fun threshold(c: Context) = prefs(c).getInt("threshold", 350)
    fun threshold(c: Context, value: Int) { prefs(c).edit().putInt("threshold", value.coerceIn(250, 500)).apply() }
}

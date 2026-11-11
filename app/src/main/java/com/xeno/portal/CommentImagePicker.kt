package com.xeno.portal

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import java.util.ArrayDeque

/** Explicit manual capture; never inferred from a long press or a menu candidate. */
class CommentImagePicker(private val service:PortalAccessibilityService) {
    data class Target(val window:Int,val id:String,val desc:String,val bounds:Rect)
    @Volatile private var generation=0
    private var expiration:Runnable?=null
    private var visible=false
    fun cancel() {
        generation++
        expiration?.let(Diagnostics.main::removeCallbacks);expiration=null
        if(visible) service.hideOverlayWindow()
        visible=false
    }
    private fun targets():List<Target> {
        val found=mutableListOf<Target>()
        var menu=false
        var visited=0
        var complete=true
        var roots=0;var rows=0;var imageDescriptions=0
        val windows=service.windows
        for(w in windows) {
            val root=w.root?:continue
            if(root.packageName?.toString()!="com.ss.android.ugc.aweme") continue
            roots++
            val eligible=w.isActive || w.isFocused
            val queue=ArrayDeque<Triple<AccessibilityNodeInfo,Boolean,Int>>()
            queue.add(Triple(root,false,0))
            while(queue.isNotEmpty() && visited<1000) {
                val (node,parentComment,depth)=queue.removeFirst();visited++
                val id=node.viewIdResourceName.orEmpty()
                if(id=="com.ss.android.ugc.aweme:id/fmu") rows++
                val inComment=parentComment || id=="com.ss.android.ugc.aweme:id/fmu"
                val text=node.text?.toString()?.trim().orEmpty()
                val desc=node.contentDescription?.toString()?.trim().orEmpty()
                if(desc in setOf("图片","图片表情")) imageDescriptions++
                if(node.isVisibleToUser && (text in MENU_LABELS || desc in MENU_LABELS)) menu=true
                val rect=Rect();node.getBoundsInScreen(rect)
                if(eligible && CommentImageRules.selectable(desc,inComment,node.isVisibleToUser,rect)) {
                    found.add(Target(w.id,id,desc,Rect(rect)))
                }
                if(node.childCount>100 || (depth>=30 && node.childCount>0)) complete=false
                if(depth<30) for(i in 0 until node.childCount.coerceAtMost(100)) {
                    if(queue.size<1000) node.getChild(i)?.let{queue.add(Triple(it,inComment,depth+1))} else complete=false
                }
            }
            if(queue.isNotEmpty()) complete=false

        }
        Diagnostics.log("COMMENT_SCAN","roots=$roots nodes=$visited commentRows=$rows imageDescriptions=$imageDescriptions candidates=${found.size} menu=$menu complete=$complete；候选仍需用户选择")
        check(!menu) { "请先手动关闭抖音长按/分享/添加表情菜单，再取图；不截取菜单遮罩" }
        check(found.isNotEmpty()) { "没有可确认的可见评论图片；不随机截取。节点结构或窗口状态需验证" }
        check(complete && visited<1000) { "节点读取达到上限，无法完整排除菜单；本次停止" }
        val unique=found.distinctBy{it.window to it.bounds}.sortedWith(compareBy<Target>{it.bounds.top}.thenBy{it.bounds.left})
        check(unique.map{it.window}.distinct().size==1) { "多个候选窗口，无法确认活动评论窗口" }
        return unique.take(6)
    }
    fun capture() {
        cancel()
        if(!Preferences.enabled(service)) {Diagnostics.stage(4,"阻断","选图截图：总开关关闭");return}
        PortalDisplay.close(service)
        val ticket=generation
        try {
            val expected=targets()
            val display=service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val rotation=display.rotation
            Diagnostics.stage(4,"进行中","用户主动选图：读取到${expected.size}个可见评论图片候选；先截图，后显示选图面板")
            service.takeScreenshot(Display.DEFAULT_DISPLAY,service.mainExecutor,object:AccessibilityService.TakeScreenshotCallback {
                override fun onFailure(errorCode:Int) {
                    if(ticket==generation) Diagnostics.stage(4,"失败","选图截图 takeScreenshot error=$errorCode")
                }
                override fun onSuccess(result:AccessibilityService.ScreenshotResult) {
                    val buffer=result.hardwareBuffer
                    var frame:Bitmap?=null
                    try {
                        if(ticket!=generation || !Preferences.enabled(service)) return
                        check(display.rotation==rotation && targets()==expected) { "截图期间图片位置/窗口变化，请停止滚动后重试" }
                        val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)?:error("无法读取截图")
                        frame=try {hardware.copy(Bitmap.Config.ARGB_8888,false)?:error("无法复制截图")} finally {hardware.recycle()}
                        val image=checkNotNull(frame)
                        val metrics=service.resources.displayMetrics
                        check(image.width==metrics.widthPixels && image.height==metrics.heightPixels) { "截图与节点坐标尺寸不一致：${image.width}x${image.height} / ${metrics.widthPixels}x${metrics.heightPixels}；不猜测缩放" }
                        check(expected.all {CommentImageRules.inside(it.bounds,image.width,image.height)}) { "图片区域超出截图，拒绝裁图" }
                        val crops=expected.map { t ->
                            CommentImageRules.crop(image,t.bounds)
                        }
                        show(ticket,expected,crops)
                    } catch(e:Exception) {if(ticket==generation) Diagnostics.exception(4,"评论选图截图",e)}
                    finally {frame?.recycle();buffer.close()}
                }
            })
        } catch(e:Exception) {Diagnostics.exception(4,"读取评论图片候选",e)}
    }
    private fun show(ticket:Int,targets:List<Target>,crops:List<Bitmap>) {
        if(ticket!=generation) return
        val density=service.resources.displayMetrics.density
        fun dp(n:Int)=(n*density).toInt()
        val panel=LinearLayout(service).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12));setBackgroundColor(android.graphics.Color.rgb(24,31,38))
        }
        panel.addView(TextView(service).apply {text="临时选图面板 · 点选要分享的图片\n冻结截图，仅当前帧；20秒后丢弃。不是自动长按。";setTextColor(android.graphics.Color.WHITE)})
        val row=LinearLayout(service)
        crops.forEachIndexed { index,bitmap ->
            val tile=LinearLayout(service).apply {orientation=LinearLayout.VERTICAL}
            tile.addView(ImageView(service).apply {setImageBitmap(bitmap);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(108),dp(108)))
            tile.addView(Button(service).apply {text="选择 ${index+1}";setOnClickListener {
                if(ticket!=generation) return@setOnClickListener
                cancel()
                val selection=generation
                Diagnostics.stage(3,"手动选择","用户点选评论${targets[index].desc} bounds=${targets[index].bounds}；不是长按原触点")
                Diagnostics.io.execute {
                    if(selection!=generation) return@execute
                    try {
                        val session=MediaSessions.create(service,bitmap,"评论${targets[index].desc} · 手动选图截图")
                        Diagnostics.main.post {
                            if(selection!=generation || !Preferences.enabled(service)) {MediaSessions.discardIfUnshared(service,session.id);return@post}
                            Diagnostics.stage(4,"成功","用户选定区域裁图 ${bitmap.width}x${bitmap.height} ${session.file.length()} bytes image/jpeg；压暗与否请检查；非自动长按验收")
                            if(Build.VERSION.SDK_INT>=36) NativeLiveUpdates.show(service) else PortalDisplay.show(service,officialOnly=true)
                        }
                    } catch(e:Exception) {Diagnostics.exception(4,"保存选定裁图",e)}
                }
            }})
            row.addView(tile)
        }
        panel.addView(HorizontalScrollView(service).apply {addView(row)})
        panel.addView(Button(service).apply {text="取消，不保存";setOnClickListener {cancel();Diagnostics.stage(4,"已取消","用户取消选图；未写入图片缓存")}})
        val params=WindowManager.LayoutParams(service.resources.displayMetrics.widthPixels-dp(24),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;y=dp(60)
        }
        service.attachOverlayWindow(panel,params);visible=true
        Diagnostics.stage(4,"待选择","已裁剪${crops.size}个内存预览；等待用户选择，不自动推断目标，不写入相册")
        expiration=Runnable {if(ticket==generation){cancel();Diagnostics.stage(4,"已超时","选图20秒无操作，丢弃内存预览")}}.also {Diagnostics.main.postDelayed(it,20_000)}
    }
    companion object {val MENU_LABELS=setOf("分享评论给","保存到相册","识别图片","添加到表情")}
}

object CommentImageRules {
    fun crop(source:Bitmap,r:Rect):Bitmap {
        require(inside(r,source.width,source.height)) { "裁图区域超出截图" }
        val image=Bitmap.createBitmap(source,r.left,r.top,r.width(),r.height())
        return if(image===source) image.copy(Bitmap.Config.ARGB_8888,false)?:error("无法复制裁图") else image
    }
    fun mediaLike(cls:String,desc:String)=listOf("Image","Picture","Video","SurfaceView","TextureView").any{cls.contains(it,true)} || desc in setOf("图片","图片表情","视频")
    fun selectable(desc:String,inComment:Boolean,visible:Boolean,r:Rect)=inComment && visible && desc in setOf("图片","图片表情") && !r.isEmpty
    fun inside(r:Rect,width:Int,height:Int)=!r.isEmpty && r.left>=0 && r.top>=0 && r.right<=width && r.bottom<=height
}

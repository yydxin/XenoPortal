package com.xeno.portal

import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class DouyinMenuTrialsTest {
    private fun start():org.robolectric.android.controller.ServiceController<PortalAccessibilityService> {
        val c=RuntimeEnvironment.getApplication();Preferences.set(c,"enabled",true);Preferences.set(c,"tree",true)
        val service=Robolectric.buildService(PortalAccessibilityService::class.java).create()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(service.get(),"onServiceConnected");DouyinMenuTrials.clear();return service
    }
    private val pkg="com.ss.android.ugc.aweme"
    @Test fun anchorsInDifferentWindowsDoNotCountAndOrdinaryShareCounterexampleStaysBlocked() {
        val service=start();val c=RuntimeEnvironment.getApplication()
        DouyinMenuTrials.start(c,DouyinMenuTrials.Case.IMAGE)
        DouyinMenuTrials.observe(listOf(MenuWindow(pkg,1,setOf("分享评论给"),emptyList()),MenuWindow(pkg,2,setOf("保存到相册"),emptyList())),"split-window")
        assertFalse(DouyinMenuTrials.results.getValue(DouyinMenuTrials.Case.IMAGE).candidate)
        assertNotNull(DouyinMenuTrials.active)
        val menu=listOf(MenuWindow(pkg,3,setOf("分享评论给","保存到相册"),listOf("image bounds")))
        DouyinMenuTrials.observe(menu,"image-panel")
        assertNull(DouyinMenuTrials.active)
        DouyinMenuTrials.start(c,DouyinMenuTrials.Case.SHARE)
        DouyinMenuTrials.observe(menu,"ordinary-share-panel")
        assertTrue(DouyinMenuTrials.comparison().contains("不具排他性"))
        assertFalse(ProbePolicy.automaticSaveAllowed(menu.first().labels))
        val report=Diagnostics.textReport()
        assertTrue(report.contains("image-panel"));assertTrue(report.contains("ordinary-share-panel"))
        DouyinMenuTrials.clear();service.destroy();shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun stickerMenuIsRetainedButCommentListIsNotAcceptedAsOrdinaryShareEvidence() {
        val service=start();val c=RuntimeEnvironment.getApplication()
        DouyinMenuTrials.start(c,DouyinMenuTrials.Case.IMAGE)
        DouyinMenuTrials.observe(listOf(MenuWindow(pkg,1,setOf("分享评论给","保存到相册"),emptyList())),"image")
        DouyinMenuTrials.start(c,DouyinMenuTrials.Case.STICKER)
        DouyinMenuTrials.observe(listOf(MenuWindow(pkg,2,setOf("添加到表情"),emptyList())),"sticker-panel")
        assertNull(DouyinMenuTrials.active)
        assertTrue(DouyinMenuTrials.results.getValue(DouyinMenuTrials.Case.STICKER).panel.contains("表情菜单"))
        DouyinMenuTrials.start(c,DouyinMenuTrials.Case.SHARE)
        DouyinMenuTrials.observe(listOf(MenuWindow(pkg,3,setOf("评论 147","图片","回复"),emptyList())),"comment-list")
        DouyinMenuTrials.finish()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(DouyinMenuTrials.comparison().contains("未取得分享面板"))
        assertEquals("证据不足",Diagnostics.stages.getValue(3).state)
        DouyinMenuTrials.clear();service.destroy();shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun samplingExpiresWithoutInventingMenuEvidence() {
        val service=start();val c=RuntimeEnvironment.getApplication()
        DouyinMenuTrials.start(c,DouyinMenuTrials.Case.STICKER)
        DouyinMenuTrials.observe(listOf(MenuWindow("com.other.app",1,setOf("分享评论给","保存到相册"),emptyList())),"other-app")
        assertTrue(DouyinMenuTrials.results.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(61))
        assertNull(DouyinMenuTrials.active)
        assertTrue(DouyinMenuTrials.status.contains("采样=0"))
        DouyinMenuTrials.clear();service.destroy();shadowOf(Looper.getMainLooper()).idle()
    }
}

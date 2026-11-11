package com.xeno.portal

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Looper
import androidx.core.content.FileProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class RuntimeSmokeTest {
    @Test fun emptyScanKeepsLastUsefulTargetSnapshotAndCopyIncludesIt() {
        Diagnostics.updateTree("captured=17:00 Douyin 图片节点 bounds=Rect(10,20,100,200)")
        shadowOf(Looper.getMainLooper()).idle()
        Diagnostics.windowScan("desktop visited=0", "empty", 0)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(Diagnostics.tree.contains("Douyin"))
        assertTrue(Diagnostics.treeAttempt.contains("visited=0"))
        assertTrue(Diagnostics.textReport().contains("图片节点"))
        assertTrue(Diagnostics.textReport().contains("desktop visited=0"))
    }
    @Test fun capabilityRefreshDoesNotEraseNotificationSubmission() {
        val c=RuntimeEnvironment.getApplication()
        Diagnostics.stage(5,"已提交","notification-test-id")
        shadowOf(Looper.getMainLooper()).idle()
        PortalDisplay.query(c)
        val finished=java.util.concurrent.CountDownLatch(1)
        Diagnostics.io.execute { finished.countDown() }
        assertTrue(finished.await(5,java.util.concurrent.TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("已提交",Diagnostics.stages.getValue(5).state)
        assertEquals("notification-test-id",Diagnostics.stages.getValue(5).detail)
    }
    @Test fun menuCandidateSnapshotSurvivesLaterOrdinaryWindowScans() {
        Diagnostics.windowScan("menu","menu-save-picture",8,true)
        Diagnostics.windowScan("normal","ordinary-app-content",20,false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("ordinary-app-content",Diagnostics.tree)
        assertEquals("menu-save-picture",Diagnostics.menuTree)
        assertTrue(Diagnostics.textReport().contains("menu-save-picture"))
    }
    @Test fun officialCompatibilityPayloadIsPostedAndOwnRegistrationIsObserved() {
        val c=RuntimeEnvironment.getApplication()
        Preferences.set(c,"enabled",true)
        val nm=c.getSystemService(android.app.NotificationManager::class.java)
        shadowOf(nm).setNotificationsEnabled(true)
        PortalDisplay.channel(c)
        val image=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
        MediaSessions.create(c,image,"official-test")
        shadowOf(Looper.getMainLooper()).idle()
        PortalDisplay.show(c,officialOnly=true,observationSeconds=30)
        val finished=java.util.concurrent.CountDownLatch(1)
        Diagnostics.io.execute { finished.countDown() }
        assertTrue(finished.await(5,java.util.concurrent.TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        val notification=nm.activeNotifications.single { it.id==PortalDisplay.NOTIFICATION_ID }.notification
        val param=org.json.JSONObject(notification.extras.getString("miui.focus.param")!!).getJSONObject("param_v2")
        assertTrue(param.has("chatInfo"))
        assertFalse(param.has("iconTextInfo"))
        assertEquals(30,param.getJSONObject("param_island").getInt("islandTimeout"))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600))
        assertTrue(PortalDisplay.lastObservation.contains("活跃通知=1"))
        PortalDisplay.close(c)
        assertTrue(nm.activeNotifications.none { it.id==PortalDisplay.NOTIFICATION_ID })
        MediaSessions.cleanup(c,all=true)
    }
    @Test fun launcherStartsWithoutAccessibilityOrNotificationPermission() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get().window.decorView)
        assertNull(PortalAccessibilityService.active)
        controller.pause().stop().destroy()
    }
    @Test fun providerOnlySharesSessionDirectoryAndRejectsOtherPrivateFiles() {
        val c = RuntimeEnvironment.getApplication()
        val privateFile = File(c.filesDir,"private-test.txt").apply { writeText("private") }
        assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(c,"${c.packageName}.files",privateFile) }
        val image = Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
        val session = MediaSessions.create(c,image,"test")
        shadowOf(Looper.getMainLooper()).idle()
        val uri = FileProvider.getUriForFile(c,"${c.packageName}.files",session.file)
        assertEquals("content",uri.scheme)
        assertNotNull(MediaSessions.find(c,session.id))
        session.file.setLastModified(System.currentTimeMillis()-MediaSessions.LEASE_MS-1000)
        assertNull(MediaSessions.find(c,session.id))
        MediaSessions.cleanup(c,all=true)
    }
    @Test fun appDoesNotRequestNetworkOverlayOrTouchExploration() {
        val c=RuntimeEnvironment.getApplication()
        val info=c.packageManager.getPackageInfo(c.packageName,PackageManager.GET_PERMISSIONS)
        val permissions=info.requestedPermissions.orEmpty().toSet()
        assertFalse("android.permission.INTERNET" in permissions)
        assertFalse("android.permission.SYSTEM_ALERT_WINDOW" in permissions)
        assertFalse("android.permission.FOREGROUND_SERVICE" in permissions)
        val flags=c.packageManager.getServiceInfo(android.content.ComponentName(c,PortalAccessibilityService::class.java),PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",flags.permission)
        val parser=c.resources.getXml(flags.metaData.getInt("android.accessibilityservice"))
        parser.use {
            while (it.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) it.next()
            val ns="http://schemas.android.com/apk/res/android"
            assertFalse(it.getAttributeBooleanValue(ns,"canRequestTouchExplorationMode",false))
            val serviceFlags=it.getAttributeIntValue(ns,"accessibilityFlags",0)
            assertEquals(0,serviceFlags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE)
        }
    }
}

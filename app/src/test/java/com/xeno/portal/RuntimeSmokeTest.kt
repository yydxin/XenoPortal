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

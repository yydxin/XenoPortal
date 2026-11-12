package com.xeno.portal

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RootBridgeTest {
    private fun request()=RootTouchRequest("a".repeat(32),100f,300f,1000,1350,0,0,1080,2400)
    @Test fun exclusiveHostUidRequired() {
        assertTrue(RootBridgePolicy.allowedPackages(arrayOf(RootBridgePolicy.HOST)))
        assertFalse(RootBridgePolicy.allowedPackages(arrayOf(RootBridgePolicy.HOST,"untrusted.shared")))
        assertFalse(RootBridgePolicy.allowedPackages(arrayOf("com.xeno.portal")))
        assertFalse(RootBridgePolicy.allowedPackages(null))
        assertFalse(RootBridgePolicy.allowedPackages(emptyArray()))
        val provider=Robolectric.buildContentProvider(RootBridgeProvider::class.java).create().get()
        assertThrows(SecurityException::class.java) {provider.call("config",null,null)}
    }
    @Test fun rejectsStaleFutureShortAndMismatchedCoordinates() {
        val good=request()
        assertNull(RootBridgePolicy.rejection(good,1400,350))
        assertNull(RootBridgePolicy.rejection(good.copy(detectedTime=1500),1550,500))
        listOf(good.copy(x=Float.NaN),good.copy(y=2400f),good.copy(displayId=1),good.copy(rotation=4),
            good.copy(downTime=1400),good.copy(detectedTime=1401),good.copy(detectedTime=1200),
            good.copy(gestureId="../../secret"),good.copy(width=0)).forEach {assertNotNull(RootBridgePolicy.rejection(it,1400,350))}
        assertNotNull(RootBridgePolicy.rejection(good,1501,350))
        assertNotNull(RootBridgePolicy.rejection(good,1400,500))
    }
    @Test fun authorizedMessagesAreGatedDeduplicatedAndDoNotStartActivities() {
        val c=RuntimeEnvironment.getApplication()
        val pm=shadowOf(c.packageManager)
        // Represent the Binder caller as the exclusively scoped host for this provider test.
        pm.setPackagesForUid(Process.myUid(),RootBridgePolicy.HOST)
        val provider=Robolectric.buildContentProvider(RootBridgeProvider::class.java).create().get()
        Preferences.set(c,"enabled",true);Preferences.set(c,"root_touch",false)
        assertFalse(provider.call("config",null,null).getBoolean("enabled"))
        Preferences.set(c,"root_touch",true)
        assertTrue(provider.call("config",null,null).getBoolean("enabled"))
        SystemClock.sleep(1000)
        val now=SystemClock.uptimeMillis()
        val message=Bundle().apply {
            putString("gestureId","b".repeat(32));putFloat("x",100f);putFloat("y",300f)
            putLong("downTime",now-350);putLong("detectedTime",now);putInt("displayId",0)
            putInt("rotation",0);putInt("width",1080);putInt("height",2400)
        }
        assertTrue(provider.call("hold",null,message).getBoolean("accepted"))
        assertFalse(provider.call("hold",null,message).getBoolean("accepted"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(Diagnostics.stages.getValue(4).detail.contains("无障碍服务未连接"))
        assertNull(shadowOf(c).nextStartedActivity)
        Preferences.set(c,"enabled",false)
        assertFalse(provider.call("hold",null,message).getBoolean("accepted"))
    }
}

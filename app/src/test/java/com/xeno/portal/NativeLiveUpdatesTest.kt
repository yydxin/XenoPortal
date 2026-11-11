package com.xeno.portal

import android.app.Notification
import android.os.Looper
import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
@LooperMode(LooperMode.Mode.PAUSED)
class NativeLiveUpdatesTest {
    @Test fun requestUsesPublicNativeContractWithoutClaimingPromotionOrMiuiIdentity() {
        val c=RuntimeEnvironment.getApplication()
        PortalDisplay.channel(c)
        val image=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
        val session=MediaSessions.create(c,image,"native-test")
        shadowOf(Looper.getMainLooper()).idle()
        val n=NativeLiveUpdates.buildNotification(c,session,30)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT!=0)
        assertEquals(0,n.flags and Notification.FLAG_PROMOTED_ONGOING)
        assertTrue(n.extras.getBoolean("android.requestPromotedOngoing"))
        assertFalse(n.extras.containsKey("miui.focus.param"))
        assertNull(n.contentView)
        assertNull(n.bigContentView)
        assertFalse(n.flags and Notification.FLAG_GROUP_SUMMARY!=0)
        // API36 framework in this test requires colorized=true (newer docs differ).
        assertTrue(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertTrue(n.hasPromotableCharacteristics())
        assertNotNull(n.contentIntent)
        assertTrue(n.actions.size in 1..3)
        assertTrue(shadowOf(n.actions.first().actionIntent).isBroadcast)
        assertEquals("待分享",n.shortCriticalText)
        MediaSessions.cleanup(c,all=true)
    }
    @Test fun promotionPermissionIsDeclaredSeparatelyFromRuntimeNotificationPermission() {
        val c=RuntimeEnvironment.getApplication()
        val info=c.packageManager.getPackageInfo(c.packageName,android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(NativeLiveUpdates.PROMOTION_PERMISSION in info.requestedPermissions.orEmpty())
        assertTrue("android.permission.POST_NOTIFICATIONS" in info.requestedPermissions.orEmpty())
        assertFalse("android.permission.FOREGROUND_SERVICE" in info.requestedPermissions.orEmpty())
        assertFalse("android.permission.INTERNET" in info.requestedPermissions.orEmpty())
    }
}

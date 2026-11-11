package com.xeno.portal

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class AlbumSaveTest {
    class AlbumProvider:ContentProvider() {
        var inserts=0;var deletes=0;var failWrite=false
        var inserted=ContentValues();var updated=ContentValues()
        lateinit var output:File
        override fun onCreate()=true
        override fun insert(uri:Uri,values:ContentValues?):Uri {
            inserts++;inserted=ContentValues(values);return Uri.parse("content://media/external/images/media/123")
        }
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            if(failWrite) throw java.io.FileNotFoundException("test write failure")
            return ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE)
        }
        override fun update(uri:Uri,values:ContentValues?,where:String?,args:Array<out String>?):Int { updated=ContentValues(values);return 1 }
        override fun delete(uri:Uri,where:String?,args:Array<out String>?):Int { deletes++;return 1 }
        override fun getType(uri:Uri)="image/jpeg"
        override fun query(uri:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,order:String?):Cursor?=null
    }
    private fun settle() {
        val done=CountDownLatch(1);Diagnostics.io.execute{done.countDown()}
        assertTrue(done.await(5,TimeUnit.SECONDS));shadowOf(Looper.getMainLooper()).idle()
    }
    private fun provider(c:Context)=AlbumProvider().apply {
        output=File(c.cacheDir,"saved-test.jpg")
        attachInfo(c,ProviderInfo().apply{authority="media"})
        ShadowContentResolver.registerProviderInternal("media",this)
    }
    @Test fun albumActionIsPrivateBroadcastAndSavesOnceWithoutLaunchingActivity() {
        val c=RuntimeEnvironment.getApplication()
        Preferences.set(c,"enabled",true)
        val album=provider(c)
        val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
        val session=MediaSessions.create(c,bitmap,"direct-save-test")
        shadowOf(Looper.getMainLooper()).idle()
        val expected=session.file.readBytes()
        // Robolectric registers manifest receivers only through filters; production uses
        // the explicit, non-exported manifest component (no public intent filter).
        val receiver=AlbumSaveReceiver()
        c.registerReceiver(receiver,IntentFilter(AlbumSaveReceiver.ACTION),Context.RECEIVER_NOT_EXPORTED)
        val pi=AlbumSaveReceiver.pending(c,session,"test")
        assertTrue(shadowOf(pi).isBroadcast)
        assertFalse(shadowOf(pi).isActivity)
        assertEquals(AlbumSaveReceiver::class.java.name,shadowOf(pi).savedIntent.component!!.className)
        val info=c.packageManager.getReceiverInfo(ComponentName(c,AlbumSaveReceiver::class.java),0)
        assertFalse(info.exported)
        pi.send();pi.send()
        shadowOf(Looper.getMainLooper()).idle();settle()
        assertEquals(Diagnostics.textReport(),1,album.inserts)
        assertEquals("Pictures/XenoPortal",album.inserted.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals(1,album.inserted.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertEquals(0,album.updated.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertArrayEquals(expected,album.output.readBytes())
        assertNull(shadowOf(c).nextStartedActivity)
        assertFalse(session.file.exists())
        assertTrue(Diagnostics.stages.getValue(6).detail.contains("Broadcast"))
        assertEquals("成功",Diagnostics.stages.getValue(7).state)
        pi.send();shadowOf(Looper.getMainLooper()).idle();settle()
        assertEquals(1,album.inserts)
        c.unregisterReceiver(receiver)
    }
    @Test fun failedWriteDeletesOnlyInsertedPendingRowAndKeepsSourceForRetry() {
        val c=RuntimeEnvironment.getApplication();val album=provider(c);album.failWrite=true
        val session=MediaSessions.create(c,Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888),"failure-test")
        shadowOf(Looper.getMainLooper()).idle()
        var result:AlbumSaveResult?=null
        MediaSessions.saveToAlbum(c,session){result=it};settle()
        assertEquals(AlbumSaveResult.FAILED,result)
        assertEquals(1,album.deletes);assertTrue(session.file.exists())
        album.failWrite=false
        MediaSessions.saveToAlbum(c,session){result=it};settle()
        assertEquals(AlbumSaveResult.SAVED,result)
        assertEquals(2,album.inserts);assertEquals(1,album.deletes)
        MediaSessions.cleanup(c,all=true)
    }
}

package com.xeno.portal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PhotoImportTest {
    private fun settle() {
        val done=CountDownLatch(1);Diagnostics.io.execute{done.countDown()}
        assertTrue(done.await(10,TimeUnit.SECONDS));shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun selectedImageBecomesBoundedPrivateJpegWithoutBroadReadPermission() {
        val c=RuntimeEnvironment.getApplication();Preferences.set(c,"enabled",true)
        shadowOf(c).denyPermissions("android.permission.READ_MEDIA_IMAGES")
        val bitmap=Bitmap.createBitmap(3000,1500,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.CYAN)
        val source=MediaSessions.create(c,bitmap,"photo-source");bitmap.recycle()
        shadowOf(Looper.getMainLooper()).idle()
        val uri=Uri.parse("content://test.photos/selected")
        shadowOf(c.contentResolver).registerInputStreamSupplier(uri) {source.file.inputStream()}
        PhotoImport.load(c,uri);settle()
        val session=MediaSessions.current!!
        assertNotEquals(source.id,session.id)
        assertTrue(session.label.contains("照片选择"))
        assertEquals("image/jpeg",session.mime)
        val result=BitmapFactory.decodeFile(session.file.path)
        assertEquals(2048,result.width);assertEquals(1024,result.height)
        assertTrue(Diagnostics.stages.getValue(4).detail.contains("非原文件"))
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,c.checkSelfPermission("android.permission.READ_MEDIA_IMAGES"))
        MediaSessions.cleanup(c,all=true)
    }
    @Test fun networkUriAndOversizedPhotoDoNotReplaceCurrentSession() {
        val c=RuntimeEnvironment.getApplication();Preferences.set(c,"enabled",true)
        val source=MediaSessions.create(c,Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888),"keep-source")
        shadowOf(Looper.getMainLooper()).idle()
        PhotoImport.load(c,Uri.parse("https://example.invalid/photo.jpg"));settle()
        assertEquals(source.id,MediaSessions.current!!.id)
        assertEquals("阻断",Diagnostics.stages.getValue(4).state)
        val huge=File(c.cacheDir,"sessions/oversized.jpg")
        java.io.RandomAccessFile(huge,"rw").use{it.setLength(PhotoImport.MAX_BYTES+1L)}
        val uri=Uri.parse("content://test.photos/oversized")
        shadowOf(c.contentResolver).registerInputStreamSupplier(uri) {huge.inputStream()}
        PhotoImport.load(c,uri);settle()
        assertEquals(source.id,MediaSessions.current!!.id)
        assertEquals("失败",Diagnostics.stages.getValue(4).state)
        assertTrue(Diagnostics.stages.getValue(4).detail.contains("25MiB"))
        huge.delete();MediaSessions.cleanup(c,all=true)
    }
}

package com.xeno.portal

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CommentImageRulesTest {
    @Test fun cropPreservesSelectedPixelsAndRejectsInvalidRegion() {
        val frame=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
        frame.eraseColor(android.graphics.Color.BLACK)
        frame.setPixel(2,3,android.graphics.Color.RED);frame.setPixel(5,6,android.graphics.Color.BLUE)
        val crop=CommentImageRules.crop(frame,Rect(2,3,6,7))
        assertEquals(4,crop.width);assertEquals(4,crop.height)
        assertEquals(android.graphics.Color.RED,crop.getPixel(0,0))
        assertEquals(android.graphics.Color.BLUE,crop.getPixel(3,3))
        assertThrows(IllegalArgumentException::class.java) { CommentImageRules.crop(frame,Rect(-1,0,4,4)) }
        val full=CommentImageRules.crop(frame,Rect(0,0,8,8))
        assertNotSame(frame,full);frame.recycle()
        assertFalse(full.isRecycled);assertFalse(crop.isRecycled)
    }
    @Test fun emptyClassImageDescriptionFromDeviceIsRecognizedButNotChosenAutomatically() {
        assertTrue(CommentImageRules.mediaLike("","图片"))
        assertTrue(CommentImageRules.selectable("图片",true,true,Rect(214,1435,795,1954)))
        assertTrue(CommentImageRules.selectable("图片表情",true,true,Rect(214,1500,580,1800)))
        assertFalse(ProbePolicy.automaticSaveAllowed(setOf("分享评论给","保存到相册","识别图片")))
    }
    @Test fun offscreenStickerAvatarsAndInputControlsAreNotSelectable() {
        assertFalse(CommentImageRules.selectable("图片表情",true,false,Rect(214,3096,580,3004)))
        assertFalse(CommentImageRules.selectable("图片",false,true,Rect(10,10,100,100)))
        assertFalse(CommentImageRules.selectable("某人的头像",true,true,Rect(10,10,100,100)))
        assertFalse(CommentImageRules.selectable("插入图片",true,true,Rect(10,10,100,100)))
        assertFalse(CommentImageRules.inside(Rect(-1,20,100,200),1440,3200))
        assertFalse(CommentImageRules.inside(Rect(20,20,100,3300),1440,3200))
        assertTrue(CommentImageRules.inside(Rect(214,2289,795,2870),1440,3200))
    }
}

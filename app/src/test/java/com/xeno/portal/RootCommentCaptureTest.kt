package com.xeno.portal

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RootCommentCaptureTest {
    private fun image()=RootImageTarget(7,"image","图片",Rect(100,200,400,500))
    @Test fun onlyUniquePointMatchMayBeCropped() {
        val a=image();val b=a.copy(id="second",bounds=Rect(100,600,400,900))
        assertEquals(a,RootCaptureRules.select(listOf(a,b),120f,220f,false,true))
        assertThrows(IllegalStateException::class.java) {RootCaptureRules.select(listOf(a,b),120f,550f,false,true)}
        assertThrows(IllegalStateException::class.java) {RootCaptureRules.select(listOf(a,a.copy(id="overlap")),120f,220f,false,true)}
        assertThrows(IllegalStateException::class.java) {RootCaptureRules.select(listOf(a),400f,220f,false,true)}
    }
    @Test fun menuIncompleteTreeAndInvalidRectangleAreRejected() {
        assertThrows(IllegalStateException::class.java) {RootCaptureRules.select(listOf(image()),120f,220f,true,true)}
        assertThrows(IllegalStateException::class.java) {RootCaptureRules.select(listOf(image()),120f,220f,false,false)}
        assertThrows(IllegalStateException::class.java) {RootCaptureRules.select(listOf(image().copy(bounds=Rect(100,500,400,200))),120f,220f,false,true)}
    }
    @Test fun frameCoordinatesRequireSameScreenRotationAndNoMagnification() {
        val r=RootTouchRequest("a".repeat(32),120f,220f,1000,1350,0,0,1080,2400)
        assertTrue(RootCaptureRules.geometry(r,0,1080,2400,1f))
        assertFalse(RootCaptureRules.geometry(r,1,1080,2400,1f))
        assertFalse(RootCaptureRules.geometry(r,0,540,1200,1f))
        assertFalse(RootCaptureRules.geometry(r,0,1080,2400,2f))
        assertFalse(RootCaptureRules.geometry(r.copy(displayId=1),0,1080,2400,1f))
    }
}

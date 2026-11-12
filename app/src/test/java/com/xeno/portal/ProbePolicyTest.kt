package com.xeno.portal
import org.junit.Assert.*
import org.junit.Test
class ProbePolicyTest {
    @Test fun packageLessMenuEventsCanInspectOnlyWhenForegroundEvidenceIsWhitelisted() {
        val white=setOf("com.ss.android.ugc.aweme")
        assertTrue(ProbePolicy.shouldInspectEvent("", "com.ss.android.ugc.aweme",white))
        assertTrue(ProbePolicy.shouldInspectEvent("com.android.systemui", "com.ss.android.ugc.aweme",white))
        assertFalse(ProbePolicy.shouldInspectEvent("", "com.miui.home",white))
        assertFalse(ProbePolicy.shouldInspectEvent("com.android.systemui", "com.xeno.portal",white))
    }
    @Test fun ordinaryShareAndMenuNeverAuthorizeSave() {
        val samples=listOf(emptySet(),setOf("保存到相册"),setOf("分享评论给","保存到相册"),setOf("分享评论给","识别图片","保存到相册"))
        samples.forEach { assertFalse(ProbePolicy.automaticSaveAllowed(it)) }
        assertTrue(ProbePolicy.menuCandidate(samples.last()))
    }
    @Test fun unknownPermissionNeverSelectsCustomOverlay() {
        assertTrue(ProbePolicy.displayRoute(3,null,true).contains("未知"))
        assertTrue(ProbePolicy.displayRoute(3,false,true).contains("canShowFocus=false"))
        assertTrue(ProbePolicy.displayRoute(3,true,false).contains("阻断"))
        assertTrue(ProbePolicy.displayRoute(2,true,true).contains("OS2"))
    }
    @Test fun sessionIdRejectsPathTraversal() {
        assertFalse(ProbePolicy.validSessionId("../private"))
        assertFalse(ProbePolicy.validSessionId("a".repeat(31)))
        assertTrue(ProbePolicy.validSessionId("a".repeat(32)))
    }
}

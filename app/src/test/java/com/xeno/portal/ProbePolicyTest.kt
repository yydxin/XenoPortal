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
    @Test fun unknownPermissionFallsBackEvenOnNewProtocol() {
        assertTrue(ProbePolicy.displayRoute(3,null,true,false).contains("自绘岛"))
        assertTrue(ProbePolicy.displayRoute(3,false,true,false).contains("自绘岛"))
        assertTrue(ProbePolicy.displayRoute(3,true,true,true).contains("自绘岛"))
        assertTrue(ProbePolicy.displayRoute(2,true,true,false).contains("OS2"))
    }
    @Test fun sessionIdRejectsPathTraversal() {
        assertFalse(ProbePolicy.validSessionId("../private"))
        assertFalse(ProbePolicy.validSessionId("a".repeat(31)))
        assertTrue(ProbePolicy.validSessionId("a".repeat(32)))
    }
}

package com.example.firststep.data;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class CompletionPolicyTest {
    private Mission mission(String id, int order, boolean verified) {
        return new Mission(id, id, "candidate", "quiz", "story", "question", "answer",
                Collections.singletonList("answer"), Collections.emptyList(), "info", order,
                verified, verified ? 1.0 : null, verified ? 2.0 : null);
    }
    @Test public void enforcesSequence() {
        Mission first = mission("M01", 1, false), second = mission("M02", 2, false);
        assertTrue(CompletionPolicy.canComplete(AppMode.DEMO, first, Collections.emptySet(),
                "CARD", "PHOTO", Arrays.asList(first, second)));
        assertFalse(CompletionPolicy.canComplete(AppMode.DEMO, second, Collections.emptySet(),
                "CARD", "LIGHT", Arrays.asList(first, second)));
        assertTrue(CompletionPolicy.canComplete(AppMode.DEMO, second,
                Collections.singleton("M01"), "AR", "SPEECH", Arrays.asList(first, second)));
    }
    @Test public void fieldRequiresVerifiedCoordinatesAndKnownDiscoveryMethod() {
        Mission unverified = mission("M01", 1, false);
        assertFalse(CompletionPolicy.canComplete(AppMode.FIELD, unverified,
                Collections.emptySet(), "AR", "PHOTO", Collections.singletonList(unverified)));
        assertFalse(CompletionPolicy.canComplete(AppMode.DEMO, unverified,
                Collections.emptySet(), "CAMERA", "TYPED", Collections.singletonList(unverified)));
        assertTrue(CompletionPolicy.canComplete(AppMode.DEMO, unverified,
                Collections.emptySet(), "AR", "HOLD", Collections.singletonList(unverified)));
        assertTrue(CompletionPolicy.canComplete(AppMode.DEMO, unverified,
                Collections.emptySet(), "AR", "ACCELEROMETER", Collections.singletonList(unverified)));
        assertFalse(CompletionPolicy.canComplete(AppMode.DEMO, unverified,
                Collections.emptySet(), "AR", "UNKNOWN", Collections.singletonList(unverified)));
    }
}

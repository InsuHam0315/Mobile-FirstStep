package com.example.firststep.engine;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class QuizValidatorTest {
    @Test public void matchesIgnoringWhitespaceAndUnicodeCompatibility() {
        assertTrue(QuizValidator.matches("학습과 자료 이용", " 학습 과 자료　이용 "));
        assertTrue(QuizValidator.matches("ABC 123", "abc123"));
    }
    @Test public void acceptedAnswersAreCheckedAndNullNeverMatches() {
        assertTrue(QuizValidator.matchesAny(Arrays.asList("자료 이용", "학습"), "학습"));
        assertFalse(QuizValidator.matchesAny(Arrays.asList("학습"), "운동"));
        assertFalse(QuizValidator.matches(null, null));
    }
}

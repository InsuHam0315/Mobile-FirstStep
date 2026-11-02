package com.example.firststep.engine;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

/** Platform-independent answer matching for quiz and speech-recognition output. */
public final class QuizValidator {
    private QuizValidator() { }
    public static boolean matches(String expected, String input) {
        if (expected == null || input == null) return false;
        return normalize(expected).equals(normalize(input));
    }
    public static boolean matchesAny(Collection<String> accepted, String input) {
        if (accepted == null || input == null) return false;
        String normalized = normalize(input);
        for (String answer : accepted) if (normalized.equals(normalize(answer))) return true;
        return false;
    }
    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim()
                .replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}

package com.example.webchat.web;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Replaces configured words with asterisks. Compiled patterns are cached and only
 * rebuilt when the word list actually changes, so a filter call doesn't pay for
 * recompiling a regex per word on every message. No Minecraft/Fabric dependencies
 * so it can be unit tested in isolation.
 */
public class ProfanityFilter {
    private List<String> cachedSourceWords = List.of();
    private List<String> cachedWords = List.of();
    private List<Pattern> cachedPatterns = List.of();

    public synchronized String filter(String message, List<String> words) {
        if (words.isEmpty()) {
            return message;
        }
        if (!words.equals(cachedSourceWords)) {
            rebuild(words);
        }

        String result = message;
        for (int i = 0; i < cachedPatterns.size(); i++) {
            String replacement = "*".repeat(cachedWords.get(i).length());
            result = cachedPatterns.get(i).matcher(result).replaceAll(replacement);
        }
        return result;
    }

    private void rebuild(List<String> words) {
        List<String> nonBlankWords = words.stream().filter(w -> w != null && !w.isBlank()).toList();
        List<Pattern> patterns = nonBlankWords.stream()
                .map(w -> Pattern.compile(Pattern.quote(w), Pattern.CASE_INSENSITIVE))
                .toList();
        cachedSourceWords = words;
        cachedWords = nonBlankWords;
        cachedPatterns = patterns;
    }
}

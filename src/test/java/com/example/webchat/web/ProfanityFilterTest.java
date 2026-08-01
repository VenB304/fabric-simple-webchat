package com.example.webchat.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfanityFilterTest {

    @Test
    void replacesConfiguredWordsCaseInsensitively() {
        ProfanityFilter filter = new ProfanityFilter();
        String result = filter.filter("this is DARN annoying", List.of("darn"));
        assertEquals("this is **** annoying", result);
    }

    @Test
    void leavesMessageUntouchedWhenNoWordsConfigured() {
        ProfanityFilter filter = new ProfanityFilter();
        String result = filter.filter("hello world", List.of());
        assertEquals("hello world", result);
    }

    @Test
    void ignoresBlankEntriesInWordList() {
        ProfanityFilter filter = new ProfanityFilter();
        String result = filter.filter("hello darn world", java.util.Arrays.asList("darn", "", null, "  "));
        assertEquals("hello **** world", result);
    }

    @Test
    void reusesCompiledPatternsAcrossCallsWithTheSameList() {
        ProfanityFilter filter = new ProfanityFilter();
        List<String> words = List.of("darn");

        assertEquals("**** one", filter.filter("darn one", words));
        assertEquals("**** two", filter.filter("darn two", words));
    }

    @Test
    void rebuildsWhenWordListChanges() {
        ProfanityFilter filter = new ProfanityFilter();
        assertEquals("**** it", filter.filter("darn it", List.of("darn")));
        assertEquals("darn it, ****", filter.filter("darn it, heck", List.of("heck")));
    }
}

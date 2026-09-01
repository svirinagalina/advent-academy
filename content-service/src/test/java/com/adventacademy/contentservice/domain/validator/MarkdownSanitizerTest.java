package com.adventacademy.contentservice.domain.validator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownSanitizerTest {

    @Test
    void removesScriptTags() {
        String input = "<p>Text</p><script>alert('xss')</script>";
        String result = MarkdownSanitizer.sanitizeHtml(input);
        assertFalse(result.contains("<script"));
    }

    @Test
    void removesIframeTags() {
        String input = "<iframe src='evil.com'></iframe>";
        String result = MarkdownSanitizer.sanitizeHtml(input);
        assertFalse(result.contains("<iframe"));
    }

    @Test
    void removesEventHandlers() {
        String input = "<img onerror='alert(1)' src='x'>";
        String result = MarkdownSanitizer.sanitizeHtml(input);
        assertFalse(result.contains("onerror"));
    }

    @Test
    void removesJavascriptUrls() {
        String input = "<a href='javascript:alert(1)'>Click</a>";
        String result = MarkdownSanitizer.sanitizeHtml(input);
        assertFalse(result.contains("javascript:"));
    }

    @Test
    void removesDataHtmlUrls() {
        String input = "<img src='data:text/html,<script>alert(1)</script>'>";
        String result = MarkdownSanitizer.sanitizeHtml(input);
        assertFalse(result.contains("data:text/html"));
    }

    @Test
    void detectsDangerousPatterns() {
        assertTrue(MarkdownSanitizer.containsDangerousPatterns("<script>alert(1)</script>"));
        assertTrue(MarkdownSanitizer.containsDangerousPatterns("<iframe></iframe>"));
        assertTrue(MarkdownSanitizer.containsDangerousPatterns("onclick='alert(1)'"));
        assertFalse(MarkdownSanitizer.containsDangerousPatterns("<p>Safe text</p>"));
    }

    @Test
    void checksHtmlSafety() {
        assertTrue(MarkdownSanitizer.isHtmlSafe("<p>Safe</p><strong>Bold</strong>"));
        assertFalse(MarkdownSanitizer.isHtmlSafe("<script>alert(1)</script>"));
        assertFalse(MarkdownSanitizer.isHtmlSafe("<div onclick='alert(1)'>Click</div>"));
    }

    @Test
    void stripsHtmlTags() {
        String input = "<p>Hello <strong>World</strong></p>";
        String result = MarkdownSanitizer.stripHtmlTags(input);
        assertEquals("Hello World", result);
    }

    @Test
    void decodeNumericEntities() {
        String input = "&#60;script&#62;alert(1)&#60;/script&#62;";
        String result = MarkdownSanitizer.decodeHtmlEntities(input);
        assertTrue(result.contains("<script"));
    }

    @Test
    void extractsPlainText() {
        String input = "<p>Paragraph 1</p><p>Paragraph 2</p>";
        String result = MarkdownSanitizer.getPlainText(input);
        assertTrue(result.contains("Paragraph 1"));
        assertTrue(result.contains("Paragraph 2"));
        assertFalse(result.contains("<p>"));
    }
}

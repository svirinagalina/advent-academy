package com.adventacademy.contentservice.domain.validator;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public class MarkdownSanitizer {

    // Dangerous patterns to reject
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script[^>]*>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern IFRAME_TAG = Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBED_TAG = Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern OBJECT_TAG = Pattern.compile("<object[^>]*>.*?</object>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_HANDLER = Pattern.compile("\\s+on\\w+\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_URL = Pattern.compile("(?i)javascript:");
    private static final Pattern DATA_HTML_URL = Pattern.compile("(?i)data:text/html");
    private static final Pattern STYLE_JAVASCRIPT = Pattern.compile("(?i)background-image\\s*:\\s*url\\s*\\(.*?javascript");

    // Allowed HTML tags for rendering
    private static final Set<String> ALLOWED_TAGS = new HashSet<>(Arrays.asList(
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6",
            "strong", "b", "em", "i", "u", "del", "s",
            "ul", "ol", "li", "blockquote", "pre", "code",
            "a", "img", "br", "hr"
    ));

    /**
     * Sanitize HTML by removing dangerous elements and attributes
     */
    public static String sanitizeHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String sanitized = html;

        // Remove dangerous tags completely
        sanitized = SCRIPT_TAG.matcher(sanitized).replaceAll("");
        sanitized = IFRAME_TAG.matcher(sanitized).replaceAll("");
        sanitized = EMBED_TAG.matcher(sanitized).replaceAll("");
        sanitized = OBJECT_TAG.matcher(sanitized).replaceAll("");

        // Remove event handlers
        sanitized = EVENT_HANDLER.matcher(sanitized).replaceAll(" ");

        // Remove javascript: URLs
        sanitized = JAVASCRIPT_URL.matcher(sanitized).replaceAll("");

        // Remove data:text/html URLs
        sanitized = DATA_HTML_URL.matcher(sanitized).replaceAll("");

        // Remove style javascript
        sanitized = STYLE_JAVASCRIPT.matcher(sanitized).replaceAll("");

        // Remove onclick, onload, etc. attributes
        sanitized = sanitized.replaceAll("(?i)\\s+on\\w+\\s*=\\s*[\"']?[^\"'\\s>]*[\"']?", "");

        // Remove href with javascript:
        sanitized = sanitized.replaceAll("(?i)href\\s*=\\s*[\"']?\\s*javascript:", "href=\"#\"");

        // Remove data: URIs from href
        sanitized = sanitized.replaceAll("(?i)href\\s*=\\s*[\"']?\\s*data:", "href=\"#\"");

        log.debug("HTML sanitized successfully");
        return sanitized;
    }

    /**
     * Remove all HTML tags
     */
    public static String stripHtmlTags(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Check if text contains dangerous patterns
     */
    public static boolean containsDangerousPatterns(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        return SCRIPT_TAG.matcher(text).find() ||
               IFRAME_TAG.matcher(text).find() ||
               EMBED_TAG.matcher(text).find() ||
               OBJECT_TAG.matcher(text).find() ||
               EVENT_HANDLER.matcher(text).find() ||
               JAVASCRIPT_URL.matcher(text).find() ||
               DATA_HTML_URL.matcher(text).find();
    }

    /**
     * Decode HTML entities to prevent encoded XSS
     */
    public static String decodeHtmlEntities(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String decoded = html;

        // Decode numeric entities
        decoded = decoded.replaceAll("&#(\\d+);", match -> {
            try {
                int code = Integer.parseInt(match.substring(2, match.length() - 1));
                return String.valueOf((char) code);
            } catch (NumberFormatException e) {
                return match;
            }
        });

        // Decode hex entities
        decoded = decoded.replaceAll("&#x([0-9a-fA-F]+);", match -> {
            try {
                int code = Integer.parseInt(match.substring(3, match.length() - 1), 16);
                return String.valueOf((char) code);
            } catch (NumberFormatException e) {
                return match;
            }
        });

        // Decode named entities
        decoded = decoded.replace("&lt;", "<");
        decoded = decoded.replace("&gt;", ">");
        decoded = decoded.replace("&amp;", "&");
        decoded = decoded.replace("&quot;", "\"");
        decoded = decoded.replace("&apos;", "'");

        return decoded;
    }

    /**
     * Validate that HTML only contains allowed tags
     */
    public static boolean isHtmlSafe(String html) {
        if (html == null || html.isEmpty()) {
            return true;
        }

        // First check for dangerous patterns
        if (containsDangerousPatterns(html)) {
            return false;
        }

        // Extract all tags and check they're allowed
        Pattern tagPattern = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b", Pattern.CASE_INSENSITIVE);
        var matcher = tagPattern.matcher(html);

        while (matcher.find()) {
            String tag = matcher.group(2).toLowerCase();
            if (!ALLOWED_TAGS.contains(tag)) {
                log.warn("Found disallowed HTML tag: {}", tag);
                return false;
            }
        }

        return true;
    }

    /**
     * Create plain-text version of HTML
     */
    public static String getPlainText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        String text = html;

        // Replace block elements with newlines
        text = text.replaceAll("(?i)</(p|div|blockquote|pre|h\\d)>", "\n");
        text = text.replaceAll("(?i)<(p|div|blockquote|pre|h\\d)[^>]*>", "");
        text = text.replaceAll("(?i)<li[^>]*>", "• ");
        text = text.replaceAll("(?i)</li>", "\n");
        text = text.replaceAll("(?i)<br\\s*/?\\s*>", "\n");

        // Remove all remaining tags
        text = text.replaceAll("<[^>]*>", "");

        // Decode entities
        text = decodeHtmlEntities(text);

        // Clean up whitespace
        text = text.replaceAll("\\n\\s*\\n", "\n\n");
        text = text.trim();

        return text;
    }
}

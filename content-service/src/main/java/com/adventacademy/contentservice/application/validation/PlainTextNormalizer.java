package com.adventacademy.contentservice.application.validation;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes user-supplied "display text" fields (title/description) used across the
 * course constructor — {@code Direction}, {@code Course}, {@code Component}, and
 * {@code Lesson} — into safe, storable plain text.
 * <p>
 * <b>Contract:</b> the output of {@link #normalize(String, int)} is guaranteed to be
 * free of HTML markup. These fields are <b>plain text, never HTML</b>: they are
 * persisted as-is, returned as-is in API responses, and must be rendered on the
 * frontend through a text-only sink (e.g. {@code element.textContent = value} or
 * React's default text interpolation), never through {@code innerHTML} or any other
 * HTML-interpreting sink. This is a distinct contract from {@code LessonContent}
 * markdown, which is intentionally rendered to sanitized HTML by
 * {@code CommonMarkMarkdownRenderer} and is out of scope here.
 * <p>
 * <b>Implementation note:</b> HTML stripping is delegated to the OWASP Java HTML
 * Sanitizer (already a project dependency, used elsewhere for lesson markdown
 * rendering) rather than a hand-rolled regex, since regex-based tag stripping is a
 * well-known source of bypasses. A policy that allows no elements and no attributes
 * strips every tag. It also discards the text content of "raw text" elements such as
 * {@code <script>} and {@code <style>}, so {@code <script>alert(1)</script>} sanitizes
 * to an empty string rather than leaking {@code alert(1)} as visible text, and
 * {@code <img src=x onerror=alert(1)>} disappears entirely.
 * <p>
 * Because the sanitizer's output is an HTML-safe text fragment, it HTML-escapes
 * characters like {@code &}, {@code <}, {@code >} that survive as literal text (e.g. a
 * title containing "Tom &amp; Jerry" as typed by the user). Those entities are decoded
 * back to their literal characters afterwards, since the stored value must be plain
 * text, not an HTML fragment.
 * <p>
 * <b>Known trade-off:</b> because stripping relies on HTML tokenization, a literal
 * {@code <} immediately followed by something that looks like a tag name (e.g.
 * {@code "5 <Foo> bar"}) is parsed as a tag and removed, even though it was not meant
 * as markup. Ordinary punctuation, quotes, hyphens, and unicode characters are
 * unaffected. This mirrors how the rest of the codebase already treats these fields
 * (an HTML-based pipeline) and is an accepted limitation for title/description fields,
 * which are not expected to contain literal angle-bracket syntax.
 */
public final class PlainTextNormalizer {

    public static final int DEFAULT_TITLE_MAX_LENGTH = 255;
    public static final int DEFAULT_DESCRIPTION_MAX_LENGTH = 2000;

    private static final PolicyFactory NO_HTML_POLICY = new HtmlPolicyBuilder().toFactory();

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]+);");

    private PlainTextNormalizer() {
    }

    /** Normalizes a title field using {@link #DEFAULT_TITLE_MAX_LENGTH}. */
    public static String normalizeTitle(String input) {
        return normalize(input, DEFAULT_TITLE_MAX_LENGTH);
    }

    /** Normalizes a description field using {@link #DEFAULT_DESCRIPTION_MAX_LENGTH}. */
    public static String normalizeDescription(String input) {
        return normalize(input, DEFAULT_DESCRIPTION_MAX_LENGTH);
    }

    /**
     * Strips any HTML markup from {@code input}, trims surrounding whitespace, and
     * truncates to at most {@code maxLength} characters.
     *
     * @return the normalized plain text, or {@code null} if {@code input} is {@code null}
     */
    public static String normalize(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        String withoutHtml = unescapeEntities(NO_HTML_POLICY.sanitize(input));
        String trimmed = withoutHtml.trim();
        if (trimmed.length() > maxLength) {
            trimmed = trimmed.substring(0, maxLength).trim();
        }
        return trimmed;
    }

    /**
     * Decodes the HTML entities that {@link #NO_HTML_POLICY} may have introduced for
     * literal {@code &}, {@code <}, {@code >}, and quote characters back to their
     * literal form, so the result is plain text rather than an HTML-escaped fragment.
     * {@code &amp;} is decoded last so that a user-typed literal sequence like
     * {@code "&lt;"} (escaped by the sanitizer to {@code "&amp;lt;"}) round-trips back
     * to {@code "&lt;"} instead of being double-decoded into {@code "<"}.
     */
    private static String unescapeEntities(String html) {
        String result = html
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        result = decodeNumericEntities(result);
        return result.replace("&amp;", "&");
    }

    private static String decodeNumericEntities(String input) {
        Matcher matcher = NUMERIC_ENTITY.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String ref = matcher.group(1);
            try {
                int codePoint = (ref.startsWith("x") || ref.startsWith("X"))
                        ? Integer.parseInt(ref.substring(1), 16)
                        : Integer.parseInt(ref);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (IllegalArgumentException e) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

package com.adventacademy.contentservice.infrastructure.markdown;

import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import com.adventacademy.contentservice.application.port.MarkdownRendererContractTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CommonMark-specific rendering on top of the shared
 * {@link MarkdownRendererContractTest} guarantees (assets, mermaid, XSS).
 */
class CommonMarkMarkdownRendererTest extends MarkdownRendererContractTest {

    @Override
    protected MarkdownRenderer createRenderer() {
        return new CommonMarkMarkdownRenderer();
    }

    @Test
    void renderList() {
        String html = renderer.render("- один\n- два");

        assertThat(html).contains("<li>один</li>");
    }

    @Test
    void rendersBlockQuote() {
        String html = renderer.render("> мудрая мысль");

        assertThat(html).contains("<blockquote>");
    }

    @Test
    void rendersFencedCodeBlockWithoutLanguage() {
        String html = renderer.render("""
                ```
                int x = 1;
                ```
                """);

        assertThat(html).contains("<pre><code>");
        // The OWASP sanitizer entity-encodes '=' in text nodes; browsers decode it
        // back, so stored HTML keeps &#61; while the reader still sees "int x = 1;".
        assertThat(html).contains("int x &#61; 1;");
    }

    @Test
    void rendersTable() {
        String html = renderer.render("| a | b |\n| --- | --- |\n| 1 | 2 |");

        assertThat(html).contains("<table>");
    }

    @Test
    void rendersLink() {
        String html = renderer.render("[сайт](https://example.com)");

        assertThat(html).contains("<a href=\"https://example.com\"");
    }

    @Test
    void unsupportedMarkupDoesNotBreakRendering() {
        String html = renderer.render("<blink>мигалка</blink> обычный текст");

        assertThat(html).contains("обычный текст");
    }
}

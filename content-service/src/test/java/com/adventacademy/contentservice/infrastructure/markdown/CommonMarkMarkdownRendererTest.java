package com.adventacademy.contentservice.infrastructure.markdown;

import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonMarkMarkdownRendererTest {

    private final MarkdownRenderer renderer = new CommonMarkMarkdownRenderer();

    @Test
    void rendersHeading() {
        String html = renderer.render("# Заголовок");

        assertThat(html).contains("<h1>Заголовок</h1>");
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
    void rendersBlock() {
        String html = renderer.render("```\\nint x = 1;\\n```");

        assertThat(html).contains("<code>");
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
    void rendersLinkWithLink() {
        String html = renderer.render("<script>alert(1)</script>обычный текст");
        assertThat(html).contains("обычный текст");
        assertThat(html).doesNotContain("<script");
    }

    @Test
    void removesEventHandlerAttributes() {
        String html = renderer.render("<img src=\"x\" onerror=\"alert(1)\">");
        assertThat(html).doesNotContain("onerror");
    }

    @Test
    void removesJavascriptLinks() {
        String html = renderer.render("[жми](javascript:alert(1))");
        assertThat(html).doesNotContain("javascript:");
    }

    @Test
    void rejectsBlankMarkdown() {
        assertThatThrownBy(() -> renderer.render(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> renderer.render(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsupportedMarkupDoesNotBreakRendering() {
        String html = renderer.render("<blink>мигалка</blink> обычный текст");
        assertThat(html).contains("обычный текст");
    }
    @Test
    void preservesMermaidFencedBlockWithLanguageClass() {
        String markdown = """
```mermaid
            flowchart TD
                A --> B
```
            """;

        String html = renderer.render(markdown);

        assertThat(html).contains("<pre><code class=\"language-mermaid\">");
    }

    @Test
    void allowsAssetImageLink() {
        String assetUrl = "/api/assets/11111111-1111-1111-1111-111111111111";
        String html = renderer.render("![diagram](" + assetUrl + ")");

        assertThat(html).contains("<img src=\"" + assetUrl + "\"");
    }
    @Test
    void preservesAltTextForValidAssetImage() {
        String assetUrl = "/api/assets/11111111-1111-1111-1111-111111111111";
        String html = renderer.render("![pic](" + assetUrl + ")");

        assertThat(html).contains("alt=\"pic\"");
    }

    @Test
    void stripsExternalImageLink() {
        String html = renderer.render("![evil](https://attacker.example/tracker.png)");

        assertThat(html).doesNotContain("<img");
        assertThat(html).doesNotContain("attacker.example");
    }

    @Test
    void stripsClassAttributeNotMatchingLanguagePattern() {
        String html = renderer.render("<pre class=\"evil-class\"><code>x</code></pre>");

        assertThat(html).doesNotContain("evil-class");
    }
}
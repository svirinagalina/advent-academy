package com.adventacademy.contentservice.application.port;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract every {@link MarkdownRenderer} implementation must satisfy.
 * <p>
 * Subclasses supply the implementation under test via {@link #createRenderer()};
 * the cases below are the guarantees lesson content relies on, so a second
 * implementation cannot silently drop sanitizing or asset support.
 */
public abstract class MarkdownRendererContractTest {

    private static final String ASSET_URL = "/api/assets/11111111-1111-1111-1111-111111111111";

    protected MarkdownRenderer renderer;

    protected abstract MarkdownRenderer createRenderer();

    @BeforeEach
    void createRendererUnderTest() {
        renderer = createRenderer();
    }

    @Test
    void rendersMarkdownToHtml() {
        String html = renderer.render("# Заголовок");

        assertThat(html).contains("<h1>Заголовок</h1>");
    }

    @Test
    void rejectsBlankMarkdown() {
        assertThatThrownBy(() -> renderer.render(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> renderer.render(null))
                .isInstanceOf(IllegalArgumentException.class);
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
        String html = renderer.render("![diagram](" + ASSET_URL + ")");

        assertThat(html).contains("<img src=\"" + ASSET_URL + "\"");
    }

    @Test
    void preservesAltTextForValidAssetImage() {
        String html = renderer.render("![pic](" + ASSET_URL + ")");

        assertThat(html).contains("alt=\"pic\"");
    }

    @Test
    void stripsExternalImageLink() {
        String html = renderer.render("![evil](https://attacker.example/tracker.png)");

        assertThat(html).doesNotContain("<img");
        assertThat(html).doesNotContain("attacker.example");
    }

    @Test
    void stripsScriptTagButKeepsSurroundingText() {
        String html = renderer.render("<script>alert(1)</script>обычный текст");

        assertThat(html).contains("обычный текст");
        assertThat(html).doesNotContain("<script");
    }

    @Test
    void removesEventHandlerAttributes() {
        String html = renderer.render("<img src=\"" + ASSET_URL + "\" onerror=\"alert(1)\">");

        assertThat(html).doesNotContain("onerror");
    }

    @Test
    void removesJavascriptLinks() {
        String html = renderer.render("[жми](javascript:alert(1))");

        assertThat(html).doesNotContain("javascript:");
    }

    @Test
    void stripsClassAttributeNotMatchingLanguagePattern() {
        String html = renderer.render("<pre class=\"evil-class\"><code>x</code></pre>");

        assertThat(html).doesNotContain("evil-class");
    }
}

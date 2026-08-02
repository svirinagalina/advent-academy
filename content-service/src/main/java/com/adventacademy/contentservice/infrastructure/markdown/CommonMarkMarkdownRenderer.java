package com.adventacademy.contentservice.infrastructure.markdown;

import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import com.adventacademy.contentservice.application.port.MarkdownRenderingException;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * MarkdownRenderer implementation based on CommonMark.
 * <p>
 * Parses Markdown input to HTML and sanitizes the result using OWASP HTML sanitizer
 * to prevent XSS and other unsafe HTML constructs.
 * <p>
 * Extends the default OWASP policy with two lesson-specific rules:
 * - {@code <pre><code class="language-xxx">} is preserved (needed for fenced
 *   code blocks, including {@code mermaid} diagrams rendered by the frontend);
 * - {@code <img src>} is only allowed when it points at an already-resolved
 *   asset link ({@code /api/assets/<uuid>}), so externally hosted images are
 *   stripped out.
 */
@Component
public class CommonMarkMarkdownRenderer implements MarkdownRenderer {

    private static final Pattern LANGUAGE_CLASS = Pattern.compile("^language-[a-zA-Z0-9_-]+$");
    private static final Pattern ASSET_IMAGE_SRC = Pattern.compile(
            "^/api/assets/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;
    private final PolicyFactory sanitizer = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(new HtmlPolicyBuilder()
                    .allowElements("pre", "code", "img").allowAttributes("class").matching(LANGUAGE_CLASS).onElements("pre", "code")
                    .allowAttributes("src").matching(ASSET_IMAGE_SRC).onElements("img")
                    .disallowWithoutAttributes("img")
                    .toFactory());

    public CommonMarkMarkdownRenderer() {
        List<Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.htmlRenderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    @Override
    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("markdown must not be blank");
        }
        try {
            Node document = parser.parse(markdown);
            String html = htmlRenderer.render(document);
            return sanitizer.sanitize(html);
        } catch (Exception e) {
            throw new MarkdownRenderingException("Failed to render markdown", e);
        }
    }
}
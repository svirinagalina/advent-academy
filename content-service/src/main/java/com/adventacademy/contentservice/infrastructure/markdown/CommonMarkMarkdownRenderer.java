package com.adventacademy.contentservice.infrastructure.markdown;

import com.adventacademy.contentservice.application.port.MarkdownRenderer;
import com.adventacademy.contentservice.application.port.MarkdownRenderingException;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommonMarkMarkdownRenderer implements MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;
    private final PolicyFactory sanitizer = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.IMAGES);

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
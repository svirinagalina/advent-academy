package com.adventacademy.contentservice.application.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class MarkdownRendererContractTest {
    private static class FakeMarkdownRenderer implements MarkdownRenderer {
        @Override
        public String render(String markdown) {
            if (markdown == null || markdown.isBlank()) {
                throw new IllegalArgumentException("markdown must not be blank");
            }
            if (markdown.contains("BOOM")) {
                throw new MarkdownRenderingException("cannot render");
            }
            return "<p>" + markdown + "</p>";
        }
    }
    @Test
    void rendersMarkdownToHtml() {
        MarkdownRenderer renderer = new FakeMarkdownRenderer();

        String html = renderer.render("привет");

        assertThat(html).isEqualTo("<p>привет</p>");
    }
    @Test
    void rejectsBlankMarkdown() {
        MarkdownRenderer renderer = new FakeMarkdownRenderer();

        assertThatThrownBy(() -> renderer.render(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> renderer.render(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void throwsRenderingExceptionWhenRenderingFails() {
        MarkdownRenderer renderer = new FakeMarkdownRenderer();

        assertThatThrownBy(() -> renderer.render("BOOM"))
                .isInstanceOf(MarkdownRenderingException.class);
    }
}

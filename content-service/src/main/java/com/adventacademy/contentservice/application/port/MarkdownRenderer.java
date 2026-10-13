package com.adventacademy.contentservice.application.port;
/**
 * Port for converting Markdown text to HTML.
 * <p>
 * Hides the concrete Markdown library behind the application layer:
 * implementations live in the infrastructure layer and must not leak
 * library-specific exceptions.
 */

public interface MarkdownRenderer {
    /**
     * Renders the given Markdown text to HTML.
     *
     * @param markdown the Markdown source text; must not be {@code null} or blank
     * @return the rendered HTML, never {@code null}
     * @throws IllegalArgumentException   if {@code markdown} is {@code null} or blank
     * @throws MarkdownRenderingException if the text cannot be rendered
     */
String render(String markdown);

}

package com.adventacademy.contentservice.application.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextNormalizerTest {

    @Test
    void nullInputStaysNull() {
        assertThat(PlainTextNormalizer.normalizeTitle(null)).isNull();
        assertThat(PlainTextNormalizer.normalizeDescription(null)).isNull();
    }

    @Test
    void ordinaryTextIsUnchangedApartFromTrim() {
        assertThat(PlainTextNormalizer.normalizeTitle("  Java для начинающих  "))
                .isEqualTo("Java для начинающих");
    }

    @Test
    void legitimatePunctuationAndUnicodeSurvive() {
        String input = "O'Brien's \"Advanced\" Java — курс №1 (2026)";
        assertThat(PlainTextNormalizer.normalizeTitle(input)).isEqualTo(input);
    }

    @Test
    void literalAmpersandRoundTrips() {
        assertThat(PlainTextNormalizer.normalizeTitle("Spring & Hibernate"))
                .isEqualTo("Spring & Hibernate");
    }

    @Test
    void literalEscapedSequenceTypedByUserIsCanonicalizedToItsCharacter() {
        // Юзер буквально напечатал символы &, l, t, ; — HTML-парсер внутри санитайзера
        // декодирует их как entity для '<': на уровне HTML напечатанное "&lt;" и "<"
        // неразличимы, оба нормализуются в сам символ. Это безопасный plain text.
        assertThat(PlainTextNormalizer.normalizeTitle("5 &lt; 10")).isEqualTo("5 < 10");
    }

    @Test
    void scriptTagAndItsContentAreRemovedEntirely() {
        assertThat(PlainTextNormalizer.normalizeTitle("<script>alert(1)</script>")).isEmpty();
        assertThat(PlainTextNormalizer.normalizeDescription("hello<script>alert(1)</script>world"))
                .isEqualTo("helloworld");
    }

    @Test
    void styleTagAndItsContentAreRemovedEntirely() {
        assertThat(PlainTextNormalizer.normalizeDescription("<style>body{display:none}</style>ok"))
                .isEqualTo("ok");
    }

    @Test
    void imgWithEventHandlerIsRemoved() {
        assertThat(PlainTextNormalizer.normalizeDescription("<img src=x onerror=alert(1)>note"))
                .isEqualTo("note");
    }

    @Test
    void inlineTagsAreStrippedButTextIsKept() {
        assertThat(PlainTextNormalizer.normalizeDescription("<b>bold</b> and <i>italic</i>"))
                .isEqualTo("bold and italic");
    }

    @Test
    void anchorWithJavascriptHrefIsNeutralized() {
        String result = PlainTextNormalizer.normalizeDescription(
                "<a href=\"javascript:alert(1)\">click me</a>");
        assertThat(result).isEqualTo("click me");
        assertThat(result).doesNotContain("javascript:");
    }

    @Test
    void eventAttributeOnAllowedLookingTagIsRemovedAlongWithTag() {
        assertThat(PlainTextNormalizer.normalizeDescription("<div onclick=\"steal()\">text</div>"))
                .isEqualTo("text");
    }

    @Test
    void truncatesToMaxLength() {
        String longTitle = "a".repeat(300);
        assertThat(PlainTextNormalizer.normalizeTitle(longTitle))
                .hasSize(PlainTextNormalizer.DEFAULT_TITLE_MAX_LENGTH);
    }

    @Test
    void knownTradeOff_literalAngleBracketsLookingLikeATagAreDropped() {
        // Documented limitation: HTML-tokenization-based stripping treats "<Foo>" as a
        // tag even when the user meant literal angle brackets. This is intentional and
        // consistent with the rest of the codebase's HTML-based content pipeline.
        assertThat(PlainTextNormalizer.normalizeTitle("5 <Foo> bar")).isEqualTo("5  bar");
    }
}

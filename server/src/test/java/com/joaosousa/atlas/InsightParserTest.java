package com.joaosousa.atlas;

import com.joaosousa.atlas.service.InsightService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers insight-provider-spec.md §10 verification 8. The failure this guards against is
 * silent: a near-miss on the labels used to dump the whole reply, labels included, into the
 * insight body — which reads as an app bug rather than a model that formatted its answer
 * slightly differently.
 */
class InsightParserTest {

    @Test
    void parsesThePlainFormatTheStoredInsightsUse() {
        InsightService.ParsedInsight parsed = InsightService.parseRawText(
                "VERDICT: Plateau — slightly regressing\nINSIGHT: Your weight has been flat.");

        assertEquals("Plateau — slightly regressing", parsed.verdict());
        assertEquals("Your weight has been flat.", parsed.text());
    }

    @Test
    void parsesMarkdownEmphasisAroundTheLabels() {
        InsightService.ParsedInsight parsed = InsightService.parseRawText(
                "**VERDICT:** Strong progress\n\n**INSIGHT:** Fat is dropping steadily.");

        assertEquals("Strong progress", parsed.verdict());
        assertEquals("Fat is dropping steadily.", parsed.text());
    }

    @Test
    void parsesLeadingWhitespaceAndLowercaseLabels() {
        InsightService.ParsedInsight parsed = InsightService.parseRawText(
                "\n\n  verdict: Holding steady\ninsight: Nothing much has changed.");

        assertEquals("Holding steady", parsed.verdict());
        assertEquals("Nothing much has changed.", parsed.text());
    }

    @Test
    void keepsMultiParagraphInsightBodiesWhole() {
        InsightService.ParsedInsight parsed = InsightService.parseRawText(
                "VERDICT: Good\nINSIGHT: First paragraph.\n\nSecond paragraph.");

        assertEquals("Good", parsed.verdict());
        assertEquals("First paragraph.\n\nSecond paragraph.", parsed.text());
    }

    /** A weaker model ignoring the format is a normal outcome, not an error. */
    @Test
    void unlabelledProseBecomesTheBodyWithNoVerdict() {
        InsightService.ParsedInsight parsed = InsightService.parseRawText(
                "You are making steady progress and should keep going.");

        assertNull(parsed.verdict());
        assertEquals("You are making steady progress and should keep going.", parsed.text());
    }

    @Test
    void aVerdictWithNoInsightLabelIsNotHalfParsed() {
        InsightService.ParsedInsight parsed = InsightService.parseRawText("VERDICT: Plateau");

        assertNull(parsed.verdict());
        assertEquals("VERDICT: Plateau", parsed.text());
    }

    @Test
    void nullSurvives() {
        InsightService.ParsedInsight parsed = InsightService.parseRawText(null);

        assertNull(parsed.verdict());
        assertNull(parsed.text());
    }
}

package org.pdfweb.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageRangesTest {

    @Test
    void parseIntListHandlesCommaSeparated() {
        assertThat(PageRanges.parseIntList("1,3,5")).containsExactly(1, 3, 5);
    }

    @Test
    void parseIntListIgnoresBlankAndInvalid() {
        assertThat(PageRanges.parseIntList(" 1 , , x , 4 ")).containsExactly(1, 4);
    }

    @Test
    void parseRangesHandlesMixedTokens() {
        List<PageRanges.Range> ranges = PageRanges.parse("1-3,5,8-10");
        assertThat(ranges).containsExactly(
                new PageRanges.Range(1, 3),
                new PageRanges.Range(5, 5),
                new PageRanges.Range(8, 10));
    }

    @Test
    void parseOpenEndedRangeYieldsZeroEnd() {
        assertThat(PageRanges.parse("3-")).containsExactly(new PageRanges.Range(3, 0));
    }
}

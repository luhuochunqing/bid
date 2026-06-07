package com.xiyu.bid.biddraftagent.domain.validation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QualificationMatcherBugTest {

    @Test
    void testIsSmartMatch() {
        QualificationMatcher matcher = new QualificationMatcher();

        // This simulates: qualification is "ISO" (length 3).
        // It checks if "ISO" is in the text "The required qualification is ISOLATED".
        // The policy is: Word boundary match for short names.
        // Let's test the word boundary logic
        boolean match1 = matcher.isSmartMatch("This is ISOLATED", "ISO");
        assertThat(match1).isFalse();

        boolean match2 = matcher.isSmartMatch("This requires ISO 9001", "ISO");
        assertThat(match2).isTrue();

        // What if qualification name has special regex characters?
        // Like "C++"
        boolean match3 = matcher.isSmartMatch("Experience in C++ is required", "C++");
        assertThat(match3).isTrue();
    }
}

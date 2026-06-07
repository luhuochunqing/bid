// Input: raw user-authored content (with or without @[name](id) tokens)
// Output: ParsedContent value with plainText and distinct mentioned user ids
// Pos: Test/提及解析纯函数门禁
package com.xiyu.bid.mention.core;

import com.xiyu.bid.mention.core.MentionParsingPolicy.ParsedContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MentionParsingPolicy — pure parser")
class MentionParsingPolicyTest {

    @Test
    @DisplayName("null returns empty ParsedContent")
    void nullInput_ReturnsEmpty() {
        ParsedContent result = MentionParsingPolicy.parse(null);

        assertThat(result.plainText()).isEqualTo("");
        assertThat(result.mentionedUserIds()).isEmpty();
    }

    @Test
    @DisplayName("blank returns empty ParsedContent")
    void blankInput_ReturnsEmpty() {
        ParsedContent result = MentionParsingPolicy.parse("   ");

        assertThat(result.plainText()).isEqualTo("");
        assertThat(result.mentionedUserIds()).isEmpty();
    }

    @Test
    @DisplayName("empty string returns empty ParsedContent")
    void emptyInput_ReturnsEmpty() {
        ParsedContent result = MentionParsingPolicy.parse("");

        assertThat(result.plainText()).isEqualTo("");
        assertThat(result.mentionedUserIds()).isEmpty();
    }

    @Test
    @DisplayName("single mention token extracts id and rewrites to plain @name")
    void singleMention_ExtractsIdAndRewrites() {
        ParsedContent result = MentionParsingPolicy.parse("@[张三](7)");

        assertThat(result.plainText()).isEqualTo("@张三");
        assertThat(result.mentionedUserIds()).containsExactly(7L);
    }

    @Test
    @DisplayName("multiple mentions extract all ids in order")
    void multipleMentions_ExtractAll() {
        ParsedContent result = MentionParsingPolicy.parse("@[a](1) and @[b](2)");

        assertThat(result.plainText()).isEqualTo("@a and @b");
        assertThat(result.mentionedUserIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("duplicate ids are collapsed")
    void duplicateIds_AreCollapsed() {
        ParsedContent result = MentionParsingPolicy.parse("@[a](1) @[a](1)");

        assertThat(result.mentionedUserIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("invalid raw @ (no brackets) yields no ids")
    void invalidFormat_YieldsNoIds() {
        ParsedContent result = MentionParsingPolicy.parse("@张三");

        assertThat(result.mentionedUserIds()).isEmpty();
        // plainText must preserve the original text when no token matches
        assertThat(result.plainText()).isEqualTo("@张三");
    }

    @Test
    @DisplayName("caps mentions at MAX_MENTIONS (20) even when input has more")
    void oversizeInput_CapsAtTwentyIds() {
        String content = Stream.iterate(1, i -> i + 1)
            .limit(25)
            .map(i -> "@[u" + i + "](" + i + ")")
            .collect(Collectors.joining(" "));

        ParsedContent result = MentionParsingPolicy.parse(content);

        assertThat(result.mentionedUserIds()).hasSize(20);
        assertThat(result.mentionedUserIds()).isEqualTo(List.of(
            1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
            11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L));
    }
}

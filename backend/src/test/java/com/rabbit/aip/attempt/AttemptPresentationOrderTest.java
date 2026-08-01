package com.rabbit.aip.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttemptPresentationOrderTest {

    private static final UUID ATTEMPT = UUID.fromString(
            "99999999-9999-9999-9999-999999999901"
    );
    private static final List<UUID> ITEMS = List.of(
            UUID.fromString("55555555-5555-5555-5555-555555555501"),
            UUID.fromString("55555555-5555-5555-5555-555555555502"),
            UUID.fromString("55555555-5555-5555-5555-555555555503"),
            UUID.fromString("55555555-5555-5555-5555-555555555504")
    );

    @Test
    void shuffledOrderIsStableForRefreshAndResume() {
        List<UUID> first = AttemptPresentationOrder.order(
                ATTEMPT, "questions", ITEMS, value -> value, true
        );
        List<UUID> resumed = AttemptPresentationOrder.order(
                ATTEMPT, "questions", ITEMS, value -> value, true
        );

        assertThat(first).containsExactlyElementsOf(resumed);
        assertThat(first).containsExactlyInAnyOrderElementsOf(ITEMS);
        assertThat(first).isNotEqualTo(ITEMS);
    }

    @Test
    void disabledShufflePreservesAuthoredOrder() {
        assertThat(AttemptPresentationOrder.order(
                ATTEMPT, "questions", ITEMS, value -> value, false
        )).containsExactlyElementsOf(ITEMS);
    }

    @Test
    void eachAttemptReceivesItsOwnStableOrder() {
        UUID anotherAttempt = UUID.fromString(
                "99999999-9999-9999-9999-999999999902"
        );

        assertThat(AttemptPresentationOrder.order(
                ATTEMPT, "questions", ITEMS, value -> value, true
        )).isNotEqualTo(AttemptPresentationOrder.order(
                anotherAttempt, "questions", ITEMS, value -> value, true
        ));
    }

    @Test
    void questionAndOptionScopesDoNotReuseTheSameOrderingKey() {
        long questionRank = AttemptPresentationOrder.rank(
                ATTEMPT, "questions", ITEMS.get(0)
        );
        long optionRank = AttemptPresentationOrder.rank(
                ATTEMPT, "options:" + ITEMS.get(0), ITEMS.get(0)
        );

        assertThat(questionRank).isNotEqualTo(optionRank);
    }
}

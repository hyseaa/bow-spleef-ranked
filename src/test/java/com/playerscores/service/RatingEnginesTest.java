package com.playerscores.service;

import com.playerscores.dto.InitialRating;
import com.playerscores.dto.RatingResult;
import com.playerscores.dto.TeamRatingInput;
import com.playerscores.model.RatingAlgorithm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-001-05, REQ-001-06: one engine per rating algorithm, checked at startup. */
class RatingEnginesTest {

    @Test
    void returnsTheEngineOfEachAlgorithm() {
        RatingEngine classic = engine(RatingAlgorithm.CLASSIC_ELO);
        RatingEngine openSkill = engine(RatingAlgorithm.OPENSKILL);

        RatingEngines engines = new RatingEngines(List.of(openSkill, classic));

        assertThat(engines.get(RatingAlgorithm.CLASSIC_ELO)).isSameAs(classic);
        assertThat(engines.get(RatingAlgorithm.OPENSKILL)).isSameAs(openSkill);
    }

    @Test
    void missingEngine_failsAtStartup() {
        assertThatThrownBy(() -> new RatingEngines(List.of(engine(RatingAlgorithm.OPENSKILL))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLASSIC_ELO");
    }

    @Test
    void duplicateEngine_failsAtStartup() {
        assertThatThrownBy(() -> new RatingEngines(List.of(
                engine(RatingAlgorithm.OPENSKILL), engine(RatingAlgorithm.CLASSIC_ELO), engine(RatingAlgorithm.OPENSKILL))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENSKILL");
    }

    private static RatingEngine engine(RatingAlgorithm algorithm) {
        return new RatingEngine() {
            @Override
            public RatingAlgorithm algorithm() {
                return algorithm;
            }

            @Override
            public InitialRating initialRating() {
                return new InitialRating(1000, 25.0, 25.0 / 3);
            }

            @Override
            public Map<UUID, RatingResult> rateMatch(List<TeamRatingInput> teams) {
                return Map.of();
            }
        };
    }
}

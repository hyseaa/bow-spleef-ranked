package com.playerscores.rating;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerscores.dto.InitialRating;
import com.playerscores.dto.PlayerEloSnapshot;
import com.playerscores.dto.RatingResult;
import com.playerscores.dto.TeamRatingInput;
import com.playerscores.service.RatingEngine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden rating data for spec 001 (task T1): match sequences in {@code rating/golden-sequences.json}
 * and the values the original implementations produced, in {@code rating/golden-classic.json}
 * (api@760113a) and {@code rating/golden-openskill.json} (api@a4a8c08).
 */
public final class GoldenRatings {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** One match: per team, its score and players, in rating order. */
    public record GoldenTeam(int score, List<UUID> players) {}

    private GoldenRatings() {
    }

    public static List<String> sequenceNames() {
        List<String> names = new ArrayList<>();
        load("rating/golden-sequences.json").get("sequences").fieldNames().forEachRemaining(names::add);
        return names;
    }

    public static List<List<GoldenTeam>> sequence(String name) {
        List<List<GoldenTeam>> matches = new ArrayList<>();
        for (JsonNode match : load("rating/golden-sequences.json").get("sequences").get(name)) {
            List<GoldenTeam> teams = new ArrayList<>();
            for (JsonNode team : match.get("teams")) {
                List<UUID> players = new ArrayList<>();
                team.get("players").forEach(p -> players.add(UUID.fromString(p.asText())));
                teams.add(new GoldenTeam(team.get("score").asInt(), players));
            }
            matches.add(teams);
        }
        return matches;
    }

    /** Expected values per match: player → node with before, after, matchesPlayedBefore (and mu, sigma for OpenSkill). */
    public static List<Map<UUID, JsonNode>> expected(String file, String sequence) {
        List<Map<UUID, JsonNode>> out = new ArrayList<>();
        for (JsonNode match : load(file).get("sequences").get(sequence)) {
            Map<UUID, JsonNode> players = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = match.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                players.put(UUID.fromString(e.getKey()), e.getValue());
            }
            out.add(players);
        }
        return out;
    }

    /**
     * Replays a sequence through an engine the way EloRecomputeService does (new players start
     * with the engine's initial rating; ratings and matches played are updated after each match)
     * and asserts every value against the golden file.
     */
    public static void assertEngineMatchesGolden(RatingEngine engine, String file, String sequenceName, boolean checkMuSigma) {
        List<List<GoldenTeam>> matches = sequence(sequenceName);
        List<Map<UUID, JsonNode>> expected = expected(file, sequenceName);
        InitialRating initial = engine.initialRating();
        Map<UUID, PlayerEloSnapshot> state = new LinkedHashMap<>();

        for (int m = 0; m < matches.size(); m++) {
            List<TeamRatingInput> inputs = new ArrayList<>();
            long teamId = 0;
            for (GoldenTeam team : matches.get(m)) {
                List<PlayerEloSnapshot> players = new ArrayList<>();
                for (UUID uuid : team.players()) {
                    PlayerEloSnapshot s = state.computeIfAbsent(uuid, u -> snapshot(u, initial.elo(), initial.mu(), initial.sigma(), 0));
                    players.add(snapshot(uuid, s.getElo(), s.getMu(), s.getSigma(), s.getMatchesPlayed()));
                }
                inputs.add(new TeamRatingInput(teamId++, team.score(), players));
            }
            Map<UUID, RatingResult> results = engine.rateMatch(inputs);
            for (Map.Entry<UUID, JsonNode> e : expected.get(m).entrySet()) {
                PlayerEloSnapshot s = state.get(e.getKey());
                RatingResult r = results.get(e.getKey());
                String where = sequenceName + " match " + m + " player " + e.getKey();
                assertThat(s.getElo()).as(where + " before").isEqualTo(e.getValue().get("before").asInt());
                assertThat(s.getMatchesPlayed()).as(where + " matches played").isEqualTo(e.getValue().get("matchesPlayedBefore").asInt());
                assertThat(r.elo()).as(where + " after").isEqualTo(e.getValue().get("after").asInt());
                if (checkMuSigma) {
                    assertThat(r.mu()).as(where + " mu").isEqualTo(e.getValue().get("mu").asDouble());
                    assertThat(r.sigma()).as(where + " sigma").isEqualTo(e.getValue().get("sigma").asDouble());
                }
                s.setElo(r.elo());
                s.setMu(r.mu());
                s.setSigma(r.sigma());
                s.setMatchesPlayed(s.getMatchesPlayed() + 1);
            }
        }
    }

    public static PlayerEloSnapshot snapshot(UUID uuid, int elo, double mu, double sigma, int matchesPlayed) {
        PlayerEloSnapshot s = new PlayerEloSnapshot();
        s.setPlayerUuid(uuid);
        s.setElo(elo);
        s.setMu(mu);
        s.setSigma(sigma);
        s.setMatchesPlayed(matchesPlayed);
        return s;
    }

    private static JsonNode load(String resource) {
        try (InputStream in = GoldenRatings.class.getClassLoader().getResourceAsStream(resource)) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

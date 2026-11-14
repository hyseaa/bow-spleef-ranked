package net.bowspleef.ranked.client;

import net.bowspleef.ranked.config.WebhookProperties;
import net.bowspleef.ranked.dto.DuelCancelledPayload;
import net.bowspleef.ranked.dto.MatchResponse;
import net.bowspleef.ranked.dto.MatchWebhookPayload;
import net.bowspleef.ranked.dto.RankSnapshotPayload;
import net.bowspleef.ranked.dto.SeasonDeactivatedPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** KI-07: a webhook is sent only after the transaction that triggered it has committed. */
class MatchWebhookClientTest {

    private static final String URL = "http://bot.test/internal/match-result";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final List<String> executed = new ArrayList<>();

    private MatchWebhookClient client(boolean enabled) {
        // synchronous executor: the thread hand-off is not what is tested here
        return new MatchWebhookClient(new WebhookProperties(enabled, URL, "secret"), builder, command -> {
            executed.add("sent");
            command.run();
        });
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void outsideATransaction_isSentAtOnce_withTheBearerSecret() {
        server.expect(requestTo(URL)).andExpect(method(POST)).andExpect(header("Authorization", "Bearer secret"))
                .andRespond(withSuccess());

        client(true).notifyRanksUpdated(ranks());

        server.verify();
    }

    @Test
    void insideATransaction_isNotSentBeforeCommit_REQ_KI07() {
        TransactionSynchronizationManager.initSynchronization();

        client(true).notifyMatchResult(match());
        client(true).notifyRanksUpdated(ranks());

        assertThat(executed).as("nothing handed to the sender before the commit").isEmpty();
        server.verify(); // no request expected, none made
    }

    @Test
    void insideATransaction_isSentAfterCommit_inTheOrderOfTheCalls_REQ_KI07() {
        server.expect(requestTo(URL)).andRespond(withSuccess());
        server.expect(requestTo(URL)).andRespond(withSuccess());
        TransactionSynchronizationManager.initSynchronization();

        MatchWebhookClient client = client(true);
        client.notifyMatchResult(match());
        client.notifyRanksUpdated(ranks());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(executed).hasSize(2);
        server.verify();
    }

    @Test
    void aRolledBackTransactionSendsNothing_REQ_KI07() {
        TransactionSynchronizationManager.initSynchronization();

        client(true).notifyMatchResult(match());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(executed).isEmpty();
        server.verify();
    }

    @Test
    void disabledWebhookSendsNothing() {
        client(false).notifyMatchResult(match());
        client(false).notifyRanksUpdated(ranks());

        assertThat(executed).isEmpty();
        server.verify();
    }

    @Test
    void aFailingBotDoesNotBreakTheCaller() {
        server.expect(requestTo(URL)).andRespond(withStatus(INTERNAL_SERVER_ERROR));

        client(true).notifyRanksUpdated(ranks());

        server.verify();
    }

    @Test
    void duelCancelledIsSentWithItsEventDuelAndReason_REQ_003_10() {
        server.expect(requestTo(URL)).andExpect(method(POST)).andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.event").value("DUEL_CANCELLED"))
                .andExpect(jsonPath("$.duelId").value(894))
                .andExpect(jsonPath("$.reason").value("AGREEMENT"))
                .andRespond(withSuccess());

        client(true).notifyDuelCancelled(new DuelCancelledPayload(894L, "AGREEMENT"));

        server.verify();
    }

    @Test
    void duelCancelledIsNotSentBeforeCommitNorAfterARollback_REQ_003_10() {
        TransactionSynchronizationManager.initSynchronization();

        client(true).notifyDuelCancelled(new DuelCancelledPayload(894L, "AGREEMENT"));
        assertThat(executed).as("nothing before the commit").isEmpty();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(executed).isEmpty();
        server.verify();
    }

    @Test
    void duelCancelledIsNotSentWhenTheWebhookIsDisabled_REQ_003_10() {
        client(false).notifyDuelCancelled(new DuelCancelledPayload(894L, "AGREEMENT"));

        assertThat(executed).isEmpty();
        server.verify();
    }

    @Test
    void seasonDeactivatedIsSentWithItsEventSeasonAndGameType_REQ_005_04() {
        server.expect(requestTo(URL)).andExpect(method(POST)).andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.event").value("SEASON_DEACTIVATED"))
                .andExpect(jsonPath("$.seasonId").value(2))
                .andExpect(jsonPath("$.gameType").value("bowspleef_duel_ranked"))
                .andRespond(withSuccess());

        client(true).notifySeasonDeactivated(new SeasonDeactivatedPayload(2L, "bowspleef_duel_ranked"));

        server.verify();
    }

    @Test
    void seasonDeactivatedIsNotSentBeforeCommitNorAfterARollback_REQ_005_04() {
        TransactionSynchronizationManager.initSynchronization();

        client(true).notifySeasonDeactivated(new SeasonDeactivatedPayload(2L, "gt"));
        assertThat(executed).as("nothing before the commit").isEmpty();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(executed).isEmpty();
        server.verify();
    }

    @Test
    void seasonDeactivatedIsNotSentWhenTheWebhookIsDisabled_REQ_005_04() {
        client(false).notifySeasonDeactivated(new SeasonDeactivatedPayload(2L, "gt"));

        assertThat(executed).isEmpty();
        server.verify();
    }

    private static MatchWebhookPayload match() {
        return new MatchWebhookPayload(new MatchResponse(1L, "gt", "GT", "test", null, List.of(), 1L, null), List.of());
    }

    private static RankSnapshotPayload ranks() {
        return new RankSnapshotPayload("RANKS_UPDATED", 1L, "gt", true, List.of());
    }
}

package net.bowspleef.ranked.service;

import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.ReplayAddedPayload;
import net.bowspleef.ranked.dto.ReplayFile;
import net.bowspleef.ranked.exception.InvalidReplayException;
import net.bowspleef.ranked.exception.MatchNotFoundException;
import net.bowspleef.ranked.exception.ReplayAlreadyExistsException;
import net.bowspleef.ranked.exception.ReplayNotFoundException;
import net.bowspleef.ranked.exception.ReplayTooLargeException;
import net.bowspleef.ranked.mapper.MatchMapper;
import net.bowspleef.ranked.mapper.ReplayMapper;
import net.bowspleef.ranked.model.Match;
import net.bowspleef.ranked.model.MatchReplay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Spec 024, ADR-0026: a replay is stored once per match, opaque, within a size limit. */
@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

    private static final long MATCH = 412L;
    private static final byte[] BYTES = {1, 2, 3, 4};

    @Mock
    private MatchMapper matchMapper;
    @Mock
    private ReplayMapper replayMapper;
    @Mock
    private MatchWebhookClient webhookClient;
    @Mock
    private UsernameCache usernameCache;

    private ReplayService service;

    @BeforeEach
    void setUp() {
        service = new ReplayService(matchMapper, replayMapper, webhookClient, usernameCache, 10);
    }

    private void matchExists() {
        when(matchMapper.findById(MATCH)).thenReturn(Optional.of(mock(Match.class)));
    }

    @Test
    void aReplayForAMissingMatchIsRefused_REQ_024_14() {
        when(matchMapper.findById(MATCH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.store(MATCH, "dread-pit", 690, 1, BYTES))
                .isInstanceOf(MatchNotFoundException.class);
        verify(replayMapper, never()).insert(any());
    }

    @Test
    void anEmptyBodyOrABadParameterIsRefused_REQ_024_14() {
        assertThatThrownBy(() -> service.store(MATCH, "dread-pit", 690, 1, new byte[0]))
                .isInstanceOf(InvalidReplayException.class);
        assertThatThrownBy(() -> service.store(MATCH, "dread-pit", 690, 1, null))
                .isInstanceOf(InvalidReplayException.class);
        assertThatThrownBy(() -> service.store(MATCH, "Dread Pit!", 690, 1, BYTES))
                .isInstanceOf(InvalidReplayException.class);
        assertThatThrownBy(() -> service.store(MATCH, "", 690, 1, BYTES))
                .isInstanceOf(InvalidReplayException.class);
        assertThatThrownBy(() -> service.store(MATCH, "dread-pit", 0, 1, BYTES))
                .isInstanceOf(InvalidReplayException.class);
        assertThatThrownBy(() -> service.store(MATCH, "dread-pit", ReplayService.MAX_DURATION_TICKS + 1, 1, BYTES))
                .isInstanceOf(InvalidReplayException.class);
        assertThatThrownBy(() -> service.store(MATCH, "dread-pit", 690, 0, BYTES))
                .isInstanceOf(InvalidReplayException.class);
        verifyNoInteractions(replayMapper, webhookClient);
    }

    @Test
    void aReplayAboveTheLimitIsRefused_REQ_024_14() {
        assertThatThrownBy(() -> service.store(MATCH, "dread-pit", 690, 1, new byte[11]))
                .isInstanceOf(ReplayTooLargeException.class);
        verifyNoInteractions(replayMapper, webhookClient);
    }

    @Test
    void theFirstReplayIsStoredAndTheBotIsTold_REQ_024_03_REQ_024_19() {
        matchExists();
        when(replayMapper.findSha256(MATCH)).thenReturn(Optional.empty());
        when(replayMapper.insert(any())).thenReturn(1);

        assertThat(service.store(MATCH, "dread-pit", 690, 1, BYTES)).isTrue();

        ArgumentCaptor<MatchReplay> stored = ArgumentCaptor.forClass(MatchReplay.class);
        verify(replayMapper).insert(stored.capture());
        assertThat(stored.getValue().getMatchId()).isEqualTo(MATCH);
        assertThat(stored.getValue().getMap()).isEqualTo("dread-pit");
        assertThat(stored.getValue().getDurationTicks()).isEqualTo(690);
        assertThat(stored.getValue().getFormatVersion()).isEqualTo(1);
        assertThat(stored.getValue().getSizeBytes()).isEqualTo(4);
        assertThat(stored.getValue().getSha256()).hasSize(64);
        assertThat(stored.getValue().getData()).isEqualTo(BYTES);
        verify(webhookClient).notifyReplayAdded(new ReplayAddedPayload(MATCH));
    }

    @Test
    void theSameBytesAgainAreNotStoredTwiceNorAnnounced_REQ_024_14() {
        matchExists();
        when(replayMapper.findSha256(MATCH)).thenReturn(Optional.of(ReplayService.sha256(BYTES)));

        assertThat(service.store(MATCH, "dread-pit", 690, 1, BYTES)).isFalse();

        verify(replayMapper, never()).insert(any());
        verifyNoInteractions(webhookClient);
    }

    @Test
    void otherBytesForAMatchThatHasAReplayAreRefused_REQ_024_14() {
        matchExists();
        when(replayMapper.findSha256(MATCH)).thenReturn(Optional.of(ReplayService.sha256(new byte[]{9})));

        assertThatThrownBy(() -> service.store(MATCH, "dread-pit", 690, 1, BYTES))
                .isInstanceOf(ReplayAlreadyExistsException.class);
        verify(replayMapper, never()).insert(any());
        verifyNoInteractions(webhookClient);
    }

    @Test
    void twoCallsAtOnce_theSecondIsTheSameFileOrRefused_REQ_024_14() {
        matchExists();
        when(replayMapper.findSha256(MATCH)).thenReturn(Optional.empty(), Optional.of(ReplayService.sha256(BYTES)));
        when(replayMapper.insert(any())).thenReturn(0);

        assertThat(service.store(MATCH, "dread-pit", 690, 1, BYTES)).isFalse();
        verifyNoInteractions(webhookClient);
    }

    @Test
    void aStoredReplayIsReadBack_REQ_024_05() {
        MatchReplay row = new MatchReplay();
        row.setMatchId(MATCH);
        row.setMap("dread-pit");
        row.setDurationTicks(690);
        row.setFormatVersion(1);
        row.setData(BYTES);
        when(replayMapper.findByMatch(MATCH)).thenReturn(Optional.of(row));

        assertThat(service.read(MATCH)).isEqualTo(new ReplayFile("dread-pit", 690, 1, BYTES));
    }

    @Test
    void readingAMissingReplayIsRefused_REQ_024_10() {
        when(replayMapper.findByMatch(MATCH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read(MATCH)).isInstanceOf(ReplayNotFoundException.class);
    }
}

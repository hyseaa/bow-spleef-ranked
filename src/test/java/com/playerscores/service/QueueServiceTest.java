package com.playerscores.service;

import com.playerscores.exception.QueueTeamGameTypeException;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.PlayerStatsMapper;
import com.playerscores.mapper.QueueMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.GameType;
import com.playerscores.model.Player;
import com.playerscores.model.RankedSeason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private RankedSeasonMapper rankedSeasonMapper;
    @Mock
    private PlayerStatsMapper playerStatsMapper;
    @Mock
    private QueueMapper queueMapper;
    @Mock
    private GameTypeMapper gameTypeMapper;
    @Mock
    private UsernameCache usernameCache;

    @InjectMocks
    private QueueService queueService;

    private GameType gameType(String name, int teamSize) {
        GameType gt = new GameType();
        gt.setName(name);
        gt.setDisplayName(name);
        gt.setRanked(true);
        gt.setTeamSize(teamSize);
        return gt;
    }

    @Test
    void join_teamGameType_isRefusedBeforeAnythingIsQueued_KI_03() {
        when(gameTypeMapper.findByName("duel_2v2")).thenReturn(Optional.of(gameType("duel_2v2", 2)));

        assertThatThrownBy(() -> queueService.join("123", "duel_2v2"))
                .isInstanceOf(QueueTeamGameTypeException.class);

        verify(queueMapper, never()).upsert(any());
        verifyNoInteractions(playerMapper, rankedSeasonMapper);
    }

    @Test
    void join_soloGameType_isQueued_KI_03() {
        Player player = new Player();
        player.setUuid(UUID.randomUUID());
        RankedSeason season = new RankedSeason();
        season.setId(5L);
        when(gameTypeMapper.findByName("duel")).thenReturn(Optional.of(gameType("duel", 1)));
        when(playerMapper.findByDiscordId("123")).thenReturn(Optional.of(player));
        when(rankedSeasonMapper.findActiveByGameType("duel")).thenReturn(Optional.of(season));
        when(playerStatsMapper.findSeasonElo(any(), any())).thenReturn(Optional.empty());
        when(queueMapper.findMatch(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Optional.empty());

        assertThat(queueService.join("123", "duel").matched()).isFalse();
        verify(queueMapper).upsert(any());
    }
}

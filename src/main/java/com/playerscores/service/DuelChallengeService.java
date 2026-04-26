package com.playerscores.service;

import com.playerscores.dto.CreateDuelChallengeRequest;
import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.DuelChallengeResponse;
import com.playerscores.dto.MatchResponse;
import com.playerscores.dto.ReportDuelScoresRequest;
import com.playerscores.dto.TeamRequest;
import com.playerscores.exception.DuelChallengeForbiddenException;
import com.playerscores.exception.DuelChallengeNotFoundException;
import com.playerscores.exception.DuelChallengeNotPendingException;
import com.playerscores.exception.GameTypeNotFoundException;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.mapper.DuelChallengeMapper;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.DuelChallenge;
import com.playerscores.model.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DuelChallengeService {

    private final DuelChallengeMapper duelChallengeMapper;
    private final PlayerMapper playerMapper;
    private final GameTypeMapper gameTypeMapper;
    private final MatchService matchService;
    private final UsernameCache usernameCache;

    @Value("${duel.challenge-ttl-minutes:60}")
    private int challengeTtlMinutes;

    @Transactional
    public DuelChallengeResponse create(CreateDuelChallengeRequest request) {
        log.info("Creating duel challenge: challengerDiscordId={}, challengedDiscordId={}, gameType={}",
                request.challengerDiscordId(), request.challengedDiscordId(), request.gameType());

        Player challenger = playerMapper.findByDiscordId(request.challengerDiscordId())
                .orElseThrow(() -> new PlayerNotFoundException(request.challengerDiscordId()));
        Player challenged = playerMapper.findByDiscordId(request.challengedDiscordId())
                .orElseThrow(() -> new PlayerNotFoundException(request.challengedDiscordId()));

        gameTypeMapper.findByName(request.gameType())
                .orElseThrow(() -> new GameTypeNotFoundException(request.gameType()));

        DuelChallenge challenge = new DuelChallenge();
        challenge.setChallengerUuid(challenger.getUuid());
        challenge.setChallengedUuid(challenged.getUuid());
        challenge.setGameType(request.gameType());
        challenge.setExpiresAt(OffsetDateTime.now().plusMinutes(challengeTtlMinutes));
        duelChallengeMapper.insert(challenge);

        log.info("Duel challenge created: id={}, challenger={}, challenged={}, gameType={}",
                challenge.getId(), challenger.getUuid(), challenged.getUuid(), request.gameType());
        return toResponse(challenge);
    }

    @Transactional(readOnly = true)
    public DuelChallengeResponse getChallenge(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public DuelChallengeResponse accept(Long id, String challengedDiscordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID callerUuid = resolveDiscordId(challengedDiscordId);
        requireChallenged(challenge, callerUuid);

        int updated = duelChallengeMapper.updateStatus(id, "PENDING", "ACTIVE");
        if (updated == 0) {
            throw new DuelChallengeNotPendingException(id, challenge.getStatus());
        }
        log.info("Duel challenge accepted: id={}", id);
        challenge.setStatus("ACTIVE");
        return toResponse(challenge);
    }

    @Transactional
    public DuelChallengeResponse refuse(Long id, String challengedDiscordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID callerUuid = resolveDiscordId(challengedDiscordId);
        requireChallenged(challenge, callerUuid);

        int updated = duelChallengeMapper.updateStatus(id, "PENDING", "REFUSED");
        if (updated == 0) {
            throw new DuelChallengeNotPendingException(id, challenge.getStatus());
        }
        log.info("Duel challenge refused: id={}", id);
        challenge.setStatus("REFUSED");
        return toResponse(challenge);
    }

    @Transactional
    public DuelChallengeResponse cancel(Long id, String discordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID callerUuid = resolveDiscordId(discordId);

        if (!callerUuid.equals(challenge.getChallengerUuid()) && !callerUuid.equals(challenge.getChallengedUuid())) {
            throw new DuelChallengeForbiddenException("Only a participant can cancel a duel challenge");
        }

        int updated = duelChallengeMapper.cancel(id);
        if (updated == 0) {
            throw new DuelChallengeNotPendingException(id, challenge.getStatus());
        }
        log.info("Duel challenge cancelled: id={}", id);
        challenge.setStatus("CANCELLED");
        return toResponse(challenge);
    }

    @Transactional
    public DuelChallengeResponse reportScores(Long id, ReportDuelScoresRequest request) {
        DuelChallenge challenge = findOrThrow(id);
        UUID reporterUuid = resolveDiscordId(request.reporterDiscordId());
        requireParticipant(challenge, reporterUuid);

        int updated = duelChallengeMapper.setReportedScores(
                id, reporterUuid, request.scoreChallenger(), request.scoreChallenged());
        if (updated == 0) {
            throw new DuelChallengeNotPendingException(id, challenge.getStatus());
        }
        log.info("Duel scores reported: id={}, reporter={}, scores={}-{}",
                id, reporterUuid, request.scoreChallenger(), request.scoreChallenged());
        challenge.setStatus("REPORTED");
        challenge.setReporterUuid(reporterUuid);
        challenge.setScoreChallenger(request.scoreChallenger());
        challenge.setScoreChallenged(request.scoreChallenged());
        return toResponse(challenge);
    }

    @Transactional
    public DuelChallengeResponse confirmScores(Long id, String confirmerDiscordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID confirmerUuid = resolveDiscordId(confirmerDiscordId);
        requireParticipant(challenge, confirmerUuid);

        if (confirmerUuid.equals(challenge.getReporterUuid())) {
            throw new DuelChallengeForbiddenException("The reporter cannot confirm their own score report");
        }
        if (!"REPORTED".equals(challenge.getStatus())) {
            throw new DuelChallengeNotPendingException(id, challenge.getStatus());
        }

        CreateMatchRequest matchRequest = new CreateMatchRequest(
                challenge.getGameType(),
                "discord_bot",
                List.of(
                        new TeamRequest(challenge.getScoreChallenger(), List.of(challenge.getChallengerUuid())),
                        new TeamRequest(challenge.getScoreChallenged(), List.of(challenge.getChallengedUuid()))
                ),
                null
        );
        MatchResponse match = matchService.createMatch(matchRequest);
        duelChallengeMapper.setMatchId(id, match.id());

        log.info("Duel challenge completed: id={}, matchId={}", id, match.id());
        challenge.setStatus("COMPLETED");
        challenge.setMatchId(match.id());
        return toResponse(challenge);
    }

    @Transactional
    public DuelChallengeResponse disputeScores(Long id, String disputerDiscordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID disputerUuid = resolveDiscordId(disputerDiscordId);
        requireParticipant(challenge, disputerUuid);

        if (disputerUuid.equals(challenge.getReporterUuid())) {
            throw new DuelChallengeForbiddenException("The reporter cannot dispute their own score report");
        }
        if (!"REPORTED".equals(challenge.getStatus())) {
            throw new DuelChallengeNotPendingException(id, challenge.getStatus());
        }

        int updated = duelChallengeMapper.clearReportedScores(id);
        if (updated == 0) {
            throw new DuelChallengeNotPendingException(id, challenge.getStatus());
        }
        log.info("Duel scores disputed: id={}, disputer={}", id, disputerUuid);
        challenge.setStatus("ACTIVE");
        challenge.setReporterUuid(null);
        challenge.setScoreChallenger(null);
        challenge.setScoreChallenged(null);
        return toResponse(challenge);
    }

    private DuelChallenge findOrThrow(Long id) {
        return duelChallengeMapper.findById(id)
                .orElseThrow(() -> new DuelChallengeNotFoundException(id));
    }

    private UUID resolveDiscordId(String discordId) {
        return playerMapper.findByDiscordId(discordId)
                .map(Player::getUuid)
                .orElseThrow(() -> new PlayerNotFoundException(discordId));
    }

    private void requireChallenged(DuelChallenge challenge, UUID callerUuid) {
        if (!callerUuid.equals(challenge.getChallengedUuid())) {
            throw new DuelChallengeForbiddenException("Only the challenged player can perform this action");
        }
    }

    private void requireParticipant(DuelChallenge challenge, UUID callerUuid) {
        if (!callerUuid.equals(challenge.getChallengerUuid()) && !callerUuid.equals(challenge.getChallengedUuid())) {
            throw new DuelChallengeForbiddenException("Only a participant can perform this action");
        }
    }

    private DuelChallengeResponse toResponse(DuelChallenge c) {
        return new DuelChallengeResponse(
                c.getId(),
                c.getChallengerUuid(),
                usernameCache.get(c.getChallengerUuid()),
                c.getChallengedUuid(),
                usernameCache.get(c.getChallengedUuid()),
                c.getGameType(),
                c.getStatus(),
                c.getReporterUuid(),
                c.getScoreChallenger(),
                c.getScoreChallenged(),
                c.getMatchId(),
                c.getCreatedAt(),
                c.getExpiresAt()
        );
    }
}

package com.playerscores.service;

import com.playerscores.client.MatchWebhookClient;
import com.playerscores.dto.CreateDuelChallengeRequest;
import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.DuelCancelledPayload;
import com.playerscores.dto.DuelChallengeResponse;
import com.playerscores.dto.MatchResponse;
import com.playerscores.dto.PlayerSummaryResponse;
import com.playerscores.dto.ReportDuelScoresRequest;
import com.playerscores.dto.TeamRequest;
import com.playerscores.exception.DuelAlreadyOpenException;
import com.playerscores.exception.DuelCancelAlreadyRequestedException;
import com.playerscores.exception.DuelCancelNeedsAgreementException;
import com.playerscores.exception.DuelChallengeForbiddenException;
import com.playerscores.exception.DuelChallengeNotFoundException;
import com.playerscores.exception.DuelChallengeNotPendingException;
import com.playerscores.exception.DuplicatePlayerInMatchException;
import com.playerscores.exception.GameTypeNotFoundException;
import com.playerscores.exception.InvalidTeamSizeException;
import com.playerscores.exception.NoActiveRankedSeasonException;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.exception.PlayerNotInPartyException;
import com.playerscores.mapper.DuelChallengeMapper;
import com.playerscores.mapper.DuelChallengeParticipantMapper;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.PartyMapper;
import com.playerscores.mapper.PartyMemberMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.DuelChallenge;
import com.playerscores.model.DuelChallengeParticipant;
import com.playerscores.model.GameType;
import com.playerscores.model.Party;
import com.playerscores.model.PartyMember;
import com.playerscores.model.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DuelChallengeService {

    private final DuelChallengeMapper duelChallengeMapper;
    private final DuelChallengeParticipantMapper participantMapper;
    private final PlayerMapper playerMapper;
    private final GameTypeMapper gameTypeMapper;
    private final PartyMapper partyMapper;
    private final PartyMemberMapper partyMemberMapper;
    private final MatchService matchService;
    private final UsernameCache usernameCache;
    private final MatchWebhookClient matchWebhookClient;

    @Value("${duel.challenge-ttl-minutes:60}")
    private int challengeTtlMinutes;

    @Value("${duel.active-expiry-hours:24}")
    private int activeExpiryHours;

    @Transactional
    public DuelChallengeResponse create(CreateDuelChallengeRequest request) {
        log.info("Creating duel challenge: challengerDiscordId={}, challengedDiscordId={}, gameType={}",
                request.challengerDiscordId(), request.challengedDiscordId(), request.gameType());

        Player challenger = playerMapper.findByDiscordId(request.challengerDiscordId())
                .orElseThrow(() -> new PlayerNotFoundException(request.challengerDiscordId()));
        Player challenged = playerMapper.findByDiscordId(request.challengedDiscordId())
                .orElseThrow(() -> new PlayerNotFoundException(request.challengedDiscordId()));

        GameType gameType = gameTypeMapper.findByName(request.gameType())
                .orElseThrow(() -> new GameTypeNotFoundException(request.gameType()));

        List<UUID> challengerRoster;
        List<UUID> challengedRoster;
        UUID challengedLeaderUuid;
        if (gameType.getTeamSize() == 1) {
            // 1v1 stays party-less: two players challenge each other directly
            challengerRoster = List.of(challenger.getUuid());
            challengedRoster = List.of(challenged.getUuid());
            challengedLeaderUuid = challenged.getUuid();
        } else {
            Party challengerParty = findPartyOf(challenger.getUuid())
                    .filter(p -> p.getLeaderUuid().equals(challenger.getUuid()))
                    .orElseThrow(() -> new DuelChallengeForbiddenException(
                            "Team challenges can only be created by a party leader"));
            Party challengedParty = findPartyOf(challenged.getUuid())
                    .orElseThrow(() -> new PlayerNotInPartyException(request.challengedDiscordId()));
            if (challengerParty.getId().equals(challengedParty.getId())) {
                throw new DuplicatePlayerInMatchException("A party cannot challenge itself");
            }
            challengerRoster = memberUuids(challengerParty);
            challengedRoster = memberUuids(challengedParty);
            // The challenge is addressed to the opposing party's leader,
            // even when the request targeted one of its regular members
            challengedLeaderUuid = challengedParty.getLeaderUuid();
        }
        validateRosters(gameType, challengerRoster, challengedRoster);

        // KI-09: one open duel at a time between the same two players for a game type
        rejectIfAlreadyOpen(challenger.getUuid(), challengedLeaderUuid, request.gameType());

        DuelChallenge challenge = new DuelChallenge();
        challenge.setChallengerUuid(challenger.getUuid());
        challenge.setChallengedUuid(challengedLeaderUuid);
        challenge.setGameType(request.gameType());
        challenge.setExpiresAt(OffsetDateTime.now().plusMinutes(challengeTtlMinutes));
        duelChallengeMapper.insert(challenge);

        insertParticipants(challenge.getId(), challengerRoster, DuelChallengeParticipant.SIDE_CHALLENGER);
        insertParticipants(challenge.getId(), challengedRoster, DuelChallengeParticipant.SIDE_CHALLENGED);

        log.info("Duel challenge created: id={}, challenger={}, challenged={}, gameType={}, teamSize={}",
                challenge.getId(), challenger.getUuid(), challenge.getChallengedUuid(),
                request.gameType(), gameType.getTeamSize());
        return toResponse(challenge);
    }

    private void rejectIfAlreadyOpen(UUID one, UUID other, String gameType) {
        String first = one.compareTo(other) <= 0 ? one.toString() : other.toString();
        String second = one.compareTo(other) <= 0 ? other.toString() : one.toString();
        duelChallengeMapper.lockPair(first + ":" + second + ":" + gameType);
        if (duelChallengeMapper.existsOpenBetween(one, other, gameType)) {
            log.warn("Duel challenge refused: an open duel already exists for {} and {} in {}", one, other, gameType);
            throw new DuelAlreadyOpenException(gameType);
        }
    }

    private Optional<Party> findPartyOf(UUID playerUuid) {
        return partyMemberMapper.findByPlayerUuid(playerUuid)
                .flatMap(m -> partyMapper.findById(m.getPartyId()));
    }

    /** The party's roster in join order (the leader first). */
    private List<UUID> memberUuids(Party party) {
        return partyMemberMapper.findByPartyId(party.getId()).stream()
                .map(PartyMember::getPlayerUuid)
                .toList();
    }

    private void validateRosters(GameType gameType, List<UUID> challengerRoster, List<UUID> challengedRoster) {
        if (challengerRoster.size() != gameType.getTeamSize()) {
            throw new InvalidTeamSizeException(gameType.getName(), gameType.getTeamSize(), challengerRoster.size());
        }
        if (challengedRoster.size() != gameType.getTeamSize()) {
            throw new InvalidTeamSizeException(gameType.getName(), gameType.getTeamSize(), challengedRoster.size());
        }
        Set<UUID> seen = new HashSet<>();
        for (List<UUID> roster : List.of(challengerRoster, challengedRoster)) {
            for (UUID uuid : roster) {
                if (!seen.add(uuid)) {
                    throw new DuplicatePlayerInMatchException(uuid);
                }
            }
        }
    }

    private void insertParticipants(Long challengeId, List<UUID> roster, String side) {
        for (UUID uuid : roster) {
            DuelChallengeParticipant participant = new DuelChallengeParticipant();
            participant.setChallengeId(challengeId);
            participant.setPlayerUuid(uuid);
            participant.setSide(side);
            participantMapper.insert(participant);
        }
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
        // Once accepted, a duel is only cancelled with the other player's agreement (spec 003)
        if (isCancellable(challenge)) {
            throw new DuelCancelNeedsAgreementException(id, challenge.getStatus());
        }

        int updated = duelChallengeMapper.cancel(id);
        if (updated == 0) {
            throw new DuelChallengeNotPendingException(id, challenge.getStatus());
        }
        log.info("Duel challenge cancelled: id={}", id);
        challenge.setStatus("CANCELLED");
        return toResponse(challenge);
    }

    /**
     * Asks the other participant to agree to cancel an ACTIVE or REPORTED duel (spec 003). Only the request is
     * recorded here; the duel is cancelled by {@link #acceptCancel}.
     */
    @Transactional
    public DuelChallengeResponse requestCancel(Long id, String discordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID callerUuid = resolveDiscordId(discordId);
        requireParticipant(challenge, callerUuid);

        if (duelChallengeMapper.requestCancel(id, callerUuid) == 0) {
            // not cancellable, or a request already waits: told apart by what the row holds now
            DuelChallenge current = findOrThrow(id);
            if (isCancellable(current) && current.getCancelRequestedBy() != null) {
                throw new DuelCancelAlreadyRequestedException(id);
            }
            throw new DuelChallengeNotPendingException(id, current.getStatus());
        }
        log.info("Duel cancellation requested: id={}, requester={}", id, callerUuid);
        challenge.setCancelRequestedBy(callerUuid);
        return toResponse(challenge);
    }

    /** The other participant agrees: the duel is cancelled and the bot is told so that it closes the channel. */
    @Transactional
    public DuelChallengeResponse acceptCancel(Long id, String discordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID callerUuid = resolveDiscordId(discordId);
        requireParticipant(challenge, callerUuid);
        requireNotRequester(challenge, callerUuid);

        if (duelChallengeMapper.acceptCancel(id, callerUuid) == 0) {
            throw new DuelChallengeNotPendingException(id, findOrThrow(id).getStatus());
        }
        log.info("Duel cancelled by agreement: id={}, accepter={}", id, callerUuid);
        challenge.setStatus("CANCELLED");
        matchWebhookClient.notifyDuelCancelled(new DuelCancelledPayload(id, DuelCancelledPayload.REASON_AGREEMENT));
        return toResponse(challenge);
    }

    /** The other participant declines: the request is cleared and the duel goes on. */
    @Transactional
    public DuelChallengeResponse declineCancel(Long id, String discordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID callerUuid = resolveDiscordId(discordId);
        requireParticipant(challenge, callerUuid);
        requireNotRequester(challenge, callerUuid);

        if (duelChallengeMapper.declineCancel(id, callerUuid) == 0) {
            throw new DuelChallengeNotPendingException(id, findOrThrow(id).getStatus());
        }
        log.info("Duel cancellation declined: id={}, decliner={}", id, callerUuid);
        challenge.setCancelRequestedBy(null);
        return toResponse(challenge);
    }

    /**
     * Cancels the ACTIVE duels nobody has played for {@code duel.active-expiry-hours} (spec 006) and tells the bot,
     * after the commit, so that it closes their channels. A REPORTED duel never expires.
     */
    @Transactional
    public int expireStaleActiveDuels() {
        List<Long> expired = duelChallengeMapper.cancelStaleActive(activeExpiryHours);
        for (Long id : expired) {
            log.info("Duel challenge expired: id={} was ACTIVE for more than {} hour(s)", id, activeExpiryHours);
            matchWebhookClient.notifyDuelCancelled(new DuelCancelledPayload(id, DuelCancelledPayload.REASON_EXPIRED));
        }
        return expired.size();
    }

    /**
     * A participant left the Discord server (spec 006): the duel is cancelled with no result, without the other
     * player's agreement (none is possible), and the bot is told so that it closes the channel. The API cannot
     * verify the departure; it trusts the caller as for every Discord fact (ADR-0005).
     */
    @Transactional
    public DuelChallengeResponse cancelPlayerLeft(Long id, String discordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID leftUuid = resolveDiscordId(discordId);
        requireParticipant(challenge, leftUuid);

        if (duelChallengeMapper.cancelAbandoned(id) == 0) {
            throw new DuelChallengeNotPendingException(id, findOrThrow(id).getStatus());
        }
        log.info("Duel cancelled because a participant left: id={}, player={}", id, leftUuid);
        challenge.setStatus("CANCELLED");
        matchWebhookClient.notifyDuelCancelled(new DuelCancelledPayload(id, DuelCancelledPayload.REASON_PLAYER_LEFT));
        return toResponse(challenge);
    }

    private static boolean isCancellable(DuelChallenge challenge) {
        return "ACTIVE".equals(challenge.getStatus()) || "REPORTED".equals(challenge.getStatus());
    }

    private void requireNotRequester(DuelChallenge challenge, UUID callerUuid) {
        if (callerUuid.equals(challenge.getCancelRequestedBy())) {
            throw new DuelChallengeForbiddenException("The player who asked to cancel cannot answer their own request");
        }
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

    @Transactional(noRollbackFor = NoActiveRankedSeasonException.class)
    public DuelChallengeResponse confirmScores(Long id, String confirmerDiscordId) {
        DuelChallenge challenge = findOrThrow(id);
        UUID confirmerUuid = resolveDiscordId(confirmerDiscordId);
        requireParticipant(challenge, confirmerUuid);

        if (confirmerUuid.equals(challenge.getReporterUuid())) {
            throw new DuelChallengeForbiddenException("The reporter cannot confirm their own score report");
        }

        // Atomic claim: only one of two racing confirm calls can win this CAS. CONFIRMING must
        // never be committed independently of this method (no REQUIRES_NEW / separate transaction) -
        // it only ever exists inside this still-open transaction, so a crash mid-method rolls it
        // back to REPORTED automatically; a thrown exception below reverts it explicitly.
        int claimed = duelChallengeMapper.updateStatus(id, "REPORTED", "CONFIRMING");
        if (claimed == 0) {
            throw new DuelChallengeNotPendingException(id, challenge.getStatus());
        }

        CreateMatchRequest matchRequest = new CreateMatchRequest(
                challenge.getGameType(),
                "discord_bot",
                List.of(
                        new TeamRequest(challenge.getScoreChallenger(),
                                rosterUuids(challenge, DuelChallengeParticipant.SIDE_CHALLENGER)),
                        new TeamRequest(challenge.getScoreChallenged(),
                                rosterUuids(challenge, DuelChallengeParticipant.SIDE_CHALLENGED))
                ),
                null
        );
        MatchResponse match;
        try {
            match = matchService.createMatch(matchRequest);
        } catch (NoActiveRankedSeasonException e) {
            // The season was deactivated while the duel was played: no match can be made, ever. Cancel the duel
            // (a system decision, not a player's, spec 003) and tell the bot so that it closes the channel. The
            // exception does not roll the transaction back (noRollbackFor), so the cancellation is committed.
            duelChallengeMapper.updateStatus(id, "CONFIRMING", "CANCELLED");
            matchWebhookClient.notifyDuelCancelled(
                    new DuelCancelledPayload(id, DuelCancelledPayload.REASON_NO_ACTIVE_SEASON));
            throw e;
        } catch (RuntimeException e) {
            duelChallengeMapper.updateStatus(id, "CONFIRMING", "REPORTED");
            throw e;
        }

        int completed = duelChallengeMapper.setMatchId(id, match.id());
        if (completed == 0) {
            log.error("Duel challenge {} was in CONFIRMING but setMatchId affected 0 rows (matchId={}) - invariant violated",
                    id, match.id());
            throw new IllegalStateException("Duel challenge " + id + " lost its CONFIRMING claim unexpectedly");
        }

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

    /** One side's roster from the participants; falls back to the captain for legacy challenges. */
    private List<UUID> rosterUuids(DuelChallenge challenge, String side) {
        List<UUID> uuids = participantMapper.findByChallengeId(challenge.getId()).stream()
                .filter(p -> side.equals(p.getSide()))
                .map(DuelChallengeParticipant::getPlayerUuid)
                .toList();
        if (!uuids.isEmpty()) {
            return uuids;
        }
        return List.of(DuelChallengeParticipant.SIDE_CHALLENGER.equals(side)
                ? challenge.getChallengerUuid()
                : challenge.getChallengedUuid());
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
                toTeamSummaries(c, DuelChallengeParticipant.SIDE_CHALLENGER),
                toTeamSummaries(c, DuelChallengeParticipant.SIDE_CHALLENGED),
                c.getGameType(),
                c.getStatus(),
                c.getReporterUuid(),
                c.getScoreChallenger(),
                c.getScoreChallenged(),
                c.getMatchId(),
                c.getCreatedAt(),
                c.getExpiresAt(),
                c.getCancelRequestedBy()
        );
    }

    private List<PlayerSummaryResponse> toTeamSummaries(DuelChallenge c, String side) {
        return rosterUuids(c, side).stream()
                .map(uuid -> new PlayerSummaryResponse(uuid, usernameCache.get(uuid)))
                .toList();
    }
}

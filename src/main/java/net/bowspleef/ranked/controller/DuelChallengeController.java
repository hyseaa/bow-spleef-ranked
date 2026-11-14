package net.bowspleef.ranked.controller;

import net.bowspleef.ranked.dto.CreateDuelChallengeRequest;
import net.bowspleef.ranked.dto.DuelChallengeResponse;
import net.bowspleef.ranked.dto.ReportDuelScoresRequest;
import net.bowspleef.ranked.service.DuelChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/duel-challenges")
@RequiredArgsConstructor
@Tag(name = "Duel Challenges", description = "Duel challenge lifecycle management")
public class DuelChallengeController {

    private final DuelChallengeService duelChallengeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a duel challenge",
            description = "For team game types the challenger must lead a party and may target any member "
                    + "of the opposing party; the returned challengedUuid is that party's LEADER, "
                    + "who alone can accept/refuse/confirm.")
    public DuelChallengeResponse create(@Valid @RequestBody CreateDuelChallengeRequest request) {
        log.info("POST /api/v1/duel-challenges: challenger={}, challenged={}, gameType={}",
                request.challengerDiscordId(), request.challengedDiscordId(), request.gameType());
        return duelChallengeService.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a duel challenge by ID")
    public DuelChallengeResponse getChallenge(@PathVariable Long id) {
        log.info("GET /api/v1/duel-challenges/{}", id);
        return duelChallengeService.getChallenge(id);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a duel challenge (challenged player)")
    public DuelChallengeResponse accept(@PathVariable Long id,
                                        @RequestParam @NotBlank String challengedDiscordId) {
        log.info("POST /api/v1/duel-challenges/{}/accept: challengedDiscordId={}", id, challengedDiscordId);
        return duelChallengeService.accept(id, challengedDiscordId);
    }

    @PostMapping("/{id}/refuse")
    @Operation(summary = "Refuse a duel challenge (challenged player)")
    public DuelChallengeResponse refuse(@PathVariable Long id,
                                        @RequestParam @NotBlank String challengedDiscordId) {
        log.info("POST /api/v1/duel-challenges/{}/refuse: challengedDiscordId={}", id, challengedDiscordId);
        return duelChallengeService.refuse(id, challengedDiscordId);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a duel challenge that nobody has accepted yet (any participant)",
            description = "Only valid while the challenge is PENDING. Once accepted (ACTIVE or REPORTED) a duel is "
                    + "cancelled by agreement: cancel-request, then cancel-accept by the other player. Answers 409 "
                    + "DUEL_CANCEL_NEEDS_AGREEMENT otherwise.")
    public DuelChallengeResponse cancel(@PathVariable Long id,
                                        @RequestParam @NotBlank String discordId) {
        log.info("POST /api/v1/duel-challenges/{}/cancel: discordId={}", id, discordId);
        return duelChallengeService.cancel(id, discordId);
    }

    @PostMapping("/{id}/cancel-request")
    @Operation(summary = "Ask to cancel an ACTIVE or REPORTED duel (any participant)",
            description = "Records the request; the other participant must accept it. 409 DUEL_CANCEL_ALREADY_REQUESTED "
                    + "while a request waits.")
    public DuelChallengeResponse cancelRequest(@PathVariable Long id,
                                               @RequestParam @NotBlank String discordId) {
        log.info("POST /api/v1/duel-challenges/{}/cancel-request: discordId={}", id, discordId);
        return duelChallengeService.requestCancel(id, discordId);
    }

    @PostMapping("/{id}/cancel-accept")
    @Operation(summary = "Agree to cancel the duel (the participant who did not ask)",
            description = "Cancels the duel and tells the bot (DUEL_CANCELLED webhook).")
    public DuelChallengeResponse cancelAccept(@PathVariable Long id,
                                              @RequestParam @NotBlank String discordId) {
        log.info("POST /api/v1/duel-challenges/{}/cancel-accept: discordId={}", id, discordId);
        return duelChallengeService.acceptCancel(id, discordId);
    }

    @PostMapping("/{id}/cancel-decline")
    @Operation(summary = "Decline the cancellation request (the participant who did not ask)",
            description = "Clears the request; the duel goes on.")
    public DuelChallengeResponse cancelDecline(@PathVariable Long id,
                                               @RequestParam @NotBlank String discordId) {
        log.info("POST /api/v1/duel-challenges/{}/cancel-decline: discordId={}", id, discordId);
        return duelChallengeService.declineCancel(id, discordId);
    }

    @PostMapping("/{id}/cancel-player-left")
    @Operation(summary = "Cancel an ACTIVE or REPORTED duel because a participant left the Discord server",
            description = "`discordId` is the participant who left. Cancels with no result and without the other "
                    + "player's agreement, and tells the bot (DUEL_CANCELLED, reason PLAYER_LEFT). Meant for the bot.")
    public DuelChallengeResponse cancelPlayerLeft(@PathVariable Long id,
                                                  @RequestParam @NotBlank String discordId) {
        log.info("POST /api/v1/duel-challenges/{}/cancel-player-left: discordId={}", id, discordId);
        return duelChallengeService.cancelPlayerLeft(id, discordId);
    }

    @PostMapping("/{id}/report")
    @Operation(summary = "Report match scores (any participant)")
    public DuelChallengeResponse report(@PathVariable Long id,
                                        @Valid @RequestBody ReportDuelScoresRequest request) {
        log.info("POST /api/v1/duel-challenges/{}/report: reporter={}", id, request.reporterDiscordId());
        return duelChallengeService.reportScores(id, request);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm reported scores (the other participant)")
    public DuelChallengeResponse confirm(@PathVariable Long id,
                                         @RequestParam @NotBlank String confirmerDiscordId) {
        log.info("POST /api/v1/duel-challenges/{}/confirm: confirmerDiscordId={}", id, confirmerDiscordId);
        return duelChallengeService.confirmScores(id, confirmerDiscordId);
    }

    @PostMapping("/{id}/dispute")
    @Operation(summary = "Dispute reported scores (the other participant)")
    public DuelChallengeResponse dispute(@PathVariable Long id,
                                         @RequestParam @NotBlank String disputerDiscordId) {
        log.info("POST /api/v1/duel-challenges/{}/dispute: disputerDiscordId={}", id, disputerDiscordId);
        return duelChallengeService.disputeScores(id, disputerDiscordId);
    }
}

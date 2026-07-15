package com.playerscores.controller;

import com.playerscores.dto.CreatePartyRequest;
import com.playerscores.dto.PartyInviteRequest;
import com.playerscores.dto.PartyResponse;
import com.playerscores.service.PartyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/parties")
@RequiredArgsConstructor
@Tag(name = "Parties", description = "Persistent teams: a leader creates a party, invites players, "
        + "and can then challenge another party to a team duel")
public class PartyController {

    private final PartyService partyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a party (the creator becomes its leader)")
    public PartyResponse create(@Valid @RequestBody CreatePartyRequest request) {
        log.info("POST /api/v1/parties: leader={}", request.leaderDiscordId());
        return partyService.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a party by ID")
    public PartyResponse getParty(@PathVariable Long id) {
        log.info("GET /api/v1/parties/{}", id);
        return partyService.getParty(id);
    }

    @GetMapping("/by-player/{discordId}")
    @Operation(summary = "Get the party a player belongs to")
    public PartyResponse getPartyByPlayer(@PathVariable String discordId) {
        log.info("GET /api/v1/parties/by-player/{}", discordId);
        return partyService.getPartyByPlayer(discordId);
    }

    @PostMapping("/{id}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite a player to the party (leader only); re-inviting refreshes the invite's TTL")
    public PartyResponse invite(@PathVariable Long id, @Valid @RequestBody PartyInviteRequest request) {
        log.info("POST /api/v1/parties/{}/invites: inviter={}, invitee={}",
                id, request.inviterDiscordId(), request.inviteeDiscordId());
        return partyService.invite(id, request);
    }

    @PostMapping("/{id}/invites/accept")
    @Operation(summary = "Accept a party invite (fails if the player is already in a party)")
    public PartyResponse acceptInvite(@PathVariable Long id,
                                      @RequestParam @NotBlank String discordId) {
        log.info("POST /api/v1/parties/{}/invites/accept: discordId={}", id, discordId);
        return partyService.accept(id, discordId);
    }

    @PostMapping("/{id}/invites/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Decline a party invite")
    public void declineInvite(@PathVariable Long id,
                              @RequestParam @NotBlank String discordId) {
        log.info("POST /api/v1/parties/{}/invites/decline: discordId={}", id, discordId);
        partyService.decline(id, discordId);
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave the party (the leader leaving disbands it)")
    public void leave(@PathVariable Long id,
                      @RequestParam @NotBlank String discordId) {
        log.info("POST /api/v1/parties/{}/leave: discordId={}", id, discordId);
        partyService.leave(id, discordId);
    }

    @PostMapping("/{id}/kick")
    @Operation(summary = "Kick a member from the party (leader only)")
    public PartyResponse kick(@PathVariable Long id,
                              @RequestParam @NotBlank String leaderDiscordId,
                              @RequestParam @NotBlank String targetDiscordId) {
        log.info("POST /api/v1/parties/{}/kick: leader={}, target={}", id, leaderDiscordId, targetDiscordId);
        return partyService.kick(id, leaderDiscordId, targetDiscordId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disband the party (leader only)")
    public void disband(@PathVariable Long id,
                        @RequestParam @NotBlank String leaderDiscordId) {
        log.info("DELETE /api/v1/parties/{}: leader={}", id, leaderDiscordId);
        partyService.disband(id, leaderDiscordId);
    }
}

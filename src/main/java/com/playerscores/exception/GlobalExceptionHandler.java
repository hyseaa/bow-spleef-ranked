package com.playerscores.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PlayerNotFoundException.class)
    public ProblemDetail handlePlayerNotFound(PlayerNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MatchNotFoundException.class)
    public ProblemDetail handleMatchNotFound(MatchNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MojangUsernameNotFoundException.class)
    public ProblemDetail handleMojangNotFound(MojangUsernameNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "MINECRAFT_USERNAME_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DiscordMismatchException.class)
    public ProblemDetail handleDiscordMismatch(DiscordMismatchException ex) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "DISCORD_MISMATCH", ex.getMessage());
        pd.setProperty("linkedDiscordUsername", ex.getLinkedDiscordUsername());
        return pd;
    }

    @ExceptionHandler(VerificationException.class)
    public ProblemDetail handleVerification(VerificationException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "DISCORD_NOT_LINKED", ex.getMessage());
    }

    @ExceptionHandler(PlayerSeasonEloNotFoundException.class)
    public ProblemDetail handlePlayerSeasonEloNotFound(PlayerSeasonEloNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "PLAYER_SEASON_ELO_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ActiveSeasonAlreadyExistsException.class)
    public ProblemDetail handleActiveSeasonAlreadyExists(ActiveSeasonAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, "ACTIVE_SEASON_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(GameTypeNotFoundException.class)
    public ProblemDetail handleGameTypeNotFound(GameTypeNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "GAME_TYPE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(NoActiveRankedSeasonException.class)
    public ProblemDetail handleNoActiveSeason(NoActiveRankedSeasonException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "NO_ACTIVE_RANKED_SEASON", ex.getMessage());
    }

    @ExceptionHandler(DuelChallengeNotFoundException.class)
    public ProblemDetail handleDuelChallengeNotFound(DuelChallengeNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "DUEL_CHALLENGE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuelChallengeNotPendingException.class)
    public ProblemDetail handleDuelChallengeNotPending(DuelChallengeNotPendingException ex) {
        return problem(HttpStatus.CONFLICT, "DUEL_CHALLENGE_NOT_PENDING", ex.getMessage());
    }

    @ExceptionHandler(DuelChallengeForbiddenException.class)
    public ProblemDetail handleDuelChallengeForbidden(DuelChallengeForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "DUEL_CHALLENGE_FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(InvalidTeamSizeException.class)
    public ProblemDetail handleInvalidTeamSize(InvalidTeamSizeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_TEAM_SIZE", ex.getMessage());
    }

    @ExceptionHandler(DuplicatePlayerInMatchException.class)
    public ProblemDetail handleDuplicatePlayer(DuplicatePlayerInMatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "DUPLICATE_PLAYER", ex.getMessage());
    }

    @ExceptionHandler(PartyNotFoundException.class)
    public ProblemDetail handlePartyNotFound(PartyNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "PARTY_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(PartyInviteNotFoundException.class)
    public ProblemDetail handlePartyInviteNotFound(PartyInviteNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "PARTY_INVITE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(PartyForbiddenException.class)
    public ProblemDetail handlePartyForbidden(PartyForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "PARTY_FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(AlreadyInPartyException.class)
    public ProblemDetail handleAlreadyInParty(AlreadyInPartyException ex) {
        return problem(HttpStatus.CONFLICT, "ALREADY_IN_PARTY", ex.getMessage());
    }

    @ExceptionHandler(PlayerNotInPartyException.class)
    public ProblemDetail handlePlayerNotInParty(PlayerNotInPartyException ex) {
        return problem(HttpStatus.CONFLICT, "NOT_IN_PARTY", ex.getMessage());
    }

    @ExceptionHandler(PartyFullException.class)
    public ProblemDetail handlePartyFull(PartyFullException ex) {
        return problem(HttpStatus.CONFLICT, "PARTY_FULL", ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "invalid"));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        return pd;
    }
}

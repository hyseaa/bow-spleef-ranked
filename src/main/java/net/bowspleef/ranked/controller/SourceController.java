package net.bowspleef.ranked.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.bowspleef.ranked.dto.SourceRequest;
import net.bowspleef.ranked.dto.SourceResponse;
import net.bowspleef.ranked.service.SourceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The registered sources of matches (spec 033, ADR-0033): administration only, the clients do not call it. */
@Slf4j
@RestController
@RequestMapping("/api/v1/sources")
@Tag(name = "Sources", description = "The sources of matches, with the name to show and their kind")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a source",
            description = "`kind` is `bot` or `custom_server`. `409` (`SOURCE_ALREADY_EXISTS`) when the name is "
                    + "already registered; `400` (`VALIDATION_FAILED`) for a blank or too long field or another kind.")
    public SourceResponse create(@Valid @RequestBody SourceRequest request) {
        log.info("Call to API: POST /api/v1/sources with parameters: request={}", request);
        SourceResponse response = sourceService.create(request);
        log.info("Call to API: POST /api/v1/sources completed");
        return response;
    }

    @GetMapping
    @Operation(summary = "List all sources")
    public List<SourceResponse> getAll() {
        log.info("Call to API: GET /api/v1/sources with no parameters");
        List<SourceResponse> response = sourceService.getAll();
        log.info("Call to API: GET /api/v1/sources completed");
        return response;
    }
}

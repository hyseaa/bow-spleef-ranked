package com.playerscores.controller;

import com.playerscores.dto.GameTypeRequest;
import com.playerscores.dto.GameTypeResponse;
import com.playerscores.service.GameTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/game-types")
@Tag(name = "Game Types", description = "Game type management")
public class GameTypeController {

    private final GameTypeService gameTypeService;

    public GameTypeController(GameTypeService gameTypeService) {
        this.gameTypeService = gameTypeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a game type")
    public GameTypeResponse create(@Valid @RequestBody GameTypeRequest request) {
        return gameTypeService.create(request);
    }

    @GetMapping
    @Operation(summary = "List all game types")
    public List<GameTypeResponse> getAll() {
        return gameTypeService.getAll();
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get a game type by name")
    public GameTypeResponse getByName(@PathVariable String name) {
        return gameTypeService.getByName(name);
    }
}

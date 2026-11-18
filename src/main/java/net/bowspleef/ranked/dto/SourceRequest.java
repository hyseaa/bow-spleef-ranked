package net.bowspleef.ranked.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.bowspleef.ranked.model.SourceKind;

/** A source to register (spec 033); a kind other than bot and custom_server cannot be read (400). */
public record SourceRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 100) String displayName,
        @NotNull SourceKind kind
) {}

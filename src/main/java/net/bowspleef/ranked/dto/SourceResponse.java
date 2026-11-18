package net.bowspleef.ranked.dto;

import net.bowspleef.ranked.model.SourceKind;

public record SourceResponse(String name, String displayName, SourceKind kind) {}

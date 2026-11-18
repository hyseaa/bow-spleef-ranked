package net.bowspleef.ranked.model;

import lombok.Data;

/** A registered source of matches (spec 033): the value of match.source, its name to show and its kind. */
@Data
public class Source {
    private String name;
    private String displayName;
    /** The stored value of a {@link SourceKind}. */
    private String kind;
}

package net.bowspleef.ranked.dto;

/** A stored replay as it is given back to the plugin (spec 024): its file and what was said about it. */
public record ReplayFile(String map, int durationTicks, int formatVersion, byte[] data) {}

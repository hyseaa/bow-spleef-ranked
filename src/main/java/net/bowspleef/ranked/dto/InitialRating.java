package net.bowspleef.ranked.dto;

/** The rating a player starts a season with, for a given rating algorithm. */
public record InitialRating(int elo, double mu, double sigma) {}

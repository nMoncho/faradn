package net.nmoncho.faradn.document;

/**
 * Cuts the paper ({@code GS V}). A partial cut leaves a small bridge so the
 * receipt stays attached.
 */
public record Cut(boolean partial) implements Block {
}

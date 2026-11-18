package net.bowspleef.ranked.exception;

/** A source whose name is already registered (spec 033). */
public class SourceAlreadyExistsException extends RuntimeException {
    public SourceAlreadyExistsException(String name) {
        super("Source '" + name + "' already exists");
    }
}

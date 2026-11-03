package dev.astral.domain;

/** Expected rule rejection, deliberately separate from infrastructure failures. */
public final class RuleViolation extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public RuleViolation(String message) { super(message); }
}

package norswap.sigh.interpreter;

/**
 * Simple wrapper for exceptions thrown while running the interpreter.
 */
public final class LengthException extends RuntimeException {
    public LengthException (String message) {
        super(message);
    }
}


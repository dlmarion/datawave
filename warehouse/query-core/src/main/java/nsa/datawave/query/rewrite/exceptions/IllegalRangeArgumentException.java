package nsa.datawave.query.rewrite.exceptions;

/**
 *
 */
public class IllegalRangeArgumentException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    public IllegalRangeArgumentException(Exception other) {
        super(other);
    }
    
}

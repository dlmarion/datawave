package nsa.datawave.webservice.query.result.util.protostuff;

/**
 * Meant to be implemented by enum types used in protostuff serialization
 * 
 */
public interface FieldAccessor {
    
    /**
     * 
     * @return Field index number to be used for protostuff serialization
     */
    public int getFieldNumber();
    
    /**
     * @return Field name to be used for protostuff serialization
     */
    public String getFieldName();
    
}

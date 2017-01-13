package nsa.datawave.ingest.data.config;

import java.util.Map;

import nsa.datawave.data.type.Type;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * Base container class implementation of the NormalizedContentInterface. This class is utilized to retain the original and transformed content and labels for a
 * value pair utilized for ingest into Accumulo.
 */
public class BaseNormalizedContent implements NormalizedContentInterface, Cloneable {
    /** The original field label of data. */
    protected String _fieldName = null;
    
    /** The original field value of data. */
    protected String _eventFieldValue = null;
    
    /** The transformed field value of data. */
    protected String _indexedFieldValue = null;
    
    /** The security markings for the field value pair. */
    protected Map<String,String> _markings = null;
    
    /** The field processing error if any. */
    protected Throwable error = null;
    
    public BaseNormalizedContent() {
        
    }
    
    public BaseNormalizedContent(String field, String value) {
        _fieldName = field;
        _eventFieldValue = value;
        _indexedFieldValue = value;
    }
    
    public BaseNormalizedContent(String field, String value, Map<String,String> markings) {
        this(field, value);
        _markings = markings;
    }
    
    public BaseNormalizedContent(NormalizedContentInterface n) {
        setFieldName(n.getIndexedFieldName());
        setIndexedFieldValue(n.getIndexedFieldValue());
        setEventFieldValue(n.getEventFieldValue());
        setMarkings(n.getMarkings());
        setError(n.getError());
    }
    
    /**
     * Setter for field label
     * 
     * @param fieldName
     *            the value to set
     */
    public void setFieldName(String fieldName) {
        _fieldName = fieldName;
    }
    
    /**
     * Getter for field label
     * 
     * @return the field lavel
     */
    public String getIndexedFieldName() {
        return _fieldName;
    }
    
    /**
     * Getter for field label
     * 
     * @return the field lavel
     */
    public String getEventFieldName() {
        return _fieldName;
    }
    
    /**
     * Getter for the normalized field value
     * 
     * @return the normalized field value
     */
    public String getIndexedFieldValue() {
        return _indexedFieldValue;
    }
    
    /**
     * Setter for normalized field value
     * 
     * @param normalizedFieldValue
     *            the value to set
     */
    public void setIndexedFieldValue(String normalizedFieldValue) {
        _indexedFieldValue = normalizedFieldValue;
    }
    
    /**
     * Getter for the original field value
     * 
     * @return the original field value
     */
    public String getEventFieldValue() {
        return _eventFieldValue;
    }
    
    public void setEventFieldValue(String originalFieldValue) {
        _eventFieldValue = originalFieldValue;
    }
    
    @Override
    public Map<String,String> getMarkings() {
        return _markings;
    }
    
    @Override
    public void setMarkings(Map<String,String> markings) {
        this._markings = markings;
    }
    
    /**
     * Setter for the processing error
     * 
     * @param error
     *            The processing error
     */
    public void setError(Throwable error) {
        this.error = error;
    }
    
    /**
     * Getter for the processing error
     * 
     * @return the processing error
     */
    public Throwable getError() {
        return error;
    }
    
    public int hashCode() {
        HashCodeBuilder b = new HashCodeBuilder();
        b.append(_fieldName).append(_indexedFieldValue).append(_eventFieldValue);
        b.append(_markings);
        return b.toHashCode();
    }
    
    public boolean equals(Object o) {
        if (o instanceof BaseNormalizedContent) {
            BaseNormalizedContent n = (BaseNormalizedContent) o;
            EqualsBuilder b = new EqualsBuilder();
            b.append(_fieldName, n._fieldName);
            b.append(_indexedFieldValue, n._indexedFieldValue);
            b.append(_eventFieldValue, n._eventFieldValue);
            b.append(_markings, n._markings);
            return b.isEquals();
        }
        return false;
    }
    
    public Object clone() {
        Object clone = null;
        try {
            clone = super.clone();
        } catch (CloneNotSupportedException cnse) {
            // not possible
        }
        return clone;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("fieldName=").append(this._fieldName);
        sb.append(", indexedFieldValue=").append(this._indexedFieldValue);
        sb.append(", eventFieldValue=").append(this._eventFieldValue);
        sb.append(", markings=").append(this._markings);
        return sb.toString();
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see nsa.datawave.ingest.data.config.NormalizedContentInterface#normalize(nsa.datawave.data.type.Type)
     */
    @Override
    public void normalize(Type<?> datawaveType) {
        try {
            this.setIndexedFieldValue(datawaveType.normalize(this.getIndexedFieldValue()));
        } catch (Exception e) {
            this.setError(e);
        }
    }
}

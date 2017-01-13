package nsa.datawave.webservice.query.result.event;

import java.util.Map;

import com.google.common.base.Charsets;
import org.apache.accumulo.core.security.ColumnVisibility;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 *
 */
@XmlAccessorType(XmlAccessType.NONE)
public abstract class FieldCardinalityBase implements HasMarkings {
    
    protected Map<String,String> markings;
    
    public abstract String getColumnVisibility();
    
    public abstract void setColumnVisibility(String columnVisibility);
    
    public void setColumnVisibility(ColumnVisibility columnVisibility) {
        String cvString = (columnVisibility == null) ? null : new String(columnVisibility.getExpression(), Charsets.UTF_8);
        setColumnVisibility(cvString);
    }
    
    public abstract Long getCardinality();
    
    public abstract void setCardinality(Long cardinality);
    
    public abstract String getUpper();
    
    public abstract void setUpper(String upper);
    
    public abstract String getLower();
    
    public abstract void setLower(String lower);
    
    public abstract int hashCode();
    
    public abstract boolean equals(Object o);
}

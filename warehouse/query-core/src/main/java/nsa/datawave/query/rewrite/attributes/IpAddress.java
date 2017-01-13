package nsa.datawave.query.rewrite.attributes;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

import nsa.datawave.query.jexl.DatawaveJexlContext;
import nsa.datawave.query.rewrite.collections.FunctionalSet;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.hadoop.io.WritableUtils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class IpAddress extends Attribute<IpAddress> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private nsa.datawave.data.type.util.IpAddress value;
    private String normalizedValue;
    
    protected IpAddress() {
        super(null, true);
    }
    
    public IpAddress(String ipAddress, Key docKey, boolean toKeep) {
        super(docKey, toKeep);
        setValue(ipAddress);
        setNormalizedValue(ipAddress);
        validate();
    }
    
    @Override
    public long sizeInBytes() {
        return sizeInBytes(value.toString()) + sizeInBytes(normalizedValue) + super.sizeInBytes(8);
        // 8 for string references
    }
    
    protected void validate() {
        if (this.value == null || this.normalizedValue == null) {
            throw new IllegalArgumentException("Unable to create ip value " + this.value + ", " + this.normalizedValue);
        }
    }
    
    @Override
    public Object getData() {
        return this.value;
    }
    
    private void setValue(String value) {
        this.value = nsa.datawave.data.type.util.IpAddress.parse(value);
    }
    
    private void setNormalizedValue(String value) {
        this.normalizedValue = nsa.datawave.data.type.util.IpAddress.parse(value).toZeroPaddedString();
    }
    
    @Override
    public void write(DataOutput out) throws IOException {
        write(out, false);
    }
    
    @Override
    public void write(DataOutput out, boolean reducedResponse) throws IOException {
        writeMetadata(out, reducedResponse);
        
        WritableUtils.writeString(out, this.value.toString());
    }
    
    @Override
    public void readFields(DataInput in) throws IOException {
        readMetadata(in);
        String ipString = WritableUtils.readString(in);
        setValue(ipString);
        setNormalizedValue(ipString);
        validate();
    }
    
    @Override
    public int compareTo(IpAddress o) {
        int cmp = value.compareTo(o.value);
        
        if (0 == cmp) {
            // Compare the ColumnVisibility as well
            return this.compareMetadata(o);
        }
        
        return cmp;
    }
    
    @Override
    public boolean equals(Object o) {
        if (null == o) {
            return false;
        }
        
        if (o instanceof IpAddress) {
            return 0 == this.compareTo((IpAddress) o);
        }
        
        return false;
    }
    
    @Override
    public int hashCode() {
        HashCodeBuilder hcb = new HashCodeBuilder(163, 157);
        hcb.append(super.hashCode()).append(this.value);
        
        return hcb.toHashCode();
    }
    
    @Override
    public Collection<ValueTuple> visit(Collection<String> fieldNames, DatawaveJexlContext context) {
        validate();
        return FunctionalSet.singleton(new ValueTuple(fieldNames, this.value, normalizedValue, this));
    }
    
    @Override
    public void write(Kryo kryo, Output output) {
        write(kryo, output, false);
    }
    
    @Override
    public void write(Kryo kryo, Output output, Boolean reducedResponse) {
        writeMetadata(kryo, output, reducedResponse);
        
        output.writeString(this.value.toString());
    }
    
    @Override
    public void read(Kryo kryo, Input input) {
        readMetadata(kryo, input);
        String ipAddressString = input.readString();
        setValue(ipAddressString);
        setNormalizedValue(ipAddressString);
        validate();
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see nsa.datawave.query.rewrite.attributes.Attribute#deepCopy()
     */
    @Override
    public IpAddress copy() {
        return new IpAddress(this.value.toString(), this.getMetadata(), this.isToKeep());
    }
    
}

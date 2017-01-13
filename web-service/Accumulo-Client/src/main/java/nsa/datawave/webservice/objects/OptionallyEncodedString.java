package nsa.datawave.webservice.objects;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

import org.apache.commons.codec.binary.Base64;
import org.apache.xerces.util.XMLChar;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
public class OptionallyEncodedString {
    
    @XmlAttribute(required = false)
    private Boolean base64Encoded = null;
    
    @XmlValue
    private String value = null;
    
    public OptionallyEncodedString() {}
    
    public OptionallyEncodedString(String value) {
        setValue(value);
    }
    
    public Boolean getBase64Encoded() {
        return base64Encoded;
    }
    
    public String getValue() {
        if (this.base64Encoded != null && this.base64Encoded.equals(Boolean.TRUE)) {
            byte[] incoming = null;
            String decoded = null;
            
            try {
                incoming = value.getBytes("UTF-8");
                byte[] decodedBytes = Base64.decodeBase64(incoming);
                decoded = new String(decodedBytes, Charset.forName("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                // Should never happen with UTF-8!!! (but if it does we will be
                // returning a null)
            }
            
            return decoded;
        } else {
            return value;
        }
    }
    
    public byte[] getValueAsBytes() {
        try {
            byte[] incoming = value.getBytes("UTF-8");
            if (this.base64Encoded != null && this.base64Encoded.equals(Boolean.TRUE)) {
                byte[] decoded = Base64.decodeBase64(incoming);
                return decoded;
            } else {
                return incoming;
            }
        } catch (UnsupportedEncodingException e) {
            // Should never happen with UTF-8!!! (but if it does we will be
            // returning a null)
        }
        
        // Should never get here
        return null;
    }
    
    public void setBase64Encoded(Boolean base64Encoded) {
        this.base64Encoded = base64Encoded;
    }
    
    public void setValue(String value) {
        if (isValidXML(value)) {
            this.value = value;
        } else {
            this.value = new String(Base64.encodeBase64(value.getBytes(Charset.forName("UTF-8"))));
            this.base64Encoded = true;
        }
    }
    
    private static boolean isValidXML(String s) {
        for (char c : s.toCharArray()) {
            try {
                if (XMLChar.isValid(c) == false) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}

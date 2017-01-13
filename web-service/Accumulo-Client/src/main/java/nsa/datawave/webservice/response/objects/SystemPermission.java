package nsa.datawave.webservice.response.objects;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
public class SystemPermission implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public enum SystemPermissionType {
        GRANT, CREATE_TABLE, DROP_TABLE, ALTER_TABLE, CREATE_USER, DROP_USER, ALTER_USER, SYSTEM
    };
    
    @XmlValue
    private SystemPermissionType permission = null;
    
    public SystemPermission() {
        
    }
    
    public SystemPermission(String permission) {
        this.permission = SystemPermissionType.valueOf(permission);
    }
    
    public SystemPermissionType getPermission() {
        return permission;
    }
    
    public void setPermission(SystemPermissionType permission) {
        this.permission = permission;
    }
    
    public void setPermission(String permission) {
        this.permission = SystemPermissionType.valueOf(permission);
    }
}

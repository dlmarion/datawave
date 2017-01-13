package nsa.datawave.data.type;

import nsa.datawave.data.normalizer.Normalizer;
import nsa.datawave.data.type.util.IpAddress;

public class IpV4AddressType extends BaseType<IpAddress> {
    
    private static final long serialVersionUID = 7214683578627273557L;
    
    public IpV4AddressType() {
        super(Normalizer.IP_ADDRESS_NORMALIZER);
    }
}

package nsa.datawave.metrics.iterators;

import nsa.datawave.metrics.keys.AnalyticEntryKey;
import nsa.datawave.metrics.keys.InvalidKeyException;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;

public class AnalyticDataTypeFilter extends DataTypeFilter {
    private AnalyticEntryKey aek = new AnalyticEntryKey();
    
    @Override
    public boolean accept(Key k, Value v) {
        try {
            aek.parse(k);
            return getTypes().contains(aek.getDataType());
        } catch (InvalidKeyException e) {
            return false;
        }
    }
    
}

package nsa.datawave.query.rewrite.function;

import nsa.datawave.query.rewrite.attributes.Document;
import org.apache.accumulo.core.data.Key;

import javax.annotation.Nullable;
import java.util.Map;

/**
 *
 *
 */
public class NoOpMaskedValueFilter implements MaskedValueFilterInterface {
    
    private boolean includeGroupingContext;
    private boolean reducedResponse;
    
    public NoOpMaskedValueFilter() {
        this(false, false);
    }
    
    public NoOpMaskedValueFilter(boolean _includeGroupingContext, boolean _reducedResponse) {
        this.includeGroupingContext = _includeGroupingContext;
        this.reducedResponse = _reducedResponse;
    }
    
    @Override
    public void setIncludeGroupingContext(boolean includeGroupingContext) {
        this.includeGroupingContext = includeGroupingContext;
    }
    
    @Override
    public boolean isIncludeGroupingContext() {
        return includeGroupingContext;
    }
    
    @Override
    public void setReducedResponse(boolean reducedResponse) {
        this.reducedResponse = reducedResponse;
    }
    
    @Override
    public boolean isReducedResponse() {
        return reducedResponse;
    }
    
    @Nullable
    @Override
    public Map.Entry<Key,Document> apply(Map.Entry<Key,Document> keyDocumentEntry) {
        return keyDocumentEntry;
    }
}

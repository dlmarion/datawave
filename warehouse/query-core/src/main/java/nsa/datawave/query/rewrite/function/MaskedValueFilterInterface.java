package nsa.datawave.query.rewrite.function;

import java.util.Map.Entry;

import nsa.datawave.query.rewrite.attributes.Document;

import org.apache.accumulo.core.data.Key;

public interface MaskedValueFilterInterface extends Permutation<Entry<Key,Document>> {
    
    void setIncludeGroupingContext(boolean includeGroupingContext);
    
    boolean isIncludeGroupingContext();
    
    void setReducedResponse(boolean reducedResponse);
    
    boolean isReducedResponse();
    
    /**
     * No-op implementation that has no effect on the targeted {@code Entry<Key,Document>} instance
     */
    public static class NoOp implements MaskedValueFilterInterface {
        
        private boolean includeGroupingContext = false;
        private boolean reducedResponse = false;
        
        /**
         * Does nothing. Simply returns the value passed in, as-is.
         */
        @Override
        public Entry<Key,Document> apply(Entry<Key,Document> arg0) {
            return arg0;
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
    }
}

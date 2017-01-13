package nsa.datawave.query.rewrite.planner;

import nsa.datawave.query.model.QueryModel;
import nsa.datawave.query.rewrite.config.RefactoredShardQueryConfiguration;
import nsa.datawave.query.rewrite.exceptions.DatawaveFatalQueryException;
import nsa.datawave.query.util.MetadataHelper;
import nsa.datawave.query.util.MetadataHelperFactory;
import nsa.datawave.webservice.common.logging.ThreadConfigurableLogger;
import nsa.datawave.webservice.query.exception.DatawaveErrorCode;
import nsa.datawave.webservice.query.exception.QueryException;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.log4j.Logger;

/**
 * Uses the RefactoredShardQueryConfiguration or the MetadataHelper to fetch a QueryModel
 */
public class MetadataHelperQueryModelProvider implements QueryModelProvider {
    
    private static final Logger log = ThreadConfigurableLogger.getLogger(MetadataHelperQueryModelProvider.class);
    
    // this must be the correct, initialized metadatahalper.Don't inject it, set it before calling getQueryModel
    protected MetadataHelper metadataHelper;
    protected RefactoredShardQueryConfiguration config;
    
    @Override
    public QueryModel getQueryModel() {
        QueryModel queryModel = null;
        if (config.getQueryModel() != null) {
            log.debug("Using a cached query model");
            queryModel = config.getQueryModel();
        } else if (null != config.getModelName() && null != config.getModelTableName()) {
            log.debug("Generating a query model");
            try {
                queryModel = metadataHelper.getQueryModel(config.getModelTableName(), config.getModelName(), config.getUnevaluatedFields(),
                                config.getDatatypeFilter());
                config.setQueryModel(queryModel);
            } catch (TableNotFoundException e) {
                QueryException qe = new QueryException(DatawaveErrorCode.QUERY_MODEL_FETCH_ERROR, e);
                log.error(qe);
                throw new DatawaveFatalQueryException(qe);
            }
            
            if (log.isTraceEnabled()) {
                log.trace("forward queryModel: " + queryModel.getForwardQueryMapping());
                log.trace("reverse queryModel: " + queryModel.getReverseQueryMapping());
            }
        }
        return queryModel;
    }
    
    public MetadataHelper getMetadataHelper() {
        return metadataHelper;
    }
    
    public void setMetadataHelper(MetadataHelper metadataHelper) {
        this.metadataHelper = metadataHelper;
    }
    
    public RefactoredShardQueryConfiguration getConfig() {
        return config;
    }
    
    public void setConfig(RefactoredShardQueryConfiguration config) {
        this.config = config;
    }
    
    public static class Factory extends QueryModelProvider.Factory {
        
        public QueryModelProvider createQueryModelProvider() {
            return new MetadataHelperQueryModelProvider();
        }
    }
}

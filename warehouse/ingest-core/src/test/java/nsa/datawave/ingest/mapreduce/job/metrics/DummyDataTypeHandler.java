package nsa.datawave.ingest.mapreduce.job.metrics;

import com.google.common.collect.Multimap;
import nsa.datawave.ingest.data.RawRecordContainer;
import nsa.datawave.ingest.data.Type;
import nsa.datawave.ingest.data.config.NormalizedContentInterface;
import nsa.datawave.ingest.data.config.ingest.IngestHelperInterface;
import nsa.datawave.ingest.mapreduce.handler.DataTypeHandler;
import nsa.datawave.ingest.mapreduce.job.BulkIngestKey;
import nsa.datawave.ingest.metadata.RawRecordMetadata;
import org.apache.accumulo.core.data.Value;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.StatusReporter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

/**
 * A {@link DataTypeHandler} used for unit testing. Currently does nothing except exist.
 */
public class DummyDataTypeHandler<K> implements DataTypeHandler<K> {
    
    @Override
    public void setup(TaskAttemptContext context) {}
    
    @Override
    public String[] getTableNames(Configuration conf) {
        return new String[0];
    }
    
    @Override
    public int[] getTableLoaderPriorities(Configuration conf) {
        return new int[0];
    }
    
    @Override
    public Multimap<BulkIngestKey,Value> processBulk(K key, RawRecordContainer event, Multimap<String,NormalizedContentInterface> fields,
                    StatusReporter reporter) {
        return null;
    }
    
    @Override
    public IngestHelperInterface getHelper(Type datatype) {
        return null;
    }
    
    @Override
    public void close(TaskAttemptContext context) {
        
    }
    
    @Override
    public RawRecordMetadata getMetadata() {
        return null;
    }
}

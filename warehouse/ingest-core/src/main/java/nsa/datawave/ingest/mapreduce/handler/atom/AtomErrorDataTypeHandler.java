package nsa.datawave.ingest.mapreduce.handler.atom;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import nsa.datawave.data.hash.UID;
import nsa.datawave.ingest.data.RawDataErrorNames;
import nsa.datawave.ingest.data.RawRecordContainer;
import nsa.datawave.ingest.data.TypeRegistry;
import nsa.datawave.ingest.data.config.MarkingsHelper;
import nsa.datawave.ingest.data.config.NormalizedContentInterface;
import nsa.datawave.ingest.data.config.NormalizedFieldAndValue;
import nsa.datawave.ingest.data.config.ingest.ErrorShardedIngestHelper;
import nsa.datawave.ingest.mapreduce.handler.error.ErrorShardedDataTypeHandler;
import nsa.datawave.ingest.mapreduce.job.BulkIngestKey;
import nsa.datawave.ingest.mapreduce.job.writer.ContextWriter;
import nsa.datawave.marking.MarkingFunctions;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * This class differs from the parent in that when it sees a field name of ERROR it creates a category name using the field name and value.
 *
 * @param <KEYIN>
 * @param <KEYOUT>
 * @param <VALUEOUT>
 */
public class AtomErrorDataTypeHandler<KEYIN,KEYOUT,VALUEOUT> extends AtomDataTypeHandler<KEYIN,KEYOUT,VALUEOUT> {
    
    public static final String JOB_NAME_FIELD = "JOB_NAME";
    public static final String JOB_ID_FIELD = "JOB_ID";
    public static final String UUID_FIELD = "EVENT_UUID";
    public static final String ERROR_FIELD = "ERROR";
    public static final String STACK_TRACE_FIELD = "STACKTRACE";
    public static final String EVENT_CONTENT_FIELD = "EVENT";
    public static final String COLUMN_VISIBILITY = "columnVisibility";
    
    private MarkingsHelper mHelper = null;
    private byte[] defaultVisibility = null;
    private Configuration conf = null;
    private ErrorShardedIngestHelper errorHelper = null;
    private MarkingFunctions markingFunctions;
    
    @Override
    public void setup(TaskAttemptContext context) {
        super.setup(context);
        
        this.errorHelper = (ErrorShardedIngestHelper) (TypeRegistry.getType("error").newIngestHelper());
        this.errorHelper.setup(context.getConfiguration());
        
        this.conf = context.getConfiguration();
        markingFunctions = MarkingFunctions.Factory.createMarkingFunctions();
        try {
            defaultVisibility = markingFunctions.flatten(markingFunctions.translateToColumnVisibility(mHelper.getDefaultMarkings()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse security marking configuration", e);
        }
        
    }
    
    @Override
    public long process(KEYIN key, RawRecordContainer record, Multimap<String,NormalizedContentInterface> eventFields,
                    TaskInputOutputContext<KEYIN,? extends RawRecordContainer,KEYOUT,VALUEOUT> context, ContextWriter<KEYOUT,VALUEOUT> contextWriter)
                    throws IOException, InterruptedException {
        int count = 0;
        
        if (!helpers.containsKey(record.getDataType()))
            return count;
        
        // write out the event into a value before we muck with it
        DataOutputBuffer buffer = new DataOutputBuffer();
        record.write(buffer);
        buffer.reset();
        
        // make a copy of the event to avoid side effects
        record = record.copy();
        
        // set the event date to now to enable keeping track of when this error occurred (determines date for shard)
        record.setDate(System.currentTimeMillis());
        
        // set the default markings if needed
        if (record.hasError(RawDataErrorNames.MISSING_DATA_ERROR)) {
            record.setSecurityMarkings(mHelper.getDefaultMarkings());
        }
        
        // add the error fields to our list of fields
        Multimap<String,NormalizedContentInterface> allFields = HashMultimap.create();
        if (eventFields != null) {
            for (NormalizedContentInterface n : eventFields.values()) {
                // if we had an error, then add a field for that
                if (n.getError() != null) {
                    String fieldName = n.getEventFieldName() + '_' + STACK_TRACE_FIELD;
                    ErrorShardedDataTypeHandler.getStackTrace(buffer, n.getError());
                    allFields.put(fieldName, new NormalizedFieldAndValue(fieldName, new String(buffer.getData(), 0, buffer.getLength())));
                    allFields.put(ERROR_FIELD, new NormalizedFieldAndValue(ERROR_FIELD, n.getEventFieldName()));
                    buffer.reset();
                }
            }
        }
        
        // job name
        allFields.put(JOB_NAME_FIELD, new NormalizedFieldAndValue(JOB_NAME_FIELD, context.getJobName()));
        // job id
        allFields.put(JOB_ID_FIELD, new NormalizedFieldAndValue(JOB_ID_FIELD, context.getJobID().getJtIdentifier()));
        // uuids
        if (record.getAltIds() != null) {
            for (String uuid : record.getAltIds()) {
                allFields.put(UUID_FIELD, new NormalizedFieldAndValue(UUID_FIELD, uuid));
            }
        }
        
        // event errors
        for (String error : record.getErrors()) {
            allFields.put(ERROR_FIELD, new NormalizedFieldAndValue(ERROR_FIELD, error));
        }
        
        // event runtime exception if any
        if (record.getAuxData() instanceof Exception) {
            allFields.put(ERROR_FIELD, new NormalizedFieldAndValue(ERROR_FIELD, RawDataErrorNames.RUNTIME_EXCEPTION));
            ErrorShardedDataTypeHandler.getStackTrace(buffer, (Exception) (record.getAuxData()));
            allFields.put(STACK_TRACE_FIELD, new NormalizedFieldAndValue(STACK_TRACE_FIELD, new String(buffer.getData(), 0, buffer.getLength())));
            buffer.reset();
        }
        
        // normalize the new set of fields.
        allFields = errorHelper.normalizeMap(allFields);
        
        // now that we have captured the fields, revalidate the event to generate a new visibility as needed
        if (null != errorHelper.getPolicyEnforcer()) {
            try {
                errorHelper.getPolicyEnforcer().validate(record);
            } catch (Throwable t) {
                throw new RuntimeException("Error handling failed event validation on file: " + record.getRawFileName() + " record number: "
                                + record.getRawRecordNumber(), t);
            }
        }
        
        // ensure we get a uid with a time element
        record.setId(UID.builder().newId(record.getRawData(), new Date(record.getDate())));
        
        Text tname = new Text(tableName);
        Set<Key> categories = new HashSet<>();
        
        for (NormalizedContentInterface nci : allFields.get(ERROR_FIELD)) {
            String columnQualifier = getVisibilityColumnString(record, nci);
            Key k = createKey(nci.getEventFieldValue(), record.getId().toString(), columnQualifier, record.getAltIds().iterator().next(),
                            record.getVisibility(), record.getDate());
            BulkIngestKey bk = new BulkIngestKey(tname, k);
            contextWriter.write(bk, NULL_VALUE, context);
            count++;
            Key categoryKey = new Key(nci.getEventFieldValue(), "", "", record.getVisibility(), record.getDate());
            categories.add(categoryKey);
        }
        
        Text categoryTableName = new Text(this.categoryTableName);
        for (Key catKey : categories) {
            BulkIngestKey bk = new BulkIngestKey(categoryTableName, catKey);
            contextWriter.write(bk, NULL_VALUE, context);
            count++;
        }
        
        return count;
    }
    
    protected String getVisibilityColumnString(RawRecordContainer event, NormalizedContentInterface value) {
        
        ColumnVisibility visibility = getVisibilityColumnVisibility(event, value);
        return visibility.toString();
        
    }
    
    protected ColumnVisibility getVisibilityColumnVisibility(RawRecordContainer event, NormalizedContentInterface value) {
        ColumnVisibility visibility = event.getVisibility();
        if (value.getMarkings() != null && !value.getMarkings().isEmpty()) {
            try {
                visibility = markingFunctions.translateToColumnVisibility(value.getMarkings());
            } catch (MarkingFunctions.Exception e) {
                throw new RuntimeException("Cannot convert record-level markings into a column visibility", e);
                
            }
        }
        return visibility;
        
    }
}

package nsa.datawave.webservice.mr.input;

import java.io.IOException;

import nsa.datawave.ingest.data.RawRecordContainer;
import nsa.datawave.ingest.input.reader.event.EventSequenceFileInputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

public class SecureEventSequenceFileInputFormat<K> extends EventSequenceFileInputFormat<K> {
    
    @Override
    public RecordReader<K,RawRecordContainer> createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException {
        SecureEventSequenceFileRecordReader<K> reader = new SecureEventSequenceFileRecordReader<>();
        try {
            reader.initialize(split, context);
        } catch (InterruptedException e) {
            throw new IOException("Error initializing SecureEventSequenceFileRecordReader", e);
        }
        return reader;
    }
    
}

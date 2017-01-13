package nsa.datawave.ingest.mapreduce.partition;

import nsa.datawave.ingest.mapreduce.handler.shard.ShardIdFactory;
import nsa.datawave.ingest.mapreduce.job.*;
import org.apache.accumulo.core.data.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.junit.*;

import java.text.*;
import java.util.*;

public class ShardIdPartitionerTest {
    Configuration conf = new Configuration();
    ShardIdPartitioner partitioner = null;
    
    @Before
    public void setUp() {
        conf = new Configuration();
        partitioner = new ShardIdPartitioner();
        partitioner.setConf(conf);
    }
    
    @After
    public void tearDown() {
        conf.clear();
        conf = null;
        partitioner = null;
    }
    
    @Test
    public void testShardIdScheme() throws ParseException {
        Map<String,Path> shardedTableMapFiles = new HashMap<>();
        shardedTableMapFiles.put("shard", new Path("/path/that/is/fake/to/make/the/test/pass"));
        
        conf.setInt(ShardIdFactory.NUM_SHARDS, 31);
        // now generate keys and ensure we have even distribution of the partitions
        int shardCount = 0;
        int[] partitions = new int[58];
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        Date date = format.parse("20100101");
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        for (int dateCnt = 0; dateCnt < 348; dateCnt++) {
            for (int shard = 0; shard < 31; shard++) {
                shardCount++;
                StringBuilder shardId = new StringBuilder();
                shardId.append(format.format(c.getTime()));
                shardId.append('_').append(shard);
                Key key = new Key(shardId.toString());
                Value value = new Value();
                int partition = partitioner.getPartition(new BulkIngestKey(new Text("shard"), key), value, 58);
                partitions[partition]++;
            }
            c.add(Calendar.DATE, 1);
        }
        int expected = shardCount / 58;
        boolean errored = false;
        for (int i = 0; i < 58; i++) {
            try {
                Assert.assertEquals("Unexpected distribution for partition " + i + ": ", expected, partitions[i]);
            } catch (Throwable e) {
                System.err.println(e.getMessage());
                errored = true;
            }
        }
        if (errored) {
            Assert.fail("Failed to get expected distribution.  See console for unexpected entries");
        }
    }
    
}

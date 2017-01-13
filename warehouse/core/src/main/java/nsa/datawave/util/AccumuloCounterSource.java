package nsa.datawave.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

import nsa.datawave.util.CounterDump.CounterSource;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.BatchScanner;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.hadoop.io.Text;

import com.google.common.collect.Lists;

public class AccumuloCounterSource extends CounterSource {
    
    protected Connector connector;
    
    Collection<Range> ranges = Lists.newArrayList();
    
    Collection<String> cfs = Lists.newArrayList();
    
    protected String queryTable;
    
    protected String username;
    
    protected Iterator<Entry<Key,Value>> iterator = null;;
    
    protected Key topKey = null;
    
    protected Value topValue = null;
    
    public AccumuloCounterSource(String instanceStr, String zookeepers, String username, String password, String table) throws AccumuloException,
                    AccumuloSecurityException {
        ZooKeeperInstance instance = new ZooKeeperInstance(instanceStr, zookeepers);
        connector = instance.getConnector(username, password.getBytes());
        queryTable = table;
        this.username = username;
    }
    
    @Override
    public byte[] getNextCounterData() {
        return topValue.get();
    }
    
    @Override
    public String getNextIdentifier() {
        return topKey.toString();
    }
    
    @Override
    public boolean hasNext() {
        
        if (null == iterator) {
            try {
                Authorizations auths = connector.securityOperations().getUserAuthorizations(username);
                BatchScanner scanner = connector.createBatchScanner(queryTable, auths, 100);
                scanner.setRanges(ranges);
                for (String cf : cfs) {
                    
                    scanner.fetchColumnFamily(new Text(cf));
                    
                }
                
                iterator = scanner.iterator();
                
            } catch (TableNotFoundException | AccumuloException | AccumuloSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        nextIterator();
        return null != topKey;
    }
    
    private void nextIterator() {
        if (iterator.hasNext()) {
            Entry<Key,Value> val = iterator.next();
            topValue = val.getValue();
            topKey = val.getKey();
        } else
            topKey = null;
        
    }
    
    @Override
    public void remove() {
        // unsupported. no need.
        
    }
    
    private void addRange(Range range) {
        ranges.add(range);
    }
    
    public void addColumnFaily(String cf) {
        cfs.add(cf);
    }
    
    public static void main(String[] args) throws AccumuloException, AccumuloSecurityException {
        String instance = args[0];
        String zookeepers = args[1];
        String username = args[2];
        String password = args[3];
        String table = args[4];
        String startRow = args[5];
        String endRow = args[6];
        String columnFamily = args[7];
        AccumuloCounterSource source = new AccumuloCounterSource(instance, zookeepers, username, password, table);
        Range range = new Range(startRow, endRow);
        source.addRange(range);
        source.addColumnFaily(columnFamily);
        CounterDump dumper = new CounterDump(source);
        System.out.println(dumper.toString());
    }
    
}

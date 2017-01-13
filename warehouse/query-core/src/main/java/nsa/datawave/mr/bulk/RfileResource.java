package nsa.datawave.mr.bulk;

import java.util.Collection;
import java.util.Set;

import nsa.datawave.ingest.data.config.ingest.AccumuloHelper;
import nsa.datawave.query.tables.AccumuloResource;
import nsa.datawave.query.tables.BatchResource;
import nsa.datawave.query.tables.SessionOptions;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.time.StopWatch;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class RfileResource extends BatchResource {
    
    private static final Logger log = Logger.getLogger(RfileResource.class);
    
    Configuration conf;
    
    protected RfileResource(Connector cxn) {
        super(cxn);
    }
    
    public RfileResource(AccumuloResource copy) {
        super(copy);
    }
    
    /**
     * Initializes the scanner resource
     * 
     * @param auths
     * @param tableName
     * @throws TableNotFoundException
     * 
     */
    @Override
    protected void init(final String tableName, final Set<Authorizations> auths, Collection<Range> currentRange) throws TableNotFoundException {
        Preconditions.checkNotNull(tableName);
        Preconditions.checkArgument(null != currentRange && currentRange.size() > 0);
        
        // copy the appropriate variables.
        ranges = Lists.newArrayList(currentRange);
        
        this.tableName = tableName;
        
        this.auths = Sets.newHashSet(auths);
        
        if (log.isTraceEnabled())
            log.trace("Creating scanner resource from " + tableName + " " + auths + " " + currentRange);
        
        internalTimer = new StopWatch();
        internalTimer.start();
        
        // let's pre-compute the hashcode.
        hashCode += new HashCodeBuilder().append(tableName).append(auths).append(ranges).toHashCode();
        
        conf = new Configuration();
        
        Connector con = getConnector();
        
        final String instanceName = con.getInstance().getInstanceName();
        final String zookeepers = con.getInstance().getZooKeepers();
        
        AccumuloHelper.setInstanceName(conf, instanceName);
        AccumuloHelper.setUsername(conf, con.whoami());
        
        AccumuloHelper.setZooKeepers(conf, zookeepers);
        BulkInputFormat.setZooKeeperInstance(conf, instanceName, zookeepers);
        
        conf.set(MultiRfileInputformat.CACHE_METADATA, "true");
        
        baseScanner = new RfileScanner(getConnector(), conf, tableName, auths, 1);
        
        if (baseScanner != null) {
            ((RfileScanner) baseScanner).setRanges(currentRange);
        }
        
    }
    
    /**
     * Sets the option on this currently running resource.
     * 
     * @param options
     * @return
     */
    @Override
    public AccumuloResource setOptions(SessionOptions options) {
        super.setOptions(options);
        
        if (log.isDebugEnabled()) {
            log.debug("Setting Options");
        }
        if (null != options.getConfiguration() && null != options.getConfiguration().getAccumuloPassword()) {
            if (log.isDebugEnabled()) {
                log.debug("Setting and configuration");
            }
            AccumuloHelper.setPassword(conf, options.getConfiguration().getAccumuloPassword().getBytes());
            BulkInputFormat.setMemoryInput(conf, getConnector().whoami(), options.getConfiguration().getAccumuloPassword().getBytes(), tableName, auths
                            .iterator().next());
            ((RfileScanner) baseScanner).setConfiguration(conf);
        }
        return this;
    }
    
    @Override
    public String toString() {
        
        StringBuilder builder = new StringBuilder();
        builder.append("RFileScanner").append(" ");
        builder.append("tableName=").append(tableName).append(" ");
        builder.append("auths=").append(auths).append(" ");
        builder.append("ranges=").append(ranges).append(" ");
        return builder.toString();
        
    }
}

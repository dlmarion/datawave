package nsa.datawave.query.index.lookup;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;

import nsa.datawave.query.index.lookup.IndexStream.StreamContext;
import nsa.datawave.query.rewrite.CloseableIterable;
import nsa.datawave.query.rewrite.config.RefactoredShardQueryConfiguration;
import nsa.datawave.query.rewrite.exceptions.DatawaveQueryException;
import nsa.datawave.query.rewrite.iterator.FieldIndexOnlyQueryIterator;
import nsa.datawave.query.rewrite.iterator.QueryOptions;
import nsa.datawave.query.rewrite.iterator.errors.ErrorKey;
import nsa.datawave.query.rewrite.jexl.visitors.JexlStringBuildingVisitor;
import nsa.datawave.query.rewrite.planner.DefaultQueryPlanner;
import nsa.datawave.query.rewrite.planner.QueryPlan;
import nsa.datawave.query.tables.ScannerFactory;
import nsa.datawave.query.util.MetadataHelper;
import nsa.datawave.util.time.DateHelper;

import org.apache.accumulo.core.client.BatchScanner;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.PartialKey;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.util.PeekingIterator;
import org.apache.commons.jexl2.parser.JexlNode;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;

public class ShardRangeStream extends RangeStream {
    
    public ShardRangeStream(RefactoredShardQueryConfiguration config, ScannerFactory scanners, MetadataHelper helper) {
        super(config, scanners, helper);
    }
    
    @Override
    public CloseableIterable<QueryPlan> streamPlans(JexlNode node) {
        try {
            String queryString = JexlStringBuildingVisitor.buildQuery(node);
            
            int stackStart = config.getBaseIteratorPriority() + 40;
            BatchScanner scanner = scanners.newScanner(config.getShardTableName(), config.getAuthorizations(), config.getNumQueryThreads(), config.getQuery(),
                            true);
            
            IteratorSetting cfg = new IteratorSetting(stackStart++, "query", FieldIndexOnlyQueryIterator.class);
            
            DefaultQueryPlanner.addOption(cfg, QueryOptions.QUERY, queryString, false);
            
            try {
                DefaultQueryPlanner.addOption(cfg, QueryOptions.INDEX_ONLY_FIELDS,
                                QueryOptions.buildIndexOnlyFieldsString(metadataHelper.getIndexOnlyFields(config.getDatatypeFilter())), true);
            } catch (TableNotFoundException e) {
                throw new RuntimeException(e);
            }
            
            DefaultQueryPlanner.addOption(cfg, QueryOptions.START_TIME, Long.toString(config.getBeginDate().getTime()), false);
            DefaultQueryPlanner.addOption(cfg, QueryOptions.DATATYPE_FILTER, config.getDatatypeFilterAsString(), false);
            DefaultQueryPlanner.addOption(cfg, QueryOptions.END_TIME, Long.toString(config.getEndDate().getTime()), false);
            
            DefaultQueryPlanner.configureTypeMappings(config, cfg, metadataHelper, true);
            
            scanner.setRanges(Collections.singleton(rangeForTerm(null, null, config)));
            
            scanner.addScanIterator(cfg);
            
            Iterator<Entry<Key,Value>> kvIter = scanner.iterator();
            
            itr = Collections.emptyIterator();
            
            if (kvIter.hasNext()) {
                PeekingIterator<Entry<Key,Value>> peeking = new PeekingIterator<>(kvIter);
                Entry<Key,Value> peekKey = peeking.peek();
                ErrorKey errorKey = ErrorKey.getErrorKey(peekKey.getKey());
                if (errorKey != null) {
                    switch (errorKey.getErrorType()) {
                        case UNINDEXED_FIELD:
                            this.context = StreamContext.UNINDEXED;
                            break;
                        case UNKNOWN:
                            this.context = StreamContext.ABSENT;
                    }
                } else {
                    itr = Iterators.transform(peeking, new FieldIndexParser(node));
                    this.context = StreamContext.PRESENT;
                }
                
            } else {
                this.context = StreamContext.ABSENT;
                
            }
            
        } catch (TableNotFoundException | DatawaveQueryException e) {
            throw new RuntimeException(e);
        } finally {
            // shut down the executor as all threads have completed
            shutdownThreads();
        }
        
        return this;
    }
    
    @Override
    public Range rangeForTerm(String term, String field, Date start, Date end) {
        return new Range(new Key(DateHelper.format(start) + "_"), false, new Key(DateHelper.format(end) + "_" + '\uffff'), false);
    }
    
    public class FieldIndexParser implements Function<Entry<Key,Value>,QueryPlan> {
        protected JexlNode node;
        
        /**
         * @param node
         */
        public FieldIndexParser(JexlNode node) {
            this.node = node;
        }
        
        public QueryPlan apply(Entry<Key,Value> entry) {
            QueryPlan newPlan = new QueryPlan(node, new Range(new Key(entry.getKey().getRow(), entry.getKey().getColumnFamily()), true, entry.getKey()
                            .followingKey(PartialKey.ROW_COLFAM), false));
            return newPlan;
        }
    }
}

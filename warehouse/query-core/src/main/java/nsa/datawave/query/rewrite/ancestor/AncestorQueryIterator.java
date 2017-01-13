package nsa.datawave.query.rewrite.ancestor;

import java.io.IOException;
import java.util.*;

import nsa.datawave.query.jexl.DatawaveJexlContext;
import nsa.datawave.query.rewrite.attributes.Attribute;
import nsa.datawave.query.rewrite.attributes.Document;
import nsa.datawave.query.rewrite.attributes.ValueTuple;
import nsa.datawave.query.rewrite.function.JexlEvaluation;
import nsa.datawave.query.rewrite.jexl.HitListArithmetic;
import nsa.datawave.query.rewrite.predicate.EventDataQueryFilter;
import nsa.datawave.query.util.*;
import nsa.datawave.util.StringUtils;
import nsa.datawave.query.rewrite.function.AncestorEquality;
import nsa.datawave.query.rewrite.iterator.NestedQueryIterator;
import nsa.datawave.query.rewrite.iterator.QueryIterator;
import nsa.datawave.query.rewrite.iterator.SourcedOptions;
import nsa.datawave.query.rewrite.iterator.logic.IndexIterator;
import nsa.datawave.query.rewrite.jexl.visitors.IteratorBuildingVisitor;
import nsa.datawave.query.rewrite.predicate.AncestorEventDataFilter;
import nsa.datawave.query.rewrite.predicate.ConfiguredPredicate;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.IteratorEnvironment;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;
import org.apache.commons.jexl2.JexlArithmetic;
import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.hadoop.io.Text;
import org.apache.log4j.Logger;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 * This is an ancestor QueryIterator implementation (all ancestor's metadata up to the TLD is included with each child)
 */
public class AncestorQueryIterator extends QueryIterator {
    private static final Logger log = Logger.getLogger(AncestorQueryIterator.class);
    
    public AncestorQueryIterator() {}
    
    public AncestorQueryIterator(AncestorQueryIterator other, IteratorEnvironment env) {
        super(other, env);
    }
    
    @Override
    public AncestorQueryIterator deepCopy(IteratorEnvironment env) {
        return new AncestorQueryIterator(this, env);
    }
    
    @Override
    public boolean validateOptions(Map<String,String> options) {
        boolean success = super.validateOptions(options);
        super.equality = new AncestorEquality();
        // we need the hit list arithmetic in any case (see getJexlEvaluation below)
        super.arithmetic = new HitListArithmetic(false);
        return success;
    }
    
    @Override
    public void init(SortedKeyValueIterator<Key,Value> source, Map<String,String> options, IteratorEnvironment env) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("AncestorQueryIterator init()");
        }
        
        super.init(source, options, env);
        
        // TODO: Figure out why this is in the TLD logic:
        
        // Replace the fieldIndexKeyDataTypeFilter with a chain of "anded" index-filtering predicates.
        // If no other predicates are configured via the indexfiltering.classes property, the method
        // simply returns the existing fieldIndexKeyDataTypeFilter value. Otherwise, the returned value
        // contains an "anded" chain of newly configured predicates following the existing
        // fieldIndexKeyDataTypeFilter value (assuming it is defined with something other than the default
        // "ALWAYS_TRUE" KeyIdentity.Function).
        fieldIndexKeyDataTypeFilter = parseIndexFilteringChain(new SourcedOptions<String,String>(source, env, options));
        
        disableIndexOnlyDocuments = false;
    }
    
    @Override
    public EventDataQueryFilter getEvaluationFilter() {
        // return a new script each time as this is not thread safe (maintains state)
        return new AncestorEventDataFilter(script);
    }
    
    @Override
    protected JexlEvaluation getJexlEvaluation(NestedQueryIterator<Key> documentSource) {
        return new JexlEvaluation(query, getArithmetic()) {
            private Key currentKey = null;
            
            private boolean isCurrentDoc(Key key) {
                return currentKey.getColumnFamilyData().equals(key.getColumnFamilyData());
            }
            
            private boolean isFromCurrentDoc(ValueTuple tuple) {
                Attribute<?> source = tuple.getSource();
                if (source != null && source.isMetadataSet()) {
                    return isCurrentDoc(source.getMetadata());
                }
                return false;
            }
            
            @Override
            public boolean isMatched(Object o) {
                boolean matched = false;
                if (super.isMatched(o)) {
                    // verify that we have at least one value within the current document being evaluated (dependent on ValueComparator below)
                    Set<ValueTuple> hits = ((HitListArithmetic) getArithmetic()).getHitTuples();
                    for (ValueTuple hit : hits) {
                        if (isFromCurrentDoc(hit)) {
                            matched = true;
                            break;
                        }
                    }
                }
                return matched;
            }
            
            @Override
            public boolean apply(Tuple3<Key,Document,DatawaveJexlContext> input) {
                currentKey = input.first();
                return super.apply(input);
            }
        };
    }
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate<Key> parseIndexFilteringChain(final Map<String,String> options) {
        // Create a list to gather up the predicates
        List<Predicate<Key>> predicates = Collections.emptyList();
        
        final String functions = (null != options) ? options.get(IndexIterator.INDEX_FILTERING_CLASSES) : StringUtils.EMPTY_STRING;
        if ((null != functions) && !functions.isEmpty()) {
            try {
                for (final String fClassName : StringUtils.splitIterable(functions, ',', true)) {
                    // Log it
                    if (log.isTraceEnabled()) {
                        log.trace("Configuring index-filtering class: " + fClassName);
                    }
                    
                    final Class<?> fClass = Class.forName(fClassName);
                    if (Predicate.class.isAssignableFrom(fClass)) {
                        // Create and configure the predicate
                        final Predicate p = (Predicate) fClass.newInstance();
                        if (p instanceof ConfiguredPredicate) {
                            ((ConfiguredPredicate) p).configure(options);
                        }
                        
                        // Initialize a mutable List instance and add the default filter, if defined
                        if (predicates.isEmpty()) {
                            predicates = new LinkedList<>();
                            final Predicate<Key> existingPredicate = fieldIndexKeyDataTypeFilter;
                            if ((null != existingPredicate) && (((Object) existingPredicate) != Predicates.alwaysTrue())) {
                                predicates.add(existingPredicate);
                            }
                        }
                        
                        // Add the newly instantiated predicate
                        predicates.add(p);
                    } else {
                        log.error(fClass + " is not a function or predicate. Postprocessing will not be performed.");
                        return fieldIndexKeyDataTypeFilter;
                    }
                }
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                log.error("Could not instantiate postprocessing chain!", e);
            }
        }
        
        // Assign the return value
        final Predicate<Key> predicate;
        if (!predicates.isEmpty()) {
            if (predicates.size() == 1) {
                predicate = predicates.get(0);
            } else {
                predicate = Predicates.and(predicates);
            }
        } else {
            predicate = fieldIndexKeyDataTypeFilter;
        }
        
        return predicate;
    }
    
    @Override
    protected IteratorBuildingVisitor createIteratorBuildingVisitor(final Range documentRange, boolean isQueryFullySatisfied, boolean sortedUIDs) {
        if (log.isTraceEnabled()) {
            log.trace(documentRange);
        }
        
        // We need to pull tokenized fields from the field index as well as index only fields
        Set<String> indexOnlyFields = new HashSet<String>(this.getIndexOnlyFields());
        indexOnlyFields.addAll(this.getTermFrequencyFields());
        
        return new AncestorIndexBuildingVisitor(this, this.myEnvironment, this.getTimeFilter(), this.getTypeMetadata(), indexOnlyFields,
                        this.getFieldIndexKeyDataTypeFilter(), this.fiAggregator, this.getHdfsFileSystem(), this.getHdfsCacheBaseURI(),
                        this.getHdfsCacheBaseURIAlternativesAsList(), this.getHdfsCacheSubDirPrefix(), this.getHdfsFileCompressionCodec(),
                        this.isHdfsCacheReused(), this.getHdfsCacheBufferSize(), this.getHdfsCacheScanPersistThreshold(), this.getHdfsCacheScanTimeout(),
                        this.getMaxIndexRangeSplit(), this.getMaxIvaratorSources(), this.getTypeMetadata().keySet(), Collections.<String> emptySet(),
                        this.getTermFrequencyFields(), isQueryFullySatisfied, sortedUIDs).limit(documentRange);
    }
    
    /**
     * Create a comparator used to order values within lists in the JexlContext.
     * 
     * @param from
     * @return a ValueComparator
     */
    @Override
    public Comparator<Object> getValueComparator(Tuple3<Key,Document,Map<String,Object>> from) {
        return new ValueComparator(from.second().getMetadata());
    }
    
    /**
     * A comparator which will ensure that values for the specific document are sorted first. This allows us to not use exhaustive matching within the jexl
     * context to determine whether the document of interest is actually a match.
     */
    private static class ValueComparator implements Comparator<Object> {
        final Text cf;
        
        public ValueComparator(Key metadata) {
            cf = (metadata == null ? new Text() : metadata.getColumnFamily());
        }
        
        @Override
        public int compare(Object o1, Object o2) {
            if (cf.getLength() == 0) {
                return new CompareToBuilder().append(o1, o2).toComparison();
            } else {
                boolean o1Matches = (o1 instanceof ValueTuple && (((ValueTuple) o1).getSource().getMetadata().getColumnFamily().equals(cf)));
                boolean o2Matches = (o2 instanceof ValueTuple && (((ValueTuple) o2).getSource().getMetadata().getColumnFamily().equals(cf)));
                if (o1Matches == o2Matches) {
                    return new CompareToBuilder().append(o1, o2).toComparison();
                } else if (o1Matches) {
                    return -1;
                } else {
                    return 1;
                }
            }
        }
    }
    
}

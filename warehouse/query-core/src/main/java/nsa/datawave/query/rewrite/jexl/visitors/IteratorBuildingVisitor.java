package nsa.datawave.query.rewrite.jexl.visitors;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import nsa.datawave.core.iterators.SourcePool;
import nsa.datawave.core.iterators.ThreadLocalPooledSource;
import nsa.datawave.query.jexl.DatawaveJexlContext;
import nsa.datawave.query.parser.JavaRegexAnalyzer;
import nsa.datawave.query.parser.JavaRegexAnalyzer.JavaRegexParseException;
import nsa.datawave.query.rewrite.Constants;
import nsa.datawave.query.rewrite.attributes.ValueTuple;
import nsa.datawave.query.rewrite.exceptions.DatawaveFatalQueryException;
import nsa.datawave.query.rewrite.iterator.NestedIterator;
import nsa.datawave.query.rewrite.iterator.PowerSet;
import nsa.datawave.query.rewrite.iterator.SourceFactory;
import nsa.datawave.query.rewrite.iterator.SourceManager;
import nsa.datawave.query.rewrite.iterator.builder.AbstractIteratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.AndIteratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.IndexFilterIteratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.IndexIteratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.IndexListIteratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.IndexRangeIteratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.IndexRegexIteratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.IteratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.IvaratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.NegationBuilder;
import nsa.datawave.query.rewrite.iterator.builder.OrIteratorBuilder;
import nsa.datawave.query.rewrite.iterator.builder.TermFrequencyIndexBuilder;
import nsa.datawave.query.rewrite.iterator.profile.QuerySpanCollector;
import nsa.datawave.query.rewrite.jexl.ArithmeticJexlEngines;
import nsa.datawave.query.rewrite.jexl.DefaultArithmetic;
import nsa.datawave.query.rewrite.jexl.JexlASTHelper;
import nsa.datawave.query.rewrite.jexl.JexlASTHelper.IdentifierOpLiteral;
import nsa.datawave.query.rewrite.jexl.LiteralRange;
import nsa.datawave.query.rewrite.jexl.LiteralRange.NodeOperand;
import nsa.datawave.query.rewrite.jexl.RefactoredDatawaveJexlEngine;
import nsa.datawave.query.rewrite.jexl.functions.FieldIndexAggregator;
import nsa.datawave.query.rewrite.jexl.functions.IdentityAggregator;
import nsa.datawave.query.rewrite.jexl.nodes.ExceededOrThresholdMarkerJexlNode;
import nsa.datawave.query.rewrite.jexl.nodes.ExceededValueThresholdMarkerJexlNode;
import nsa.datawave.query.rewrite.predicate.EventDataQueryFilter;
import nsa.datawave.query.rewrite.predicate.Filter;
import nsa.datawave.query.rewrite.predicate.NegationPredicate;
import nsa.datawave.query.rewrite.predicate.TimeFilter;
import nsa.datawave.query.util.IteratorToSortedKeyValueIterator;
import nsa.datawave.query.util.TypeMetadata;
import nsa.datawave.webservice.query.exception.DatawaveErrorCode;
import nsa.datawave.webservice.query.exception.QueryException;
import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.PartialKey;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.IteratorEnvironment;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;
import org.apache.commons.jexl2.JexlArithmetic;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.Script;
import org.apache.commons.jexl2.parser.ASTAndNode;
import org.apache.commons.jexl2.parser.ASTDelayedPredicate;
import org.apache.commons.jexl2.parser.ASTEQNode;
import org.apache.commons.jexl2.parser.ASTERNode;
import org.apache.commons.jexl2.parser.ASTFunctionNode;
import org.apache.commons.jexl2.parser.ASTIdentifier;
import org.apache.commons.jexl2.parser.ASTJexlScript;
import org.apache.commons.jexl2.parser.ASTMethodNode;
import org.apache.commons.jexl2.parser.ASTNENode;
import org.apache.commons.jexl2.parser.ASTNRNode;
import org.apache.commons.jexl2.parser.ASTNotNode;
import org.apache.commons.jexl2.parser.ASTNumberLiteral;
import org.apache.commons.jexl2.parser.ASTOrNode;
import org.apache.commons.jexl2.parser.ASTReference;
import org.apache.commons.jexl2.parser.ASTReferenceExpression;
import org.apache.commons.jexl2.parser.ASTStringLiteral;
import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.jexl2.parser.ParserTreeConstants;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.log4j.Logger;

import static org.apache.commons.jexl2.parser.JexlNodes.children;

/**
 * A visitor that builds a tree of iterators. The main points are at ASTAndNodes and ASTOrNodes, where the code will build AndIterators and OrIterators,
 * respectively. This will automatically roll up binary representations of subtrees into a generic n-ary tree because there isn't a true mapping between JEXL
 * AST trees and iterator trees. A JEXL tree can have subtrees rooted at an ASTNotNode whereas an iterator tree cannot.
 * 
 */
public class IteratorBuildingVisitor extends BaseVisitor {
    private static final Logger log = Logger.getLogger(IteratorBuildingVisitor.class);
    
    public static final String NULL_DELIMETER = "\u0000";
    
    @SuppressWarnings("rawtypes")
    protected NestedIterator root;
    protected SourceManager source;
    protected SortedKeyValueIterator<Key,Value> limitedSource = null;
    protected Map<Entry<String,String>,Entry<Key,Value>> limitedMap = null;
    protected Collection<String> includeReferences = PowerSet.instance();
    protected Collection<String> excludeReferences = Collections.emptyList();
    protected Predicate<Key> datatypeFilter;
    protected TimeFilter timeFilter;
    
    protected FileSystem hdfsFileSystem;
    protected String hdfsCacheDirURI;
    protected List<String> hdfsCacheDirURIAlternatives;
    protected String hdfsCacheSubDirPrefix;
    protected long hdfsCacheScanPersistThreshold = 100000L;
    protected long hdfsCacheScanTimeout = 1000L * 60 * 60;
    protected int hdfsCacheBufferSize = 10000;
    protected boolean hdfsCacheDirReused = false;
    protected String hdfsFileCompressionCodec;
    protected int maxRangeSplit = 11;
    protected SourcePool ivaratorSources = null;
    protected SortedKeyValueIterator<Key,Value> ivaratorSource = null;
    protected int ivaratorCount = 0;
    
    protected TypeMetadata typeMetadata;
    protected Set<String> indexOnlyFields;
    protected EventDataQueryFilter attrFilter;
    protected Set<String> fieldsToAggregate;
    protected Set<String> termFrequencyFields;
    protected FieldIndexAggregator fiAggregator;
    
    protected Range rangeLimiter;
    
    // should the UIDs be sorted. If so, then ivarators will be used. Otherwise it is determined that
    // each leaf of the tree can return unsorted UIDs (i.e. no intersections are required). In this
    // case the keys will be modified to include enough context to restart at the correct place.
    protected boolean sortedUIDs = true;
    
    protected boolean limitLookup;
    
    protected Class<? extends IteratorBuilder> iteratorBuilderClass = IndexIteratorBuilder.class;
    
    private Collection<String> unindexedFields = Lists.newArrayList();
    
    protected NegationPredicate predicate = null;
    
    protected boolean disableFiEval = false;
    
    protected boolean collectTimingDetails = false;
    
    protected QuerySpanCollector querySpanCollector = null;
    
    protected boolean limitOverride = false;
    // this is final. It will be set by the SatisfactionVisitor and cannot be
    // changed here.
    // prior code that changed its value from true to false will now log a
    // warning, so that the
    // SatisfactionVisitor can be changed to accomodate the conditions that
    // caused it.
    protected final boolean isQueryFullySatisfied;
    
    /**
     * Keep track of the iterator environment since we are deep copying
     */
    protected IteratorEnvironment env;
    
    public boolean isQueryFullySatisfied() {
        if (limitLookup) {
            return false;
        } else
            return isQueryFullySatisfied;
    }
    
    /**
     * A convenience constructor that accepts all terms doesn't give the index iterators built a key filter or transformer.
     * 
     * @param sourceFactory
     * @param typeMetadata
     * @param indexOnlyFields
     */
    public IteratorBuildingVisitor(SourceFactory sourceFactory, IteratorEnvironment env, TimeFilter timeFilter, TypeMetadata typeMetadata,
                    Set<String> indexOnlyFields, boolean isQueryFullySatisfied) {
        this(sourceFactory, env, timeFilter, typeMetadata, indexOnlyFields, Predicates.<Key> alwaysTrue(), new IdentityAggregator(null), null, null, null,
                        null, null, false, 10000, 100000L, 1000L * 60 * 60, 11, 33, PowerSet.<String> instance(), Collections.<String> emptyList(), Collections
                                        .<String> emptySet(), isQueryFullySatisfied, true);
        
    }
    
    /**
     * A convenience constructor that accepts all terms.
     * 
     * @param sourceFactory
     * @param typeMetadata
     * @param indexOnlyFields
     * @param datatypeFilter
     */
    public IteratorBuildingVisitor(SourceFactory sourceFactory, IteratorEnvironment env, TimeFilter timeFilter, TypeMetadata typeMetadata,
                    Set<String> indexOnlyFields, Predicate<Key> datatypeFilter, FieldIndexAggregator keyTform, FileSystem hdfsFileSystem,
                    String hdfsCacheDirURI, List<String> hdfsCacheDirURIAlternatives, String hdfsCacheSubDirPrefix, String hdfsFileCompressionCodec,
                    boolean hdfsCacheDirReused, int hdfsCacheBufferSize, long hdfsCacheScanPersistThreshold, long hdfsCacheScanTimeout, int maxRangeSplit,
                    int maxIvaratorSources, boolean isQueryFullySatisfied) {
        this(sourceFactory, env, timeFilter, typeMetadata, indexOnlyFields, datatypeFilter, keyTform, hdfsFileSystem, hdfsCacheDirURI,
                        hdfsCacheDirURIAlternatives, hdfsCacheSubDirPrefix, hdfsFileCompressionCodec, hdfsCacheDirReused, hdfsCacheBufferSize,
                        hdfsCacheScanPersistThreshold, hdfsCacheScanTimeout, maxRangeSplit, maxIvaratorSources, PowerSet.<String> instance(), Collections
                                        .<String> emptyList(), Collections.<String> emptySet(), isQueryFullySatisfied, true);
    }
    
    public IteratorBuildingVisitor(SourceFactory sourceFactory, IteratorEnvironment env, TimeFilter timeFilter, TypeMetadata typeMetadata,
                    Set<String> indexOnlyFields, Predicate<Key> datatypeFilter, FieldIndexAggregator fiAggregator, FileSystem hdfsFileSystem,
                    String hdfsCacheDirURI, List<String> hdfsCacheDirURIAlternatives, String hdfsCacheSubDirPrefix, String hdfsFileCompressionCodec,
                    boolean hdfsCacheDirReused, int hdfsCacheBufferSize, long hdfsCacheScanPersistThreshold, long hdfsCacheScanTimeout, int maxRangeSplit,
                    int maxIvaratorSources, Collection<String> includes, Collection<String> excludes, Set<String> termFrequencyFields,
                    boolean isQueryFullySatisfied, boolean sortedUIDs) {
        SortedKeyValueIterator<Key,Value> skvi = sourceFactory.getSourceDeepCopy();
        this.source = new SourceManager(skvi);
        this.env = env;
        Map<String,String> options = Maps.newHashMap();
        try {
            this.source.init(skvi, options, env);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        this.timeFilter = timeFilter;
        this.typeMetadata = typeMetadata;
        this.indexOnlyFields = indexOnlyFields;
        this.fieldsToAggregate = indexOnlyFields;
        this.attrFilter = attrFilter;
        this.fiAggregator = fiAggregator;
        this.datatypeFilter = datatypeFilter;
        this.hdfsFileSystem = hdfsFileSystem;
        this.hdfsCacheDirURI = hdfsCacheDirURI;
        this.hdfsCacheDirURIAlternatives = hdfsCacheDirURIAlternatives;
        this.hdfsCacheSubDirPrefix = (hdfsCacheSubDirPrefix == null ? "" : hdfsCacheSubDirPrefix);
        this.hdfsFileCompressionCodec = hdfsFileCompressionCodec;
        this.hdfsCacheDirReused = hdfsCacheDirReused;
        this.hdfsCacheBufferSize = hdfsCacheBufferSize;
        this.hdfsCacheScanPersistThreshold = hdfsCacheScanPersistThreshold;
        this.hdfsCacheScanTimeout = hdfsCacheScanTimeout;
        this.maxRangeSplit = maxRangeSplit;
        this.includeReferences = includes;
        this.excludeReferences = excludes;
        this.termFrequencyFields = termFrequencyFields;
        predicate = new NegationPredicate(indexOnlyFields);
        this.isQueryFullySatisfied = isQueryFullySatisfied;
        this.sortedUIDs = sortedUIDs;
        this.ivaratorSources = new SourcePool(sourceFactory, maxIvaratorSources);
        this.ivaratorSource = new ThreadLocalPooledSource<Key,Value>(ivaratorSources);
    }
    
    @SuppressWarnings("unchecked")
    public <T> NestedIterator<T> root() {
        return root;
    }
    
    @Override
    public Object visit(ASTJexlScript node, Object data) {
        if (limitLookup) {
            limitedSource = source.deepCopy(env);
            limitedMap = Maps.newHashMap();
        }
        return super.visit(node, data);
    }
    
    @Override
    public Object visit(ASTAndNode and, Object data) {
        
        if (ExceededOrThresholdMarkerJexlNode.instanceOf(and)) {
            if (!limitLookup) {
                
                JexlNode source = ExceededOrThresholdMarkerJexlNode.getExceededOrThresholdSource(and);
                // if the parent is our ExceededOrThreshold marker, then use an
                // Ivarator to get the job done
                if (source instanceof ASTAndNode) {
                    try {
                        ivarateList(source, data);
                    } catch (IOException ioe) {
                        throw new DatawaveFatalQueryException(ioe);
                    }
                } else {
                    QueryException qe = new QueryException(DatawaveErrorCode.UNEXPECTED_SOURCE_NODE, MessageFormat.format("{0}",
                                    "Limited ExceededOrThresholdMarkerJexlNode"));
                    throw new DatawaveFatalQueryException(qe);
                    
                }
            } else {
                QueryException qe = new QueryException(DatawaveErrorCode.UNEXPECTED_SOURCE_NODE, MessageFormat.format("{0}",
                                "Limited ExceededOrThresholdMarkerJexlNode"));
                throw new DatawaveFatalQueryException(qe);
            }
            // we should not reach this case. This is an unallowed case.
            
        } else if (null != data && data instanceof IndexRangeIteratorBuilder) {
            // index checking has already been done, otherwise we would not have
            // an "ExceededValueThresholdMarker"
            // hence the "IndexAgnostic" method can be used here
            Map<LiteralRange<?>,List<JexlNode>> ranges = JexlASTHelper.getBoundedRangesIndexAgnostic(and, null, true);
            if (ranges.size() != 1) {
                QueryException qe = new QueryException(DatawaveErrorCode.MULTIPLE_RANGES_IN_EXPRESSION);
                throw new DatawaveFatalQueryException(qe);
            }
            ((IndexRangeIteratorBuilder) data).setRange(ranges.keySet().iterator().next());
        } else if (ExceededValueThresholdMarkerJexlNode.instanceOf(and)) {
            // if the parent is our ExceededValueThreshold marker, then use an
            // Ivarator to get the job done
            JexlNode source = ExceededValueThresholdMarkerJexlNode.getExceededValueThresholdSource(and);
            if (!limitLookup) {
                if (source instanceof ASTAndNode) {
                    try {
                        if (JexlASTHelper.getFunctionNodes(source).isEmpty()) {
                            ivarateRange(source, data);
                        } else {
                            ivarateFilter(source, data);
                        }
                    } catch (IOException ioe) {
                        throw new DatawaveFatalQueryException("Unable to ivarate", ioe);
                    }
                } else if (source instanceof ASTERNode || source instanceof ASTNRNode) {
                    try {
                        ivarateRegex(source, data);
                    } catch (IOException ioe) {
                        throw new DatawaveFatalQueryException("Unable to ivarate", ioe);
                    }
                } else {
                    QueryException qe = new QueryException(DatawaveErrorCode.UNEXPECTED_SOURCE_NODE, MessageFormat.format("{0}",
                                    "ExceededValueThresholdMarkerJexlNode"));
                    throw new DatawaveFatalQueryException(qe);
                }
            } else {
                
                String identifier = null;
                LiteralRange<?> range = null;
                boolean negated = false;
                if (source instanceof ASTAndNode) {
                    range = buildLiteralRange(source, null);
                    identifier = range.getFieldName();
                } else {
                    if (source instanceof ASTNRNode)
                        negated = true;
                    range = buildLiteralRange(source);
                    identifier = JexlASTHelper.getIdentifier(source);
                }
                if (data instanceof AbstractIteratorBuilder) {
                    AbstractIteratorBuilder oib = (AbstractIteratorBuilder) data;
                    if (oib.isInANot()) {
                        negated = true;
                    }
                }
                NestedIterator<Key> nested = null;
                if (indexOnlyFields.contains(identifier) || termFrequencyFields.contains(identifier)) {
                    
                    nested = buildExceededFromTermFrequency(identifier, range, data);
                    
                } else {
                    /**
                     * This is okay since 1) We are doc specific 2) We are not index only or tf 3) Therefore, we must evaluate against the document for this
                     * expression 4) Return a stubbed range in case we have a disjunction that breaks the current doc.
                     */
                    if (!limitOverride && (!negated && !(null != data && data instanceof NegationBuilder)))
                        nested = createExceededCheck(identifier, range);
                }
                
                if (null != nested && null != data && data instanceof AbstractIteratorBuilder) {
                    
                    AbstractIteratorBuilder iterators = (AbstractIteratorBuilder) data;
                    if (negated) {
                        iterators.addExclude(nested);
                    } else {
                        iterators.addInclude(nested);
                    }
                } else {
                    if (isQueryFullySatisfied == true) {
                        log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
                    }
                    return nested;
                    
                }
                
            }
        } else if (null != data && data instanceof AndIteratorBuilder) {
            and.childrenAccept(this, data);
        } else {
            // Create an AndIterator and recursively add the children
            AbstractIteratorBuilder andItr = new AndIteratorBuilder();
            and.childrenAccept(this, andItr);
            
            // If there is no parent
            if (data == null) {
                // Make this AndIterator the root node
                if (andItr.includes().size() != 0) {
                    root = andItr.build();
                }
            } else {
                // Otherwise, add this AndIterator to its parent
                AbstractIteratorBuilder parent = (AbstractIteratorBuilder) data;
                if (andItr.includes().size() != 0) {
                    parent.addInclude(andItr.build());
                }
            }
            
            if (log.isTraceEnabled()) {
                log.trace("ASTAndNode visit: pretty formatting of:\nparent.includes:" + formatIncludesOrExcludes(andItr.includes()) + "\nparent.excludes:"
                                + formatIncludesOrExcludes(andItr.excludes()));
            }
            
        }
        
        return null;
    }
    
    private LiteralRange<?> buildLiteralRange(JexlNode source) {
        if (source instanceof ASTERNode)
            return buildLiteralRange(((ASTERNode) source));
        else if (source instanceof ASTNRNode)
            return buildLiteralRange(((ASTNRNode) source));
        else
            return null;
        
    }
    
    public static LiteralRange<?> buildLiteralRange(ASTERNode node) {
        JavaRegexAnalyzer analyzer;
        try {
            analyzer = new JavaRegexAnalyzer(String.valueOf(JexlASTHelper.getLiteralValue(node)));
            
            LiteralRange<String> range = new LiteralRange<String>(JexlASTHelper.getIdentifier(node), NodeOperand.AND);
            range.updateLower(analyzer.getLeadingOrTrailingLiteral(), true);
            range.updateUpper(analyzer.getLeadingOrTrailingLiteral() + Constants.MAX_UNICODE_STRING, true);
            return range;
        } catch (JavaRegexParseException | NoSuchElementException e) {
            throw new DatawaveFatalQueryException(e);
        }
    }
    
    LiteralRange<?> buildLiteralRange(ASTNRNode node) {
        JavaRegexAnalyzer analyzer;
        try {
            analyzer = new JavaRegexAnalyzer(String.valueOf(JexlASTHelper.getLiteralValue(node)));
            
            LiteralRange<String> range = new LiteralRange<String>(JexlASTHelper.getIdentifier(node), NodeOperand.AND);
            range.updateLower(analyzer.getLeadingOrTrailingLiteral(), true);
            range.updateUpper(analyzer.getLeadingOrTrailingLiteral() + Constants.MAX_UNICODE_STRING, true);
            
            return range;
        } catch (JavaRegexParseException | NoSuchElementException e) {
            throw new DatawaveFatalQueryException(e);
        }
    }
    
    /**
     * 
     * @param identifier
     * @param data
     */
    private NestedIterator<Key> buildExceededFromTermFrequency(String identifier, LiteralRange<?> range, Object data) {
        if (limitLookup) {
            
            TermFrequencyIndexBuilder builder = new TermFrequencyIndexBuilder();
            builder.setSource(source.deepCopy(env));
            builder.setTypeMetadata(typeMetadata);
            builder.setIndexOnlyFields(indexOnlyFields);
            builder.setFieldsToAggregate(fieldsToAggregate);
            builder.setTimeFilter(timeFilter);
            builder.setAttrFilter(attrFilter);
            builder.setDatatypeFilter(datatypeFilter);
            
            Key startKey = rangeLimiter.getStartKey();
            
            StringBuilder strBuilder = new StringBuilder("fi");
            strBuilder.append(NULL_DELIMETER).append(range.getFieldName());
            Text cf = new Text(strBuilder.toString());
            
            strBuilder = new StringBuilder(range.getLower().toString());
            
            strBuilder.append(NULL_DELIMETER).append(startKey.getColumnFamily());
            Text cq = new Text(strBuilder.toString());
            
            Key seekBeginKey = new Key(startKey.getRow(), cf, cq, startKey.getTimestamp());
            
            strBuilder = new StringBuilder(range.getUpper().toString());
            
            strBuilder.append(NULL_DELIMETER).append(startKey.getColumnFamily());
            cq = new Text(strBuilder.toString());
            
            Key seekEndKey = new Key(startKey.getRow(), cf, cq, startKey.getTimestamp());
            
            builder.setRange(new Range(seekBeginKey, true, seekEndKey, true));
            builder.setField(identifier);
            
            return builder.build();
        } else {
            QueryException qe = new QueryException(DatawaveErrorCode.UNEXPECTED_SOURCE_NODE, MessageFormat.format("{0}", "buildExceededFromTermFrequency"));
            throw new DatawaveFatalQueryException(qe);
        }
        
    }
    
    @Override
    public Object visit(ASTOrNode or, Object data) {
        if (null != data && data instanceof OrIteratorBuilder) {
            or.childrenAccept(this, data);
        } else {
            // Create an OrIterator and recursively add the children
            AbstractIteratorBuilder orItr = new OrIteratorBuilder();
            orItr.setSortedUIDs(sortedUIDs);
            if (data instanceof NegationBuilder) {
                orItr.setInANot(true);
            }
            or.childrenAccept(this, orItr);
            
            // If there is no parent
            if (data == null) {
                // Make this OrIterator the root node
                if (orItr.includes().size() != 0) {
                    root = orItr.build();
                }
            } else {
                // Otherwise, add this OrIterator to its parent
                AbstractIteratorBuilder parent = (AbstractIteratorBuilder) data;
                if (orItr.includes().size() != 0) {
                    parent.addInclude(orItr.build());
                }
                if (log.isTraceEnabled()) {
                    log.trace("ASTOrNode visit: pretty formatting of:\nparent.includes:" + formatIncludesOrExcludes(orItr.includes()) + "\nparent.excludes:"
                                    + formatIncludesOrExcludes(orItr.excludes()));
                }
            }
        }
        
        return null;
    }
    
    @Override
    public Object visit(ASTNotNode not, Object data) {
        // We have no parent
        if (root == null && data == null) {
            // We don't support querying only on a negation
            throw new IllegalStateException("Root node cannot be a negation!");
        }
        
        NegationBuilder stub = new NegationBuilder();
        
        // Add all of the children to this negation
        not.childrenAccept(this, stub);
        
        // Then add the children to the parent's children
        AbstractIteratorBuilder parent = (AbstractIteratorBuilder) data;
        /*
         * because we're in a negation, the includes become excludes to the parent. conversely, the excludes in the child tree become includes to the parent.
         */
        parent.excludes().addAll(stub.includes());
        parent.includes().addAll(stub.excludes());
        
        if (log.isTraceEnabled()) {
            log.trace("pretty formatting of:\nparent.includes:" + formatIncludesOrExcludes(parent.includes()) + "\nparent.excludes:"
                            + formatIncludesOrExcludes(parent.excludes()));
        }
        return null;
    }
    
    private String formatIncludesOrExcludes(List<NestedIterator> in) {
        String builder = in.toString();
        builder = builder.replaceAll("OrIterator:", "\n\tOrIterator:");
        builder = builder.replaceAll("Includes:", "\n\t\tIncludes:");
        builder = builder.replaceAll("Excludes:", "\n\t\tExcludes:");
        builder = builder.replaceAll("Bridge:", "\n\t\t\tBridge:");
        return builder.toString();
    }
    
    @Override
    public Object visit(ASTNENode node, Object data) {
        // We have no parent already defined
        if (data == null) {
            // We don't support querying only on a negation
            throw new IllegalStateException("Root node cannot be a negation");
        }
        IndexIteratorBuilder builder = null;
        try {
            builder = iteratorBuilderClass.asSubclass(IndexIteratorBuilder.class).newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        builder.setSource(source.deepCopy(env));
        builder.setTypeMetadata(typeMetadata);
        builder.setIndexOnlyFields(indexOnlyFields);
        builder.setFieldsToAggregate(fieldsToAggregate);
        builder.setTimeFilter(timeFilter);
        builder.setDatatypeFilter(datatypeFilter);
        builder.setKeyTransform(fiAggregator);
        node.childrenAccept(this, builder);
        
        // A EQNode may be of the form FIELD == null. The evaluation can
        // handle this, so we should just not build an IndexIterator for it.
        if (null == builder.getValue()) {
            
            // SatisfactionVisitor should have already initialized this to false
            if (isQueryFullySatisfied == true) {
                log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
            }
            return null;
        }
        
        AbstractIteratorBuilder iterators = (AbstractIteratorBuilder) data;
        // Add the negated IndexIteratorBuilder to the parent as an *exclude*
        if (!iterators.hasSeen(builder.getField(), builder.getValue()) && includeReferences.contains(builder.getField())
                        && !excludeReferences.contains(builder.getField())) {
            iterators.addExclude(builder.build());
        } else {
            // SatisfactionVisitor should have already initialized this to false
            if (isQueryFullySatisfied == true) {
                log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
            }
        }
        
        return null;
    }
    
    @Override
    public Object visit(ASTMethodNode node, Object data) {
        // if i find a method node then i can't build an index for the identifier it is called on
        return null;
    }
    
    @Override
    public Object visit(ASTEQNode node, Object data) {
        IndexIteratorBuilder builder = null;
        try {
            builder = iteratorBuilderClass.asSubclass(IndexIteratorBuilder.class).newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        
        /**
         * If we have an unindexed type enforced, we've been configured to assert whether the field is indexed.
         */
        if (isUnindexed(node)) {
            if (isQueryFullySatisfied == true) {
                log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
            }
            return null;
        }
        // boolean to tell us if we've overridden our subtree due to
        // a negation or
        boolean isNegation = false;
        if (data instanceof NegationBuilder) {
            NegationBuilder nb = (NegationBuilder) data;
            isNegation = true;
        } else if (data instanceof AbstractIteratorBuilder) {
            AbstractIteratorBuilder oib = (AbstractIteratorBuilder) data;
            isNegation = oib.isInANot();
        }
        builder.setSource(getSourceIterator(node, isNegation));
        builder.setTimeFilter(getTimeFilter(node));
        builder.setTypeMetadata(typeMetadata);
        builder.setIndexOnlyFields(indexOnlyFields);
        builder.setFieldsToAggregate(fieldsToAggregate);
        builder.setDatatypeFilter(datatypeFilter);
        builder.setKeyTransform(fiAggregator);
        builder.canBuildDocument(!limitLookup && this.isQueryFullySatisfied);
        node.childrenAccept(this, builder);
        
        // A EQNode may be of the form FIELD == null. The evaluation can
        // handle this, so we should just not build an IndexIterator for it.
        if (null == builder.getValue()) {
            return null;
        }
        
        // We have no parent already defined
        if (data == null) {
            // Make this EQNode the root
            if (!includeReferences.contains(builder.getField()) && excludeReferences.contains(builder.getField())) {
                throw new IllegalStateException(builder.getField() + " is a blacklisted reference.");
            } else if (builder.getField() != null) {
                root = builder.build();
                
                if (log.isTraceEnabled()) {
                    log.trace("Build IndexIterator: " + root);
                }
            }
        } else {
            AbstractIteratorBuilder iterators = (AbstractIteratorBuilder) data;
            // Add this IndexIterator to the parent
            final boolean isNew = !iterators.hasSeen(builder.getField(), builder.getValue());
            final boolean inclusionReference = includeReferences.contains(builder.getField());
            final boolean notExcluded = !excludeReferences.contains(builder.getField());
            
            if (isNew && inclusionReference && notExcluded) {
                iterators.addInclude(builder.build());
            } else {
                if (isQueryFullySatisfied == true) {
                    log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
                }
            }
        }
        
        return null;
    }
    
    protected TimeFilter getTimeFilter(ASTEQNode node) {
        final String identifier = JexlASTHelper.getIdentifier(node);
        if (limitLookup && !limitOverride && !indexOnlyFields.contains(identifier)) {
            return TimeFilter.alwaysTrue();
        }
        
        return timeFilter;
        
    }
    
    protected SortedKeyValueIterator<Key,Value> getSourceIterator(final ASTEQNode node, boolean negation) {
        
        SortedKeyValueIterator<Key,Value> kvIter = null;
        String identifier = JexlASTHelper.getIdentifier(node);
        try {
            if (limitLookup && !negation) {
                
                if (!disableFiEval && indexOnlyFields.contains(identifier)) {
                    kvIter = source.deepCopy(env);
                    seekIndexOnlyDocument(kvIter, node);
                } else if (disableFiEval && indexOnlyFields.contains(identifier)) {
                    kvIter = createIndexOnlyKey(node);
                } else if (limitOverride) {
                    kvIter = createIndexOnlyKey(node);
                } else {
                    kvIter = new IteratorToSortedKeyValueIterator(getNodeEntry(node).iterator());
                }
                
            } else {
                kvIter = source.deepCopy(env);
                seekIndexOnlyDocument(kvIter, node);
            }
            
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        return kvIter;
    }
    
    private SortedKeyValueIterator<Key,Value> createIndexOnlyKey(ASTEQNode node) throws IOException {
        Key newStartKey = getKey(node);
        
        IdentifierOpLiteral op = JexlASTHelper.getIdentifierOpLiteral(node);
        if (null == op || null == op.getLiteralValue()) {
            // deep copy since this is likely a null literal
            return source.deepCopy(env);
        }
        
        String fn = op.deconstructIdentifier();
        String literal = String.valueOf(op.getLiteralValue());
        
        if (log.isTraceEnabled()) {
            log.trace("createIndexOnlyKey for " + fn + " " + literal + " " + newStartKey);
        }
        List<Entry<Key,Value>> kv = Lists.newArrayList();
        if (null != limitedMap.get(Maps.immutableEntry(fn, literal))) {
            kv.add(limitedMap.get(Maps.immutableEntry(fn, literal)));
        } else {
            
            SortedKeyValueIterator<Key,Value> mySource = limitedSource;
            // if source size > 0, we are free to use up to that number for this query
            if (source.getSourceSize() > 0)
                mySource = source.deepCopy(env);
            
            mySource.seek(new Range(newStartKey, true, newStartKey.followingKey(PartialKey.ROW_COLFAM_COLQUAL), false), Collections.<ByteSequence> emptyList(),
                            false);
            
            if (mySource.hasTop()) {
                kv.add(Maps.immutableEntry(mySource.getTopKey(), Constants.NULL_VALUE));
                
            }
        }
        
        SortedKeyValueIterator<Key,Value> kvIter = new IteratorToSortedKeyValueIterator(kv.iterator());
        return kvIter;
    }
    
    /**
     * @param kvIter
     * @param node
     * @throws IOException
     */
    private void seekIndexOnlyDocument(SortedKeyValueIterator<Key,Value> kvIter, ASTEQNode node) throws IOException {
        if (null != rangeLimiter && limitLookup) {
            
            Key newStartKey = getKey(node);
            
            kvIter.seek(new Range(newStartKey, true, newStartKey.followingKey(PartialKey.ROW_COLFAM_COLQUAL), false), Collections.<ByteSequence> emptyList(),
                            false);
            
        }
    }
    
    /**
     * @param node
     * @return
     */
    protected Collection<Entry<Key,Value>> getNodeEntry(ASTEQNode node) {
        Key key = getKey(node);
        return Collections.singleton(Maps.immutableEntry(key, Constants.NULL_VALUE));
        
    }
    
    /**
     * @param identifier
     * @param range
     * @return
     */
    protected Collection<Entry<Key,Value>> getExceededEntry(String identifier, LiteralRange<?> range) {
        
        Key key = getIvaratorKey(identifier, range);
        return Collections.singleton(Maps.immutableEntry(key, Constants.NULL_VALUE));
        
    }
    
    protected Key getIvaratorKey(String identifier, LiteralRange<?> range) {
        
        Key startKey = rangeLimiter.getStartKey();
        
        Object objValue = range.getLower();
        String value = null == objValue ? "null" : objValue.toString();
        
        StringBuilder builder = new StringBuilder("fi");
        builder.append(NULL_DELIMETER).append(identifier);
        Text cf = new Text(builder.toString());
        
        builder = new StringBuilder(value);
        
        builder.append(NULL_DELIMETER).append(startKey.getColumnFamily());
        Text cq = new Text(builder.toString());
        
        return new Key(startKey.getRow(), cf, cq, startKey.getTimestamp());
    }
    
    protected Key getKey(JexlNode node) {
        Key startKey = rangeLimiter.getStartKey();
        String identifier = JexlASTHelper.getIdentifier(node);
        Object objValue = JexlASTHelper.getLiteralValue(node);
        String value = null == objValue ? "null" : objValue.toString();
        
        StringBuilder builder = new StringBuilder("fi");
        builder.append(NULL_DELIMETER).append(identifier);
        Text cf = new Text(builder.toString());
        
        builder = new StringBuilder(value);
        
        builder.append(NULL_DELIMETER).append(startKey.getColumnFamily());
        Text cq = new Text(builder.toString());
        
        return new Key(startKey.getRow(), cf, cq, startKey.getTimestamp());
    }
    
    @Override
    public Object visit(ASTReference node, Object o) {
        // Recurse only if not delayed
        if (!ASTDelayedPredicate.instanceOf(node)) {
            super.visit(node, o);
        } else {
            JexlNode subNode = ASTDelayedPredicate.getQueryPropertySource(node, ASTDelayedPredicate.class);
            if (subNode instanceof ASTEQNode) {
                String fn = JexlASTHelper.getIdentifier(subNode);
                if (indexOnlyFields.contains(fn)) {
                    if (limitLookup) {
                        visitDelayedIndexOnly((ASTEQNode) subNode, o);
                    } else {
                        node.jjtGetChild(0).jjtAccept(this, o);
                    }
                } else {
                    if (isQueryFullySatisfied == true) {
                        log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
                    }
                }
            } else {
                if (isQueryFullySatisfied == true) {
                    log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
                }
            }
            log.warn("Will not process ASTDelayedPredicate.");
        }
        
        return null;
    }
    
    /**
     * This method should only be used when we know it is not a term frequency or index only in the limited case, as we will subsequently evaluate this
     * expression during final evaluation
     * 
     * @param identifier
     * @param range
     * @return
     */
    protected NestedIterator<Key> createExceededCheck(String identifier, LiteralRange<?> range) {
        IndexIteratorBuilder builder = null;
        try {
            builder = iteratorBuilderClass.asSubclass(IndexIteratorBuilder.class).newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        
        // boolean to tell us if we've overridden our subtree due to
        // a negation or
        IteratorToSortedKeyValueIterator kvIter = new IteratorToSortedKeyValueIterator(getExceededEntry(identifier, range).iterator());
        builder.setSource(kvIter);
        builder.setValue(null != range.getLower() ? range.getLower().toString() : "null");
        builder.setField(identifier);
        builder.setTimeFilter(TimeFilter.alwaysTrue());
        builder.setTypeMetadata(typeMetadata);
        builder.setIndexOnlyFields(indexOnlyFields);
        builder.setFieldsToAggregate(fieldsToAggregate);
        builder.setDatatypeFilter(datatypeFilter);
        builder.setKeyTransform(fiAggregator);
        
        return builder.build();
    }
    
    protected Object visitDelayedIndexOnly(ASTEQNode node, Object data) {
        IndexIteratorBuilder builder = null;
        try {
            builder = iteratorBuilderClass.asSubclass(IndexIteratorBuilder.class).newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        
        /**
         * If we have an unindexed type enforced, we've been configured to assert whether the field is indexed.
         */
        if (isUnindexed(node)) {
            if (isQueryFullySatisfied == true) {
                log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
            }
            return null;
        }
        
        // boolean to tell us if we've overridden our subtree due to
        // a negation or
        boolean isNegation = (null != data && data instanceof NegationBuilder);
        builder.setSource(getSourceIterator(node, isNegation));
        
        builder.setTimeFilter(getTimeFilter(node));
        builder.setTypeMetadata(typeMetadata);
        builder.setIndexOnlyFields(indexOnlyFields);
        builder.setFieldsToAggregate(fieldsToAggregate);
        builder.setDatatypeFilter(datatypeFilter);
        builder.setKeyTransform(fiAggregator);
        
        node.childrenAccept(this, builder);
        
        // A EQNode may be of the form FIELD == null. The evaluation can
        // handle this, so we should just not build an IndexIterator for it.
        if (null == builder.getValue()) {
            if (isQueryFullySatisfied == true) {
                log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
            }
            return null;
        }
        
        // We have no parent already defined
        if (data == null) {
            // Make this EQNode the root
            if (!includeReferences.contains(builder.getField()) && excludeReferences.contains(builder.getField())) {
                throw new IllegalStateException(builder.getField() + " is a blacklisted reference.");
            } else {
                root = builder.build();
                
                if (log.isTraceEnabled()) {
                    log.trace("Build IndexIterator: " + root);
                }
            }
        } else {
            AbstractIteratorBuilder iterators = (AbstractIteratorBuilder) data;
            // Add this IndexIterator to the parent
            final boolean isNew = !iterators.hasSeen(builder.getField(), builder.getValue());
            final boolean inclusionReference = includeReferences.contains(builder.getField());
            final boolean notExcluded = !excludeReferences.contains(builder.getField());
            if (isNew && inclusionReference && notExcluded) {
                iterators.addInclude(builder.build());
            } else {
                if (isQueryFullySatisfied == true) {
                    log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
                }
            }
        }
        
        return null;
    }
    
    @Override
    public Object visit(ASTReferenceExpression node, Object o) {
        // Recurse
        node.jjtGetChild(0).jjtAccept(this, o);
        
        return null;
    }
    
    @Override
    public Object visit(ASTIdentifier node, Object o) {
        // Set the literal in the IndexIterator
        if (o instanceof IndexIteratorBuilder) {
            IndexIteratorBuilder builder = (IndexIteratorBuilder) o;
            builder.setField(JexlASTHelper.deconstructIdentifier(node.image));
        }
        
        if (isUnindexed(node)) {
            if (isQueryFullySatisfied == true) {
                log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false in the SatisfactionVisitor");
            }
        }
        
        return null;
    }
    
    @Override
    public Object visit(ASTStringLiteral node, Object o) {
        // Set the literal in the IndexIterator
        AbstractIteratorBuilder builder = (AbstractIteratorBuilder) o;
        builder.setValue(node.image);
        
        return null;
    }
    
    @Override
    public Object visit(ASTNumberLiteral node, Object o) {
        // Set the literal in the IndexIterator
        AbstractIteratorBuilder builder = (AbstractIteratorBuilder) o;
        builder.setValue(node.image);
        
        return null;
    }
    
    private boolean isUsable(Path path) throws IOException {
        try {
            if (!hdfsFileSystem.mkdirs(path)) {
                throw new IOException("Unable to mkdirs: fs.mkdirs(" + path + ")->false");
            }
        } catch (MalformedURLException e) {
            throw new IOException("Unable to load hadoop configuration", e);
        } catch (Exception e) {
            log.warn("Unable to access " + path, e);
            return false;
        }
        return true;
    }
    
    /**
     * Create a cache directory path for a specified regex node. If alternatives have been specified, then random alternatives will be attempted until one is
     * found that can be written to.
     * 
     * @return A path
     */
    private URI getTemporaryCacheDir() throws IOException {
        // first lets increment the count for a unique subdirectory
        String subdirectory = hdfsCacheSubDirPrefix + "term" + Integer.toString(++ivaratorCount);
        
        if (hdfsCacheDirURIAlternatives != null && !hdfsCacheDirURIAlternatives.isEmpty()) {
            for (int i = 0; i < hdfsCacheDirURIAlternatives.size(); i++) {
                String hdfsCacheDirURI = hdfsCacheDirURIAlternatives.get(i);
                Path path = new Path(hdfsCacheDirURI, subdirectory);
                if (isUsable(path)) {
                    return path.toUri();
                }
            }
            throw new IOException("Unable to find a usable hdfs cache dir out of " + hdfsCacheDirURIAlternatives);
        }
        Path path = new Path(hdfsCacheDirURI, subdirectory);
        if (!isUsable(path)) {
            throw new IOException("Unable to access hdfs cache " + path.toUri());
        }
        return path.toUri();
    }
    
    /**
     * Build the iterator stack using the regex ivarator (field index caching regex iterator)
     * 
     * @param source
     * @param data
     */
    public void ivarateRegex(JexlNode source, Object data) throws IOException {
        IndexRegexIteratorBuilder builder = new IndexRegexIteratorBuilder();
        if (source instanceof ASTERNode || source instanceof ASTNRNode) {
            builder.setNegated(source instanceof ASTNRNode);
            builder.setField(JexlASTHelper.getIdentifier(source));
            builder.setValue(String.valueOf(JexlASTHelper.getLiteralValue(source)));
        } else {
            QueryException qe = new QueryException(DatawaveErrorCode.UNEXPECTED_SOURCE_NODE,
                            MessageFormat.format("{0}", "ExceededValueThresholdMarkerJexlNode"));
            throw new DatawaveFatalQueryException(qe);
        }
        builder.canBuildDocument(!limitLookup && this.isQueryFullySatisfied);
        ivarate(builder, source, data);
    }
    
    /**
     * Build the iterator stack using the regex ivarator (field index caching regex iterator)
     * 
     * @param source
     * @param data
     */
    public void ivarateList(JexlNode source, Object data) throws IOException {
        IndexListIteratorBuilder builder = new IndexListIteratorBuilder();
        builder.setNegated(false);
        
        Map<String,Object> parameters;
        try {
            parameters = ExceededOrThresholdMarkerJexlNode.getParameters(source);
        } catch (URISyntaxException e) {
            QueryException qe = new QueryException(DatawaveErrorCode.UNPARSEABLE_URI_PARAMETER, MessageFormat.format("Class: {0}",
                            ExceededOrThresholdMarkerJexlNode.class.getSimpleName()));
            throw new DatawaveFatalQueryException(qe);
        }
        
        builder.setField(String.valueOf(parameters.get(ExceededOrThresholdMarkerJexlNode.FIELD_PROP)));
        
        if (parameters.containsKey(ExceededOrThresholdMarkerJexlNode.VALUES_PROP)) {
            builder.setValues((Set<String>) parameters.get(ExceededOrThresholdMarkerJexlNode.VALUES_PROP));
        }
        if (parameters.containsKey(ExceededOrThresholdMarkerJexlNode.FST_URI_PROP)) {
            builder.setFstURI((URI) parameters.get(ExceededOrThresholdMarkerJexlNode.FST_URI_PROP));
        }
        
        builder.canBuildDocument(!limitLookup && this.isQueryFullySatisfied);
        
        ivarate(builder, source, data);
    }
    
    /**
     * Build the iterator stack using the regex ivarator (field index caching regex iterator)
     * 
     * @param source
     * @param data
     * @return
     */
    public LiteralRange<?> buildLiteralRange(JexlNode source, Object data) {
        // index checking has already been done, otherwise we would not have an
        // "ExceededValueThresholdMarker"
        // hence the "IndexAgnostic" method can be used here
        if (source instanceof ASTAndNode) {
            Map<LiteralRange<?>,List<JexlNode>> ranges = JexlASTHelper.getBoundedRangesIndexAgnostic((ASTAndNode) source, null, true);
            if (ranges.size() != 1) {
                QueryException qe = new QueryException(DatawaveErrorCode.MULTIPLE_RANGES_IN_EXPRESSION);
                throw new DatawaveFatalQueryException(qe);
            }
            return ranges.keySet().iterator().next();
        } else {
            QueryException qe = new QueryException(DatawaveErrorCode.UNEXPECTED_SOURCE_NODE,
                            MessageFormat.format("{0}", "ExceededValueThresholdMarkerJexlNode"));
            throw new DatawaveFatalQueryException(qe);
        }
    }
    
    /**
     * Build the iterator stack using the regex ivarator (field index caching regex iterator)
     * 
     * @param source
     * @param data
     */
    public void ivarateRange(JexlNode source, Object data) throws IOException {
        IndexRangeIteratorBuilder builder = new IndexRangeIteratorBuilder();
        // index checking has already been done, otherwise we would not have an
        // "ExceededValueThresholdMarker"
        // hence the "IndexAgnostic" method can be used here
        if (source instanceof ASTAndNode) {
            Map<LiteralRange<?>,List<JexlNode>> ranges = JexlASTHelper.getBoundedRangesIndexAgnostic((ASTAndNode) source, null, true);
            if (ranges.size() != 1) {
                QueryException qe = new QueryException(DatawaveErrorCode.MULTIPLE_RANGES_IN_EXPRESSION);
                throw new DatawaveFatalQueryException(qe);
            }
            builder.setRange(ranges.keySet().iterator().next());
        } else {
            QueryException qe = new QueryException(DatawaveErrorCode.UNEXPECTED_SOURCE_NODE,
                            MessageFormat.format("{0}", "ExceededValueThresholdMarkerJexlNode"));
            throw new DatawaveFatalQueryException(qe);
        }
        builder.canBuildDocument(!limitLookup && this.isQueryFullySatisfied);
        ivarate(builder, source, data);
    }
    
    /**
     * Build the iterator stack using the filter ivarator (field index caching filter iterator)
     *
     * @param source
     * @param data
     */
    public void ivarateFilter(JexlNode source, Object data) throws IOException {
        IndexFilterIteratorBuilder builder = new IndexFilterIteratorBuilder();
        // index checking has already been done, otherwise we would not have an
        // "ExceededValueThresholdMarker"
        // hence the "IndexAgnostic" method can be used here
        if (source instanceof ASTAndNode) {
            Map<LiteralRange<?>,List<JexlNode>> ranges = JexlASTHelper.getBoundedRangesIndexAgnostic((ASTAndNode) source, null, true);
            if (ranges.size() != 1) {
                QueryException qe = new QueryException(DatawaveErrorCode.MULTIPLE_RANGES_IN_EXPRESSION);
                throw new DatawaveFatalQueryException(qe);
            }
            List<ASTFunctionNode> functions = JexlASTHelper.getFunctionNodes(source);
            builder.setRangeAndFunction(ranges.keySet().iterator().next(), new FunctionFilter(functions));
        } else {
            QueryException qe = new QueryException(DatawaveErrorCode.UNEXPECTED_SOURCE_NODE, MessageFormat.format("{0}", "ASTFunctionNode"));
            throw new DatawaveFatalQueryException(qe);
        }
        ivarate(builder, source, data);
    }
    
    public static class FunctionFilter implements Filter {
        private Script script;
        
        public FunctionFilter(List<ASTFunctionNode> nodes) {
            ASTJexlScript script = new ASTJexlScript(ParserTreeConstants.JJTJEXLSCRIPT);
            if (nodes.size() > 1) {
                ASTAndNode andNode = new ASTAndNode(ParserTreeConstants.JJTANDNODE);
                children(script, andNode);
                children(andNode, nodes.toArray(new JexlNode[nodes.size()]));
            } else {
                children(script, nodes.get(0));
            }
            
            String query = JexlStringBuildingVisitor.buildQuery(script);
            JexlArithmetic arithmetic = new DefaultArithmetic();
            
            // Get a JexlEngine initialized with the correct JexlArithmetic for
            // this Document
            RefactoredDatawaveJexlEngine engine = ArithmeticJexlEngines.getEngine(arithmetic);
            
            // Evaluate the JexlContext against the Script
            this.script = engine.createScript(query);
        }
        
        @Override
        public boolean keep(Key k) {
            // fieldname is after fi\0
            String fieldName = k.getColumnFamily().toString().substring(3);
            
            // fieldvalue is first portion of cq
            String fieldValue = k.getColumnQualifier().toString();
            // pull off datatype and uid
            int index = fieldValue.lastIndexOf('\0');
            index = fieldValue.lastIndexOf('\0', index - 1);
            fieldValue = fieldValue.substring(0, index);
            
            // create a jexl context with this valud
            JexlContext context = new DatawaveJexlContext();
            context.set(fieldName, new ValueTuple(fieldName, fieldValue, fieldValue, null));
            
            boolean matched = false;
            
            Object o = script.execute(context);
            
            // Jexl might return us a null depending on the AST
            if (o != null && Boolean.class.isAssignableFrom(o.getClass())) {
                Boolean result = (Boolean) o;
                matched = result;
            } else if (o != null && Collection.class.isAssignableFrom(o.getClass())) {
                // if the function returns a collection of matches, return
                // true/false
                // based on the number of matches
                Collection<?> matches = (Collection<?>) o;
                matched = (matches.size() > 0);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Unable to process non-Boolean result from JEXL evaluation '" + o + "' for function query");
                }
            }
            return matched;
        }
    }
    
    /**
     * Set up a builder for an ivarator
     * 
     * @param builder
     * @param node
     * @param data
     */
    public void ivarate(IvaratorBuilder builder, JexlNode node, Object data) throws IOException {
        builder.setSource(ivaratorSource);
        builder.setTimeFilter(timeFilter);
        builder.setTypeMetadata(typeMetadata);
        builder.setIndexOnlyFields(indexOnlyFields);
        builder.setFieldsToAggregate(fieldsToAggregate);
        builder.setDatatypeFilter(datatypeFilter);
        builder.setKeyTransform(fiAggregator);
        builder.setHdfsFileSystem(hdfsFileSystem);
        builder.setHdfsCacheDirURI(getTemporaryCacheDir().toString());
        builder.setHdfsCacheBufferSize(hdfsCacheBufferSize);
        builder.setHdfsCacheScanPersistThreshold(hdfsCacheScanPersistThreshold);
        builder.setHdfsCacheScanTimeout(hdfsCacheScanTimeout);
        builder.setHdfsCacheReused(hdfsCacheDirReused);
        builder.setHdfsFileCompressionCodec(hdfsFileCompressionCodec);
        builder.setMaxRangeSplit(maxRangeSplit);
        builder.setCollectTimingDetails(collectTimingDetails);
        builder.setQuerySpanCollector(querySpanCollector);
        builder.setSortedUIDs(sortedUIDs);
        
        // We have no parent already defined
        if (data == null) {
            // Make this EQNode the root
            if (!includeReferences.contains(builder.getField()) && excludeReferences.contains(builder.getField())) {
                throw new IllegalStateException(builder.getField() + " is a blacklisted reference.");
            } else {
                root = builder.build();
                
                if (log.isTraceEnabled()) {
                    log.trace("Build IndexIterator: " + root);
                }
            }
        } else {
            AbstractIteratorBuilder iterators = (AbstractIteratorBuilder) data;
            // Add this IndexIterator to the parent
            if (!iterators.hasSeen(builder.getField(), builder.getValue()) && includeReferences.contains(builder.getField())
                            && !excludeReferences.contains(builder.getField())) {
                iterators.addInclude(builder.build());
            } else {
                if (isQueryFullySatisfied == true) {
                    log.warn("Determined that isQueryFullySatisfied should be false, but it was not preset to false by the SatisfactionVisitor");
                }
            }
        }
    }
    
    public IteratorBuildingVisitor setRange(Range documentRange) {
        this.rangeLimiter = documentRange;
        return this;
    }
    
    /**
     * @param documentRange
     * @return
     */
    public IteratorBuildingVisitor limit(Range documentRange) {
        return setRange(documentRange).setLimitLookup(true);
    }
    
    /**
     * Limits the number of source counts.
     * 
     * @param sourceCount
     * @return
     */
    public IteratorBuildingVisitor limit(long sourceCount) {
        source.setInitialSize(sourceCount);
        return this;
    }
    
    /**
     * @param limitLookup
     */
    public IteratorBuildingVisitor setLimitLookup(boolean limitLookup) {
        if (rangeLimiter != null) {
            this.limitLookup = limitLookup;
        }
        return this;
    }
    
    public IteratorBuildingVisitor setIteratorBuilder(Class<? extends IteratorBuilder> clazz) {
        this.iteratorBuilderClass = clazz;
        return this;
    }
    
    public IteratorBuildingVisitor setFieldsToAggregate(Set<String> facetedFields) {
        fieldsToAggregate = Sets.newHashSet(facetedFields);
        return this;
    }
    
    protected boolean isUnindexed(ASTEQNode node) {
        final String fieldName = JexlASTHelper.getIdentifier(node);
        return unindexedFields.contains(fieldName);
    }
    
    protected boolean isUnindexed(ASTIdentifier node) {
        final String fieldName = JexlASTHelper.deconstructIdentifier(node.image);
        return unindexedFields.contains(fieldName);
    }
    
    public IteratorBuildingVisitor setUnindexedFields(Collection<String> unindexedField) {
        this.unindexedFields.addAll(unindexedField);
        return this;
    }
    
    public IteratorBuildingVisitor disableIndexOnly(boolean disableFiEval) {
        this.disableFiEval = disableFiEval;
        return this;
    }
    
    public void setCollectTimingDetails(boolean collectTimingDetails) {
        this.collectTimingDetails = collectTimingDetails;
    }
    
    public void setQuerySpanCollector(QuerySpanCollector querySpanCollector) {
        this.querySpanCollector = querySpanCollector;
    }
    
    public IteratorBuildingVisitor limitOverride(boolean limitOverride) {
        this.limitOverride = limitOverride;
        return this;
    }
}

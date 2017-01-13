package nsa.datawave.query.rewrite.jexl.functions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import nsa.datawave.marking.ColumnVisibilityCache;
import nsa.datawave.query.rewrite.attributes.Attribute;
import nsa.datawave.query.rewrite.attributes.AttributeFactory;
import nsa.datawave.query.rewrite.attributes.Document;
import nsa.datawave.query.rewrite.attributes.DocumentKey;
import nsa.datawave.query.rewrite.jexl.JexlASTHelper;
import nsa.datawave.query.rewrite.predicate.EventDataQueryFilter;
import nsa.datawave.query.rewrite.tld.TLD;
import nsa.datawave.query.util.Tuple2;

import org.apache.accumulo.core.data.ArrayByteSequence;
import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.hadoop.io.Text;

public class IdentityAggregator implements FieldIndexAggregator {
    protected Set<String> indexOnlyFields;
    protected EventDataQueryFilter attrFilter;
    
    protected Set<String> compositeFields;
    
    public IdentityAggregator(Set<String> indexOnlyFields, Set<String> compositeFields, EventDataQueryFilter attrFilter) {
        this.indexOnlyFields = indexOnlyFields;
        this.compositeFields = compositeFields;
        this.attrFilter = attrFilter;
    }
    
    public IdentityAggregator(Set<String> indexOnlyFields, EventDataQueryFilter attrFilter) {
        this(indexOnlyFields, null, attrFilter);
    }
    
    public IdentityAggregator(Set<String> indexOnlyFields) {
        this(indexOnlyFields, null, null);
    }
    
    @Override
    public Key apply(SortedKeyValueIterator<Key,Value> itr) throws IOException {
        Key key = itr.getTopKey();
        Text row = key.getRow();
        ByteSequence pointer = parsePointer(key.getColumnQualifierData());
        while (itr.hasTop() && samePointer(row, pointer, itr.getTopKey()))
            itr.next();
        
        return TLD.buildParentKey(row, pointer, parseFieldNameValue(key.getColumnFamilyData(), key.getColumnQualifierData()), key.getColumnVisibility(),
                        key.getTimestamp());
    }
    
    protected boolean samePointer(Text row, ByteSequence pointer, Key key) {
        if (row.equals(key.getRow())) {
            ByteSequence pointer2 = parsePointer(key.getColumnQualifierData());
            return (pointer.equals(pointer2));
        }
        return false;
    }
    
    @Override
    public Key apply(SortedKeyValueIterator<Key,Value> itr, Document doc, AttributeFactory attrs) throws IOException {
        Key key = itr.getTopKey();
        Text row = key.getRow();
        ByteSequence pointer = parsePointer(key.getColumnQualifierData());
        Key nextKey = key;
        while (nextKey != null && samePointer(row, pointer, nextKey)) {
            Key topKey = nextKey;
            Tuple2<String,String> fieldNameValue = parserFieldNameValue(topKey);
            
            Attribute<?> attr = attrs.create(fieldNameValue.first(), fieldNameValue.second(), topKey, true);
            // only keep fields that are index only and pass the attribute filter
            boolean toKeep = indexOnlyFields == null || indexOnlyFields.contains(JexlASTHelper.removeGroupingContext(fieldNameValue.first()));
            // also keep any fields that are composites
            toKeep |= this.compositeFields != null && this.compositeFields.contains(JexlASTHelper.removeGroupingContext(fieldNameValue.first()));
            attr.setToKeep(toKeep);
            doc.put(fieldNameValue.first(), attr);
            key = nextKey;
            itr.next();
            nextKey = (itr.hasTop() ? itr.getTopKey() : null);
        }
        
        Key docKey = new Key(row, new Text(pointer.toArray()), new Text(), ColumnVisibilityCache.get(key.getColumnVisibilityData()), key.getTimestamp());
        Attribute<?> attr = new DocumentKey(docKey, false);
        doc.put(Document.DOCKEY_FIELD_NAME, attr);
        
        return TLD.buildParentKey(row, pointer, parseFieldNameValue(key.getColumnFamilyData(), key.getColumnQualifierData()), key.getColumnVisibility(),
                        key.getTimestamp());
    }
    
    protected ByteSequence parseFieldNameValue(ByteSequence cf, ByteSequence cq) {
        return TLD.parseFieldAndValueFromFI(cf, cq);
    }
    
    protected Tuple2<String,String> parserFieldNameValue(Key topKey) {
        return new Tuple2<String,String>(topKey.getColumnFamily().toString().substring(3), parseValue(topKey.getColumnQualifier().toString()));
    }
    
    private static final ArrayByteSequence EMPTY_BYTES = new ArrayByteSequence(new byte[0]);
    
    protected ByteSequence parsePointer(ByteSequence qualifier) {
        ArrayList<Integer> deezNulls = TLD.lastInstancesOf(0, qualifier, 2);
        // we want the last two tokens for the datatype and uid
        if (deezNulls.size() == 2) {
            final int start = deezNulls.get(1) + 1, stop = qualifier.length();
            return qualifier.subSequence(start, stop);
        }
        return EMPTY_BYTES;
    }
    
    protected String parseValue(String qualifier) {
        int index = qualifier.lastIndexOf('\0');
        index = qualifier.lastIndexOf('\0', index - 1);
        return qualifier.substring(0, index);
    }
}

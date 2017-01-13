package nsa.datawave.query.language.tree;

import nsa.datawave.query.search.FieldedTerm;
import nsa.datawave.query.search.Term;
import nsa.datawave.query.search.WildcardFieldedTerm;

import org.apache.log4j.Logger;

public class SelectorNode extends QueryNode {
    private final static Logger log = Logger.getLogger(SelectorNode.class.getName());
    
    private Term query;
    
    public SelectorNode(String query) {
        super(null, null);
        String field = FieldedTerm.parseField(query);
        String selector = FieldedTerm.parseSelector(query);
        if (!selector.contains("*") && !selector.contains("?")) {
            this.query = new FieldedTerm(query);
        } else {
            int firstWildcardIndex = WildcardFieldedTerm.getFirstWildcardIndex(selector);
            
            if (firstWildcardIndex >= 0) {
                this.query = new WildcardFieldedTerm(field, selector);
            } else {
                this.query = new FieldedTerm(field, selector);
            }
        }
    }
    
    public SelectorNode(String field, String selector) {
        super(null, null);
        if (!selector.contains("*") && !selector.contains("?")) {
            this.query = new FieldedTerm(field, selector);
        } else {
            int firstWildcardIndex = WildcardFieldedTerm.getFirstWildcardIndex(selector);
            
            if (firstWildcardIndex >= 0) {
                this.query = new WildcardFieldedTerm(field, selector);
            } else {
                this.query = new FieldedTerm(field, selector);
            }
        }
    }
    
    public SelectorNode(FieldedTerm fieldedTerm) {
        super(null, null);
        this.query = fieldedTerm;
    }
    
    @Override
    public String toString() {
        return query.toString().replaceAll("\0", "");
    }
    
    /**
     * Since this node does not have any children, return toString()
     */
    @Override
    public String getContents() {
        return toString();
    }
    
    @Override
    protected boolean isParentDifferent() {
        return true;
    }
    
    @Override
    public QueryNode clone() {
        return new SelectorNode(query.toString());
    }
    
    public void setQuery(FieldedTerm query) {
        this.query = query;
    }
    
    public Term getQuery() {
        return query;
    }
}

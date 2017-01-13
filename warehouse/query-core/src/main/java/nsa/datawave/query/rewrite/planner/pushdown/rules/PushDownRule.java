package nsa.datawave.query.rewrite.planner.pushdown.rules;

import nsa.datawave.query.rewrite.jexl.visitors.RebuildingVisitor;
import nsa.datawave.query.rewrite.planner.pushdown.Cost;
import nsa.datawave.query.rewrite.planner.pushdown.PushDownVisitor;

import org.apache.commons.jexl2.parser.ASTJexlScript;
import org.apache.commons.jexl2.parser.JexlNode;

/**
 * Purpose: Base class which aids in pushing nodes down when necessary.
 * 
 * Assumptions: The PushDownRule always assumes that the visitor will be run by PushDownVisitor, which will ultimately ensure that not all nodes are pushed
 * down.
 */
public abstract class PushDownRule extends RebuildingVisitor {
    
    protected PushDownVisitor parentVisitor = null;
    
    @Override
    public Object visit(ASTJexlScript node, Object data) {
        setPushDown((PushDownVisitor) data);
        return super.visit(node, null);
    }
    
    protected void setPushDown(PushDownVisitor visitor) {
        this.parentVisitor = visitor;
    }
    
    public abstract Cost getCost(JexlNode node);
    
    /**
     * Determines if parent is a type
     * 
     * @param currentNode
     * @param clazz
     * @return
     */
    public static boolean isParent(final JexlNode currentNode, final Class<? extends JexlNode> clazz) {
        JexlNode parentNode = currentNode.jjtGetParent();
        
        return parentNode.getClass().isAssignableFrom(clazz);
    }
    
}

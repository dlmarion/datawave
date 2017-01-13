package nsa.datawave.query.rewrite.jexl.visitors;

import nsa.datawave.query.rewrite.jexl.JexlNodeFactory;

import org.apache.commons.jexl2.parser.ASTJexlScript;
import org.apache.commons.jexl2.parser.ASTNumberLiteral;
import org.apache.commons.jexl2.parser.ASTUnaryMinusNode;
import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.jexl2.parser.ParserTreeConstants;

public class FixNegativeNumbersVisitor extends RebuildingVisitor {
    
    public static ASTJexlScript fix(JexlNode root) {
        FixNegativeNumbersVisitor vis = new FixNegativeNumbersVisitor();
        return (ASTJexlScript) root.jjtAccept(vis, null);
    }
    
    @Override
    public Object visit(ASTUnaryMinusNode astumn, Object data) {
        if (astumn.jjtGetNumChildren() == 1 && astumn.jjtGetChild(0) instanceof ASTNumberLiteral) {
            ASTNumberLiteral node = (ASTNumberLiteral) astumn.jjtGetChild(0);
            ASTNumberLiteral newNode = new ASTNumberLiteral(ParserTreeConstants.JJTNUMBERLITERAL);
            String value = "-" + node.image;
            newNode.image = value;
            newNode.jjtSetParent(node.jjtGetParent());
            if (JexlNodeFactory.NATURAL_NUMBERS.contains(node.getLiteralClass())) {
                newNode.setNatural(value);
            } else if (JexlNodeFactory.REAL_NUMBERS.contains(node.getLiteralClass())) {
                newNode.setReal(value);
            } else {
                throw new IllegalArgumentException("Could not ascertain type of ASTNumberLiteral: " + node);
            }
            newNode.jjtSetValue(value);
            return newNode;
        } else {
            return super.visit(astumn, data);
        }
        
    }
    
}

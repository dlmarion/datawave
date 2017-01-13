package nsa.datawave.query.rewrite.jexl.functions;

import org.apache.commons.jexl2.parser.ASTStringLiteral;
import org.apache.commons.jexl2.parser.JexlNode;

import com.google.common.base.Predicate;

class StringLiteralsOnly implements Predicate<JexlNode> {
    public boolean apply(JexlNode node) {
        return node instanceof ASTStringLiteral;
    }
}

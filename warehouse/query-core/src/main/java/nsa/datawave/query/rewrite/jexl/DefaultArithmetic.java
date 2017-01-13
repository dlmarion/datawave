package nsa.datawave.query.rewrite.jexl;

import nsa.datawave.query.rewrite.attributes.ValueTuple;
import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class DefaultArithmetic extends DatawaveArithmetic {
    
    private static final String LESS_THAN = "<", GREATER_THAN = ">", LESS_THAN_OR_EQUAL = "<=", GREATER_THAN_OR_EQUAL = ">=";
    
    private static final Logger log = Logger.getLogger(DefaultArithmetic.class);
    
    /**
     * Default to being lenient so we don't have to add "null" for every field in the query that doesn't exist in the document
     */
    public DefaultArithmetic() {
        super(false);
    }
    
    /**
     * This method differs from the parent in that we are not calling String.matches() because it does not match on a newline. Instead we are handling this
     * case.
     *
     * @param left
     *            first value
     * @param right
     *            second value
     * @return test result.
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean matches(Object left, Object right) {
        left = allValues(left);
        right = allValues(right);
        if (left == null && right == null) {
            // if both are null L == R
            return true;
        }
        if (left == null || right == null) {
            // we know both aren't null, therefore L != R
            return false;
        }
        
        Set<Object> elements;
        
        // for every element in left, check if one matches the right pattern
        if (left instanceof Set) {
            elements = (Set<Object>) left;
        } else {
            elements = Collections.singleton(left);
        }
        
        Set<Pattern> patterns;
        if (right instanceof Pattern) {
            patterns = Collections.singleton((Pattern) right);
        } else if (right instanceof Set) {
            patterns = new HashSet<>();
            for (Object r : (Set<Object>) right) {
                if (r instanceof Pattern) {
                    patterns.add((Pattern) r);
                } else {
                    patterns.add(JexlPatternCache.getPattern(r.toString()));
                }
            }
        } else {
            patterns = Collections.singleton(JexlPatternCache.getPattern(right.toString()));
        }
        
        for (Object o : elements) {
            for (Pattern p : patterns) {
                if (p.matcher(o.toString()).matches()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * This method deals with the ValueTuple objects and turns them into the normalized value parts
     * 
     * @param o
     * @return
     */
    private Object normalizedValues(Object o) {
        if (o instanceof Set) {
            Set<Object> normalizedValues = new HashSet<>();
            for (Object value : ((Set<?>) o)) {
                addAll(normalizedValues, normalizedValues(value));
            }
            if (normalizedValues.size() == 1) {
                return normalizedValues.iterator().next();
            } else {
                return normalizedValues;
            }
        } else {
            return ValueTuple.getNormalizedValue(o);
        }
    }
    
    /**
     * This method deals with the ValueTuple objects and turns them into all of the value parts
     * 
     * @param o
     * @return
     */
    private Object allValues(Object o) {
        Set<Object> allValues = new HashSet<>();
        if (o instanceof Set) {
            for (Object value : ((Set<?>) o)) {
                addAll(allValues, allValues(value));
            }
        } else {
            allValues.add(ValueTuple.getNormalizedValue(o));
            allValues.add(ValueTuple.getValue(o));
        }
        if (allValues.size() == 1) {
            return allValues.iterator().next();
        } else {
            return allValues;
        }
    }
    
    private void addAll(Collection<Object> set, Object o) {
        if (o instanceof Collection) {
            set.addAll((Collection<?>) o);
        } else {
            set.add(o);
        }
    }
    
    /**
     * This method differs from the parent class in that we are going to try and do a better job of coercing the types. As a last resort we will do a string
     * comparison and try not to throw a NumberFormatException. The JexlArithmetic class performs coercion to a particular type if either the left or the right
     * match a known type. We will look at the type of the right operator and try to make the left of the same type.
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public boolean equals(Object left, Object right) {
        left = normalizedValues(left);
        right = normalizedValues(right);
        // super class takes care of this: left = fixLeft(left, right);
        
        // When one variable is a Set, treat the equality as #contains
        if (left instanceof Set && !(right instanceof Set)) {
            Set<Object> set = (Set<Object>) left;
            
            for (Object o : set) {
                // take advantage of numeric conversions
                if (super.equals(o, right)) {
                    return true;
                }
            }
            
            return false;
        } else if (!(left instanceof Set) && right instanceof Set) {
            // if multiple possible right hand values, then true if any intersection
            Set<Object> set = (Set<Object>) right;
            
            for (Object o : set) {
                if (equals(left, o)) {
                    return true;
                }
            }
            
            return false;
        } else if (left instanceof Set && right instanceof Set) {
            // both are sets
            Set<Object> rightSet = (Set<Object>) right;
            Set<Object> leftSet = (Set<Object>) left;
            for (final Object leftO : leftSet) {
                Object normalizedLeftO = ValueTuple.getNormalizedValue(leftO);
                for (final Object rightO : rightSet) {
                    Object normalizedRightO = ValueTuple.getNormalizedValue(rightO);
                    if (equals(normalizedLeftO, normalizedRightO)) {
                        return true;
                    }
                }
            }
            return false;
            
        }
        
        return super.equals(left, right);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean lessThan(Object left, Object right) {
        left = normalizedValues(left);
        right = normalizedValues(right);
        // super class takes care of this: left = fixLeft(left, right);
        // When one variable is a Set, check for existence for one value that satisfies the lessThan operator
        if (left instanceof Set && !(right instanceof Set)) {
            Set<Object> set = (Set<Object>) left;
            
            for (Object o : set) {
                if (super.compare(o, right, LESS_THAN) < 0) {
                    return true;
                }
            }
            
            return false;
        } else if (right instanceof Set) {
            Set<Object> set = (Set<Object>) right;
            
            for (Object o : set) {
                if (lessThan(left, o)) {
                    return true;
                }
            }
            
            return false;
        }
        
        return super.lessThan(left, right);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean lessThanOrEqual(Object left, Object right) {
        left = normalizedValues(left);
        right = normalizedValues(right);
        // super class takes care of this: left = fixLeft(left, right);
        // When one variable is a Set, check for existence for one value that satisfies the lessThan operator
        if (left instanceof Set && !(right instanceof Set)) {
            Set<Object> set = (Set<Object>) left;
            
            for (Object o : set) {
                if (compare(o, right, LESS_THAN_OR_EQUAL) <= 0) {
                    return true;
                }
            }
            
            return false;
        } else if (right instanceof Set) {
            Set<Object> set = (Set<Object>) right;
            
            for (Object o : set) {
                if (lessThanOrEqual(left, o)) {
                    return true;
                }
            }
            
            return false;
        }
        
        return super.lessThanOrEqual(left, right);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean greaterThan(Object left, Object right) {
        left = normalizedValues(left);
        right = normalizedValues(right);
        // super class takes care of this: left = fixLeft(left, right);
        // When one variable is a Set, check for existence for one value that satisfies the greaterThan operator
        if (left instanceof Set && !(right instanceof Set)) {
            Set<Object> set = (Set<Object>) left;
            
            for (Object o : set) {
                if (compare(o, right, GREATER_THAN) > 0) {
                    return true;
                }
            }
            
            return false;
        } else if (right instanceof Set) {
            Set<Object> set = (Set<Object>) right;
            
            for (Object o : set) {
                if (greaterThan(left, o)) {
                    return true;
                }
            }
            
            return false;
        }
        
        return super.greaterThan(left, right);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean greaterThanOrEqual(Object left, Object right) {
        left = normalizedValues(left);
        right = normalizedValues(right);
        // super class takes care of this: left = fixLeft(left, right);
        // When one variable is a Set, check for existence for one value that satisfies the greaterThan operator
        if (left instanceof Set && !(right instanceof Set)) {
            Set<Object> set = (Set<Object>) left;
            
            for (Object o : set) {
                if (compare(o, right, GREATER_THAN_OR_EQUAL) >= 0) {
                    return true;
                }
            }
            
            return false;
        } else if (right instanceof Set) {
            Set<Object> set = (Set<Object>) right;
            
            for (Object o : set) {
                if (greaterThanOrEqual(left, o)) {
                    return true;
                }
            }
            
            return false;
        }
        
        return super.greaterThanOrEqual(left, right);
    }
    
    @Override
    public long toLong(Object val) {
        if (val == null) {
            controlNullOperand();
            return 0L;
        } else if (val instanceof Double) {
            if (!Double.isNaN((Double) val)) {
                return 0;
            } else {
                return ((Double) val).longValue();
            }
        } else if (val instanceof Number) {
            return ((Number) val).longValue();
        } else if (val instanceof String) {
            if ("".equals(val)) {
                return 0;
            } else {
                // val could actually have a mantissa (e.g. "65.0")
                // that would throw a NumberFormatException
                try {
                    return Long.parseLong((String) val);
                } catch (NumberFormatException e) {
                    Double d = Double.parseDouble((String) val);
                    return d.longValue();
                }
            }
        } else if (val instanceof Boolean) {
            return (Boolean) val ? 1L : 0L;
        } else if (val instanceof Character) {
            return (Character) val;
        }
        
        throw new ArithmeticException("Long coercion: " + val.getClass().getName() + ":(" + val + ")");
    }
    
    /**
     * Convert the left hand object if required to the same numberic class as the right hand side.
     * 
     * @param left
     * @param right
     * @return the fixed left hand object
     */
    protected Object fixLeft(Object left, Object right) {
        
        if (null == left || null == right) {
            return left;
        }
        
        Class<? extends Number> rightNumberClass = getNumberClass(right, false);
        boolean rightIsNumber = (rightNumberClass != null);
        
        // if the right is a Number (sans converting String objects)
        if (rightIsNumber) {
            // then convert the left to a number as well
            left = convertToNumber(left, rightNumberClass);
        }
        
        return left;
    }
    
}

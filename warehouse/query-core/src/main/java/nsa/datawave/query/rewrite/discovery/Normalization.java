package nsa.datawave.query.rewrite.discovery;

import nsa.datawave.data.type.Type;
import nsa.datawave.data.normalizer.NormalizationException;

/**
 * The use case here was that a method worked the same for both patterns and literals, except for the method call on the normalizer. This abstracts that away
 * which call is made to make client code a bit more flexible.
 *
 * See {@link PatternNormalization} and {@link LiteralNormalization}.
 */
public interface Normalization {
    public String normalize(Type<?> normalizer, String field, String value) throws NormalizationException;
}

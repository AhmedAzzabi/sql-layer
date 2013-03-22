

package com.akiban.server.expression.std;

import com.akiban.server.expression.ExpressionType;

import com.akiban.server.types.AkType;

import com.akiban.sql.types.CharacterTypeAttributes;
import com.akiban.sql.StandardException;
import com.akiban.server.error.SQLParserInternalException;

import com.akiban.server.collation.AkCollator;
import com.akiban.server.collation.AkCollatorFactory;

public class ExpressionTypes
{
    public static final ExpressionType BOOL = newType(AkType.BOOL);
    public static final ExpressionType DATE = newType(AkType.DATE);
    public static final ExpressionType DATETIME = newType(AkType.DATETIME);
    public static final ExpressionType DOUBLE = newType(AkType.DOUBLE);
    public static final ExpressionType FLOAT = newType(AkType.FLOAT);
    public static final ExpressionType INT = newType(AkType.INT);
    public static final ExpressionType LONG = newType(AkType.LONG);
    public static final ExpressionType NULL = newType(AkType.NULL);
    public static final ExpressionType TEXT = newType(AkType.TEXT);
    public static final ExpressionType TIME = newType(AkType.TIME);
    public static final ExpressionType TIMESTAMP = newType(AkType.TIMESTAMP);
    public static final ExpressionType UNSUPPORTED = newType(AkType.UNSUPPORTED);
    public static final ExpressionType U_BIGINT = newType(AkType.U_BIGINT);
    public static final ExpressionType U_DOUBLE = newType(AkType.U_DOUBLE);
    public static final ExpressionType U_FLOAT = newType(AkType.U_FLOAT);
    public static final ExpressionType U_INT = newType(AkType.U_INT);
    public static final ExpressionType YEAR = newType(AkType.YEAR);
    public static final ExpressionType INTERVAL_MILLIS = newType(AkType.INTERVAL_MILLIS);
    public static final ExpressionType INTERVAL_MONTH = newType(AkType.INTERVAL_MONTH);

    public static ExpressionType decimal(int precision, int scale) {
        return newType(AkType.DECIMAL, precision, scale);
    }

    public static ExpressionType varchar(int length) {
        return varchar(length, null);
    }

    public static ExpressionType varchar(int length, CharacterTypeAttributes characterAttributes) {
        return newType(AkType.VARCHAR, length, 0, characterAttributes);
    }

    public static ExpressionType varbinary(int length) {
        return newType(AkType.VARBINARY, length);
    }

    private static ExpressionType newType(AkType type) {
        return new ExpressionTypeImpl(type, 0, 0, null);
    }

    private static ExpressionType newType(AkType type, int precision) {
        return new ExpressionTypeImpl(type, precision, 0, null);
    }

    public static ExpressionType newType(AkType type, int precision, int scale) {
        return new ExpressionTypeImpl(type, precision, scale, null);
    }

    public static ExpressionType newType(AkType type, int precision, int scale, 
                                         CharacterTypeAttributes characterAttributes) {
        return new ExpressionTypeImpl(type, precision, scale, characterAttributes);
    }

    static class ExpressionTypeImpl implements ExpressionType {
        @Override
        public AkType getType() {
            return type;
        }

        @Override
        public int getPrecision() {
            return precision;
        }
        
        @Override
        public int getScale() {
            return scale;
        }

        @Override
        public CharacterTypeAttributes getCharacterAttributes() {
            return characterAttributes;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder(type.toString());
            str.append("(").append(precision).append(",").append(scale).append(")");
            if (characterAttributes != null)
                str.append(" ").append(characterAttributes);
            return str.toString();
        }
        
        ExpressionTypeImpl(AkType type, int precision, int scale, CharacterTypeAttributes characterAttributes) {
            this.type = type;
            this.precision = precision;
            this.scale = scale;
            this.characterAttributes = characterAttributes;
        }

        private AkType type;
        private int precision, scale;
        private CharacterTypeAttributes characterAttributes;
    }

    /** If operating on <code>type1</code> and <code>type2</code>,
     * would the operation be under some appropriate collation? 
     * @return that collation or <code>null</code>
     */
    public static AkCollator operationCollation(ExpressionType type1, ExpressionType type2) {
        if (!((type1 != null) &&
              ((type1.getType() == AkType.VARCHAR) || (type1.getType() == AkType.TEXT)) &&
              (type2 != null) &&
              ((type2.getType() == AkType.VARCHAR) || (type2.getType() == AkType.TEXT))))
            return null;
        return mergeAkCollators(type1.getCharacterAttributes(), type2.getCharacterAttributes());
    }

    public static AkCollator mergeAkCollators(CharacterTypeAttributes type1Atts, CharacterTypeAttributes type2Atts) {
        CharacterTypeAttributes att;
        try {
            att = CharacterTypeAttributes.mergeCollations(type1Atts, type2Atts);
        }
        catch (StandardException ex) {
            throw new SQLParserInternalException(ex);
        }
        if (att != null) {
            String coll = att.getCollation();
            if (coll != null)
                return AkCollatorFactory.getAkCollator(coll);
        }
        return null;
    }

    private ExpressionTypes() {
    }
}
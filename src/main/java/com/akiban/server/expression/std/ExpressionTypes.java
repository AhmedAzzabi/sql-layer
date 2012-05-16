/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */


package com.akiban.server.expression.std;

import com.akiban.server.expression.ExpressionType;

import com.akiban.server.types.AkType;

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

    public static ExpressionType varchar(int length, String collation) {
        return newType(AkType.VARCHAR, length, 0, collation);
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
                                         String collation) {
        return new ExpressionTypeImpl(type, precision, scale, collation);
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
        public String getCollation() {
            return collation;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder(type.toString());
            str.append("(").append(precision).append(",").append(scale).append(")");
            if (collation != null)
                str.append(" COLLATE ").append(collation);
            return str.toString();
        }
        
        ExpressionTypeImpl(AkType type, int precision, int scale, String collation) {
            this.type = type;
            this.precision = precision;
            this.scale = scale;
            this.collation = collation;
        }

        private AkType type;
        private int precision, scale;
        private String collation;
    }
    
    private ExpressionTypes() {
    }
}
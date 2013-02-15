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

package com.akiban.server.types3;

import com.akiban.server.error.AkibanInternalException;
import com.akiban.server.types3.pvalue.PUnderlying;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.TPreparedExpression;
import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.util.AkibanAppender;

import java.util.ArrayList;
import java.util.List;

public final class TInstance {

    // static helpers

    public static TClass tClass(TInstance tInstance) {
        return tInstance == null ? null : tInstance.typeClass();
    }

    public static PUnderlying pUnderlying(TInstance tInstance) {
        TClass tClass = tClass(tInstance);
        return tClass == null ? null : tClass.underlyingType();
    }

    // TInstance interface

    public void writeCanonical(PValueSource in, PValueTarget out) {
        tclass.writeCanonical(in, this, out);
    }

    public void writeCollating(PValueSource in, PValueTarget out) {
        tclass.writeCollating(in, this, out);
    }

    public void readCollating(PValue in, PValue out) {
        tclass.readCollating(in, this, out);
    }

    public void format(PValueSource source, AkibanAppender out) {
        tclass.format(this, source, out);
    }

    public void formatAsLiteral(PValueSource source, AkibanAppender out) {
        tclass.formatAsLiteral(this, source, out);
    }

    public void formatAsJson(PValueSource source, AkibanAppender out) {
        tclass.formatAsJson(this, source, out);
    }

    public Object attributeToObject(Attribute attribute) {
        if (enumClass != attribute.getClass())
            throw new IllegalArgumentException("Illegal attribute: " + attribute.name());
        int value;
        int attributeIndex = attribute.ordinal();
        switch (attributeIndex) {
        case 0: value = attr0; break;
        case 1: value = attr1; break;
        case 2: value = attr2; break;
        case 3: value = attr3; break;
        default: throw new AssertionError("index out of range for " + tclass +  ": " + attribute);
        }
        return tclass.attributeToObject(attributeIndex, value);
    }

    public int attribute(Attribute attribute) {
        if (enumClass != attribute.getClass())
            throw new IllegalArgumentException("Illegal attribute: " + attribute.name());
        int index = attribute.ordinal();
        switch (index) {
        case 0: return attr0;
        case 1: return attr1;
        case 2: return attr2;
        case 3: return attr3;
        default: throw new IllegalArgumentException("index out of range for " + tclass +  ": " + index);
        }
    }

    public TClass typeClass() {
        return tclass;
    }

    public boolean nullability() {
        return isNullable;
    }

    public Object getMetaData() {
        return metaData;
    }

    public TInstance withNullable(boolean isNullable) {
        return (isNullable == this.isNullable)
                ? this
                : new TInstance(tclass, enumClass, tclass.nAttributes(), attr0, attr1, attr2, attr3, isNullable);
    }
    /**
     * Convenience method for <tt>typeClass().dataTypeDescriptor(this)</tt>.
     * @return this instance's DataTypeDescriptor
     * @see TClass#dataTypeDescriptor(TInstance)
     */
    public DataTypeDescriptor dataTypeDescriptor() {
        return tclass.dataTypeDescriptor(this);
    }

    /**
     * @param o additional meta data for this TInstance
     * @return
     * <code>false</code> if this method has already been called on this object.
     * The new meta data will <e>not</e> override the current one.
     * <code>true</code> if this object's meta data is still <code>null</code>.
     */
    public boolean setMetaData (Object o) {
        if (metaData != null)
            return false;
        metaData = o;
        return true;
    }

    public String toStringIgnoringNullability(boolean useShorthand) {
        String className = useShorthand ? tclass.name().unqualifiedName() : tclass.name().toString();
        int nattrs = tclass.nAttributes();
        if (nattrs == 0)
            return className;
        // assume 5 digits per attribute as a wild guess. If it's wrong, no biggie. 2 chars for open/close paren
        int capacity = className.length() + 2 + (5*nattrs);
        StringBuilder sb = new StringBuilder(capacity);
        sb.append(className).append('(');
        long[] attrs = new long[] { attr0, attr1, attr2, attr3 };
        for (int i = 0; i < nattrs; ++i) {
            tclass.attributeToString(i, attrs[i], sb);
            if (i+1 < nattrs)
                sb.append(", ");
        }
        sb.append(')');
        return sb.toString();
    }

    public static List<? extends TInstance> createTInstances(List<? extends TPreparedExpression> pExpressions)
    {
        if (pExpressions == null) {
            return null;
        }
        int n = pExpressions.size();
        List<TInstance> tInstances = new ArrayList<TInstance>(n);
        for (int i = 0; i < n; i++) {
            tInstances.add(pExpressions.get(i).resultType());
        }
        return tInstances;
    }

    // object interface

    @Override
    public String toString() {
        String result = toStringIgnoringNullability(false);
        if (tclass.nAttributes() != 0) // TODO there's no reason to do this except that it's backwards-compatible
            result += (isNullable ? " NULL" : " NOT NULL"); // with existing tests.
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return equalsIncludingNullable(o);
    }

    public boolean equalsIncludingNullable(Object o) {
        return equals(o, true);
    }

    public boolean equalsExcludingNullable(Object o) {
        return equals(o, false);
    }

    private boolean equals(Object o, boolean withNullable) {
        if (this == o) return true;
        if (!(o instanceof TInstance)) return false;

        TInstance other = (TInstance) o;

        return attr0 == other.attr0
                && attr1 == other.attr1
                && attr2 == other.attr2
                && attr3 == other.attr3
                && ((!withNullable) || (isNullable == other.isNullable))
                && tclass.equals(other.tclass);
    }

    @Override
    public int hashCode() {
        int result = tclass.hashCode();
        result = 31 * result + attr0;
        result = 31 * result + attr2;
        result = 31 * result + attr1;
        result = 31 * result + attr3;
        result = 31 * result + (isNullable ? 0 : 1);
        return result;
    }

    // package-private

    static TInstance create(TClass tclass, Class<?> enumClass, int nAttrs, int attr0, int attr1, int attr2, int attr3,
                            boolean isNullable)
    {
        TInstance result = new TInstance(tclass, enumClass, nAttrs, attr0, attr1, attr2, attr3, isNullable);
        tclass.validate(result);
        return result;
    }

    static TInstance create(TInstance template, int attr0, int attr1, int attr2, int attr3, boolean nullable) {
        return create(template.tclass, template.enumClass, template.tclass.nAttributes(),
                attr0, attr1, attr2, attr3, nullable);
    }

    Class<?> enumClass() {
        return enumClass;
    }

    int attrByPos(int i) {
        switch (i) {
        case 0: return attr0;
        case 1: return attr1;
        case 2: return attr2;
        case 3: return attr3;
        default: throw new AssertionError("out of range: " + i);
        }
    }

    // state
    private TInstance(TClass tclass, Class<?> enumClass, int nAttrs, int attr0, int attr1, int attr2, int attr3,
              boolean isNullable)
    {
        if (tclass.nAttributes() != nAttrs) {
            throw new AkibanInternalException(tclass.name() + " requires "+ tclass.nAttributes()
                    + " attributes, saw " + nAttrs);
        }
        // normalize inputs past nattrs
        switch (nAttrs) {
        case 0:
            attr0 = -1;
        case 1:
            attr1 = -1;
        case 2:
            attr2 = -1;
        case 3:
            attr3 = -1;
        case 4:
            break;
        default:
            throw new IllegalArgumentException("too many nattrs: " + nAttrs + " (" + enumClass.getSimpleName() + ')');
        }
        this.tclass = tclass;
        this.attr0 = attr0;
        this.attr1 = attr1;
        this.attr2 = attr2;
        this.attr3 = attr3;
        this.enumClass = enumClass;
        this.isNullable = isNullable;
    }
    private final TClass tclass;
    private final int attr0, attr1, attr2, attr3;
    private final boolean isNullable;

    private Object metaData;

    private final Class<?> enumClass;
}

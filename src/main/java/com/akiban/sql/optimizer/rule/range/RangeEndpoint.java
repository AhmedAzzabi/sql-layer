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

package com.akiban.sql.optimizer.rule.range;

import com.akiban.server.types.AkType;
import com.akiban.sql.optimizer.plan.ConstantExpression;

public abstract class RangeEndpoint implements Comparable<RangeEndpoint> {

    public abstract boolean isUpperWild();
    public abstract ConstantExpression getValueExpression();
    public abstract Object getValue();
    public abstract boolean isInclusive();
    public abstract String describeValue();

    @Override
    public int compareTo(RangeEndpoint o) {
        ComparisonResult comparison = compareEndpoints(this, o);
        switch (comparison) {
        case LT:
        case LT_BARELY:
            return -1;
        case GT:
        case GT_BARELY:
            return 1;
        case EQ:
            return 0;
        case INVALID:
        default:
            throw new IllegalComparisonException(this.getValue(), o.getValue());
        }
    }

    public ComparisonResult comparePreciselyTo(RangeEndpoint other) {
        return compareEndpoints(this, other);
    }

    public static ValueEndpoint inclusive(ConstantExpression value) {
        return new ValueEndpoint(value, true);
    }

    public static  ValueEndpoint exclusive(ConstantExpression value) {
        return new ValueEndpoint(value, false);
    }

    public static RangeEndpoint of(ConstantExpression value, boolean inclusive) {
        return new ValueEndpoint(value, inclusive);
    }

    private RangeEndpoint() {}

    /**
     * Returns whether the two endpoints are LT, GT or EQ to each other.
     * @param point1 the first point
     * @param point2 the second point
     * @return LT if point1 is less than point2; GT if point1 is greater than point2; EQ if point1 is greater than
     * point2; and INVALID if the two points can't be compared
     */
    private static ComparisonResult compareEndpoints(RangeEndpoint point1, RangeEndpoint point2)
    {
        if (point1.equals(point2))
            return ComparisonResult.EQ;
        // At this point we know they're not both upper wild. If either one is, we know the answer.
        if (point1.isUpperWild())
            return ComparisonResult.GT;
        if (point2.isUpperWild())
            return ComparisonResult.LT;

        // neither is wild
        ComparisonResult comparison = compareObjects(point1.getValue(), point2.getValue());
        if (comparison == ComparisonResult.EQ && (point1.isInclusive() != point2.isInclusive())) {
            if (point1.isInclusive())
                return ComparisonResult.LT_BARELY;
            assert point2.isInclusive() : point2;
            return ComparisonResult.GT_BARELY;
        }
        return comparison;
    }

    private static ComparisonResult compareObjects(Object one, Object two) {
        // if both are null, they're equal. Otherwise, at most one can be null; if either is null, we know the
        // answer. Otherwise, we know neither is null, and we can test their values (after checking the classes)
        if (one == two)
            return ComparisonResult.EQ;
        if (one == null)
            return ComparisonResult.LT;
        if (two == null)
            return ComparisonResult.GT;
        if (!one.getClass().equals(two.getClass()) || !Comparable.class.isInstance(one))
            return ComparisonResult.INVALID;
        Comparable oneT = (Comparable) one;
        Comparable twoT = (Comparable) two;
        @SuppressWarnings("unchecked") // we know that oneT and twoT are both Comparables of the same class
                int compareResult = (oneT).compareTo(twoT);
        if (compareResult < 0)
            return ComparisonResult.LT;
        else if (compareResult > 0)
            return ComparisonResult.GT;
        else
            return ComparisonResult.EQ;
    }

    public static final RangeEndpoint UPPER_WILD = new Wild();
    public static final RangeEndpoint NULL_EXCLUSIVE = exclusive(new ConstantExpression(null, AkType.NULL));
    public static final RangeEndpoint NULL_INCLUSIVE = inclusive(NULL_EXCLUSIVE.getValueExpression());

    private static class Wild extends RangeEndpoint {

        @Override
        public boolean isUpperWild() {
            return true;
        }

        @Override
        public Object getValue() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConstantExpression getValueExpression() {
            return null;
        }

        @Override
        public boolean isInclusive() {
            return false;
        }

        @Override
        public String toString() {
            return "(*)";
        }

        @Override
        public String describeValue() {
            return "*";
        }
    }

    private static class ValueEndpoint extends RangeEndpoint {

        @Override
        public ConstantExpression getValueExpression() {
            return valueExpression;
        }

        @Override
        public Object getValue() {
            return valueExpression.getValue();
        }

        @Override
        public boolean isInclusive() {
            return inclusive;
        }

        @Override
        public boolean isUpperWild() {
            return false;
        }

        @Override
        public String toString() {
            return valueExpression + (inclusive ? " inclusive" : " exclusive");
        }

        @Override
        public String describeValue() {
            Object value = getValue();
            return value == null ? "NULL" : value.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ValueEndpoint that = (ValueEndpoint) o;
            return inclusive == that.inclusive && !(valueExpression != null ? !valueExpression.equals(that.valueExpression) : that.valueExpression != null);

        }

        @Override
        public int hashCode() {
            int result = valueExpression != null ? valueExpression.hashCode() : 0;
            result = 31 * result + (inclusive ? 1 : 0);
            return result;
        }

        private ValueEndpoint(ConstantExpression valueExpression, boolean inclusive) {
            this.valueExpression = valueExpression;
            this.inclusive = inclusive;
        }

        private ConstantExpression valueExpression;
        private boolean inclusive;
    }

    static class IllegalComparisonException extends RuntimeException {
        private IllegalComparisonException(Object one, Object two) {
            super(String.format("couldn't sort objects <%s> and <%s>",
                    one,
                    two
            ));
        }
    }

    enum RangePointComparison {
        MIN() {
            @Override
            protected Object select(Object one, Object two, ComparisonResult comparison) {
                return comparison == ComparisonResult.LT ? one : two;
            }
        },
        MAX() {
            @Override
            protected Object select(Object one, Object two, ComparisonResult comparison) {
                return comparison == ComparisonResult.GT ? one : two;
            }
        }
        ;

        protected abstract Object select(Object one, Object two, ComparisonResult comparison);

        public Object get(Object one, Object two) {
            ComparisonResult comparisonResult = compareObjects(one, two);
            switch (comparisonResult) {
            case EQ:
                return one;
            case LT_BARELY:
            case LT:
            case GT_BARELY:
            case GT:
                return select(one, two, comparisonResult.normalize());
            case INVALID:
                return null;
            default:
                throw new AssertionError(comparisonResult.name());
            }
        }

        public static final Object INVALID_COMPARISON = new Object();
    }
}

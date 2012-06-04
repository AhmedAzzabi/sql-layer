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

package com.akiban.qp.operator;

import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.util.ValueSourceHasher;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.util.ArgumentValidation;
import com.akiban.util.BloomFilter;
import com.akiban.util.tap.InOutTap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * <h1>Overview</h1>
 * <p/>
 * Select_BloomFilter checks whether an input row, projected to a given set of expressions, is present in a
 * "filtering" set of rows. The implementation is optimized to avoid IO operations on the filtering set of rows
 * through the use of a bloom filter.
 * <p/>
 * <h1>Arguments</h1>
 * <p/>
 *
 * <li><b>Operator input:</b></li> Operator providing the input stream
 * <li><b>Operator onPositive:</b></li> Operator used to provide output rows
 * <li><b>List<BoundFieldExpression> fields:</b></li> expressions applied to the input row to obtain a
 * bloom filter hash key
 * <li><b>int bindingPosition:</b></li> Location in the query context of the bloom filter, which has been
 * loaded by the Using_BloomFilter operator. This bindingPosition is also used to hold a row from
 * the input stream that passes the filter and needs to be tested for existence using the onPositive
 * operator.
 * <p/>
 * <h1>Behavior</h1>
 * <p/>
 * Each call of next operates as follows. A row is obtained from the input operator. The expressions from fields are
 * evaluated and the resulting values
 * are used to probe the bloom filter. If the filter returns false, this indicates that there is no matching row
 * in the filtering set of rows and null is returned. If the filter returns true, then the onPositive operator
 * is used to locate the matching row. If a row is located then the input row (not the row from onPositive)
 * is returned, otherwise null is returned.
 * <p/>
 * <h1>Output</h1>
 * <p/>
 * A subset of rows from the input stream.
 * <p/>
 * <h1>Assumptions</h1>
 * <p/>
 * None.
 * <p/>
 * <h1>Performance</h1>
 * <p/>
 * This operator should generate very little IO activity, although bloom filters are probabilistic.
 * <p/>
 * <h1>Memory Requirements</h1>
 * <p/>
 * This operator relies on the bloom filter created by Using_BloomFilter.
 */

class Select_BloomFilter extends Operator
{
    // Object interface

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }

    // Operator interface


    @Override
    public void findDerivedTypes(Set<RowType> derivedTypes)
    {
        input.findDerivedTypes(derivedTypes);
        onPositive.findDerivedTypes(derivedTypes);
    }

    @Override
    protected Cursor cursor(QueryContext context)
    {
        return new Execution(context);
    }

    @Override
    public List<Operator> getInputOperators()
    {
        return Arrays.asList(input, onPositive);
    }

    @Override
    public String describePlan()
    {
        return String.format("%s\n%s", describePlan(input), describePlan(onPositive));
    }

    // Select_BloomFilter interface

    public Select_BloomFilter(Operator input,
                              Operator onPositive,
                              List<? extends Expression> fields,
                              int bindingPosition)
    {
        ArgumentValidation.notNull("input", input);
        ArgumentValidation.notNull("onPositive", onPositive);
        ArgumentValidation.notNull("fields", fields);
        ArgumentValidation.isGT("fields.size()", fields.size(), 0);
        ArgumentValidation.isGTE("bindingPosition", bindingPosition, 0);
        this.input = input;
        this.onPositive = onPositive;
        this.bindingPosition = bindingPosition;
        this.fields = fields;
    }

    // Class state

    private static final InOutTap TAP_OPEN = OPERATOR_TAP.createSubsidiaryTap("operator: Select_BloomFilter open");
    private static final InOutTap TAP_NEXT = OPERATOR_TAP.createSubsidiaryTap("operator: Select_BloomFilter next");

    // Object state

    private final Operator input;
    private final Operator onPositive;
    private final int bindingPosition;
    private final List<? extends Expression> fields;

    // Inner classes

    private class Execution extends OperatorExecutionBase implements Cursor
    {
        // Cursor interface

        @Override
        public void open()
        {
            TAP_OPEN.in();
            try {
                filter = context.getBloomFilter(bindingPosition);
                context.setBloomFilter(bindingPosition, null);
                inputCursor.open();
                idle = false;
            } finally {
                TAP_OPEN.out();
            }
        }

        @Override
        public Row next()
        {
            TAP_NEXT.in();
            try {
                Row row;
                do {
                    row = inputCursor.next();
                    if (row == null) {
                        close();
                    } else if (!filter.maybePresent(hashProjectedRow(row)) || !rowReallyHasMatch(row)) {
                        row = null;
                    }
                } while (!idle && row == null);
                return row;
            } finally {
                TAP_NEXT.out();
            }
        }

        @Override
        public void close()
        {
            if (!idle) {
                inputCursor.close();
                idle = true;
            }
        }

        @Override
        public void destroy()
        {
            if (filter != null) {
                close();
                inputCursor.destroy();
                onPositiveCursor.destroy();
                filter = null;
            }
        }

        @Override
        public boolean isIdle()
        {
            return filter != null && idle;
        }

        @Override
        public boolean isActive()
        {
            return filter != null && !idle;
        }

        @Override
        public boolean isDestroyed()
        {
            return filter == null;
        }

        // Execution interface

        Execution(QueryContext context)
        {
            super(context);
            this.inputCursor = input.cursor(context);
            this.onPositiveCursor = onPositive.cursor(context);
            for (Expression field : fields) {
                ExpressionEvaluation eval = field.evaluation();
                eval.of(context);
                fieldEvals.add(eval);
            }
        }

        // For use by this class

        private int hashProjectedRow(Row row)
        {
            int hash = 0;
            for (ExpressionEvaluation fieldEval : fieldEvals) {
                fieldEval.of(row);
                hash = hash ^ ValueSourceHasher.hash(fieldEval.eval());
            }
            return hash;
        }

        private boolean rowReallyHasMatch(Row row)
        {
            // bindingPosition is used to hold onto a row for use during the evaluation of expressions
            // during onPositiveCursor.open(). This is somewhat sleazy, but the alternative is to
            // complicate the Select_BloomFilter API, requiring the caller to specify another binding position.
            // It is safe to reuse the binding position in this way because the filter is extracted and stored
            // in a field during open(), while the use of the binding position for use in the onPositive lookup
            // occurs during next().
            context.setRow(bindingPosition, row);
            onPositiveCursor.open();
            try {
                return onPositiveCursor.next() != null;
            } finally {
                onPositiveCursor.close();
                context.setRow(bindingPosition, null);
            }
        }

        // Object state

        private final Cursor inputCursor;
        private final Cursor onPositiveCursor;
        private BloomFilter filter; // null indicates destroyed
        private final List<ExpressionEvaluation> fieldEvals = new ArrayList<ExpressionEvaluation>();
        private boolean idle = true;
    }
}

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

package com.akiban.qp.row;

import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.rowtype.ProjectedRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.Quote;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.util.ValueHolder;
import com.akiban.util.AkibanAppender;

import java.util.ArrayList;
import java.util.List;

public class ProjectedRow extends AbstractRow
{
    // Object interface

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder();
        AkibanAppender appender = AkibanAppender.of(buffer);
        buffer.append('(');
        boolean first = true;
        for (ExpressionEvaluation evaluation : evaluations) {
            if (first) {
                first = false;
            } else {
                buffer.append(", ");
            }
            evaluation.eval().appendAsString(appender, Quote.NONE);
        }
        buffer.append(')');
        return buffer.toString();
    }

    // Row interface

    @Override
    public RowType rowType()
    {
        return rowType;
    }

    @Override
    public ValueSource eval(int i) {
        ValueHolder holder = holders[i];
        if (holder == null) {
            holders[i] = holder = new ValueHolder();
            holder.copyFrom(evaluations.get(i).eval());
        }
        return holder;
    }

    @Override
    public HKey hKey()
    {
        return null;
    }

    // AbstractRow interface


    @Override
    protected void beforeAcquire() {
        row.acquire();
    }

    @Override
    public void afterRelease()
    {
        row.release();
    }

    // ProjectedRow interface

    public ProjectedRow(ProjectedRowType rowType, Row row, QueryContext context, List<Expression> expressions)
    {
        this.rowType = rowType;
        this.row = row;
        this.evaluations = createEvaluations(expressions, row, context);
        this.holders = new ValueHolder[expressions.size()];
    }

    /** Make sure all the <code>ValueHolder</code>s are full. */
    public void freeze() {
        for (int i = 0; i < holders.length; i++) {
            if (holders[i] == null) {
                eval(i);
            }
        }
    }

    // For use by this class

    private List<ExpressionEvaluation> createEvaluations(List<Expression> expressions, 
                                                         Row row, QueryContext context)
    {
        List<ExpressionEvaluation> result = new ArrayList<ExpressionEvaluation>();
        for (Expression expression : expressions) {
            ExpressionEvaluation evaluation = expression.evaluation();
            evaluation.of(context);
            evaluation.of(row);
            result.add(evaluation);
        }
        return result;
    }

    // Object state

    private final ProjectedRowType rowType;
    private final Row row;
    private final List<ExpressionEvaluation> evaluations;
    private final ValueHolder[] holders;
}

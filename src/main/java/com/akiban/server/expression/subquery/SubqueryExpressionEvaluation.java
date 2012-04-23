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

package com.akiban.server.expression.subquery;

import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.types.ValueSource;

public abstract class SubqueryExpressionEvaluation extends ExpressionEvaluation.Base {

    @Override
    public void of(QueryContext context) {
        this.context = context;
        this.cursor = API.cursor(subquery, context);
    }

    @Override
    public void of(Row row) {
        if (row.rowType() != outerRowType) {
            throw new IllegalArgumentException("wrong row type: " + outerRowType +
                                               " != " + row.rowType());
        }
        outerRow = row;
    }

    @Override
    public final ValueSource eval() {
        context.setRow(bindingPosition, outerRow);
        cursor.open();
        try {
            return doEval();
        }
        finally {
            cursor.close();
        }
    }

    @Override
    public void destroy()
    {
        if (cursor != null) {
            cursor.destroy();
        }
    }

    // Shareable interface

    @Override
    public void acquire() {
        outerRow.acquire();
    }

    @Override
    public boolean isShared() {
        return outerRow.isShared();
    }

    @Override
    public void release() {
        outerRow.release();
    }

    // for use by subclasses

    protected abstract ValueSource doEval();

    protected QueryContext queryContext() {
        return context;
    }

    protected Row next() {
        Row row = cursor.next();
        if ((row != null) &&
            (row.rowType() != innerRowType)) {
            throw new IllegalArgumentException("wrong row type: " + innerRowType +
                                               " != " + row.rowType());
        }
        return row;
    }

    protected SubqueryExpressionEvaluation(Operator subquery,
                                           RowType outerRowType, RowType innerRowType, 
                                           int bindingPosition) {
        this.subquery = subquery;
        this.outerRowType = outerRowType;
        this.innerRowType = innerRowType;
        this.bindingPosition = bindingPosition;
    }

    private final Operator subquery;
    private final RowType outerRowType;
    private final RowType innerRowType;
    private final int bindingPosition;
    private Cursor cursor;
    private QueryContext context;
    private Row outerRow;

}

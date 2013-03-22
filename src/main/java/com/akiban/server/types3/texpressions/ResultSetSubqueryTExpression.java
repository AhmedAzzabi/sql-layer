
package com.akiban.server.types3.texpressions;

import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.aksql.aktypes.AkResultSet;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.server.types3.pvalue.PValueSource;

public class ResultSetSubqueryTExpression extends SubqueryTExpression
{
    private static final class InnerEvaluation implements TEvaluatableExpression
    {
        @Override
        public PValueSource resultValue() {
            return pvalue;
        }

        @Override
        public void evaluate() {
            context.setRow(bindingPosition, outerRow);
            Cursor cursor = API.cursor(subquery, context);
            cursor.open();
            pvalue.putObject(cursor);
        }

        @Override
        public void with(Row row) {
            if (row.rowType() != outerRowType) {
                throw new IllegalArgumentException("wrong row type: " + outerRowType +
                                                   " != " + row.rowType());
            }
            outerRow = row;
        }

        @Override
        public void with(QueryContext context) {
            this.context = context;
        }

        InnerEvaluation(Operator subquery, RowType outerRowType, int bindingPosition)
        {
            this.subquery = subquery;
            this.outerRowType = outerRowType;
            this.bindingPosition = bindingPosition;
            this.pvalue = new PValue();
        }

        private final Operator subquery;
        private final RowType outerRowType;
        private final int bindingPosition;
        private final PValue pvalue;
        private QueryContext context;
        private Row outerRow;
    }

    public ResultSetSubqueryTExpression(Operator subquery, TInstance tInstance,
                                        RowType outerRowType, RowType innerRowType, 
                                        int bindingPosition)
    {
        super(subquery, outerRowType, innerRowType, bindingPosition);
        assert (tInstance.typeClass() instanceof AkResultSet) : tInstance;
        this.tInstance = tInstance;
    }

    @Override
    public TInstance resultType()
    {
        return tInstance;
    }

    @Override
    public TEvaluatableExpression build()
    {
        return new InnerEvaluation(subquery(), outerRowType(), bindingPosition());
    }

    private final TInstance tInstance;
}

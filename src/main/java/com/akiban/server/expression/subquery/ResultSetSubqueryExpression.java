/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.akiban.server.expression.subquery;

import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.operator.QueryBindings;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.util.ValueHolder;

public final class ResultSetSubqueryExpression extends SubqueryExpression {

    @Override
    public ExpressionEvaluation evaluation() {
        return new InnerEvaluation(subquery(), outerRowType(),
                                   bindingPosition());
    }

    @Override
    public AkType valueType() {
        return AkType.RESULT_SET;
    }

    @Override
    public String toString() {
        return "RESULT_SET(" + subquery() + ")";
    }

    @Override
    public String name () {
        return "RESULT_SET";
    }
    
    public ResultSetSubqueryExpression(Operator subquery,
                                       RowType outerRowType, RowType innerRowType, 
                                       int bindingPosition) {
        super(subquery, outerRowType, innerRowType, bindingPosition);
    }

    // TODO: Could refactor SubqueryExpressionEvaluation into a common piece.
    private static final class InnerEvaluation extends ExpressionEvaluation.Base {
        @Override
        public void of(QueryContext context) {
            this.context = context;
        }

        @Override
        public void of(QueryBindings bindings) {
            this.bindings = bindings;
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
        public ValueSource eval() {
            bindings.setRow(bindingPosition, outerRow);
            Cursor cursor = API.cursor(subquery, context, bindings);
            cursor.open();
            return new ValueHolder(AkType.RESULT_SET, cursor);
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

        protected InnerEvaluation(Operator subquery,
                                  RowType outerRowType,
                                  int bindingPosition) {
            this.subquery = subquery;
            this.outerRowType = outerRowType;
            this.bindingPosition = bindingPosition;
        }

        private final Operator subquery;
        private final RowType outerRowType;
        private final int bindingPosition;
        private QueryContext context;
        private QueryBindings bindings;
        private Row outerRow;
    }

}

/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.qp.expression;

import com.akiban.qp.physicaloperator.Bindings;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.types.ValueSource;
import com.akiban.sql.optimizer.ExpressionRow;

import java.util.Arrays;

public final class RowBasedUnboundExpressions implements UnboundExpressions {
    @Override
    public BoundExpressions get(Bindings bindings) {
        return new ExpressionsAndBindings(rowType, expressions, bindings);
    }

    @Override
    public String toString() {
        return "UnboundExpressions" + Arrays.toString(expressions);
    }

    public RowBasedUnboundExpressions(RowType rowType, Expression[] expressions) {
        this.expressions = expressions;
        this.rowType = rowType;
    }

    private final Expression[] expressions;
    private final RowType rowType;

    private static class ExpressionsAndBindings implements BoundExpressions {

        @Override
        public ValueSource eval(int index) {
            return expressionRow.eval(index);
        }

        ExpressionsAndBindings(RowType rowType, Expression[] expressions, Bindings bindings) {
            expressionRow = new ExpressionRow(rowType, bindings, expressions);
        }

        private final ExpressionRow expressionRow;
    }
}

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

package com.akiban.server.expression.std;

import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.types.AkType;
import com.akiban.server.types.NullValueSource;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.conversion.Converters;
import com.akiban.server.types.util.ValueHolder;

import java.util.List;

public final class CoalesceExpression extends AbstractCompositeExpression {
    @Override
    protected void describe(StringBuilder sb) {
        sb.append("COALESCE");
    }

    @Override
    public ExpressionEvaluation evaluation() {
        return new InnerEvaluation(valueType(), childrenEvaluations());
    }

    public CoalesceExpression(List<? extends Expression> children) {
        super(childrenType(children), children);
    }

    private static final class InnerEvaluation extends AbstractCompositeExpressionEvaluation {

        @Override
        public ValueSource eval() {
            for (ExpressionEvaluation childEvaluation : children()) {
                ValueSource childSource = childEvaluation.eval();
                if (!childSource.isNull()) {
                    return type.equals(childSource.getConversionType())
                            ? childSource
                            : Converters.convert(childSource, holder());
                }
            }
            return NullValueSource.only();
        }

        private InnerEvaluation(AkType type, List<? extends ExpressionEvaluation> children) {
            super(children);
            this.type = type;
        }

        private ValueHolder holder() {
            if (holder == null) {
                holder = new ValueHolder();
                holder.expectType(type);
            }
            return holder;
        }

        private final AkType type;
        private ValueHolder holder;
    }
}

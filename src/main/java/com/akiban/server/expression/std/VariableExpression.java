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

import com.akiban.qp.operator.Bindings;
import com.akiban.qp.row.Row;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.types.AkType;
import com.akiban.server.types.FromObjectValueSource;
import com.akiban.server.types.ValueSource;

public final class VariableExpression implements Expression {

    // Expression interface

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public boolean needsBindings() {
        return true;
    }

    @Override
    public boolean needsRow() {
        return false;
    }

    @Override
    public ExpressionEvaluation evaluation() {
        return new InnerEvaluation(type, position);
    }

    @Override
    public AkType valueType() {
        return type;
    }

    public VariableExpression(AkType type, int position) {
        this.type = type;
        this.position = position;
    }

    // Object interface

    @Override
    public String toString() {
        return String.format("Variable(pos=%d)", position);
    }

    // object state

    private final AkType type;
    private final int position;

    private static class InnerEvaluation implements ExpressionEvaluation {
        @Override
        public void of(Row row) {
        }

        @Override
        public void of(Bindings bindings) {
            this.bindings = bindings;
        }

        @Override
        public ValueSource eval() {
            return source.setExplicitly(bindings.get(position), type);
        }

        @Override
        public void acquire() {
            ++ownedBy;
        }

        @Override
        public boolean isShared() {
            return ownedBy > 1;
        }

        @Override
        public void release() {
            assert ownedBy > 0 : ownedBy;
            --ownedBy;
        }

        private InnerEvaluation(AkType type, int position) {
            this.type = type;
            this.position = position;
        }

        private final AkType type;
        private final int position;
        private final FromObjectValueSource source = new FromObjectValueSource();
        private Bindings bindings;
        private int ownedBy = 0;
    }
}

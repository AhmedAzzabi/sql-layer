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

package com.akiban.server.expression.std;

import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.expression.Expression;
import com.akiban.server.types.AkType;

public abstract class AbstractBinaryExpression extends AbstractCompositeExpression {

    protected final Expression left() {
        return children().get(0);
    }

    protected final Expression right() {
        return children().get(1);
    }

    protected AbstractBinaryExpression(AkType type, Expression first, Expression second) {
        super(type, first, second);
        if (children().size() != 2) {
            throw new WrongExpressionArityException(2, children().size());
        }
    }
}

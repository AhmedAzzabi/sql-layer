/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
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

package com.foundationdb.server.expression.std;

import com.foundationdb.qp.row.Row;
import com.foundationdb.qp.row.ValuesRow;
import com.foundationdb.qp.rowtype.ValuesRowType;
import com.foundationdb.server.error.AkibanInternalException;
import com.foundationdb.server.expression.Expression;
import com.foundationdb.server.expression.ExpressionEvaluation;
import com.foundationdb.server.types.AkType;
import com.foundationdb.server.types.util.ValueHolder;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class FieldExpressionTest {
    @Test
    public void twoRows() {
        ValuesRowType dummyType = new ValuesRowType(null, 1, AkType.LONG);
        Expression fieldExpression = new FieldExpression(dummyType, 0);

        assertFalse("shouldn't be constant", fieldExpression.isConstant());
        assertEquals("type", AkType.LONG, fieldExpression.valueType());
        ExpressionEvaluation evaluation = fieldExpression.evaluation();

        evaluation.of(new ValuesRow(dummyType, new Object[]{27L}));
        assertEquals("evaluation.eval()", new ValueHolder(AkType.LONG, 27L), new ValueHolder(evaluation.eval()));

        evaluation.of(new ValuesRow(dummyType, new Object[]{23L}));
        assertEquals("evaluation.eval()", new ValueHolder(AkType.LONG, 23L), new ValueHolder(evaluation.eval()));
    }

    @Test(expected = IllegalStateException.class)
    public void noRows() {
        final ExpressionEvaluation evaluation;
        try {
            ValuesRowType dummyType = new ValuesRowType(null, 1, AkType.LONG);
            Expression fieldExpression = new FieldExpression(dummyType, 0);
            assertEquals("type", AkType.LONG, fieldExpression.valueType());
            evaluation = fieldExpression.evaluation();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        evaluation.eval();
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongRow() {
        final ExpressionEvaluation evaluation;
        final Row badRow;
        try {
            ValuesRowType dummyType1 = new ValuesRowType(null, 1, AkType.LONG);
            evaluation = new FieldExpression(dummyType1, 0).evaluation();
            ValuesRowType dummyType2 = new ValuesRowType(null, 2, AkType.LONG); // similar, but not same!
            badRow = new ValuesRow(dummyType2, new Object[] { 31L });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        evaluation.of(badRow);
    }

    @Test(expected = IllegalArgumentException.class)
    public void indexTooLow() {
        ValuesRowType dummyType = new ValuesRowType(null, 1, AkType.LONG);
        new FieldExpression(dummyType, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void indexTooHigh() {
        ValuesRowType dummyType = new ValuesRowType(null, 1, AkType.LONG);
        new FieldExpression(dummyType, 1);
    }

    @Test(expected = AkibanInternalException.class)
    @Ignore
    public void wrongFieldType() {
        final ExpressionEvaluation evaluation;
        final Row badRow;
        try {
            ValuesRowType dummyType = new ValuesRowType(null, 1, AkType.LONG);
            evaluation = new FieldExpression(dummyType, 0).evaluation();
            badRow = new ValuesRow(dummyType, new Object[] { 31.4159 });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        evaluation.of(badRow);
    }

    @Test(expected = NullPointerException.class)
    public void nullRowType() {
        new FieldExpression(null, 0);
    }
    
    @Test
    public void testSharing() {
        ValuesRowType dummyType = new ValuesRowType(null, 1, AkType.LONG);
        ExpressionEvaluation evaluation = new FieldExpression(dummyType, 0).evaluation();

        ValuesRow row = new ValuesRow(dummyType, new Object[]{27L});
        evaluation.of(row);

        assertEquals("evaluation.isShared()", false, evaluation.isShared());
        assertEquals("row.isShared", false, row.isShared());

        // first acquire doesn't mean it's shared
        evaluation.acquire();
        assertEquals("evaluation.isShared()", false, evaluation.isShared());
        assertEquals("row.isShared", false, row.isShared());

        // next does
        evaluation.acquire();
        assertEquals("evaluation.isShared()", true, evaluation.isShared());
        assertEquals("row.isShared", true, row.isShared());

        // now, three own it (very shared!)
        evaluation.acquire();
        assertEquals("evaluation.isShared()", true, evaluation.isShared());
        assertEquals("row.isShared", true, row.isShared());

        // back down to two owners, still shared
        evaluation.release();
        assertEquals("evaluation.isShared()", true, evaluation.isShared());
        assertEquals("row.isShared", true, row.isShared());

        // down to one owner, not shared anymore
        evaluation.release();
        assertEquals("evaluation.isShared()", false, evaluation.isShared());
        assertEquals("row.isShared", false, row.isShared());

        // no owners, very not shared
        evaluation.release();
        assertEquals("evaluation.isShared()", false, evaluation.isShared());
        assertEquals("row.isShared", false, row.isShared());
    }
}

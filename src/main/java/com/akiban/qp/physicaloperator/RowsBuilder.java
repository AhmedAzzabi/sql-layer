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

package com.akiban.qp.physicaloperator;

import com.akiban.qp.row.AbstractRow;
import com.akiban.qp.row.HKey;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.ValuesRowType;
import com.akiban.server.types.AkType;
import com.akiban.server.types.FromObjectValueSource;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.util.ValueHolder;
import com.akiban.util.ArgumentValidation;
import com.akiban.util.Strings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RowsBuilder {

    public Deque<Row> rows() {
        return rows;
    }

    public RowType rowType() {
        return rowType;
    }

    public RowsBuilder row(Object... values) {
        ValueHolder[] holders = new ValueHolder[values.length];
        FromObjectValueSource tmpSource = new FromObjectValueSource();
        for (int i = 0; i < values.length; ++i) {
            ValueHolder holder = new ValueHolder();
            tmpSource.setExplicitly(values[i], types[i]);
            holder.copyFrom(tmpSource);
            holders[i] = holder;
        }
        return row(holders);
    }

    public RowsBuilder row(ValueHolder... values) {
        ArgumentValidation.isEQ("values.length", values.length, types.length);
        for (int i=0; i < values.length; ++i) {
            ValueHolder value = values[i];
            AkType valueType = value.getConversionType();
            AkType requiredType = types[i];
            if (valueType != AkType.NULL && valueType != requiredType) {
                throw new IllegalArgumentException("type at " + i + " must be " + requiredType + ", is " + valueType);
            }
        }
        InternalRow row = new InternalRow(rowType, Arrays.asList(values));
        rows.add(row);
        return this;
    }

    public RowsBuilder addRow(Row row) {
        List<ValueHolder> values = new ArrayList<ValueHolder>();
        for (int i = 0; i < rowType.nFields(); ++i) {
            ValueSource rowSource = row.bindSource(i);
            ValueHolder holder = new ValueHolder(rowSource);
            values.add(holder);
        }
        InternalRow newRow = new InternalRow(rowType, values);
        rows.add(newRow);
        return this;
    }

    public RowsBuilder addRows(Collection<? extends Row> rowsToAdd) {
        for (Row row : rowsToAdd) {
            addRow(row);
        }
        return this;
    }

    public RowsBuilder(AkType... types) {
        rowType = new ValuesRowType(null, COUNT.incrementAndGet(), types.length);
        this.types = types;
    }

    // object state

    private final RowType rowType;
    private final AkType[] types;
    private final Deque<Row> rows = new ArrayDeque<Row>();

    // class state

    private static final AtomicInteger COUNT = new AtomicInteger();

    // nested classes

    private static class InternalRow extends AbstractRow {
        @Override
        public RowType rowType() {
            return rowType;
        }

        @Override
        public HKey hKey() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ValueSource bindSource(int i) {
            return values.get(i);
        }

        @Override
        public String toString() {
            return Strings.join(values, ", ");
        }

        private InternalRow(RowType rowType, List<ValueHolder> values) {
            this.rowType = rowType;
            this.values = new ArrayList<ValueHolder>(values);
        }

        private final RowType rowType;
        private final List<ValueHolder> values;
    }
}

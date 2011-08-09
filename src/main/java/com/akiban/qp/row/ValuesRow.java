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

package com.akiban.qp.row;

import com.akiban.qp.physicaloperator.Bindings;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.ValuesRowType;
import com.akiban.server.types.ConversionSource;

import java.util.Arrays;

public class ValuesRow extends AbstractRow
{
    // Object interface

    @Override
    public String toString()
    {
        return valuesHolder.toString();
    }

    // Row interface

    @Override
    public RowType rowType()
    {
        return rowType;
    }

    @Override
    public Object field(int i, Bindings bindings)
    {
        return valuesHolder.objectAt(i);
    }

    @Override
    public ConversionSource conversionSource(int i, Bindings bindings) {
        return valuesHolder.conversionSourceAt(i);
    }

    @Override
    public HKey hKey()
    {
        return null;
    }

    // ValuesRow interface

    public ValuesRow(ValuesRowType rowType, Object[] values)
    {
        this.rowType = rowType;
        this.valuesHolder = new RowValuesHolder(values);
    }

    // Object state

    private final ValuesRowType rowType;
    private final RowValuesHolder valuesHolder;
}

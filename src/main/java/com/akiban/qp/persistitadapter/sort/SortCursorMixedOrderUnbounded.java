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

package com.akiban.qp.persistitadapter.sort;

import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.operator.API;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.persistit.exception.PersistitException;

class SortCursorMixedOrderUnbounded extends SortCursorMixedOrder
{
    // SortCursorMixedOrder interface

    @Override
    public MixedOrderScanState createScanState(SortCursorMixedOrder cursor, int field) throws PersistitException
    {
        return new MixedOrderScanStateUnbounded(cursor, field);
    }

    @Override
    public void computeBoundaries()
    {
    }

    public SortCursorMixedOrderUnbounded(PersistitAdapter adapter,
                                         RowGenerator rowGenerator,
                                         IndexKeyRange keyRange,
                                         API.Ordering ordering)
    {
        super(adapter, rowGenerator, keyRange, ordering);
    }
}

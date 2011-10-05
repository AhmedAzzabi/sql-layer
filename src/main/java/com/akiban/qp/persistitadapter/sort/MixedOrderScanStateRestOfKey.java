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

import com.persistit.Key;
import com.persistit.KeyState;
import com.persistit.exception.PersistitException;

class MixedOrderScanStateRestOfKey extends MixedOrderScanState
{
    @Override
    public boolean startScan() throws PersistitException
    {
        if (subtreeRootKey == null) {
            subtreeRootKey = new Key(cursor.exchange.getKey());
        } else {
            cursor.exchange.getKey().copyTo(subtreeRootKey);
        }
        return cursor.exchange.traverse(Key.GT, true);
    }

    @Override
    public boolean advance() throws PersistitException
    {
        boolean more = ascending ? cursor.exchange.next(true) : cursor.exchange.previous(true);
        if (more) {
            more = cursor.exchange.getKey().firstUniqueByteIndex(subtreeRootKey) > subtreeRootKey.getEncodedSize();
        }
        if (!more) {
            // Restore exchange key to where it was before exploring this subtree. But also attach one
            // more key segment since SortCursorMixedOrder is going to cut one.
            subtreeRootKey.copyTo(cursor.exchange.getKey());
            cursor.exchange.append(Key.AFTER);
        }
        return more;
    }

    public MixedOrderScanStateRestOfKey(SortCursorMixedOrder sortCursor, int field) throws PersistitException
    {
        super(sortCursor, field, true);
    }

    private Key subtreeRootKey;
}

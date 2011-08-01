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

package com.akiban.qp.persistitadapter;

import com.akiban.qp.row.HKey;
import com.persistit.Key;

class PersistitHKey implements HKey
{
    // Object interface

    @Override
    public String toString()
    {
        return hKey.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        return o == this
                || (o != null && PersistitHKey.class.equals(o.getClass()) && hKey.equals(((PersistitHKey)o).hKey));
    }

    @Override
    public int hashCode() {
        return hKey.hashCode();
    }

    // HKey interface

    public int segments()
    {
        return hKeySegments;
    }

    public void useSegments(int segments)
    {
        assert segments > 0 && segments < keyDepth.length : segments;
        // setDepth shortens the key's encoded size if necessary but doesn't lengthen it.
        // So setEncodedSize back to the original key length, permitting setDepth to work in all cases.
        // (setEncodedSize is cheap.)
        hKey.setEncodedSize(hKeySize);
        hKey.setDepth(keyDepth[segments]);
    }

    // TODO: Move into Key?
    public boolean prefixOf(HKey hKey)
    {
        PersistitHKey that = (PersistitHKey) hKey;
        if (this.hKeySize <= that.hKeySize) {
            byte[] thisBytes = this.hKey.getEncodedBytes();
            byte[] thatBytes = that.hKey.getEncodedBytes();
            for (int i = 0; i < this.hKeySize; i++) {
                if (thisBytes[i] != thatBytes[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void copyTo(HKey target)
    {
        assert target instanceof PersistitHKey;
        PersistitHKey that = (PersistitHKey) target;
        that.copyFrom(hKey);
    }

    @Override
    public void extendWithOrdinal(int ordinal)
    {
        hKey.append(ordinal);
    }

    @Override
    public void extendWithNull()
    {
        hKey.append(null);
    }

    // PersistitHKey interface

    public void copyFrom(Key source)
    {
        source.copyTo(hKey);
        hKeySize = hKey.getEncodedSize();
    }

    public void copyTo(Key target)
    {
        hKey.copyTo(target);
    }

    public PersistitHKey(PersistitAdapter adapter, com.akiban.ais.model.HKey hKeyMetadata)
    {
        this.hKey = new Key(adapter.persistit.getDb());
        this.hKeySegments = hKeyMetadata.segments().size();
        this.keyDepth = hKeyMetadata.keyDepth();
    }

    // For use by this package

    Key key()
    {
        return hKey;
    }

    // Object state

    private final Key hKey;
    private final int hKeySegments;
    private int hKeySize;
    // Identifies the persistit key depth for the ith hkey segment, 1 <= i <= #hkey segments.
    private final int[] keyDepth;
}

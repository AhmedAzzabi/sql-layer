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

package com.akiban.ais.model;

/**
 * IndexToHkey is an interface usful in constructing HKey values from an index row.
 * There are two types of entries, ordinal values and index fields. An ordinal identifies
 * a user table. Non-ordinal entires are the positions within the index row and the
 * table where the HKey values can be found.
 */
public class IndexToHKey {
    public IndexToHKey(int[] ordinals, int[] indexRowPositions, int[] fieldPositions) {
        if(ordinals.length != indexRowPositions.length || ordinals.length != fieldPositions.length) {
            throw new IllegalArgumentException(String.format("All arrays must be of equal length: %d, %d, %d",
                                               ordinals.length, indexRowPositions.length, fieldPositions.length));
        }
        this.ordinals = ordinals;
        this.indexRowPositions = indexRowPositions;
        this.fieldPositions = fieldPositions;
    }

    public boolean isOrdinal(int index) {
        return ordinals[index] >= 0;
    }

    public boolean isInIndexRow(int index) {
        return indexRowPositions[index] >= 0;
    }

    public int getOrdinal(int index) {
        return ordinals[index];
    }

    public int getIndexRowPosition(int index) {
        return indexRowPositions[index];
    }

    public int getFieldPosition(int index) {
        return fieldPositions[index];
    }

    public int getLength() {
        return ordinals.length;
    }

    /** If set, value >= 0, the ith field of the hkey is this ordinal **/
    private final int[] ordinals;
    /** If set, value >= 0, the ith field of the hkey is at this position in the index row **/
    private final int[] indexRowPositions;
    /** If set, value >= 0, the ith field of the hkey is at this field in the data row **/
    private final int[] fieldPositions;
}

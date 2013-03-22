
package com.akiban.ais.model;

import java.util.Arrays;

/**
 * IndexRowComposition presents an interface for mapping row and hkey fields
 * to the fields of an index. The leading index fields are exactly the fields
 * identified in the Index (i.e. the declared index columns). The remaining
 * fields are whatever is necessary to ensure that all of the hkey is represented.
 */
public class IndexRowComposition {
    public IndexRowComposition(int[] fieldPositions, int[] hkeyPositions) {
        if(fieldPositions.length != hkeyPositions.length) {
            throw new IllegalArgumentException("Both arrays must be of equal length: " +
                                               fieldPositions.length + ", " +
                                               hkeyPositions.length);
        }
        this.fieldPositions = fieldPositions;
        this.hkeyPositions = hkeyPositions;
    }

    public boolean isInRowData(int indexPos) {
        return fieldPositions[indexPos] >= 0;
    }

    public boolean isInHKey(int indexPos) {
        return hkeyPositions[indexPos] >= 0;
    }

    public int getFieldPosition(int indexPos) {
        return fieldPositions[indexPos];
    }

    public int getHKeyPosition(int indexPos) {
        return hkeyPositions[indexPos];
    }

    public int getLength() {
        return fieldPositions.length;
    }

    @Override
    public String toString() {
        return "fieldPos: " + Arrays.toString(fieldPositions) +
               " hkeyPos: " + Arrays.toString(hkeyPositions);
    }

    /** If set, value >= 0, is the field position for index position i **/
    private final int[] fieldPositions;
    /** If set, value >= 0, is the hkey position for index position i **/
    private final int[] hkeyPositions;
}

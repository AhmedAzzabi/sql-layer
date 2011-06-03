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

package com.akiban.server;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.akiban.ais.model.Column;
import com.akiban.ais.model.GroupTable;
import com.akiban.ais.model.HKeyColumn;
import com.akiban.ais.model.HKeySegment;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.UserTable;
import com.akiban.server.service.tree.TreeCache;
import com.akiban.server.service.tree.TreeLink;

/**
 * Contain the relevant schema information for one version of a table
 * definition. Instances of this class acquire table definition data from the
 * AIS, interpret it, and provide information on how to encode and decode fields
 * from a RowData structure.
 * 
 * 
 * @author peter
 * 
 */
public class RowDef implements TreeLink {

    private final Table table;

    private final TableStatus tableStatus;

    /**
     * Array of FieldDef, one per column
     */
    private final FieldDef[] fieldDefs;

    /**
     * Field(s) that constitute the foreign key by which this row is joined to
     * its parent table.
     */
    private int[] parentJoinFields;

    /**
     * Name of the tree storing this table - same as the group table name
     */
    private String treeName;

    /**
     * Field index of the auto-increment column; -1 if none.
     */
    private int autoIncrementField;

    /**
     * Auto-Increment delta. This is the amount by which the auto-increment
     * counter should be incremented for each new row.
     */
    private long autoIncrementDelta;

    /**
     * RowDefs of constituent user tables. Populated only if this is the RowDef
     * for a group table. Null if this is the RowDef for a user table.
     */
    private RowDef[] userTableRowDefs;

    /**
     * For a user table, the column position of the first column of the user
     * table relative to the group table.
     */
    private int columnOffset;

    /**
     * For a user table, the number of Persistit Key segments uses to encode the
     * hkey for rows of this table.
     */
    private int hkeyDepth;

    /**
     * Array of index definitions for this row
     */
    private Index[] indexes;

    /**
     * Array computed by the {@link #preComputeFieldCoordinates(FieldDef[])}
     * method to assist in looking up a field's offset and length.
     */
    private final int[][] fieldCoordinates;

    /**
     * Array computed by the {@link #preComputeFieldCoordinates(FieldDef[])}
     * method to assist in looking up a field's offset and length.
     */
    private final byte[][] varLenFieldMap;

    private AtomicReference<TreeCache> treeCache = new AtomicReference<TreeCache>();

    public RowDef(Table table, final TableStatus tableStatus) {
        this.table = table;
        this.tableStatus = tableStatus;
        tableStatus.setRowDef(this);
        table.rowDef(this);
        List<Column> columns = table.getColumnsIncludingInternal();
        this.fieldDefs = new FieldDef[columns.size()];
        for (Column column : columns) {
            this.fieldDefs[column.getPosition()] = new FieldDef(this, column);
        }
        fieldCoordinates = new int[(fieldDefs.length + 7) / 8][];
        varLenFieldMap = new byte[(fieldDefs.length + 7) / 8][];
        preComputeFieldCoordinates(fieldDefs);
        autoIncrementField = -1;
        if (table.isUserTable()) {
            final UserTable userTable = (UserTable) table;
            if (userTable.getAutoIncrementColumn() != null) {
                autoIncrementField = userTable.getAutoIncrementColumn()
                        .getPosition();
                //
                // TODO - receive non-default value from adapter.
                //
                autoIncrementDelta = 1;
                final long initialAutoIncrementValue = userTable.getAutoIncrementColumn()
                        .getInitialAutoIncrementValue().longValue();
                //
                // Safe to do these here, non-transactionally, since recovery would
                // redo these anyway.
                //
                tableStatus.setAutoIncrement(true);
                tableStatus.updateAutoIncrementValue(initialAutoIncrementValue);
            }
        }
    }

    public Table table() {
        return table;
    }

    public UserTable userTable() {
        assert table instanceof UserTable : this;
        return (UserTable) table;
    }

    public GroupTable groupTable() {
        assert table instanceof GroupTable : this;
        return (GroupTable) table;
    }

    /**
     * Display the fieldCoordinates array
     */
    public String debugToString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < fieldDefs.length; i++) {
            if (i != 0)
                sb.append(",");
            sb.append(fieldDefs[i]);
        }
        sb.append("]\n");
        for (int i = 0; i < fieldCoordinates.length; i++) {
            sb.append("--- " + i + " ---\n");
            int count = 256;
            int remainingBits = fieldDefs.length - (i * 8);
            if (remainingBits >= 0 && remainingBits < 8) {
                count = 1 << remainingBits;
            }
            for (int j = 0; j < count; j += 16) {
                for (int k = 0; k < 16; k++) {
                    sb.append((k % 8) == 0 ? "   " : " ");
                    AkServerUtil.hex(sb, fieldCoordinates[i][j + k], 8);
                }
                sb.append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * An implementation useful while debugging.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(String.format("RowDef #%d %s (%s.%s)",
                                                           table.getTableId(),
                                                           table.getName(),
                                                           treeName, getPkTreeName()));
        if (userTableRowDefs != null) {
            for (int i = 0; i < userTableRowDefs.length; i++) {
                sb.append(i == 0 ? "{" : ",");
                sb.append(userTableRowDefs[i].getRowDefId());
                sb.append("->");
                sb.append(userTableRowDefs[i].getColumnOffset());
                sb.append(":" + userTableRowDefs[i].getFieldCount());
            }
            sb.append("}");
        }
        sb.append("groupColumnOffset, fieldcount " + getColumnOffset() + ", "
                + getFieldCount());

        for (int i = 0; i < fieldDefs.length; i++) {
            sb.append(i == 0 ? "[" : ",");
            sb.append(fieldDefs[i].getType());
            if (parentJoinFields != null) {
                for (int j = 0; j < parentJoinFields.length; j++) {
                    if (parentJoinFields[j] == i) {
                        sb.append("^");
                        sb.append(j);
                        break;
                    }
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Returns the offset relative to the start of the byte array represented by
     * the supplied {@link RowData} of the field specified by the supplied
     * fieldIndex, or zero if the field is null. The location is a long which
     * encodes an offset to the data and its length in bytes. If
     * <code>offset</code> and <code>length</code> are the offset and length of
     * the data, respectively, then the location is returned as
     * 
     * <code>(long)offset+((long)length << 32)</code>
     * 
     * For fixed-length fields like numbers, the length is the fixed length of
     * the field, e.g., 8 for BIGINT values. For variable- length fields the
     * length is the number of bytes used in representing the value.
     * 
     * @param rowData
     * @param fieldIndex
     * @return
     */

    public long fieldLocation(final RowData rowData, final int fieldIndex) {
        final int fieldCount = fieldDefs.length;
        if (fieldIndex < 0 || fieldIndex >= fieldCount) {
            throw new IllegalArgumentException("Field index out of bounds: "
                    + fieldIndex);
        }
        int dataStart = rowData.getRowStartData();
        //
        // If NullMap bit is set, return zero immediately
        //
        if (((rowData.getColumnMapByte(fieldIndex / 8) >>> (fieldIndex % 8)) & 1) == 1) {
            return 0;
        }

        if (fieldDefs[fieldIndex].isFixedSize()) {
            //
            // Look up the offset and width of a fixed-width field
            //
            int offset = dataStart;
            int width = 0;
            //
            // Loop over NullMap bytes until reaching fieldIndex
            //
            for (int k = 0; k <= fieldIndex; k += 8) {
                int byteIndex = k >>> 3;
                int mapByte = (~rowData.getColumnMapByte(byteIndex)) & 0xFF;
                //
                // Look up offset and width in the statically-generated
                // fieldCoordinates array.
                //
                int bitCount = fieldIndex - k;
                if (bitCount < 8) {
                    mapByte &= (0xFF >>> (7 - bitCount));
                }
                int fc = fieldCoordinates[byteIndex][mapByte];
                //
                // Decode the offset and width fields
                //
                width = fc >>> 24;
                offset += (fc & 0xFFFFFF) + width;
            }
            //
            // Encode the width and offset fields
            //
            return ((offset & 0xFFFFFF) - width) | (((long) width) << 32);
        } else {
            //
            // Look up the offset and width of a variable-width field.
            // Previous and current refer to the coordinates of the
            // fixed-length fields delimiting the variable-length
            // data.
            //
            int offset = dataStart;
            int width = 0;
            int previous = 0;
            int current = 0;

            for (int k = 0; k < fieldCount; k += 8) {
                if (k <= fieldIndex) {
                    current = current + width;
                }
                int byteIndex = k >>> 3;
                int mapByte = (~rowData.getColumnMapByte(byteIndex)) & 0xFF;
                int bitCount1 = fieldIndex - k;
                //
                // Look up offset and width in the statically-generated
                // fieldCoordinates array.
                //
                if (k <= fieldIndex) {
                    int mbb1 = mapByte;
                    int mask = 0xFF;
                    if (bitCount1 < 7) {
                        mask >>>= (7 - bitCount1);
                        mbb1 &= mask;
                    }
                    int fc1 = fieldCoordinates[byteIndex][mbb1];
                    current = fc1 + (current & 0xFFFFFF);
                    int mbb2 = varLenFieldMap[byteIndex][bitCount1 < 8 ? mbb1
                            : mbb1 + 256] & 0xFF;
                    if (mbb2 != 0) {
                        int fc2 = fieldCoordinates[byteIndex][mbb2];
                        previous = current + fc2 - fc1;
                    }
                }
                //
                // In addition, because the overall size of the fixed-length
                // field array is not encoded, we need to compute the offset
                // of the byte after the last field. That's where the variable-
                // length bytes begin.
                //
                int bitCount2 = fieldCount - k;
                int mbb2 = mapByte;
                if (bitCount2 > 0 && bitCount2 < 8) {
                    mbb2 &= (0xFF >>> (8 - bitCount2));
                }
                int fc = fieldCoordinates[byteIndex][mbb2];
                //
                // Decode the offset and width of the last field
                //
                width = (fc) >>> 24;
                offset += (fc & 0xFFFFFF) + width;
            }
            //
            // Compute the starting and ending offsets (from the beginning of
            // the rowData byte array) of the variable-length segment.
            //
            int start = (int) rowData.getIntegerValue((previous & 0xFFFFFF)
                    + dataStart, previous >>> 24);

            int end = (int) rowData.getIntegerValue((current & 0xFFFFFF)
                    + dataStart, current >>> 24);
            //
            // Encode and return the offset and length
            //
            return (start + offset) | ((long) (end - start) << 32);
        }
    }

    public String explain(final RowData rowData) {
        final StringBuilder sb = new StringBuilder();
        sb.append("rowStart=" + rowData.getRowStart() + " rowEnd="
                + rowData.getRowEnd() + " rowSize=" + rowData.getRowSize());
        for (int i = 0; i < fieldDefs.length; i++) {
            sb.append(AkServerUtil.NEW_LINE);
            sb.append(i + ": " + fieldDefs[i] + "  ");
            final long fieldLocation = fieldLocation(rowData, i);
            final int offset = (int) fieldLocation;
            final int width = (int) (fieldLocation >>> 32);
            sb.append(" offset=" + offset + " width=" + width + "==>");
            sb.append(AkServerUtil.hex(rowData.getBytes(), offset, width));
        }
        return sb.toString();
    }

    public int getFieldCount() {
        return fieldDefs.length;
    }

    public FieldDef getFieldDef(final int index) {
        return fieldDefs[index];
    }

    public int getFieldIndex(final String fieldName) {
        for (int index = 0; index < fieldDefs.length; index++) {
            if (fieldDefs[index].getName().equals(fieldName)) {
                return index;
            }
        }
        return -1;
    }

    public FieldDef[] getFieldDefs() {
        return fieldDefs;
    }

    public int getGroupRowDefId() {
        return table instanceof GroupTable ? table.getTableId() : table
                .getGroup().getGroupTable().getTableId();
    }

    public Index[] getIndexes() {
        return indexes;
    }

    public Index getIndex(final String indexName) {
        return table.getIndex(indexName);
    }
    
    public Index getIndex(final int indexId) {
        for(Index index : indexes) {
            if(index.getIndexId() == indexId) {
                return index;
            }
        }
        return null;
    }

    @Deprecated
    public int[] getParentJoinFields() {
        return parentJoinFields;
    }

    public int getParentRowDefId() {
        UserTable userTable = (UserTable) table;
        Join parentJoin = userTable.getParentJoin();
        return parentJoin == null ? 0 : parentJoin.getParent().getTableId();
    }

    public String getPkTreeName() {
        final Index pkIndex = getPKIndex();
        return pkIndex != null ? ((IndexDef)pkIndex.indexDef()).getTreeName() : null;
    }

    public int getRowDefId() {
        return table.getTableId();
    }

    public TableStatus getTableStatus() {
        return tableStatus;
    }

    public String getTableName() {
        return table.getName().getTableName();
    }

    public String getTreeName() {
        return treeName;
    }

    public String getSchemaName() {
        return table.getName().getSchemaName();
    }

    public boolean isGroupTable() {
        return userTableRowDefs != null;
    }

    public int getOrdinal() {
        return tableStatus.getOrdinal();
    }

    public void setOrdinal(final int ordinal) {
        tableStatus.setOrdinal(ordinal);
    }

    public boolean isUserTable() {
        return !isGroupTable();
    }

    public void setIndexes(Index[] indexes) {
        this.indexes = indexes;
    }

    public void setParentJoinFields(int[] parentJoinFields) {
        this.parentJoinFields = parentJoinFields;
    }

    public void setAutoIncrementField(int autoIncrementField) {
        this.autoIncrementField = autoIncrementField;
    }

    public void setTreeName(final String treeName) {
        this.treeName = treeName;
    }

    public int getAutoIncrementField() {
        return autoIncrementField;
    }
    
    public int getHKeyDepth() {
        return hkeyDepth;
    }

    public int getColumnOffset() {
        assert !isGroupTable() || columnOffset == 0;
        return columnOffset;
    }

    public void setColumnOffset(final int columnOffset) {
        this.columnOffset = columnOffset;
    }

    public Index getPKIndex() {
        if (!isGroupTable() && indexes != null && indexes.length > 0) {
            return indexes[0];
        } else {
            return null;
        }
    }

    public RowDef[] getUserTableRowDefs() {
        return userTableRowDefs;
    }

    public void setUserTableRowDefs(final RowDef[] userTableRowDefs) {
        this.userTableRowDefs = userTableRowDefs;
    }

    public boolean isAutoIncrement() {
        return autoIncrementField != -1;
    }

    public long getAutoIncrementDelta() {
        return autoIncrementDelta;
    }
    
    /**
     * Compute lookup tables used to in the {@link #fieldLocation(RowData, int)}
     * method. This method is invoked once when a RowDef is first constructed.
     * 
     * @param fieldDefs
     */
    void preComputeFieldCoordinates(final FieldDef[] fieldDefs) {
        final int fieldCount = fieldDefs.length;
        int voffset = 0;
        for (int field = 0; field < fieldCount; field++) {
            final FieldDef fieldDef = fieldDefs[field];
            final int byteIndex = field / 8;
            final int bitIndex = field % 8;
            final int bit = 1 << bitIndex;
            if (bitIndex == 0) {
                fieldCoordinates[byteIndex] = new int[256];
                varLenFieldMap[byteIndex] = new byte[512];
            }
            final int width;
            if (fieldDef.isFixedSize()) {
                width = fieldDef.getMaxStorageSize();
            } else {
                voffset += fieldDef.getMaxStorageSize();
                width = AkServerUtil.varWidth(voffset);
            }
            for (int i = 0; i < bit; i++) {
                int from = fieldCoordinates[byteIndex][i];
                int to = ((from & 0xFFFFFF) + (from >>> 24)) | (width << 24);
                int k = i + bit;
                fieldCoordinates[byteIndex][k] = to;
                for (int j = bitIndex; --j >= 0;) {
                    if ((k & (1 << j)) != 0
                            && !fieldDefs[byteIndex * 8 + j].isFixedSize()) {
                        varLenFieldMap[byteIndex][k] = (byte) (k & ((0xFF >>> (7 - j))));
                        break;
                    }
                }
                for (int j = bitIndex + 1; --j >= 0;) {
                    if ((k & (1 << j)) != 0
                            && !fieldDefs[byteIndex * 8 + j].isFixedSize()) {
                        varLenFieldMap[byteIndex][k + 256] = (byte) (k & ((0xFF >>> (7 - j))));
                        break;
                    }
                }
            }
        }
    }

    void computeFieldAssociations(Map<Table,Integer> ordinalMap) {
        // hkeyDepth is hkey position of the last column in the last segment.
        // (Or the position
        // of the last segment if that segment has no columns.)
        if (isUserTable()) {
            List<HKeySegment> segments = userTable().hKey().segments();
            HKeySegment lastSegment = segments.get(segments.size() - 1);
            List<HKeyColumn> lastColumns = lastSegment.columns();
            hkeyDepth = 1 + (lastColumns.isEmpty() ? lastSegment
                    .positionInHKey() : lastColumns.get(lastColumns.size() - 1)
                    .positionInHKey());
        }
        for (Index index : indexes) {
            ((IndexDef)index.indexDef()).computeFieldAssociations(ordinalMap);
        }
    }

    @Override
    public boolean equals(final Object o) {
        final RowDef def = (RowDef) o;
        return this == def || def.getRowDefId() == def.getRowDefId()
                && AkServerUtil.equals(table.getName(), def.table.getName())
                && AkServerUtil.equals(treeName, def.treeName)
                && Arrays.deepEquals(fieldDefs, def.fieldDefs)
                && Arrays.deepEquals(indexes, def.indexes)
                && getOrdinal() == def.getOrdinal()
                && Arrays.equals(parentJoinFields, def.parentJoinFields);

    }

    @Override
    public int hashCode() {
        return getRowDefId() ^ table.getName().hashCode()
                ^ AkServerUtil.hashCode(treeName) ^ Arrays.hashCode(fieldDefs)
                ^ Arrays.hashCode(parentJoinFields);
    }

    @Override
    public void setTreeCache(TreeCache cache) {
        treeCache.set(cache);
    }

    @Override
    public TreeCache getTreeCache() {
        return treeCache.get();
    }
}

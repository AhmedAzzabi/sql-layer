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

package com.akiban.qp.persistitadapter.indexrow;

import com.akiban.ais.model.*;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.qp.persistitadapter.indexcursor.OldExpressionsSortKeyAdapter;
import com.akiban.qp.persistitadapter.indexcursor.PValueSortKeyAdapter;
import com.akiban.qp.persistitadapter.indexcursor.SortKeyAdapter;
import com.akiban.qp.persistitadapter.indexcursor.SortKeyTarget;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.row.IndexRow;
import com.akiban.qp.util.PersistitKey;
import com.akiban.server.PersistitKeyPValueSource;
import com.akiban.server.PersistitKeyValueSource;
import com.akiban.server.collation.AkCollator;
import com.akiban.server.geophile.Space;
import com.akiban.server.geophile.SpaceLatLon;
import com.akiban.server.rowdata.*;
import com.akiban.server.service.tree.KeyCreator;
import com.akiban.server.types.AkType;
import com.akiban.server.types3.TClass;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.Types3Switch;
import com.akiban.server.types3.common.BigDecimalWrapper;
import com.akiban.server.types3.mcompat.mtypes.MBigDecimal;
import com.akiban.server.types3.mcompat.mtypes.MNumeric;
import com.akiban.util.ArgumentValidation;
import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Value;
import com.persistit.exception.PersistitException;

import static java.lang.Math.min;

/*
 * 
 * Index row formats:
 * 
 * NON-UNIQUE INDEX:
 * 
 * - Persistit key contains all declared and undeclared (hkey) fields.
 * 
 * PRIMARY KEY INDEX:
 * 
 * - Persistit key contains all declared fields
 * 
 * - Persistit value contains all undeclared fields.
 * 
 * UNIQUE INDEX:
 * 
 * - Persistit key contains all declared fields
 * 
 * - Persistit key also contains one more long field, needed to ensure that insertion of an index row that contains
 *   at least one null and matches a row already in the index (including any nulls) is not considered a duplicate.
 *   For an index row with no nulls, this field contains zero. For a field with nulls, this field contains a value
 *   that is unique within the index. This mechanism is not needed for primary keys because primary keys can only
 *   contain NOT NULL columns.
 * 
 * - Persistit value contains all undeclared fields.
 * 
 */

public class PersistitIndexRowBuffer extends IndexRow implements Comparable<PersistitIndexRowBuffer>
{
    // Comparable interface

    @Override
    public int compareTo(PersistitIndexRowBuffer that)
    {
        return compareTo(that, null);
    }

    // RowBase interface

    @Override
    public final int compareTo(RowBase row, int thisStartIndex, int thatStartIndex, int fieldCount)
    {
        // The dependence on field positions and fieldCount is a problem for spatial indexes
        if (index.isSpatial()) {
            throw new UnsupportedOperationException(index.toString());
        }
        if (!(row instanceof PersistitIndexRowBuffer)) {
            return super.compareTo(row, thisStartIndex, thatStartIndex, fieldCount);
        }
        // field and byte indexing is as if the pKey and pValue were one contiguous array of bytes. But we switch
        // from pKey to pValue as needed to avoid having to actually copy the bytes into such an array.
        PersistitIndexRowBuffer that = (PersistitIndexRowBuffer) row;
        Key thisKey;
        Key thatKey;
        if (thisStartIndex < this.pKeyFields) {
            thisKey = this.pKey;
        } else {
            thisKey = this.pValue;
            thisStartIndex -= this.pKeyFields;
        }
        if (thatStartIndex < that.pKeyFields) {
            thatKey = that.pKey;
        } else {
            thatKey = that.pValue;
            thatStartIndex -= that.pKeyFields;
        }
        int thisPosition = thisKey.indexTo(thisStartIndex).getIndex();
        int thatPosition = thatKey.indexTo(thatStartIndex).getIndex();
        byte[] thisBytes = thisKey.getEncodedBytes();
        byte[] thatBytes = thatKey.getEncodedBytes();
        int c = 0;
        int eqSegments = 0;
        while (eqSegments < fieldCount) {
            byte thisByte = thisBytes[thisPosition++];
            byte thatByte = thatBytes[thatPosition++];
            c = (thisByte & 0xff) - (thatByte & 0xff);
            if (c != 0) {
                break;
            } else if (thisByte == 0) {
                // thisByte = thatByte = 0
                eqSegments++;
                if (thisStartIndex + eqSegments == this.pKeyFields) {
                    thisBytes = this.pValue.getEncodedBytes();
                    thisPosition = 0;
                }
                if (thatStartIndex + eqSegments == that.pKeyFields) {
                    thatBytes = that.pValue.getEncodedBytes();
                    thatPosition = 0;
                }
            }
        }
        // If c == 0 then the two subarrays must match.
        if (c < 0) {
            c = -(eqSegments + 1);
        } else if (c > 0) {
            c = eqSegments + 1;
        }
        return c;
    }

    // IndexRow interface

    @Override
    public void initialize(RowData rowData, Key hKey)
    {
        pKeyAppends = 0;
        int indexField = 0;
        IndexRowComposition indexRowComp = index.indexRowComposition();
        FieldDef[] fieldDefs = index.indexDef().getRowDef().getFieldDefs();
        RowDataSource rowDataValueSource = Types3Switch.ON ? new RowDataPValueSource() : new RowDataValueSource();
        while (indexField < indexRowComp.getLength()) {
            // handleSpatialColumn will increment pKeyAppends once for all spatial columns
            if (spatialHandler == null || !spatialHandler.handleSpatialColumn(rowData, indexField)) {
                if (indexRowComp.isInRowData(indexField)) {
                    FieldDef fieldDef = fieldDefs[indexRowComp.getFieldPosition(indexField)];
                    Column column = fieldDef.column();
                    rowDataValueSource.bind(fieldDef, rowData);
                    pKeyTarget().append(rowDataValueSource,
                                        column.getType().akType(),
                                        column.tInstance(),
                                        column.getCollator());
                } else if (indexRowComp.isInHKey(indexField)) {
                    PersistitKey.appendFieldFromKey(pKey(), hKey, indexRowComp.getHKeyPosition(indexField));
                } else {
                    throw new IllegalStateException("Invalid IndexRowComposition: " + indexRowComp);
                }
                pKeyAppends++;
            }
            indexField++;
        }
    }

    @Override
    public <S> void append(S source, AkType type, TInstance tInstance, AkCollator collator)
    {
        pKeyTarget().append(source, type, tInstance, collator);
        pKeyAppends++;
    }

    @Override
    public void close(boolean forInsert)
    {
        // Write null-separating value if necessary
        if (index.isUniqueAndMayContainNulls()) {
            long nullSeparator = 0;
            if (forInsert) {
                boolean hasNull = false;
                int keyFields = index.getKeyColumns().size();
                for (int f = 0; !hasNull && f < keyFields; f++) {
                    pKey.indexTo(f);
                    hasNull = pKey.isNull();
                }
                if (hasNull) {
                    nullSeparator = index.nextNullSeparatorValue();
                }
            }
            // else: We're creating an index row to update or delete. Don't need a new null separator value.
            pKey.append(nullSeparator);
        }
        // If necessary, copy pValue state into value. (Check pValueAppender, because that is non-null only in
        // a writeable PIRB.)
        if (pValueTarget != null) {
            value.clear();
            value.putByteArray(pValue.getEncodedBytes(), 0, pValue.getEncodedSize());
        }
    }

    // PersistitIndexRowBuffer interface

    public void appendFieldTo(int position, Key target)
    {
        if (position < pKeyFields) {
            PersistitKey.appendFieldFromKey(target, pKey, position);
        } else {
            PersistitKey.appendFieldFromKey(target, pValue, position - pKeyFields);
        }
        pKeyAppends++;
    }

    public void append(Key.EdgeValue edgeValue)
    {
        // This is unlike other appends. An EdgeValue affects Persistit iteration, so it has to go to a Persistit
        // Key, not a Value. If we're already appending to Values (pKeyAppends >= pKeyFields), then all we can
        // do is append to the value, and then rely on beforeStart() to skip rows that don't qualify. Also,
        // don't increment pKeyAppends, because this append doesn't change where we write next. (In fact, after
        // writing an EdgeValue we shouldn't be appending more anyway.)
        pKey.append(edgeValue);
    }

    public void tableBitmap(long bitmap)
    {
        value.put(bitmap);
    }

    public void copyPersistitKeyTo(Key key)
    {
        pKey.copyTo(key);
    }

    // For table index rows
    public void resetForWrite(Index index, Key key)
    {
        reset(index, key, null, true);
    }

    // For group index rows
    public void resetForWrite(Index index, Key key, Value value)
    {
        reset(index, key, value, true);
    }

    public void resetForRead(Index index, Key key, Value value)
    {
        reset(index, key, value, false);
    }

    public PersistitIndexRowBuffer(KeyCreator keyCreator)
    {
        ArgumentValidation.notNull("keyCreator", keyCreator);
        this.keyCreator = keyCreator;
    }

    public boolean keyEmpty()
    {
        return pKey.getEncodedSize() == 0;
    }

    public int compareTo(PersistitIndexRowBuffer that, boolean[] ascending)
    {
        int c;
        byte[] thisBytes = this.pKey.getEncodedBytes();
        byte[] thatBytes = that.pKey.getEncodedBytes();
        int b = 0; // byte position
        int f = 0; // field position
        int end = min(this.pKey.getEncodedSize(), that.pKey.getEncodedSize());
        while (b < end && f < pKeyFields) {
            int thisByte = thisBytes[b] & 0xff;
            int thatByte = thatBytes[b] & 0xff;
            c = thisByte - thatByte;
            if (c != 0) {
                return ascending == null || ascending[f] ? c : -c;
            } else {
                b++;
                if (thisByte == 0) {
                    f++;
                }
            }
        }
        // Compare pValues, if there are any
        thisBytes = this.pValue == null ? null : this.pValue.getEncodedBytes();
        thatBytes = that.pValue == null ? null : that.pValue.getEncodedBytes();
        if (thisBytes == null && thatBytes == null) {
            return 0;
        } else if (thisBytes == null) {
            return ascending == null || ascending[f] ? -1 : 1;
        } else if (thatBytes == null) {
            return ascending == null || ascending[f] ? 1 : -1;
        }
        b = 0;
        end = min(this.pValue.getEncodedSize(), that.pValue.getEncodedSize());
        while (b < end) {
            int thisByte = thisBytes[b] & 0xff;
            int thatByte = thatBytes[b] & 0xff;
            c = thisByte - thatByte;
            if (c != 0) {
                return ascending == null || ascending[f] ? c : -c;
            } else {
                b++;
                if (thisByte == 0) {
                    f++;
                }
            }
        }
        return 0;
    }

    // For testing only. It does an allocation per call, and is not appropriate for product use.
    public long nullSeparator()
    {
        PersistitKeyValueSource valueSource = new PersistitKeyValueSource();
        valueSource.attach(pKey, pKeyFields, AkType.LONG, null);
        return valueSource.getLong();
    }

    // For use by subclasses

    protected void attach(PersistitKeyValueSource source, int position, AkType type, AkCollator collator)
    {
        if (position < pKeyFields) {
            source.attach(pKey, position, type, collator);
        } else {
            source.attach(pValue, position - pKeyFields, type, collator);
        }
    }

    protected void attach(PersistitKeyPValueSource source, int position, TInstance type)
    {
        if (position < pKeyFields) {
            source.attach(pKey, position, type);
        } else {
            source.attach(pValue, position - pKeyFields, type);
        }
    }

    protected void copyFrom(Exchange ex)
    {
        copyFrom(ex.getKey(), ex.getValue());
    }

    protected void copyFrom(Key key, Value value)
    {
        key.copyTo(pKey);
        if (index.isUnique()) {
            byte[] source = value.getByteArray();
            pValue.setEncodedSize(source.length);
            byte[] target = pValue.getEncodedBytes();
            System.arraycopy(source, 0, target, 0, source.length);
        }
    }

    protected void constructHKeyFromIndexKey(Key hKey, IndexToHKey indexToHKey)
    {
        hKey.clear();
        for (int i = 0; i < indexToHKey.getLength(); i++) {
            if (indexToHKey.isOrdinal(i)) {
                hKey.append(indexToHKey.getOrdinal(i));
            } else {
                int indexField = indexToHKey.getIndexRowPosition(i);
                if (index.isSpatial()) {
                    // A spatial index has a single key column (the z-value), representing the declared spatial key columns.
                    if (indexField > index.firstSpatialArgument())
                        indexField -= index.dimensions() - 1;
                }
                Key keySource;
                if (indexField < pKeyFields) {
                    keySource = pKey;
                } else {
                    keySource = pValue;
                    indexField -= pKeyFields;
                }
                if (indexField < 0 || indexField > keySource.getDepth()) {
                    throw new IllegalStateException(String.format("keySource: %s, indexField: %s",
                                                                  keySource, indexField));
                }
                PersistitKey.appendFieldFromKey(hKey, keySource, indexField);
            }
        }
    }

    public void reset()
    {
        pKey.clear();
        if (pValue != null) {
            pValue.clear();
        }
    }

    // For use by this class

    private <S> SortKeyTarget<S> pKeyTarget()
    {
        return pKeyAppends < pKeyFields ? pKeyTarget : pValueTarget;
    }

    private Key pKey()
    {
        return pKeyAppends < pKeyFields ? pKey : pValue;
    }

    private void reset(Index index, Key key, Value value, boolean writable)
    {
        // TODO: Lots of this, especially allocations, should be moved to the constructor.
        // TODO: Or at least not repeated on reset.
        assert !index.isUnique() || index.isTableIndex() : index;
        this.index = index;
        this.pKey = key;
        if (this.pValue == null) {
            this.pValue = keyCreator.createKey();
        } else {
            this.pValue.clear();
        }
        this.value = value;
        if (index.isSpatial()) {
            this.nIndexFields = index.getAllColumns().size() - index.dimensions() + 1;
            this.pKeyFields = this.nIndexFields;
            this.spatialHandler = new SpatialHandler();
        } else {
            this.nIndexFields = index.getAllColumns().size();
            this.pKeyFields = index.isUnique() ? index.getKeyColumns().size() : index.getAllColumns().size();
            this.spatialHandler = null;
        }
        if (writable) {
            if (this.pKeyTarget == null) {
                this.pKeyTarget = SORT_KEY_ADAPTER.createTarget();
            }
            this.pKeyTarget.attach(key);
            this.pKeyAppends = 0;
            if (index.isUnique()) {
                if (this.pValueTarget == null) {
                    this.pValueTarget = SORT_KEY_ADAPTER.createTarget();
                }
                this.pValueTarget.attach(this.pValue);
            } else {
                this.pValueTarget = null;
            }
            if (value != null) {
                value.clear();
            }
        } else {
            if (value != null) {
                value.getByteArray(pValue.getEncodedBytes(), 0, 0, value.getArrayLength());
                pValue.setEncodedSize(value.getArrayLength());
            }
            this.pKeyTarget = null;
            this.pValueTarget = null;
        }
    }

    public Key getPKey() {
        return pKey;
    }

    public Key getPValue() {
        return pValue;
    }

    // Object state

    // The notation involving "keys" and "values" is tricky because this code deals with both the index view and
    // the persistit view, and these don't match up exactly.
    //
    // The index view of keys and values: An application-defined index has a key comprising
    // one or more columns from one table (table index) or multiple tables (group index). An index row has fields
    // corresponding to these columns, and additional fields corresponding to undeclared hkey columns.
    // Index.getKeyColumns refers to the declared columns, and Index.getAllColumns refers to the declared and
    // undeclared columns.
    //
    // The persistit view: A record managed by Persistit has a Key and a Value.
    //
    // The mapping: For a non-unique index, all of an index's columns (declared and undeclared) are stored in
    // the Persistit Key. For a unique index, the declared columns are stored in the Persistit Key while the
    // remaining columns are stored in the Persistit Value. Group indexes are never unique, so all columns
    // are in the Persistit Key and the Persistit Value is used to store the "table bitmap".
    //
    // Terminology: To try and avoid confusion, the terms pKey and pValue will be used when referring to Persistit
    // Keys and Values. The term key will refer to an index key.
    //
    // So why is pValueAppender a PersistitKeyAppender? Because it is convenient to treat index fields
    // in the style of Persistit Key fields. That permits, for example, byte[] comparisons to determine how values
    // that happen to reside in a Persistit Value (i.e., an undeclared field of an index row for a unique index).
    // So as an index row is being created, we deal entirely with Persisitit Keys, via pKeyAppender or pValueAppender.
    // Only when it is time to write the row are the bytes managed by the pValueAppender written as a single
    // Persistit Value.
    protected final KeyCreator keyCreator;
    protected Index index;
    protected int nIndexFields;
    private Key pKey;
    private Key pValue;
    private SortKeyTarget pKeyTarget;
    private SortKeyTarget pValueTarget;
    private int pKeyFields;
    private Value value;
    private int pKeyAppends = 0;
    private SpatialHandler spatialHandler;
    private final SortKeyAdapter SORT_KEY_ADAPTER =
            Types3Switch.ON
                    ? PValueSortKeyAdapter.INSTANCE
                    : OldExpressionsSortKeyAdapter.INSTANCE;

    // Inner classes

    private class SpatialHandler
    {
        public int dimensions()
        {
            return dimensions;
        }

        public boolean handleSpatialColumn(RowData rowData, int indexField)
        {
            boolean handled = false;
            if (indexField >= firstSpatialField && indexField <= lastSpatialField) {
                if (indexField == firstSpatialField) {
                    bind(rowData);
                    pKey().append(zValue());
                    pKeyAppends++;
                }
                handled = true;
            }
            return handled;
        }

        private void bind(RowData rowData)
        {
            for (int d = 0; d < dimensions; d++) {
                rowDataSource.bind(fieldDefs[d], rowData);
                if (Types3Switch.ON) {
                    RowDataPValueSource rowDataPValueSource = (RowDataPValueSource)rowDataSource;
                    TClass tclass = tinstances[d].typeClass();
                    if (tclass == MNumeric.DECIMAL) {
                        BigDecimalWrapper wrapper = MBigDecimal.getWrapper(rowDataPValueSource, tinstances[d]);
                        coords[d] =
                            d == 0
                            ? SpaceLatLon.scaleLat(wrapper.asBigDecimal())
                            : SpaceLatLon.scaleLon(wrapper.asBigDecimal());
                    }
                    else if (tclass == MNumeric.BIGINT) {
                        coords[d] = rowDataPValueSource.getInt64();
                    }
                    else if (tclass == MNumeric.INT) {
                        coords[d] = rowDataPValueSource.getInt32();
                    }
                    else {
                        assert false : fieldDefs[d].column();
                    }
                }
                else {
                    RowDataValueSource rowDataValueSource = (RowDataValueSource)rowDataSource;
                    switch (types[d]) {
                        case INT:
                            coords[d] = rowDataValueSource.getInt();
                            break;
                        case LONG:
                            coords[d] = rowDataValueSource.getLong();
                            break;
                        case DECIMAL:
                            coords[d] =
                                d == 0
                                ? SpaceLatLon.scaleLat(rowDataValueSource.getDecimal())
                                : SpaceLatLon.scaleLon(rowDataValueSource.getDecimal());
                            break;
                        default:
                            assert false : fieldDefs[d].column();
                            break;
                    }
                }
            }
        }

        private long zValue()
        {
            return space.shuffle(coords);
        }

        private final Space space;
        private final int dimensions;
        private final AkType[] types;
        private final TInstance[] tinstances;
        private final FieldDef[] fieldDefs;
        private final long[] coords;
        private final RowDataSource rowDataSource;
        private final int firstSpatialField;
        private final int lastSpatialField;

        {
            space = index.space();
            dimensions = space.dimensions();
            assert index.dimensions() == dimensions;
            if (Types3Switch.ON) {
                tinstances = new TInstance[dimensions];
                types = null;
            }
            else {
                types = new AkType[dimensions];
                tinstances = null;
            }
            fieldDefs = new FieldDef[dimensions];
            coords = new long[dimensions];
            rowDataSource = (Types3Switch.ON) ? new RowDataPValueSource() : new RowDataValueSource();
            firstSpatialField = index.firstSpatialArgument();
            lastSpatialField = firstSpatialField + dimensions - 1;
            for (int d = 0; d < dimensions; d++) {
                IndexColumn indexColumn = index.getKeyColumns().get(firstSpatialField + d);
                Column column = indexColumn.getColumn();
                if (Types3Switch.ON) {
                    tinstances[d] = column.tInstance();
                }
                else {
                    types[d] = column.getType().akType();
                }
                fieldDefs[d] = column.getFieldDef();
            }
        }
    }
}

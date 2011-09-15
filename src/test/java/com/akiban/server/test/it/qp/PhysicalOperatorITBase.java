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

package com.akiban.server.test.it.qp;

import com.akiban.ais.model.*;
import com.akiban.qp.persistitadapter.OperatorStore;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.qp.persistitadapter.PersistitGroupRow;
import com.akiban.qp.persistitadapter.PersistitIndexRow;
import com.akiban.qp.physicaloperator.*;
import com.akiban.qp.row.Row;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.row.RowHolder;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.server.rowdata.RowDef;
import com.akiban.server.api.dml.ColumnSelector;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.dml.scan.NiceRow;
import com.akiban.server.error.InvalidOperationException;
import com.akiban.server.store.PersistitStore;
import com.akiban.server.store.Store;
import com.akiban.server.test.it.ITBase;
import com.akiban.server.types.ToObjectValueTarget;
import com.persistit.exception.PersistitException;
import com.akiban.util.Strings;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.akiban.qp.physicaloperator.API.cursor;
import static org.junit.Assert.*;

public class PhysicalOperatorITBase extends ITBase
{
    @Before
    public void before() throws InvalidOperationException
    {
        customer = createTable(
            "schema", "customer",
            "cid int not null key",
            "name varchar(20)," +
            "index(name)");
        order = createTable(
            "schema", "order",
            "oid int not null key",
            "cid int",
            "salesman varchar(20)",
            "constraint __akiban_oc foreign key __akiban_oc(cid) references customer(cid)",
            "index(salesman)",
            "index(cid)");
        item = createTable(
            "schema", "item",
            "iid int not null key",
            "oid int",
            "constraint __akiban_io foreign key __akiban_io(oid) references order(oid)");
        address = createTable(
            "schema", "address",
            "aid int not null key",
            "cid int",
            "address varchar(100)",
            "constraint __akiban_ac foreign key __akiban_ac(cid) references customer(cid)",
            "index(address)");
        schema = new Schema(rowDefCache().ais());
        customerRowType = schema.userTableRowType(userTable(customer));
        orderRowType = schema.userTableRowType(userTable(order));
        itemRowType = schema.userTableRowType(userTable(item));
        addressRowType = schema.userTableRowType(userTable(address));
        customerNameIndexRowType = indexType(customer, "name");
        orderSalesmanIndexRowType = indexType(order, "salesman");
        itemOidIndexRowType = indexType(item, "oid");
        itemIidIndexRowType = indexType(item, "iid");
        customerCidIndexRowType = indexType(customer, "cid");
        addressAddressIndexRowType = indexType(address, "address");
        coi = groupTable(customer);
        db = new NewRow[]{createNewRow(customer, 1L, "xyz"),
                          createNewRow(customer, 2L, "abc"),
                          createNewRow(order, 11L, 1L, "ori"),
                          createNewRow(order, 12L, 1L, "david"),
                          createNewRow(order, 21L, 2L, "tom"),
                          createNewRow(order, 22L, 2L, "jack"),
                          createNewRow(item, 111L, 11L),
                          createNewRow(item, 112L, 11L),
                          createNewRow(item, 121L, 12L),
                          createNewRow(item, 122L, 12L),
                          createNewRow(item, 211L, 21L),
                          createNewRow(item, 212L, 21L),
                          createNewRow(item, 221L, 22L),
                          createNewRow(item, 222L, 22L)};
        Store plainStore = store();
        final PersistitStore persistitStore;
        if (plainStore instanceof OperatorStore) {
            OperatorStore operatorStore = (OperatorStore) plainStore;
            persistitStore = operatorStore.getPersistitStore();
        }
        else {
            persistitStore = (PersistitStore) plainStore;
        }
        adapter = new PersistitAdapter(schema, persistitStore, treeService(), session());
    }

    protected void use(NewRow[] db)
    {
        writeRows(db);
    }

    protected GroupTable groupTable(int userTableId)
    {
        RowDef userTableRowDef = rowDefCache().rowDef(userTableId);
        return userTableRowDef.table().getGroup().getGroupTable();
    }

    protected UserTable userTable(int userTableId)
    {
        RowDef userTableRowDef = rowDefCache().rowDef(userTableId);
        return userTableRowDef.userTable();
    }

    protected IndexRowType indexType(int userTableId, String... searchIndexColumnNamesArray)
    {
        UserTable userTable = userTable(userTableId);
        for (Index index : userTable.getIndexesIncludingInternal()) {
            List<String> indexColumnNames = new ArrayList<String>();
            for (IndexColumn indexColumn : index.getColumns()) {
                indexColumnNames.add(indexColumn.getColumn().getName());
            }
            List<String> searchIndexColumnNames = Arrays.asList(searchIndexColumnNamesArray);
            if (searchIndexColumnNames.equals(indexColumnNames)) {
                return schema.userTableRowType(userTable(userTableId)).indexRowType(index);
            }
        }
        return null;
    }

    protected ColumnSelector columnSelector(final Index index)
    {
        final int columnCount = index.getColumns().size();
        return new ColumnSelector() {
            @Override
            public boolean includesColumn(int columnPosition) {
                return columnPosition < columnCount;
            }
        };
    }

    protected TestRow row(RowType rowType, Object... fields)
    {
        return new TestRow(rowType, fields);
    }

    protected TestRow row(String hKeyString, RowType rowType, Object... fields)
    {
        return new TestRow(rowType, fields, hKeyString);
    }

    protected RowBase row(int tableId, Object... values /* alternating field position and value */)
    {
        NiceRow niceRow = new NiceRow(tableId);
        int i = 0;
        while (i < values.length) {
            int position = (Integer) values[i++];
            Object value = values[i++];
            niceRow.put(position, value);
        }
        return PersistitGroupRow.newPersistitGroupRow(adapter, niceRow.toRowData());
    }

    protected RowBase row(IndexRowType indexRowType, Object... values) {
        try {
            return new PersistitIndexRow(adapter, indexRowType, values);
        } catch(PersistitException e) {
            throw new RuntimeException(e);
        }
    }

    protected void compareRows(RowBase[] expected, Cursor cursor)
    {
        compareRows(expected, cursor, NO_BINDINGS);
    }

    protected void compareRows(RowBase[] expected, Cursor cursor, Bindings bindings)
    {
        List<RowHolder<Row>> actualRows = new ArrayList<RowHolder<Row>>(); // So that result is viewable in debugger
        try {
            cursor.open(bindings);
            RowBase actualRow;
            while ((actualRow = cursor.next()) != null) {
                int count = actualRows.size();
                assertTrue(String.format("failed test %d < %d", count, expected.length), count < expected.length);
                if(!equal(expected[count], actualRow)) {
                    String expectedString = expected[count] == null ? "null" : expected[count].toString();
                    String actualString = actualRow == null ? "null" : actualRow.toString();
                    assertEquals("row " + count, expectedString, actualString);
                }
                if (expected[count] instanceof TestRow) {
                    TestRow expectedTestRow = (TestRow) expected[count];
                    if (expectedTestRow.persistityString() != null) {
                        String actualHKeyString = actualRow == null ? "null" : actualRow.hKey().toString();
                        assertEquals(count + ": hkey", expectedTestRow.persistityString(), actualHKeyString);
                    }
                }
                actualRows.add(new RowHolder<Row>((Row) actualRow));
            }
        } finally {
            cursor.close();
        }
        assertEquals(expected.length, actualRows.size());
    }

    // Useful when scanning is expected to throw an exception
    protected void scan(Cursor cursor)
    {
        List<RowBase> actualRows = new ArrayList<RowBase>(); // So that result is viewable in debugger
        try {
            cursor.open(NO_BINDINGS);
            RowBase actualRow;
            while ((actualRow = cursor.next()) != null) {
                actualRows.add(actualRow);
            }
        } finally {
            cursor.close();
        }
    }

    @SuppressWarnings("unused") // useful for debugging
    protected void dumpToAssertion(Cursor cursor)
    {
        List<String> strings = new ArrayList<String>();
        try {
            cursor.open(NO_BINDINGS);
            Row row;
            while ((row = cursor.next()) != null) {
                strings.add(String.valueOf(row));
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            cursor.close();
        }
        strings.add(0, strings.size() == 1 ? "1 string:" : strings.size() + " strings:");
        throw new AssertionError(Strings.join(strings));
    }

    @SuppressWarnings("unused") // useful for debugging
    protected void dumpToAssertion(PhysicalOperator plan)
    {
        dumpToAssertion(cursor(plan, adapter));
    }

    protected void compareRenderedHKeys(String[] expected, Cursor cursor)
    {
        int count;
        try {
            cursor.open(NO_BINDINGS);
            count = 0;
            List<RowBase> actualRows = new ArrayList<RowBase>(); // So that result is viewable in debugger
            RowBase actualRow;
            while ((actualRow = cursor.next()) != null) {
                assertEquals(expected[count], actualRow.hKey().toString());
                count++;
                actualRows.add(actualRow);
            }
        } finally {
            cursor.close();
        }
        assertEquals(expected.length, count);
    }

    protected boolean equal(RowBase expected, RowBase actual)
    {
        ToObjectValueTarget target = new ToObjectValueTarget();
        boolean equal = expected.rowType().nFields() == actual.rowType().nFields();
        for (int i = 0; equal && i < actual.rowType().nFields(); i++) {
            Object expectedField = target.convertFromSource(expected.eval(i));
            Object actualField = target.convertFromSource(actual.eval(i));
            equal =
                expectedField == actualField || // handles case in which both are null
                expectedField != null && actualField != null && expectedField.equals(actualField);
        }
        return equal;
    }

    protected int ordinal(RowType rowType)
    {
        RowDef rowDef = (RowDef) rowType.userTable().rowDef();
        return rowDef.getOrdinal();
    }

    protected static final Bindings NO_BINDINGS = new ArrayBindings(0);

    protected int customer;
    protected int order;
    protected int item;
    protected int address;
    protected RowType customerRowType;
    protected RowType orderRowType;
    protected RowType itemRowType;
    protected RowType addressRowType;
    protected IndexRowType customerCidIndexRowType;
    protected IndexRowType customerNameIndexRowType;
    protected IndexRowType orderSalesmanIndexRowType;
    protected IndexRowType itemOidIndexRowType;
    protected IndexRowType itemIidIndexRowType;
    protected IndexRowType addressAddressIndexRowType;
    protected GroupTable coi;
    protected Schema schema;
    protected NewRow[] db;
    protected NewRow[] emptyDB = new NewRow[0];
    PersistitAdapter adapter;
}

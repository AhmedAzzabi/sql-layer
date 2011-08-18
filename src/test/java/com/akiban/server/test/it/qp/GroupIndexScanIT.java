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

import com.akiban.ais.model.GroupIndex;
import com.akiban.ais.model.UserTable;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.qp.physicaloperator.API;
import com.akiban.qp.physicaloperator.Cursor;
import com.akiban.qp.physicaloperator.PhysicalOperator;
import com.akiban.qp.physicaloperator.StoreAdapter;
import com.akiban.qp.physicaloperator.UndefBindings;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.qp.rowtype.UserTableRowType;
import com.akiban.server.test.it.ITBase;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.ToObjectConversionTarget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GroupIndexScanIT extends ITBase {

    @Test
    public void scanAtLeastO () {
        PhysicalOperator plan = API.indexScan_Default(giRowType, false, null, uTableRowType(o));
        compareResults(plan,
                array("01-01-2001", null),
                array("02-02-2002", "1111"),
                array("03-03-2003", null),
                array("03-03-2003", "3333")
        );
    }

    @Test
    public void scanAtLeastI () {
        PhysicalOperator plan = API.indexScan_Default(giRowType, false, null, uTableRowType(i));
        compareResults(plan,
                array("02-02-2002", "1111"),
                array("03-03-2003", null),
                array("03-03-2003", "3333")
        );
    }

    @Test
    public void defaultDepth() {
        PhysicalOperator explicit = API.indexScan_Default(giRowType, false, null, uTableRowType(i));
        PhysicalOperator defaulted = API.indexScan_Default(giRowType, false, null);

        List<List<?>> explicitList = planToList(explicit);
        List<List<?>> defaultedList = planToList(defaulted);

        assertEqualLists("results", explicitList, defaultedList);
    }

    @Test(expected = IllegalArgumentException.class)
    public void scanAtLeastC () {
        API.indexScan_Default(giRowType, false, null, uTableRowType(c));
    }

    @Test(expected = IllegalArgumentException.class)
    public void scanAtLeastH () {
        API.indexScan_Default(giRowType, false, null, uTableRowType(h));
    }

    @Before
    public void setUp() {
        c = createTable(SCHEMA, "c", "cid int key, name varchar(32)");
        o = createTable(SCHEMA, "o", "oid int key, c_id int", "when varchar(32)", akibanFK("c_id", "c", "cid"));
        i = createTable(SCHEMA, "i", "iid int key, o_id int", "sku varchar(6)", akibanFK("o_id", "o", "oid"));
        h = createTable(SCHEMA, "h", "hid int key, i_id int", akibanFK("i_id", "i", "iid"));
        String groupName = getUserTable(c).getGroup().getName();
        GroupIndex gi = createGroupIndex(groupName, GI_NAME, "o.when, i.sku");

        schema = new Schema(ddl().getAIS(session()));
        adapter = new PersistitAdapter(schema, persistitStore(), session());
        giRowType = schema.indexRowType(gi);

        writeRows(
                createNewRow(c, 1L, "One"),
                createNewRow(o, 10L, 1L, "01-01-2001"), // no children
                createNewRow(o, 11L, 1L, "02-02-2002"), // one child
                createNewRow(i, 100L, 11L, "1111"),
                createNewRow(o, 12L, 2L, "03-03-2003"), // orphaned, two children
                createNewRow(i, 101L, 12L, null),
                createNewRow(i, 102L, 12L, "3333")
        );
    }

    @After
    public void tearDown() {
        c = o = i = h = null;
        schema = null;
        giRowType = null;
        adapter = null;
    }

    private UserTableRowType uTableRowType(int tableId) {
        UserTable userTable = ddl().getAIS(session()).getUserTable(tableId);
        UserTableRowType rowType = schema.userTableRowType(userTable);
        if (rowType == null) {
            throw new NullPointerException(userTable.toString());
        }
        return rowType;
    }

    private void compareResults(PhysicalOperator plan, Object[]... expectedResults) {
        assertEqualLists("rows scanned", nestedList(expectedResults), planToList(plan));
    }

    private List<List<?>> planToList(PhysicalOperator plan) {
        List<List<?>> actualResults = new ArrayList<List<?>>();
        Cursor cursor =  API.cursor(plan, adapter);
        cursor.open(UndefBindings.only());
        try {
            ToObjectConversionTarget target = new ToObjectConversionTarget();
            for (Row row = cursor.next(); row != null; row = cursor.next()) {
                Object[] rowArray = new Object[row.rowType().nFields()];
                for (int i=0; i < rowArray.length; ++i) {
                    ValueSource source = row.conversionSource(i, UndefBindings.only());
                    rowArray[i] = target.convertFromSource(source);
                }
                actualResults.add(Arrays.asList(rowArray));
            }
        } finally {
            cursor.close();
        }
        return actualResults;
    }

    private List<List<?>> nestedList(Object[][] input) {
        List<List<?>> listList = new ArrayList<List<?>>();
        for (Object[] array : input) {
            listList.add(Arrays.asList(array));
        }
        return listList;
    }

    private Integer c, o, i, h;
    private Schema schema;
    private StoreAdapter adapter;
    private IndexRowType giRowType;

    private final static String SCHEMA = "schema";
    private final static String GI_NAME = "when_sku";
}

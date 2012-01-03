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

package com.akiban.server.test.it.keyupdate;

import com.akiban.ais.model.Index;
import com.akiban.ais.model.UserTable;
import com.akiban.server.error.DuplicateKeyException;
import com.akiban.server.store.statistics.IndexStatisticsService;
import com.akiban.server.test.it.ITBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class IndexEntryCountIT extends ITBase {

    @Test
    public void addAndCheckPK() {
        writeRow(cId, 3L, "Mary");
        countEntries(cId, PK, 3);
    }
    
    @Test
    public void deleteAndCheckPK() {
        deleteRow(cId, 2L, "Joe");
        countEntries(cId, PK, 1);
    }

    @Test
    public void updateAndCheckPK() {
        update(cId, 1L, "Bob").to(4L, "Bobby");
        countEntries(cId, PK, 2);
    }

    @Test
    public void addAndCheckKey() {
        Index nameIndex = createTableIndex(cId,  "name_idx", false, "name");
        countEntries(nameIndex, 2);
        writeRow(cId, 3L, "Mary");
        countEntries(nameIndex, 3);
    }

    @Test
    public void deleteAndCheckKey() {
        Index nameIndex = createTableIndex(cId,  "name_idx", false, "name");
        countEntries(nameIndex, 2);
        deleteRow(cId, 2L, "Joe");
        countEntries(nameIndex, 1);
    }

    @Test
    public void updateAndCheckKey() {
        Index nameIndex = createTableIndex(cId,  "name_idx", false, "name");
        countEntries(nameIndex, 2);
        update(cId, 1L, "Bob").to(4L, "Bobby");
        countEntries(nameIndex, 2);
    }

    @Test
    public void addFailAndCheckUnique() {
        Index nameIndex = createTableIndex(cId,  "name_idx", true, "name");
        countEntries(nameIndex, 2);
        boolean foundDuplicate = false;
        try {
            writeRow(cId, 3L, "Bob");
        } catch (DuplicateKeyException e) {
            foundDuplicate = true;
        }
        assertTrue("expected DuplicateKeyException", foundDuplicate);
        countEntries(nameIndex, 2);
    }

    @Test
    public void updateFailAndCheckUnique() {
        Index nameIndex = createTableIndex(cId, "name_idx", true, "name");
        countEntries(nameIndex, 2);
        boolean foundDuplicate = false;
        try {
            update(cId,  1L, "Bob").to(3L, "Joe");
        } catch (DuplicateKeyException e) {
            foundDuplicate = true;
        }
        assertTrue("expected DuplicateKeyException", foundDuplicate);
        countEntries(nameIndex, 2);
    }

    @Test
    public void addAndCheckGI() {
        Index nameWhen = createGroupIndex(groupName(), "gi_idx", "customers.name,orders.when");
        countEntries(nameWhen, 1);
        writeRow(oId, 12L, 2L, "2002-02-02");
        countEntries(nameWhen, 2);
    }

    @Test
    public void deleteAndCheckGI() {
        Index nameWhen = createGroupIndex(groupName(), "gi_idx", "customers.name,orders.when");
        countEntries(nameWhen, 1);
        deleteRow(oId, 11L, 1L, "2001-01-01");
        countEntries(nameWhen, 0);
    }

    @Test
    public void updateAndCheckGI() {
        Index nameWhen = createGroupIndex(groupName(), "gi_idx", "customers.name,orders.when");
        countEntries(nameWhen, 1);
        update(oId, 11L, 1L, "2001-01-01").to(12L, 2, "2002-02-02");
        countEntries(nameWhen, 1);
    }
    
    @Before
    public void createTables() {
        cId = createTable("indexcount", "customers", "cid int key, name varchar(32)");
        oId = createTable(SCHEMA, "orders", "oid int key, cid int, when varchar(32)",
                akibanFK("cid", "customers", "cid")
        );
        Index oFk = getOrdersFk();
        
        // customers
        countEntries(cId, PK, 0);

        writeRow(cId, 1L, "Bob");
        countEntries(cId, PK, 1);

        writeRow(cId, 2L, "Joe");
        countEntries(cId, PK, 2);

        // orders
        countEntries(oId, PK, 0);
        countEntries(oFk, 0);
        
        writeRow(oId, 11L, 1L, "2001-01-01");
        countEntries(oId, PK, 1);
        countEntries(oFk, 1);
    }
    
    @After
    public void truncateTables() {
        dml().truncateTable(session(), cId);
        dml().truncateTable(session(), oId);
        
        List<Index> allIndexes = new ArrayList<Index>();
        allIndexes.addAll(getUserTable(cId).getIndexes());
        allIndexes.addAll(getUserTable(oId).getIndexes());
        allIndexes.addAll(getUserTable(cId).getGroup().getIndexes());
        
        for (Index index : allIndexes) {
            countEntries(index, 0);
        }
    }

    private Index getOrdersFk() {
        UserTable orders = getUserTable(oId);
        List<Index> possible = new ArrayList<Index>(1);
        for (Index index : orders.getIndexes()) {
            if ("FOREIGN KEY".equals(index.getConstraint()))
                possible.add(index);
        }
        if (possible.size() != 1)
            throw new RuntimeException("need one FK: " + possible);
        return possible.get(0);
    }

    private String groupName() {
        return getUserTable(cId).getGroup().getName();
    }

    private void countEntries(int tableId, String indexName, int expectedEntryCount) {
        UserTable uTable = getUserTable(tableId);
        Index index = PK.equals(indexName)
                ? uTable.getPrimaryKey().getIndex()
                : uTable.getIndex(indexName);
        countEntries(index, expectedEntryCount);
    }

    private void countEntries(final Index index, int expectedEntryCount) {
        final IndexStatisticsService idxStats = serviceManager().getServiceByClass(IndexStatisticsService.class);
        long  actual = transactionallyUnchecked(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return idxStats.countEntries(session(), index);
            }
        });
        assertEquals("entries for " + index, expectedEntryCount, actual);
    }

    private int cId;
    private int oId;

    private static final String PK = "PK";
    private static final String SCHEMA = "indexcount";
}

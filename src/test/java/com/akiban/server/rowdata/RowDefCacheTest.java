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

package com.akiban.server.rowdata;

import static junit.framework.Assert.assertSame;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.GroupIndex;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.IndexRowComposition;
import com.akiban.ais.model.IndexToHKey;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.TableName;
import junit.framework.Assert;

import org.junit.Test;

import com.akiban.ais.model.HKey;
import com.akiban.ais.model.HKeyColumn;
import com.akiban.ais.model.HKeySegment;
import com.akiban.ais.model.UserTable;

public class RowDefCacheTest
{
    @Test
    public void testMultipleBadlyOrderedColumns() throws Exception
    {
        String[] ddl = {
            String.format("use `%s`; ", SCHEMA),
            "create table b(",
            "    b0 int,",
            "    b1 int,",
            "    b2 int,",
            "    b3 int,",
            "    b4 int,",
            "    b5 int,",
            "    primary key(b3, b2, b4, b1)",
            ") engine = akibandb;",
            "create table bb(",
            "    bb0 int,",
            "    bb1 int,",
            "    bb2 int,",
            "    bb3 int,",
            "    bb4 int,",
            "    bb5 int,",
            "    primary key (bb0, bb5, bb3, bb2, bb4), ",
            "CONSTRAINT `__akiban_fk_0` FOREIGN KEY `__akiban_fk_0` (`bb0`,`bb2`,`bb1`,`bb3`) REFERENCES `b` (`b3`,`b2`,`b4`,`b1`)",
            ") engine = akibandb;",
        };
        RowDefCache rowDefCache = SCHEMA_FACTORY.rowDefCache(ddl);
        RowDef b = rowDefCache.getRowDef(tableName("b"));
        UserTable bTable = b.userTable();
        checkHKey(bTable.hKey(), bTable, bTable, "b3", bTable, "b2", bTable, "b4", bTable, "b1");
        assertEquals(5, b.getHKeyDepth()); // b ordinal, b3, b2, b4, b1
        RowDef bb = rowDefCache.getRowDef(tableName("bb"));
        UserTable bbTable = bb.userTable();
        checkHKey(bbTable.hKey(),
                  bTable, bbTable, "bb0", bbTable, "bb2", bbTable, "bb1", bbTable, "bb3",
                  bbTable, bbTable, "bb5", bbTable, "bb4");
        assertEquals(8, bb.getHKeyDepth()); // b ordinal, b3, b2, b4, b1, bb ordinal, b5, b4
        assertEquals(6, b.getFieldDefs().length);
        checkField("b0", b, 0);
        checkField("b1", b, 1);
        checkField("b2", b, 2);
        checkField("b3", b, 3);
        checkField("b4", b, 4);
        checkField("b5", b, 5);
        assertEquals(6, bb.getFieldDefs().length);
        checkField("bb0", bb, 0);
        checkField("bb1", bb, 1);
        checkField("bb2", bb, 2);
        checkField("bb3", bb, 3);
        checkField("bb4", bb, 4);
        checkField("bb5", bb, 5);
        assertArrayEquals(new int[]{}, b.getParentJoinFields());
        assertArrayEquals(new int[]{0, 2, 1, 3}, bb.getParentJoinFields());
        assertEquals(b.getRowDefId(), bb.getParentRowDefId());
        assertEquals(0, b.getParentRowDefId());
        RowDef group = rowDefCache.getRowDef(b.getGroupRowDefId());
        checkField("b$b0", group, 0);
        checkField("b$b1", group, 1);
        checkField("b$b2", group, 2);
        checkField("b$b3", group, 3);
        checkField("b$b4", group, 4);
        checkField("b$b5", group, 5);
        checkField("bb$bb0", group, 6);
        checkField("bb$bb1", group, 7);
        checkField("bb$bb2", group, 8);
        checkField("bb$bb3", group, 9);
        checkField("bb$bb4", group, 10);
        checkField("bb$bb5", group, 11);
        assertEquals(group.getRowDefId(), b.getGroupRowDefId());
        assertEquals(group.getRowDefId(), bb.getGroupRowDefId());
    }

    @Test
    public void childDoesNotContributeToHKey() throws Exception
    {
        String[] ddl = {
            String.format("use `%s`;", SCHEMA),
            "create table parent (",
            "   id int,",
            "   primary key(id)",
            ") engine = akibandb;",
            "create table child (",
            "   id int,",
            "   primary key(id),",
            "   constraint `__akiban_fk0` foreign key `akibanfk` (id) references parent(id)",
            ") engine = akibandb;"
        };
        RowDefCache rowDefCache = SCHEMA_FACTORY.rowDefCache(ddl);
        RowDef parent = rowDefCache.getRowDef(tableName("parent"));
        UserTable p = parent.userTable();
        checkHKey(p.hKey(), p, p, "id");
        assertEquals(2, parent.getHKeyDepth()); // parent ordinal, id
        RowDef child = rowDefCache.getRowDef(tableName("child"));
        UserTable c = child.userTable();
        checkHKey(c.hKey(),
                  p, c, "id",
                  c);
        assertEquals(3, child.getHKeyDepth()); // parent ordinal, id, child ordinal
        assertArrayEquals("parent joins", new int[]{}, parent.getParentJoinFields());
        assertArrayEquals("child joins", new int[]{0}, child.getParentJoinFields());
    }

    @Test
    public void testUserIndexDefs() throws Exception
    {
        String[] ddl = {
            String.format("use %s;", SCHEMA),
            "create table t (",
            "    a int, ",
            "    b int, ",
            "    c int, ",
            "    d int, ",
            "    e int, ",
            "    primary key(c, a), ",
            "    key e_d(e, d), ",
            "    unique key d_b(d, b)",
            ");"
        };
        RowDefCache rowDefCache = SCHEMA_FACTORY.rowDefCache(ddl);
        RowDef t = rowDefCache.getRowDef(tableName("t"));
        assertEquals(3, t.getHKeyDepth()); // t ordinal, c, a
        Index index;
        index = t.getPKIndex();
        assertTrue(index.isPrimaryKey());
        assertTrue(index.isUnique());
        index = t.getIndex("e_d");
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        index = t.getIndex("d_b");
        assertTrue(!index.isPrimaryKey());
        assertTrue(index.isUnique());
    }

    @Test
    public void testNonCascadingPKs() throws Exception
    {
        String[] ddl = {
            String.format("use %s; ", SCHEMA),
            "create table customer(",
            "    cid int not null, ",
            "    cx int not null, ",
            "    primary key(cid), ",
            "    unique key cid_cx(cid, cx) ",
            ") engine = akibandb; ",
            "create table orders(",
            "    oid int not null, ",
            "    cid int not null, ",
            "    ox int not null, ",
            "    primary key(oid), ",
            "    unique key cid_oid(cid, oid), ",
            "    unique key oid_cid(oid, cid), ",
            "    unique key cid_oid_ox(cid, oid, ox), ",
            "    constraint __akiban_oc foreign key co(cid) references customer(cid)",
            ") engine = akibandb; ",
            "create table item(",
            "    iid int not null, ",
            "    oid int not null, ",
            "    ix int not null, ",
            "    primary key(iid), ",
            "    key oid_iid(oid, iid), ",
            "    key iid_oid(iid, oid), ",
            "    key oid_iid_ix(oid, iid, ix), ",
            "    constraint __akiban_io foreign key io(oid) references orders(oid)",
            ") engine = akibandb; "
        };
        RowDefCache rowDefCache = SCHEMA_FACTORY.rowDefCache(ddl);
        Index index;
        int[] fields;
        IndexRowComposition rowComp;
        IndexToHKey indexToHKey;
        // ------------------------- Customer ------------------------------------------
        RowDef customer = rowDefCache.getRowDef(tableName("customer"));
        checkFields(customer, "cid", "cx");
        assertEquals(2, customer.getHKeyDepth()); // customer ordinal, cid
        assertArrayEquals(new int[]{}, customer.getParentJoinFields());
        // index on cid
        index = customer.getPKIndex();
        assertNotNull(index);
        assertTrue(index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // c.cid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // c.cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        // index on cid, cx
        index = customer.getIndex("cid_cx");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // c.cid
        assertEquals(1, fields[1]); // c.cx
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // c.cid
        assertEquals(1, rowComp.getFieldPosition(1)); // c.cx
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        // ------------------------- Orders ------------------------------------------
        RowDef orders = rowDefCache.getRowDef(tableName("orders"));
        checkFields(orders, "oid", "cid", "ox");
        assertEquals(4, orders.getHKeyDepth()); // customer ordinal, cid, orders ordinal, oid
        assertArrayEquals(new int[]{1}, orders.getParentJoinFields());
        // index on oid
        index = orders.getPKIndex();
        assertNotNull(index);
        assertTrue(index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // o.oid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // o.oid
        assertEquals(1, rowComp.getFieldPosition(1)); // o.cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(3)); // index oid
        // index on cid, oid
        index = orders.getIndex("cid_oid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(1, fields[0]); // o.cid
        assertEquals(0, fields[1]); // o.oid
        rowComp = index.indexRowComposition();
        assertEquals(1, rowComp.getFieldPosition(0)); // o.cid
        assertEquals(0, rowComp.getFieldPosition(1)); // o.oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1));
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        // index on oid, cid
        index = orders.getIndex("oid_cid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // o.oid
        assertEquals(1, fields[1]); // o.cid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // o.oid
        assertEquals(1, rowComp.getFieldPosition(1)); // o.cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(3)); // index oid
        // index on cid, oid, ox
        index = orders.getIndex("cid_oid_ox");
        assertNotNull(index);
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(1, fields[0]); // o.cid
        assertEquals(0, fields[1]); // o.oid
        assertEquals(2, fields[2]); // o.ox
        rowComp = index.indexRowComposition();
        assertEquals(1, rowComp.getFieldPosition(0)); // o.cid
        assertEquals(0, rowComp.getFieldPosition(1)); // o.oid
        assertEquals(2, rowComp.getFieldPosition(2)); // o.ox
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        // ------------------------- Item ------------------------------------------
        RowDef item = rowDefCache.getRowDef(tableName("item"));
        checkFields(item, "iid", "oid", "ix");
        assertEquals(6, item.getHKeyDepth()); // customer ordinal, cid, orders ordinal, oid, item ordinal, iid
        assertArrayEquals(new int[]{1}, item.getParentJoinFields());
        // Index on iid
        index = item.getPKIndex();
        assertNotNull(index);
        assertTrue(index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // i.iid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // i.iid
        assertEquals(1, rowComp.getHKeyPosition(1)); // hkey cid
        assertEquals(1, rowComp.getFieldPosition(2)); // i.oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(5)); // index oid
        // Index on oid, iid
        index = item.getIndex("oid_iid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(1, fields[0]); // i.oid
        assertEquals(0, fields[1]); // i.iid
        rowComp = index.indexRowComposition();
        assertEquals(1, rowComp.getFieldPosition(0)); // i.oid
        assertEquals(0, rowComp.getFieldPosition(1)); // i.iid
        assertEquals(1, rowComp.getHKeyPosition(2)); // hkey cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(5)); // index iid
        // Index on iid, oid
        index = item.getIndex("iid_oid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // i.iid
        assertEquals(1, fields[1]); // i.oid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // i.iid
        assertEquals(1, rowComp.getFieldPosition(1)); // i.oid
        assertEquals(1, rowComp.getHKeyPosition(2)); // hkey cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(5)); // index iid
        // Index on oid, iid, ix
        index = item.getIndex("oid_iid_ix");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(1, fields[0]); // i.oid
        assertEquals(0, fields[1]); // i.iid
        assertEquals(2, fields[2]); // i.ix
        rowComp = index.indexRowComposition();
        assertEquals(1, rowComp.getFieldPosition(0)); // i.oid
        assertEquals(0, rowComp.getFieldPosition(1)); // i.iid
        assertEquals(2, rowComp.getFieldPosition(2)); // i.ix
        assertEquals(1, rowComp.getHKeyPosition(3)); // hkey cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(3, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(5)); // index iid
        // ------------------------- COI ------------------------------------------
        RowDef coi = rowDefCache.getRowDef(customer.getGroupRowDefId());
        checkFields(coi,
                    "customer$cid", "customer$cx",
                    "orders$oid", "orders$cid", "orders$ox",
                    "item$iid", "item$oid", "item$ix");
        assertArrayEquals(new RowDef[]{customer, orders, item}, coi.getUserTableRowDefs());
        // PK index on customer
        index = coi.getIndex("customer$PRIMARY");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // customer$cid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // customer$cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        // PK index on order
        index = coi.getIndex("orders$PRIMARY");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(2, fields[0]); // orders$oid
        rowComp = index.indexRowComposition();
        assertEquals(2, rowComp.getFieldPosition(0)); // orders$oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(3)); // index oid
        // PK index on item
        index = coi.getIndex("item$PRIMARY");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(5, fields[0]); // item$iid
        rowComp = index.indexRowComposition();
        assertEquals(5, rowComp.getFieldPosition(0)); // item$iid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(5)); // index oid
        // FK index on orders.cid
        index = coi.getIndex("orders$__akiban_oc");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(3, fields[0]); // orders$cid
        rowComp = index.indexRowComposition();
        assertEquals(3, rowComp.getFieldPosition(0)); // orders$cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        // FK index on item.oid
        index = coi.getIndex("item$__akiban_io");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(6, fields[0]); // item$oid
        rowComp = index.indexRowComposition();
        assertEquals(6, rowComp.getFieldPosition(0)); // item$oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(5)); // index iid
        // index on customer cid, cx
        index = coi.getIndex("customer$cid_cx");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // customer$cid
        assertEquals(1, fields[1]); // customer$cx
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // customer$cid
        assertEquals(1, rowComp.getFieldPosition(1)); // customer$cx
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        // index on orders cid, oid
        index = coi.getIndex("orders$cid_oid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(3, fields[0]); // orders$cid
        assertEquals(2, fields[1]); // orders$oid
        rowComp = index.indexRowComposition();
        assertEquals(3, rowComp.getFieldPosition(0)); // orders$cid
        assertEquals(2, rowComp.getFieldPosition(1)); // orders$oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        // index on orders oid, cid
        index = coi.getIndex("orders$oid_cid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(2, fields[0]); // orders$oid
        assertEquals(3, fields[1]); // orders$cid
        rowComp = index.indexRowComposition();
        assertEquals(2, rowComp.getFieldPosition(0)); // orders$oid
        assertEquals(3, rowComp.getFieldPosition(1)); // orders$cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(3)); // index oid
        // index on orders cid, oid, ox
        index = coi.getIndex("orders$cid_oid_ox");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(3, fields[0]); // orders$cid
        assertEquals(2, fields[1]); // orders$oid
        assertEquals(4, fields[2]); // orders$ox
        rowComp = index.indexRowComposition();
        assertEquals(3, rowComp.getFieldPosition(0)); // orders$cid
        assertEquals(2, rowComp.getFieldPosition(1)); // orders$oid
        assertEquals(4, rowComp.getFieldPosition(2)); // orders$ox
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        // index on item oid, iid
        index = coi.getIndex("item$oid_iid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(6, fields[0]); // item$oid
        assertEquals(5, fields[1]); // item$iid
        rowComp = index.indexRowComposition();
        assertEquals(6, rowComp.getFieldPosition(0)); // item$oid
        assertEquals(5, rowComp.getFieldPosition(1)); // item$iid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(5)); // index iid
        // index on item iid, oid
        index = coi.getIndex("item$iid_oid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(5, fields[0]); // item$iid
        assertEquals(6, fields[1]); // item$oid
        rowComp = index.indexRowComposition();
        assertEquals(5, rowComp.getFieldPosition(0)); // item$iid
        assertEquals(6, rowComp.getFieldPosition(1)); // item$oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(5)); // index iid
        // index on item oid, iid, ix
        index = coi.getIndex("item$oid_iid_ix");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(6, fields[0]); // item$oid
        assertEquals(5, fields[1]); // item$iid
        assertEquals(7, fields[2]); // item$ix
        rowComp = index.indexRowComposition();
        assertEquals(6, rowComp.getFieldPosition(0)); // item$oid
        assertEquals(5, rowComp.getFieldPosition(1)); // item$iid
        assertEquals(7, rowComp.getFieldPosition(2)); // item$ix
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(3, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(5)); // index iid
    }

    @Test
    public void testCascadingPKs() throws Exception
    {
        String[] ddl = {
            String.format("use %s; ", SCHEMA),
            "create table customer(",
            "    cid int not null, ",
            "    cx int not null, ",
            "    primary key(cid), ",
            "    key cx(cx)",
            ") engine = akibandb; ",
            "create table orders(",
            "    cid int not null, ",
            "    oid int not null, ",
            "    ox int not null, ",
            "    primary key(cid, oid), ",
            "    key ox_cid(ox, cid), ",
            "    constraint __akiban_oc foreign key co(cid) references customer(cid)",
            ") engine = akibandb; ",
            "create table item(",
            "    cid int not null, ",
            "    oid int not null, ",
            "    iid int not null, ",
            "    ix int not null, ",
            "    primary key(cid, oid, iid), ",
            "    key ix_iid_oid_cid(ix, iid, oid, cid), ",
            "    constraint __akiban_io foreign key io(cid, oid) references orders(cid, oid)",
            ") engine = akibandb; "
        };
        RowDefCache rowDefCache = SCHEMA_FACTORY.rowDefCache(ddl);
        Index index;
        int[] fields;
        IndexRowComposition rowComp;
        IndexToHKey indexToHKey;
        // ------------------------- Customer ------------------------------------------
        RowDef customer = rowDefCache.getRowDef(tableName("customer"));
        checkFields(customer, "cid", "cx");
        assertEquals(2, customer.getHKeyDepth()); // customer ordinal, cid
        assertArrayEquals(new int[]{}, customer.getParentJoinFields());
        // index on cid
        index = customer.getPKIndex();
        assertNotNull(index);
        assertTrue(index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // c.cid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // c.cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        // index on cx
        index = customer.getIndex("cx");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(1, fields[0]); // c.cx
        rowComp = index.indexRowComposition();
        assertEquals(1, rowComp.getFieldPosition(0)); // c.cx
        assertEquals(0, rowComp.getFieldPosition(1)); // c.cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        // ------------------------- Orders ------------------------------------------
        RowDef orders = rowDefCache.getRowDef(tableName("orders"));
        checkFields(orders, "cid", "oid", "ox");
        assertEquals(4, orders.getHKeyDepth()); // customer ordinal, cid, orders ordinal, oid
        assertArrayEquals(new int[]{0}, orders.getParentJoinFields());
        // index on cid, oid
        index = orders.getPKIndex();
        assertNotNull(index);
        assertTrue(index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // o.cid
        assertEquals(1, fields[1]); // o.oid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // o.cid
        assertEquals(1, rowComp.getFieldPosition(1)); // o.oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        // index on ox, cid
        index = orders.getIndex("ox_cid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(2, fields[0]); // o.ox
        assertEquals(0, fields[1]); // o.cid
        rowComp = index.indexRowComposition();
        assertEquals(2, rowComp.getFieldPosition(0)); // o.ox
        assertEquals(0, rowComp.getFieldPosition(1)); // o.cid
        assertEquals(1, rowComp.getFieldPosition(2)); // o.oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(3)); // index oid
        // ------------------------- Item ------------------------------------------
        RowDef item = rowDefCache.getRowDef(tableName("item"));
        checkFields(item, "cid", "oid", "iid", "ix");
        assertEquals(6, item.getHKeyDepth()); // customer ordinal, cid, orders ordinal, oid, item ordinal iid
        assertArrayEquals(new int[]{0, 1}, item.getParentJoinFields());
        // index on cid, oid, iid
        index = item.getPKIndex();
        assertNotNull(index);
        assertTrue(index.isPrimaryKey());
        assertTrue(index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // i.cid
        assertEquals(1, fields[1]); // i.oid
        assertEquals(2, fields[2]); // i.iid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // i.cid
        assertEquals(1, rowComp.getFieldPosition(1)); // i.oid
        assertEquals(2, rowComp.getFieldPosition(2)); // i.iid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(5)); // index oid
        // index on ix, iid, oid, cid
        index = item.getIndex("ix_iid_oid_cid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(3, fields[0]); // i.ix
        assertEquals(2, fields[1]); // i.iid
        assertEquals(1, fields[2]); // i.oid
        assertEquals(0, fields[3]); // i.cid
        rowComp = index.indexRowComposition();
        assertEquals(3, rowComp.getFieldPosition(0)); // i.ix
        assertEquals(2, rowComp.getFieldPosition(1)); // i.iid
        assertEquals(1, rowComp.getFieldPosition(2)); // i.oid
        assertEquals(0, rowComp.getFieldPosition(3)); // i.cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(3, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(5)); // index oid
        // ------------------------- COI ------------------------------------------
        RowDef coi = rowDefCache.getRowDef(customer.getGroupRowDefId());
        checkFields(coi,
                    "customer$cid", "customer$cx",
                    "orders$cid", "orders$oid", "orders$ox",
                    "item$cid", "item$oid", "item$iid", "item$ix");
        assertArrayEquals(new RowDef[]{customer, orders, item}, coi.getUserTableRowDefs());
        // customer PK index
        index = coi.getIndex("customer$PRIMARY");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(0, fields[0]); // customer$cid
        rowComp = index.indexRowComposition();
        assertEquals(0, rowComp.getFieldPosition(0)); // customer$cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        // orders PK index
        index = coi.getIndex("orders$PRIMARY");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(2, fields[0]); // orders$cid
        assertEquals(3, fields[1]); // orders$oid
        rowComp = index.indexRowComposition();
        assertEquals(2, rowComp.getFieldPosition(0)); // orders$cid
        assertEquals(3, rowComp.getFieldPosition(1)); // orders$oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        // item PK index
        index = coi.getIndex("item$PRIMARY");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(5, fields[0]); // item$cid
        assertEquals(6, fields[1]); // item$oid
        assertEquals(7, fields[2]); // item$iid
        rowComp = index.indexRowComposition();
        assertEquals(5, rowComp.getFieldPosition(0)); // item$cid
        assertEquals(6, rowComp.getFieldPosition(1)); // item$oid
        assertEquals(7, rowComp.getFieldPosition(2)); // item$oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(5)); // index iid
        // orders FK index
        index = coi.getIndex("orders$__akiban_oc");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(2, fields[0]); // orders$cid
        rowComp = index.indexRowComposition();
        assertEquals(2, rowComp.getFieldPosition(0)); // orders$cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        // item FK index
        index = coi.getIndex("item$__akiban_io");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(5, fields[0]); // item$cid
        assertEquals(6, fields[1]); // item$oid
        rowComp = index.indexRowComposition();
        assertEquals(5, rowComp.getFieldPosition(0)); // item$cid
        assertEquals(6, rowComp.getFieldPosition(1)); // item$oid
        assertEquals(7, rowComp.getFieldPosition(2)); // item$oid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(0, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(5)); // index iid
        // customer cx
        index = coi.getIndex("customer$cx");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(1, fields[0]); // customer$cx
        rowComp = index.indexRowComposition();
        assertEquals(1, rowComp.getFieldPosition(0)); // customer$cx
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        // orders ox, cid
        index = coi.getIndex("orders$ox_cid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(4, fields[0]); // orders$ox
        assertEquals(2, fields[1]); // orders$cid
        rowComp = index.indexRowComposition();
        assertEquals(4, rowComp.getFieldPosition(0)); // orders$ox
        assertEquals(2, rowComp.getFieldPosition(1)); // orders$cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(3)); // index oid
        // item ix, iid, oid, cid
        index = coi.getIndex("item$ix_iid_oid_cid");
        assertNotNull(index);
        assertTrue(!index.isPrimaryKey());
        assertTrue(!index.isUnique());
        // assertTrue(!index.isHKeyEquivalent());
        fields = indexFields(index);
        assertEquals(8, fields[0]); // item$ix
        assertEquals(7, fields[1]); // item$iid
        assertEquals(6, fields[2]); // item$oid
        assertEquals(5, fields[3]); // item$cid
        rowComp = index.indexRowComposition();
        assertEquals(8, rowComp.getFieldPosition(0)); // item$ix
        assertEquals(7, rowComp.getFieldPosition(1)); // item$iid
        assertEquals(6, rowComp.getFieldPosition(2)); // item$oid
        assertEquals(5, rowComp.getFieldPosition(3)); // item$cid
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(3, indexToHKey.getIndexRowPosition(1)); // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2)); // o ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(3)); // index oid
        assertEquals(item.getOrdinal(), indexToHKey.getOrdinal(4)); // i ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(5)); // index iid
    }

    // PersistitStoreIndexManager.analyzeIndex relies on IndexDef.I2H.fieldIndex, but only for hkey equivalent
    // indexes. Given the original computation of index hkey equivalence, that means root tables and group table
    // indexes on root PK fields.
    @Test
    public void checkI2HFieldIndex() throws Exception
    {
        String[] ddl = {
            String.format("use `%s`;", SCHEMA),
            "create table parent (",
            "   a int,",
            "   b int,",
            "   x int,",
            "   primary key(b, a)",
            ") engine = akibandb;",
            "create table child (",
            "   c int,",
            "   d int,",
            "   b int,",
            "   a int,",
            "   x int,",
            "   primary key(c, d),",
            "   constraint `__akiban_fk0` foreign key `akibanfk` (b, a) references parent(b, a)",
            ") engine = akibandb;"
        };
        RowDefCache rowDefCache = SCHEMA_FACTORY.rowDefCache(ddl);
        RowDef parent = rowDefCache.getRowDef(tableName("parent"));
        RowDef child = rowDefCache.getRowDef(tableName("child"));
        RowDef group = rowDefCache.getRowDef(parent.getGroupRowDefId());
        assertSame(group, rowDefCache.getRowDef(child.getGroupRowDefId()));
        // assertTrue(index(parent, "b", "a").isHKeyEquivalent());
        // assertTrue(index(child, "b", "a").isHKeyEquivalent()); 
        // assertTrue(index(group, "parent$b", "parent$a").isHKeyEquivalent());
        // assertTrue(!index(group, "child$c", "child$d").isHKeyEquivalent());
        // assertTrue(index(group, "child$b", "child$a").isHKeyEquivalent()); 
        Index index;
        IndexToHKey indexToHKey;
        // parent (b, a) index
        index = parent.getPKIndex();
        indexToHKey = index.indexToHKey();
        assertEquals(parent.getOrdinal(), indexToHKey.getOrdinal(0));
        assertEquals(1, indexToHKey.getFieldPosition(1));
        assertEquals(0, indexToHKey.getFieldPosition(2));
        // group (parent$b, parent$a) index
        index = group.getIndex("parent$PRIMARY");
        indexToHKey = index.indexToHKey();
        assertEquals(parent.getOrdinal(), indexToHKey.getOrdinal(0));
        assertEquals(1, indexToHKey.getFieldPosition(1));
        assertEquals(0, indexToHKey.getFieldPosition(2));
        // The remaining tests are for indexes that are hkey-equivalent only under the new computation of IndexDef
        // field associations.
        // child (b, a) index
        index = child.getIndex("__akiban_fk0");
        indexToHKey = index.indexToHKey();
        assertEquals(parent.getOrdinal(), indexToHKey.getOrdinal(0));
        assertEquals(2, indexToHKey.getFieldPosition(1));
        assertEquals(3, indexToHKey.getFieldPosition(2));
        // group (child$b, child$a) index
        index = group.getIndex("child$__akiban_fk0");
        indexToHKey = index.indexToHKey();
        assertEquals(parent.getOrdinal(), indexToHKey.getOrdinal(0));
        assertEquals(5, indexToHKey.getFieldPosition(1));
        assertEquals(6, indexToHKey.getFieldPosition(2));
    }

    @Test
    public void checkSingleTableGroupIndex() throws Exception {
        String[] ddl = { "use "+SCHEMA+";",
                         "create table customer(cid int, name varchar(32), primary key(cid)) engine=akibandb;"
        };

        final RowDefCache rowDefCache;
        {
            AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
            Table customerTable = ais.getTable(SCHEMA, "customer");
            GroupIndex index = GroupIndex.create(ais, customerTable.getGroup(), "cName", 100, false, Index.KEY_CONSTRAINT, null);
            index.addColumn(new IndexColumn(index, customerTable.getColumn("name"), 0,true, null));
            rowDefCache = SCHEMA_FACTORY.rowDefCache(ais);
        }

        Index index;
        IndexRowComposition rowComp;
        IndexToHKey indexToHKey;

        RowDef customer = rowDefCache.getRowDef(tableName("customer"));
        RowDef customer_group = rowDefCache.getRowDef(customer.getGroupRowDefId());
        // group index on name
        index = customer_group.getGroupIndex("cName");
        assertNotNull(index);
        assertFalse(index.isPrimaryKey());
        assertFalse(index.isUnique());
        rowComp = index.indexRowComposition();
        assertEquals(1, rowComp.getFieldPosition(0)); // c.name
        // customer hkey
        assertEquals(0, rowComp.getFieldPosition(1)); // c.cid
        assertEquals(2, rowComp.getLength());
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(1, indexToHKey.getIndexRowPosition(1)); // index cid
    }


    @Test
    public void checkCOGroupIndex() throws Exception {
        String[] ddl = { "use "+SCHEMA+";",
                         "create table customer(cid int, name varchar(32), primary key(cid)) engine=akibandb;",
                         "create table orders(oid int, cid int, date date, primary key(oid), "+
                                 "constraint __akiban foreign key(cid) references customer(cid)) engine=akibandb;"
        };

        final RowDefCache rowDefCache;
        {
            AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
            Table customerTable = ais.getTable(SCHEMA, "customer");
            Table ordersTable = ais.getTable(SCHEMA, "orders");
            GroupIndex index = GroupIndex.create(ais, customerTable.getGroup(), "cName_oDate", 100, false, Index.KEY_CONSTRAINT, null);
            index.addColumn(new IndexColumn(index, customerTable.getColumn("name"), 0, true, null));
            index.addColumn(new IndexColumn(index, ordersTable.getColumn("date"), 1, true, null));
            rowDefCache = SCHEMA_FACTORY.rowDefCache(ais);
        }

        Index index;
        IndexRowComposition rowComp;
        IndexToHKey indexToHKey;

        RowDef customer = rowDefCache.getRowDef(tableName("customer"));
        RowDef orders = rowDefCache.getRowDef(tableName("orders"));
        assertEquals(customer.getGroupRowDefId(), orders.getGroupRowDefId());
        RowDef customer_group = rowDefCache.getRowDef(customer.getGroupRowDefId());
        // group index on c.name,o.date
        index = customer_group.getGroupIndex("cName_oDate");
        assertNotNull(index);
        assertFalse(index.isPrimaryKey());
        assertFalse(index.isUnique());
        rowComp = index.indexRowComposition();
        // Flattened: (c.cid, c.name, o.oid, o.cid, o.date)
        assertEquals(1, rowComp.getFieldPosition(0)); // c.name
        assertEquals(4, rowComp.getFieldPosition(1)); // o.date
        // order hkey
        assertEquals(0, rowComp.getFieldPosition(2)); // c.cid
        assertEquals(2, rowComp.getFieldPosition(3)); // o.oid
        assertEquals(4, rowComp.getLength());
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(1));            // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2));   // o ordinal
        assertEquals(3, indexToHKey.getIndexRowPosition(3));            // index oid
    }

    @Test
    public void checkCOIGroupIndex() throws Exception {
        String[] ddl = { "use "+SCHEMA+";",
                         "create table customer(cid int, name varchar(32), primary key(cid)) engine=akibandb;",
                         "create table orders(oid int, cid int, date date, primary key(oid), "+
                                 "constraint __akiban foreign key(cid) references customer(cid)) engine=akibandb;",
                         "create table items(iid int, oid int, sku int, primary key(iid), "+
                                 "constraint __akiban foreign key(oid) references orders(oid)) engine=akibandb;"
        };

        final RowDefCache rowDefCache;
        {
            AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
            Table customerTable = ais.getTable(SCHEMA, "customer");
            Table ordersTable = ais.getTable(SCHEMA, "orders");
            Table itemsTable = ais.getTable(SCHEMA, "items");
            GroupIndex index = GroupIndex.create(ais, customerTable.getGroup(), "cName_oDate_iSku", 100, false, Index.KEY_CONSTRAINT, null);
            index.addColumn(new IndexColumn(index, customerTable.getColumn("name"), 0, true, null));
            index.addColumn(new IndexColumn(index, ordersTable.getColumn("date"), 1, true, null));
            index.addColumn(new IndexColumn(index, itemsTable.getColumn("sku"), 2, true, null));
            rowDefCache = SCHEMA_FACTORY.rowDefCache(ais);
        }

        Index index;
        IndexRowComposition rowComp;
        IndexToHKey indexToHKey;

        RowDef customer = rowDefCache.getRowDef(tableName("customer"));
        RowDef orders = rowDefCache.getRowDef(tableName("orders"));
        assertEquals(customer.getGroupRowDefId(), orders.getGroupRowDefId());
        RowDef items = rowDefCache.getRowDef(tableName("items"));
        assertEquals(orders.getGroupRowDefId(), items.getGroupRowDefId());
        RowDef customer_group = rowDefCache.getRowDef(customer.getGroupRowDefId());
        // group index on c.name,o.date,i.sku
        index = customer_group.getGroupIndex("cName_oDate_iSku");
        assertNotNull(index);
        assertFalse(index.isPrimaryKey());
        assertFalse(index.isUnique());
        rowComp = index.indexRowComposition();
        // Flattened: (c.cid, c.name, o.oid, o.cid, o.date, i.iid, i.oid, i.sku)
        assertEquals(1, rowComp.getFieldPosition(0)); // c.name
        assertEquals(4, rowComp.getFieldPosition(1)); // o.date
        assertEquals(7, rowComp.getFieldPosition(2)); // i.sku
        // item hkey
        assertEquals(0, rowComp.getFieldPosition(3)); // c.cid
        assertEquals(2, rowComp.getFieldPosition(4)); // i.oid
        assertEquals(5, rowComp.getFieldPosition(5)); // i.iid
        assertEquals(6, rowComp.getLength());
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(3, indexToHKey.getIndexRowPosition(1));            // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2));   // o ordinal
        assertEquals(4, indexToHKey.getIndexRowPosition(3));            // index oid
        assertEquals(items.getOrdinal(), indexToHKey.getOrdinal(4));    // i ordinal
        assertEquals(5, indexToHKey.getIndexRowPosition(5));            // index iid
    }

    @Test
    public void checkOIGroupIndex() throws Exception {
        String[] ddl = { "use "+SCHEMA+";",
                         "create table customer(cid int, name varchar(32), primary key(cid)) engine=akibandb;",
                         "create table orders(oid int, cid int, date date, primary key(oid), "+
                                 "constraint __akiban foreign key(cid) references customer(cid)) engine=akibandb;",
                         "create table items(iid int, oid int, sku int, primary key(iid), "+
                                 "constraint __akiban foreign key(oid) references orders(oid)) engine=akibandb;"
        };

        final RowDefCache rowDefCache;
        {
            AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
            Table customerTable = ais.getTable(SCHEMA, "customer");
            Table ordersTable = ais.getTable(SCHEMA, "orders");
            Table itemsTable = ais.getTable(SCHEMA, "items");
            GroupIndex index = GroupIndex.create(ais, customerTable.getGroup(), "oDate_iSku", 100, false, Index.KEY_CONSTRAINT, null);
            index.addColumn(new IndexColumn(index, ordersTable.getColumn("date"), 0, true, null));
            index.addColumn(new IndexColumn(index, itemsTable.getColumn("sku"), 1, true, null));
            rowDefCache = SCHEMA_FACTORY.rowDefCache(ais);
        }

        Index index;
        IndexRowComposition rowComp;
        IndexToHKey indexToHKey;

        RowDef customer = rowDefCache.getRowDef(tableName("customer"));
        RowDef orders = rowDefCache.getRowDef(tableName("orders"));
        assertEquals(customer.getGroupRowDefId(), orders.getGroupRowDefId());
        RowDef items = rowDefCache.getRowDef(tableName("items"));
        assertEquals(orders.getGroupRowDefId(), items.getGroupRowDefId());
        RowDef customer_group = rowDefCache.getRowDef(customer.getGroupRowDefId());
        // group index on o.oid,i.sku
        index = customer_group.getGroupIndex("oDate_iSku");
        assertNotNull(index);
        assertFalse(index.isPrimaryKey());
        assertFalse(index.isUnique());
        rowComp = index.indexRowComposition();
        // Flattened: (c.cid, c.name, o.oid, o.cid, o.date, i.iid, i.oid, i.sku)
        assertEquals(4, rowComp.getFieldPosition(0)); // o.date
        assertEquals(7, rowComp.getFieldPosition(1)); // i.sku
        // item hkey
        assertEquals(3, rowComp.getFieldPosition(2)); // o.cid
        assertEquals(2, rowComp.getFieldPosition(3)); // o.oid
        assertEquals(5, rowComp.getFieldPosition(4)); // i.iid
        assertEquals(5, rowComp.getLength());
        indexToHKey = index.indexToHKey();
        assertEquals(customer.getOrdinal(), indexToHKey.getOrdinal(0)); // c ordinal
        assertEquals(2, indexToHKey.getIndexRowPosition(1));            // index cid
        assertEquals(orders.getOrdinal(), indexToHKey.getOrdinal(2));   // o ordinal
        assertEquals(3, indexToHKey.getIndexRowPosition(3));            // index oid
        assertEquals(items.getOrdinal(), indexToHKey.getOrdinal(4));    // i ordinal
        assertEquals(4, indexToHKey.getIndexRowPosition(5));            // index iid
    }

    private void checkFields(RowDef rowdef, String... expectedFields)
    {
        FieldDef[] fields = rowdef.getFieldDefs();
        Assert.assertEquals(expectedFields.length, fields.length);
        for (int i = 0; i < fields.length; i++) {
            assertEquals(expectedFields[i], fields[i].getName());
        }
    }

    private TableName tableName(String name)
    {
        return RowDefCache.nameOf(SCHEMA, name);
    }

    private void checkField(String name, RowDef rowDef, int fieldNumber)
    {
        FieldDef field = rowDef.getFieldDefs()[fieldNumber];
        assertEquals(name, field.getName());
    }

    // Copied from AISTest
    private void checkHKey(HKey hKey, Object... elements)
    {
        int e = 0;
        int position = 0;
        for (HKeySegment segment : hKey.segments()) {
            Assert.assertEquals(position++, segment.positionInHKey());
            assertSame(elements[e++], segment.table());
            for (HKeyColumn column : segment.columns()) {
                Assert.assertEquals(position++, column.positionInHKey());
                Assert.assertEquals(elements[e++], column.column().getTable());
                Assert.assertEquals(elements[e++], column.column().getName());
            }
        }
        Assert.assertEquals(elements.length, e);
    }

    private int[] indexFields(Index index) {
        return ((IndexDef)index.indexDef()).getFields();
    }

    private static final String SCHEMA = "schema";
    private static final SchemaFactory SCHEMA_FACTORY = new SchemaFactory();
}

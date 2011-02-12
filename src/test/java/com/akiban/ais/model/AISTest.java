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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.akiban.server.SchemaFactory;

public class AISTest
{
    @Test
    public void testTableColumnsReturnedInOrder() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.t(",
            "    col2 int not null, ",
            "    col1 int not null, ",
            "    col0 int not null ",
            ") engine = akibandb;"
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        UserTable table = ais.getUserTable("s", "t");
        int expectedPosition = 0;
        for (Column column : table.getColumns()) {
            assertEquals(expectedPosition, column.getPosition().intValue());
            expectedPosition++;
        }
    }

    @Test
    public void testIndexColumnsReturnedInOrder() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.t(",
            "    col0 int not null, ",
            "    col1 int not null, ",
            "    col2 int not null, ",
            "    col3 int not null, ",
            "    col4 int not null, ",
            "    col5 int not null, ",
            "    key i(col5, col4, col3) ",
            ") engine = akibandb;"
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        UserTable table = ais.getUserTable("s", "t");
        Index index = table.getIndex("i");
        Iterator<IndexColumn> indexColumnScan = index.getColumns().iterator();
        IndexColumn indexColumn = indexColumnScan.next();
        assertEquals(5, indexColumn.getColumn().getPosition().intValue());
        assertEquals(0, indexColumn.getPosition().intValue());
        indexColumn = indexColumnScan.next();
        assertEquals(4, indexColumn.getColumn().getPosition().intValue());
        assertEquals(1, indexColumn.getPosition().intValue());
        indexColumn = indexColumnScan.next();
        assertEquals(3, indexColumn.getColumn().getPosition().intValue());
        assertEquals(2, indexColumn.getPosition().intValue());
        assertTrue(!indexColumnScan.hasNext());
    }

    @Test
    public void testPKColumnsReturnedInOrder() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.t(",
            "    col0 int not null, ",
            "    col1 int not null, ",
            "    col2 int not null, ",
            "    col3 int not null, ",
            "    col4 int not null, ",
            "    col5 int not null, ",
            "    primary key (col5, col4, col3) ",
            ") engine = akibandb;"
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        UserTable table = ais.getUserTable("s", "t");
        PrimaryKey pk = table.getPrimaryKey();
        Iterator<Column> indexColumnScan = pk.getColumns().iterator();
        Column pkColumn = indexColumnScan.next();
        assertEquals(5, pkColumn.getPosition().intValue());
        pkColumn = indexColumnScan.next();
        assertEquals(4, pkColumn.getPosition().intValue());
        pkColumn = indexColumnScan.next();
        assertEquals(3, pkColumn.getPosition().intValue());
        assertTrue(!indexColumnScan.hasNext());
    }

    @Test
    public void testJoinColumnsReturnedInOrder() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.parent(",
            "    p0 int not null, ",
            "    p1 int not null, ",
            "    primary key (p1, p0) ",
            ") engine = akibandb;",
            "create table s.child(",
            "    c0 int not null, ",
            "    c1 int not null, ",
            "    primary key (c0, c1), ",
            "   constraint `__akiban_fk` foreign key (c0, c1) references parent(p1, p0)",
            ") engine = akibandb;",
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        Join join = ais.getUserTable("s", "child").getParentJoin();
        Iterator<JoinColumn> joinColumns = join.getJoinColumns().iterator();
        JoinColumn joinColumn = joinColumns.next();
        assertEquals("p1", joinColumn.getParent().getName());
        assertEquals("c0", joinColumn.getChild().getName());
        joinColumn = joinColumns.next();
        assertEquals("p0", joinColumn.getParent().getName());
        assertEquals("c1", joinColumn.getChild().getName());
        assertTrue(!joinColumns.hasNext());
    }

    @Test
    public void testHKeyNonCascadingPKs() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.customer(",
            "    cid int not null, ",
            "    primary key (cid) ",
            ") engine = akibandb;",
            "create table s.`order`(",
            "    oid int not null, ",
            "    cid int not null, ",
            "    primary key (oid), ",
            "   constraint `__akiban_fk_oc` foreign key (cid) references customer(cid)",
            ") engine = akibandb;",
            "create table s.item(",
            "    iid int not null, ",
            "    oid int not null, ",
            "    primary key (iid), ",
            "   constraint `__akiban_fk_io` foreign key (oid) references `order`(oid)",
            ") engine = akibandb;",
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        // ---------------- Customer -------------------------------------
        UserTable customer = ais.getUserTable("s", "customer");
        GroupTable coi = customer.getGroup().getGroupTable();
        checkHKey(customer.hKey(),
                  customer, customer, "cid");
        // ---------------- Order -------------------------------------
        UserTable order = ais.getUserTable("s", "order");
        assertSame(coi, order.getGroup().getGroupTable());
        checkHKey(order.hKey(),
                  customer, order, "cid",
                  order, order, "oid");
        // ---------------- Item -------------------------------------
        UserTable item = ais.getUserTable("s", "item");
        assertSame(coi, item.getGroup().getGroupTable());
        checkHKey(item.hKey(),
                  customer, order, "cid",
                  order, item, "oid",
                  item, item, "iid");
        // ---------------- Branch hkeys -------------------------------------
        // customer
        checkBranchHKeyColumn(customer.branchHKey(), coi,
                              customer, "customer$cid", "order$cid");
        // order
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              customer, "order$cid", "customer$cid");
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              order, "order$oid", "item$oid");
        // item
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              customer, "order$cid", "customer$cid");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              order, "item$oid", "order$oid");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              item, "item$iid");
    }

    @Test
    public void testHKeyNonCascadingMultiColumnPKs() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.customer(",
            "    cid0 int not null, ",
            "    cid1 int not null, ",
            "    primary key (cid0, cid1) ",
            ") engine = akibandb;",
            "create table s.`order`(",
            "    oid0 int not null, ",
            "    oid1 int not null, ",
            "    cid0 int not null, ",
            "    cid1 int not null, ",
            "    primary key (oid0, oid1), ",
            "   constraint `__akiban_fk_oc` foreign key (cid0, cid1) references customer(cid0, cid1)",
            ") engine = akibandb;",
            "create table s.item(",
            "    iid0 int not null, ",
            "    iid1 int not null, ",
            "    oid0 int not null, ",
            "    oid1 int not null, ",
            "    primary key (iid0, iid1), ",
            "   constraint `__akiban_fk_io` foreign key (oid0, oid1) references `order`(oid0, oid1)",
            ") engine = akibandb;",
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        // ---------------- Customer -------------------------------------
        UserTable customer = ais.getUserTable("s", "customer");
        GroupTable coi = customer.getGroup().getGroupTable();
        checkHKey(customer.hKey(),
                  customer, customer, "cid0", customer, "cid1");
        // ---------------- Order -------------------------------------
        UserTable order = ais.getUserTable("s", "order");
        assertSame(coi, order.getGroup().getGroupTable());
        checkHKey(order.hKey(),
                  customer, order, "cid0", order, "cid1",
                  order, order, "oid0", order, "oid1");
        // ---------------- Item -------------------------------------
        UserTable item = ais.getUserTable("s", "item");
        assertSame(coi, item.getGroup().getGroupTable());
        checkHKey(item.hKey(),
                  customer, order, "cid0", order, "cid1",
                  order, item, "oid0", item, "oid1",
                  item, item, "iid0", item, "iid1");
        // ---------------- Branch hkeys -------------------------------------
        // customer
        checkBranchHKeyColumn(customer.branchHKey(), coi,
                              customer, "customer$cid0", "order$cid0");
        checkBranchHKeyColumn(customer.branchHKey(), coi,
                              customer, "customer$cid1", "order$cid1");
        // order
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              customer, "order$cid0", "customer$cid0");
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              customer, "order$cid1", "customer$cid1");
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              order, "order$oid0", "item$oid0");
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              order, "order$oid1", "item$oid1");
        // item
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              customer, "order$cid0", "customer$cid0");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              customer, "order$cid1", "customer$cid1");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              order, "item$oid0", "order$oid0");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              order, "item$oid1", "order$oid1");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              item, "item$iid0");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              item, "item$iid1");
    }

    @Test
    public void testHKeyCascadingPKs() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.customer(",
            "    cid int not null, ",
            "    primary key (cid) ",
            ") engine = akibandb;",
            "create table s.`order`(",
            "    cid int not null, ",
            "    oid int not null, ",
            "    primary key (cid, oid), ",
            "   constraint `__akiban_fk_oc` foreign key (cid) references customer(cid)",
            ") engine = akibandb;",
            "create table s.item(",
            "    cid int not null, ",
            "    oid int not null, ",
            "    iid int not null, ",
            "    primary key (cid, oid, iid), ",
            "   constraint `__akiban_fk_io` foreign key (cid, oid) references `order`(cid, oid)",
            ") engine = akibandb;",
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        // ---------------- Customer -------------------------------------
        UserTable customer = ais.getUserTable("s", "customer");
        GroupTable coi = customer.getGroup().getGroupTable();
        checkHKey(customer.hKey(),
                  customer, customer, "cid");
        // ---------------- Order -------------------------------------
        UserTable order = ais.getUserTable("s", "order");
        assertSame(coi, order.getGroup().getGroupTable());
        checkHKey(order.hKey(),
                  customer, order, "cid",
                  order, order, "oid");
        // ---------------- Item -------------------------------------
        UserTable item = ais.getUserTable("s", "item");
        assertSame(coi, item.getGroup().getGroupTable());
        checkHKey(item.hKey(),
                  customer, item, "cid",
                  order, item, "oid",
                  item, item, "iid");
        // ---------------- Branch hkeys -------------------------------------
        // customer
        checkBranchHKeyColumn(customer.branchHKey(), coi,
                              customer, "customer$cid", "order$cid", "item$cid");
        // order
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              customer, "order$cid", "customer$cid", "item$cid");
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              order, "order$oid", "item$oid");
        // item
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              customer, "item$cid", "customer$cid", "order$cid");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              order, "item$oid", "order$oid");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              item, "item$iid");
    }

    @Test
    public void testHKeyCascadingMultiColumnPKs() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.customer(",
            "    cid0 int not null, ",
            "    cid1 int not null, ",
            "    primary key (cid0, cid1) ",
            ") engine = akibandb;",
            "create table s.`order`(",
            "    cid0 int not null, ",
            "    cid1 int not null, ",
            "    oid0 int not null, ",
            "    oid1 int not null, ",
            "    primary key (cid0, cid1, oid0, oid1), ",
            "   constraint `__akiban_fk_oc` foreign key (cid0, cid1) references customer(cid0, cid1)",
            ") engine = akibandb;",
            "create table s.item(",
            "    cid0 int not null, ",
            "    cid1 int not null, ",
            "    oid0 int not null, ",
            "    oid1 int not null, ",
            "    iid0 int not null, ",
            "    iid1 int not null, ",
            "    primary key (cid0, cid1, oid0, oid1, iid0, iid1), ",
            "   constraint `__akiban_fk_io` foreign key (cid0, cid1, oid0, oid1) references `order`(cid0, cid1, oid0, oid1)",
            ") engine = akibandb;",
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        // ---------------- Customer -------------------------------------
        UserTable customer = ais.getUserTable("s", "customer");
        GroupTable coi = customer.getGroup().getGroupTable();
        checkHKey(customer.hKey(),
                  customer, customer, "cid0", customer, "cid1");
        // ---------------- Order -------------------------------------
        UserTable order = ais.getUserTable("s", "order");
        assertSame(coi, order.getGroup().getGroupTable());
        checkHKey(order.hKey(),
                  customer, order, "cid0", order, "cid1",
                  order, order, "oid0", order, "oid1");
        // ---------------- Item -------------------------------------
        UserTable item = ais.getUserTable("s", "item");
        assertSame(coi, item.getGroup().getGroupTable());
        checkHKey(item.hKey(),
                  customer, item, "cid0", item, "cid1",
                  order, item, "oid0", item, "oid1",
                  item, item, "iid0", item, "iid1");
        // ---------------- Branch hkeys -------------------------------------
        // customer
        checkBranchHKeyColumn(customer.branchHKey(), coi,
                              customer, "customer$cid0", "order$cid0", "item$cid0");
        checkBranchHKeyColumn(customer.branchHKey(), coi,
                              customer, "customer$cid1", "order$cid1", "item$cid1");
        // order
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              customer, "order$cid0", "customer$cid0", "item$cid0");
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              customer, "order$cid1", "customer$cid1", "item$cid1");
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              order, "order$oid0", "item$oid0");
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              order, "order$oid1", "item$oid1");
        // item
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              customer, "item$cid0", "customer$cid0", "order$cid0");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              customer, "item$cid1", "customer$cid1", "order$cid1");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              order, "item$oid0", "order$oid0");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              order, "item$oid1", "order$oid1");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              item, "item$iid0");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              item, "item$iid1");
    }

    @Test
    public void testHKeyWithBranches() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.customer(",
            "    cid int not null, ",
            "    primary key (cid) ",
            ") engine = akibandb;",
            "create table s.`order`(",
            "    oid int not null, ",
            "    cid int not null, ",
            "    primary key (oid), ",
            "   constraint `__akiban_fk_oc` foreign key (cid) references customer(cid)",
            ") engine = akibandb;",
            "create table s.item(",
            "    iid int not null, ",
            "    oid int not null, ",
            "    primary key (iid), ",
            "   constraint `__akiban_fk_io` foreign key (oid) references `order`(oid)",
            ") engine = akibandb;",
            "create table s.address(",
            "    aid int not null, ",
            "    cid int not null, ",
            "    primary key (aid), ",
            "   constraint `__akiban_fk_ac` foreign key (cid) references customer(cid)",
            ") engine = akibandb;",
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        // ---------------- Customer -------------------------------------
        UserTable customer = ais.getUserTable("s", "customer");
        GroupTable coi = customer.getGroup().getGroupTable();
        checkHKey(customer.hKey(),
                  customer, customer, "cid");
        // ---------------- Order -------------------------------------
        UserTable order = ais.getUserTable("s", "order");
        assertSame(coi, order.getGroup().getGroupTable());
        checkHKey(order.hKey(),
                  customer, order, "cid",
                  order, order, "oid");
        // ---------------- Item -------------------------------------
        UserTable item = ais.getUserTable("s", "item");
        assertSame(coi, item.getGroup().getGroupTable());
        checkHKey(item.hKey(),
                  customer, order, "cid",
                  order, item, "oid",
                  item, item, "iid");
        // ---------------- Address -------------------------------------
        UserTable address = ais.getUserTable("s", "address");
        assertSame(coi, address.getGroup().getGroupTable());
        checkHKey(address.hKey(),
                  customer, address, "cid",
                  address, address, "aid");
        // ---------------- Branch hkeys -------------------------------------
        // customer
        checkBranchHKeyColumn(customer.branchHKey(), coi,
                              customer, "customer$cid", "order$cid", "address$cid");
        // order
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              customer, "order$cid", "customer$cid");
        checkBranchHKeyColumn(order.branchHKey(), coi,
                              order, "order$oid", "item$oid");
        // item
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              customer, "order$cid", "customer$cid");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              order, "item$oid", "order$oid");
        checkBranchHKeyColumn(item.branchHKey(), coi,
                              item, "item$iid");
        // address
        checkBranchHKeyColumn(address.branchHKey(), coi,
                              customer, "address$cid", "customer$cid");
        checkBranchHKeyColumn(address.branchHKey(), coi,
                              address, "address$aid");
    }

    @Test
    public void testAkibanPKColumn() throws Exception
    {
        String[] ddl = {
            "use s; ",
            "create table s.t(",
            "    a int, ",
            "    b int",
            ") engine = akibandb;"
        };
        AkibanInformationSchema ais = SCHEMA_FACTORY.ais(ddl);
        UserTable table = (UserTable) ais.getTable("s", "t");
        // check columns
        checkColumns(table.getColumns(), "a", "b");
        checkColumns(table.getColumnsIncludingInternal(), "a", "b", Column.AKIBAN_PK_NAME);
        // check indexes
        assertTrue(table.getIndexes().isEmpty());
        assertEquals(1, table.getIndexesIncludingInternal().size());
        Index index = table.getIndexesIncludingInternal().iterator().next();
        assertEquals(Column.AKIBAN_PK_NAME, index.getColumns().get(0).getColumn().getName());
        // check PK
        assertNull(table.getPrimaryKey());
        assertSame(table.getIndexesIncludingInternal().iterator().next(), table.getPrimaryKeyIncludingInternal().getIndex());
    }

    private void checkHKey(HKey hKey, Object ... elements)
    {
        int e = 0;
        int position = 0;
        for (HKeySegment segment : hKey.segments()) {
            assertEquals(position++, segment.positionInHKey());
            assertSame(elements[e++], segment.table());
            for (HKeyColumn column : segment.columns()) {
                assertEquals(position++, column.positionInHKey());
                assertEquals(elements[e++], column.column().getTable());
                assertEquals(elements[e++], column.column().getName());
            }
        }
        assertEquals(elements.length, e);
    }

    private void checkBranchHKeyColumn(HKey hKey, GroupTable groupTable,
                                       UserTable segmentUserTable,
                                       String columnName,
                                       Object ... matches)
    {
        HKeySegment segment = null;
        for (HKeySegment s : hKey.segments()) {
            if (s.table() == segmentUserTable) {
                segment = s;
            }
        }
        assertNotNull(segment);
        HKeyColumn column = null;
        for (HKeyColumn c : segment.columns()) {
            if (c.column().getName().equals(columnName)) {
                column = c;
            }
        }
        assertNotNull(column);
        assertNotNull(column.equivalentColumns());
        Set<String> expected = new HashSet<String>();
        for (Column equivalentColumn : column.equivalentColumns()) {
            assertSame(groupTable, equivalentColumn.getTable());
            expected.add(equivalentColumn.getName());
        }
        Set<String> actual = new HashSet<String>();
        actual.add(columnName);
        for (Object m : matches) {
            actual.add((String) m);
        }
        assertEquals(expected, actual);
    }

    private void checkColumns(List<Column> actual, String ... expected)
    {
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual.get(i).getName());
        }
    }

    private static final SchemaFactory SCHEMA_FACTORY = new SchemaFactory();
}

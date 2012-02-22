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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.akiban.server.error.InvalidOperationException;

public class AISMergeTest {

    private AkibanInformationSchema t;
    private AkibanInformationSchema s;
    private AISBuilder b;
    private static final String SCHEMA= "test";
    private static final String TABLE  = "t1";
    private static final TableName TABLENAME = new TableName(SCHEMA,TABLE);
    
    @Before
    public void createSchema() throws Exception {
        t = new AkibanInformationSchema();
        s = new AkibanInformationSchema();
        b = new AISBuilder(s);
    }

    @Test
    public void simpleColumnTest () throws Exception {
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, TABLE, "c2", 1, "INT", (long)0, (long)0, false, false, null, null);
        
        b.basicSchemaIsComplete();
        
        assertNotNull(s.getUserTable(SCHEMA, TABLE));
        assertNotNull(s.getUserTable(SCHEMA, TABLE).getAIS());
        
        AISMerge merge = new AISMerge (t, s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        UserTable targetTable = t.getUserTable(TABLENAME);
        UserTable sourceTable = s.getUserTable(TABLENAME);

        assertTrue (t.isFrozen());
        assertNotSame (targetTable, sourceTable);
        assertEquals (targetTable.getName(), sourceTable.getName());
        checkColumns (targetTable.getColumns(), "c1", "c2");
        checkColumns (targetTable.getColumnsIncludingInternal(), "c1", "c2", Column.AKIBAN_PK_NAME);
        assertEquals (targetTable.getEngine(), sourceTable.getEngine());
        
        // hidden primary key gets moved/re-created
        assertEquals (targetTable.getIndexes().size() , sourceTable.getIndexes().size());
        // hidden primary key is not a user visible index. 
        assertEquals (0,targetTable.getIndexes().size());
        assertNull   (targetTable.getPrimaryKey());
        assertNotNull (targetTable.getPrimaryKeyIncludingInternal());
        assertEquals (targetTable.getColumn(Column.AKIBAN_PK_NAME).getPosition(), 
                sourceTable.getColumn(Column.AKIBAN_PK_NAME).getPosition());
        
        // merge will have created a group table for the user table we merged.
        assertEquals (t.getGroups().keySet().size(), 1);
        assertNotNull(t.getGroup(TABLE));
        checkColumns (t.getGroup(TABLE).getGroupTable().getColumns(), "t1$c1", "t1$c2", "t1$"+Column.AKIBAN_PK_NAME);
    }

    @Test
    public void simpleIndexTest() throws Exception {
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.index(SCHEMA, TABLE, "PRIMARY", true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, TABLE, "PRIMARY", "c1", 0, true, null);
        b.basicSchemaIsComplete();
        AISMerge merge = new AISMerge (t,s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        
        UserTable targetTable = t.getUserTable(TABLENAME);
        UserTable sourceTable = s.getUserTable(TABLENAME);
        
        assertEquals(targetTable.getIndexes().size(), 1);
        assertEquals(targetTable.getIndexes().size(), sourceTable.getIndexes().size());
        assertNotNull (targetTable.getPrimaryKey());
        checkColumns(targetTable.getPrimaryKey().getColumns(), "c1");
        
        assertNotNull(t.getGroup(TABLE));
        assertNotNull(t.getGroup(TABLE).getGroupTable().getIndex("t1$PRIMARY"));
    }
    
    @Test
    public void uniqueIndexTest() throws Exception {
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "int", (long)0, (long)0, false, false, null, null);
        b.index(SCHEMA, TABLE, "c1", true, Index.UNIQUE_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, TABLE, "c1", "c1", 0, true, null);
        
        AISMerge merge = new AISMerge (t,s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        
        UserTable targetTable = t.getUserTable(TABLENAME);
        UserTable sourceTable = s.getUserTable(TABLENAME);
        
        assertTrue (t.isFrozen());
        assertEquals (1,targetTable.getIndexes().size());
        assertEquals (targetTable.getIndexes().size(), sourceTable.getIndexes().size());
        assertNotNull (targetTable.getIndex("c1"));
        checkIndexColumns (targetTable.getIndex("c1").getKeyColumns(), "c1");
        assertNotNull(t.getGroup(TABLE));
        assertNotNull(t.getGroup(TABLE).getGroupTable().getIndex("t1$c1"));
        assertNull (targetTable.getPrimaryKey());

    }
    
    @Test
    public void testSimpleJoin() throws Exception {
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, TABLE, "c2", 1, "INT", (long)0, (long)0, false, false, null, null);
        b.index(SCHEMA, TABLE, "PK", true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, TABLE, "PK", "c1", 0, true, null);
        b.basicSchemaIsComplete();
        b.createGroup("FRED", SCHEMA, "_akiban_t1");
        b.addTableToGroup("FRED", SCHEMA, TABLE);
        b.groupingIsComplete();
        AISMerge merge = new AISMerge (t,s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        assertTrue (t.isFrozen());
        assertEquals (TABLE, t.getUserTable(TABLENAME).getGroup().getName());
        assertEquals ("test._akiban_t1", t.getUserTable(TABLENAME).getGroup().getGroupTable().getName().toString());
        
        b.userTable(SCHEMA, "t2");
        b.column(SCHEMA, "t2", "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, "t2", "c2", 1, "INT", (long)0, (long)0, true, false, null, null);
        b.joinTables("test/t1/test/t2", SCHEMA, TABLE, SCHEMA, "t2");
        b.joinColumns("test/t1/test/t2", SCHEMA, TABLE, "c1", SCHEMA, "t2", "c1");
        b.basicSchemaIsComplete();
        b.addJoinToGroup("FRED", "test/t1/test/t2", 0);
        b.groupingIsComplete();
        
        merge = new AISMerge (t, s.getUserTable(SCHEMA, "t2"));
        t = merge.merge().getAIS();
        
        assertEquals (1, t.getJoins().size());
        assertNotNull (t.getUserTable(SCHEMA, "t2").getParentJoin());
        assertEquals (1, t.getUserTable(TABLENAME).getChildJoins().size());
        assertNotNull (t.getGroup(TABLE));
        assertEquals (TABLE, t.getUserTable(SCHEMA, "t2").getGroup().getName());
        assertEquals (5, t.getGroupTable(SCHEMA, "_akiban_t1").getColumnsIncludingInternal().size());
    }
    
    @Test
    public void testTwoJoins() throws Exception {
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, TABLE, "c2", 1, "INT", (long)0, (long)0, false, false, null, null);
        b.index(SCHEMA, TABLE, "PK", true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, TABLE, "PK", "c1", 0, true, null);
        b.basicSchemaIsComplete();
        b.createGroup("FRED", SCHEMA, "_akiban_t1");
        b.addTableToGroup("FRED", SCHEMA, TABLE);
        b.groupingIsComplete();
        AISMerge merge = new AISMerge (t,s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        assertTrue (t.isFrozen());

        b.userTable(SCHEMA, "t2");
        b.column(SCHEMA, "t2", "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, "t2", "c2", 1, "INT", (long)0, (long)0, true, false, null, null);
        b.joinTables("test/t1/test/t2", SCHEMA, TABLE, SCHEMA, "t2");
        b.joinColumns("test/t1/test/t2", SCHEMA, TABLE, "c1", SCHEMA, "t2", "c1");
        b.basicSchemaIsComplete();
        b.addJoinToGroup("FRED", "test/t1/test/t2", 0);
        b.groupingIsComplete();
        
        merge = new AISMerge (t, s.getUserTable(SCHEMA, "t2"));
        t = merge.merge().getAIS();
        assertNotNull (t.getUserTable(SCHEMA, "t2").getParentJoin());
        assertEquals (1, t.getUserTable(TABLENAME).getChildJoins().size());
        assertNotNull (t.getGroup(TABLE));

        b.userTable(SCHEMA, "t3");
        b.column(SCHEMA, "t3", "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, "t3", "c2", 1, "Int", (long)0, (long)0, false, false, null, null);
        b.joinTables("test/t1/test/t3", SCHEMA, TABLE, SCHEMA, "t3");
        b.joinColumns("test/t1/test/t3", SCHEMA, TABLE, "c1", SCHEMA, "t3", "c1");
        b.basicSchemaIsComplete();
        b.addJoinToGroup("FRED", "test/t1/test/t3", 0);
        b.groupingIsComplete();
        
        merge = new AISMerge (t, s.getUserTable(SCHEMA, "t3"));
        t = merge.merge().getAIS();
        assertNotNull (t.getUserTable(SCHEMA, "t3").getParentJoin());
        assertEquals (2, t.getUserTable(TABLENAME).getChildJoins().size());
        assertNotNull (t.getGroup(TABLE));
        assertEquals (TABLE, t.getUserTable(SCHEMA, "t3").getGroup().getName());
        assertEquals (8, t.getGroupTable(SCHEMA, "_akiban_t1").getColumnsIncludingInternal().size());
    }

    
    @Test (expected=InvalidOperationException.class)
    public void testJoinToBadParent() throws Exception
    {
        // Table 1
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, TABLE, "c2", 1, "INT", (long)0, (long)0, false, false, null, null);
        b.index(SCHEMA, TABLE, "PK", true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, TABLE, "PK", "c1", 0, true, null);
        b.basicSchemaIsComplete();
        b.createGroup("FRED", SCHEMA, "_akiban_t1");
        b.addTableToGroup("FRED", SCHEMA, TABLE);
        b.groupingIsComplete();
        AISMerge merge = new AISMerge (t,s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        assertTrue (t.isFrozen());
        
        // table 3 : the fake table
        b.userTable(SCHEMA, "t3");
        b.column(SCHEMA, "t3", "c1", 0, "int", 0L, 0L, false, false, null, null);
        b.index(SCHEMA, "t3", "pk", true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, "t3", "pk", "c1", 0, true, null);
        b.createGroup("DOUG", SCHEMA, "_akiban_t3");
        b.addTableToGroup("DOUG", SCHEMA, "t3");
        // table 2 : join to wrong table. 
        b.userTable(SCHEMA, "t2");
        b.column(SCHEMA, "t2", "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, "t2", "c2", 1, "INT", (long)0, (long)0, true, false, null, null);
        b.joinTables("test/t1/test/t2", SCHEMA, "t3", SCHEMA, "t2");
        b.joinColumns("test/t1/test/t2", SCHEMA, "t3", "c1", SCHEMA, "t2", "c1");
        b.basicSchemaIsComplete();
        b.addJoinToGroup("DOUG", "test/t1/test/t2", 0);
        b.groupingIsComplete();
        
        merge = new AISMerge (t, s.getUserTable(SCHEMA, "t2"));
        t = merge.merge().getAIS();
    }

    @Test (expected=InvalidOperationException.class)
    public void testBadParentJoin () throws Exception
    {
        // Table 1
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, TABLE, "c2", 1, "INT", (long)0, (long)0, false, false, null, null);
        b.index(SCHEMA, TABLE, "PK", true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, TABLE, "PK", "c1", 0, true, null);
        b.basicSchemaIsComplete();
        b.createGroup("FRED", SCHEMA, "_akiban_t1");
        b.addTableToGroup("FRED", SCHEMA, TABLE);
        b.groupingIsComplete();
        AISMerge merge = new AISMerge (t,s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        assertTrue (t.isFrozen());
        
        
        b = new AISBuilder();
        // table 3 : the fake table
        b.userTable(SCHEMA, "t1");
        b.column(SCHEMA, "t1", "c5", 0, "int", 0L, 0L, false, false, null, null);
        b.index(SCHEMA, "t1", "pk", true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, "t1", "pk", "c5", 0, true, null);
        b.createGroup("DOUG", SCHEMA, "_akiban_t1");
        b.addTableToGroup("DOUG", SCHEMA, "t1");
        // table 2 : join to wrong table. 
        b.userTable(SCHEMA, "t2");
        b.column(SCHEMA, "t2", "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, "t2", "c2", 1, "INT", (long)0, (long)0, true, false, null, null);
        b.joinTables("test/t1/test/t2", SCHEMA, "t1", SCHEMA, "t2");
        b.joinColumns("test/t1/test/t2", SCHEMA, "t1", "c5", SCHEMA, "t2", "c1");
        b.basicSchemaIsComplete();
        b.addJoinToGroup("DOUG", "test/t1/test/t2", 0);
        b.groupingIsComplete();
        
        merge = new AISMerge (t, b.akibanInformationSchema().getUserTable(SCHEMA, "t2"));
        t = merge.merge().getAIS();
        
    }

    @Test
    public void testFakeTableJoin() throws Exception
    {
        // Table 1
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, TABLE, "c2", 1, "INT", (long)0, (long)0, false, false, null, null);
        b.index(SCHEMA, TABLE, "PK", true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, TABLE, "PK", "c1", 0, true, null);
        b.basicSchemaIsComplete();
        b.createGroup("FRED", SCHEMA, "_akiban_t1");
        b.addTableToGroup("FRED", SCHEMA, TABLE);
        b.groupingIsComplete();
        AISMerge merge = new AISMerge (t,s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        assertTrue (t.isFrozen());
        
        b = new AISBuilder();
        // table 3 : the fake table
        b.userTable(SCHEMA, "t1");
        b.column(SCHEMA, "t1", "c1", 0, "int", 0L, 0L, false, false, null, null);
        b.index(SCHEMA, "t1", "pk", true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, "t1", "pk", "c1", 0, true, null);
        b.createGroup("DOUG", SCHEMA, "_akiban_t1");
        b.addTableToGroup("DOUG", SCHEMA, "t1");
        // table 2 : join to wrong table. 
        b.userTable(SCHEMA, "t2");
        b.column(SCHEMA, "t2", "c1", 0, "INT", (long)0, (long)0, false, false, null, null);
        b.column(SCHEMA, "t2", "c2", 1, "INT", (long)0, (long)0, true, false, null, null);
        b.joinTables("test/t1/test/t2", SCHEMA, "t1", SCHEMA, "t2");
        b.joinColumns("test/t1/test/t2", SCHEMA, "t1", "c1", SCHEMA, "t2", "c1");
        b.basicSchemaIsComplete();
        b.addJoinToGroup("DOUG", "test/t1/test/t2", 0);
        b.groupingIsComplete();
        
        merge = new AISMerge (t, b.akibanInformationSchema().getUserTable(SCHEMA, "t2"));
        t = merge.merge().getAIS();

        assertNotNull (t.getUserTable(SCHEMA, "t2"));
        assertNotNull (t.getUserTable(SCHEMA, "t2").getParentJoin());
        assertEquals (1, t.getUserTable(TABLENAME).getChildJoins().size());
        assertNotNull (t.getGroup(TABLE));
        assertEquals (TABLE, t.getUserTable(SCHEMA, "t2").getGroup().getName());
        assertNotNull (t.getUserTable(SCHEMA, TABLE));
        assertEquals (TABLE, t.getUserTable(SCHEMA, TABLE).getGroup().getName());

    }

    @Test
    public void joinOfDifferingIntTypes() throws Exception {
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "BIGINT", 0L, 0L, false, false, null, null);
        b.index(SCHEMA, TABLE, Index.PRIMARY_KEY_CONSTRAINT, true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, TABLE, Index.PRIMARY_KEY_CONSTRAINT, "c1", 0, true, null);
        b.basicSchemaIsComplete();
        b.createGroup(TABLE, SCHEMA, "_akiban_t1");
        b.addTableToGroup(TABLE, SCHEMA, TABLE);
        b.groupingIsComplete();
        
        AISMerge merge = new AISMerge(t, s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        assertTrue(t.isFrozen());
        assertEquals(TABLE, t.getUserTable(TABLENAME).getGroup().getName());
        assertEquals("test._akiban_t1", t.getUserTable(TABLENAME).getGroup().getGroupTable().getName().toString());

        b.userTable(SCHEMA, "t2");
        b.column(SCHEMA, "t2", "c1", 0, "INT", 0L, 0L, false, false, null, null);
        b.column(SCHEMA, "t2", "parentid", 1, "INT", 0L, 0L, false, false, null, null);

        // join bigint->int
        b.joinTables("test/t1/test/t2", SCHEMA, TABLE, SCHEMA, "t2");
        b.joinColumns("test/t1/test/t2", SCHEMA, TABLE, "c1", SCHEMA, "t2", "parentid");
        b.basicSchemaIsComplete();
        b.addJoinToGroup(TABLE, "test/t1/test/t2", 0);
        b.groupingIsComplete();

        merge = new AISMerge(t, s.getUserTable(SCHEMA, "t2"));
        t = merge.merge().getAIS();

        assertEquals(1, t.getJoins().size());
        assertNotNull(t.getUserTable(SCHEMA, "t2").getParentJoin());
        assertEquals(1, t.getUserTable(TABLENAME).getChildJoins().size());
        assertNotNull(t.getGroup(TABLE));
        assertEquals(TABLE, t.getUserTable(SCHEMA, "t2").getGroup().getName());
    }

    @Test(expected= InvalidOperationException.class)
    public void joinDifferentTypes() throws Exception {
        b.userTable(SCHEMA, TABLE);
        b.column(SCHEMA, TABLE, "c1", 0, "BIGINT", 0L, 0L, false, false, null, null);
        b.index(SCHEMA, TABLE, Index.PRIMARY_KEY_CONSTRAINT, true, Index.PRIMARY_KEY_CONSTRAINT);
        b.indexColumn(SCHEMA, TABLE, Index.PRIMARY_KEY_CONSTRAINT, "c1", 0, true, null);
        b.basicSchemaIsComplete();
        b.createGroup(TABLE, SCHEMA, "_akiban_t1");
        b.addTableToGroup(TABLE, SCHEMA, TABLE);
        b.groupingIsComplete();

        AISMerge merge = new AISMerge(t, s.getUserTable(TABLENAME));
        t = merge.merge().getAIS();
        assertTrue(t.isFrozen());
        assertEquals(TABLE, t.getUserTable(TABLENAME).getGroup().getName());
        assertEquals("test._akiban_t1", t.getUserTable(TABLENAME).getGroup().getGroupTable().getName().toString());

        b.userTable(SCHEMA, "t2");
        b.column(SCHEMA, "t2", "c1", 0, "INT", 0L, 0L, false, false, null, null);
        b.column(SCHEMA, "t2", "parentid", 1, "varchar", 32L, 0L, false, false, null, null);

        // join bigint->varchar
        b.joinTables("test/t1/test/t2", SCHEMA, TABLE, SCHEMA, "t2");
        b.joinColumns("test/t1/test/t2", SCHEMA, TABLE, "c1", SCHEMA, "t2", "parentid");
        b.basicSchemaIsComplete();
        b.addJoinToGroup(TABLE, "test/t1/test/t2", 0);
        b.groupingIsComplete();

        merge = new AISMerge(t, s.getUserTable(SCHEMA, "t2"));
        t = merge.merge().getAIS();
    }

    private void checkColumns(List<Column> actual, String ... expected)
    {
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual.get(i).getName());
        }
    }
    
    private void checkIndexColumns(List<IndexColumn> actual, String ... expected)
    {
        assertEquals (expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual.get(i).getColumn().getName());
        }
    }
}

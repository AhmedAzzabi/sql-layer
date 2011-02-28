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

package com.akiban.ais.util;

import static junit.framework.Assert.assertEquals;

import org.junit.Test;

import com.akiban.ais.model.AISBuilder;
import com.akiban.ais.model.AkibanInformationSchema;

public final class DDLGeneratorTest {

    @Test
    public void testCreateTable() throws Exception {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "col", 0, "decimal unsigned", 11L, 3L, true, false, null, null);
        builder.basicSchemaIsComplete();
        builder.createGroup("myGroup", "akiban_objects", "_group0");
        builder.addTableToGroup("myGroup", "schema", "table");
        builder.groupingIsComplete();

        AkibanInformationSchema ais = builder.akibanInformationSchema();
        DDLGenerator generator = new DDLGenerator();

        assertEquals("group table",
                "create table `akiban_objects`.`_group0`(`table$col` decimal(11, 3) unsigned, `table$__akiban_pk` bigint NOT NULL, key `table$PRIMARY`(`table$__akiban_pk`)) engine=AKIBANDB",
                generator.createTable(ais.getGroup("myGroup").getGroupTable()));
    }

    @Test
    public void testColumnCharset() throws Exception {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "c1", 0, "varchar", 255L, null, true, false, "utf8", null);
        builder.basicSchemaIsComplete();
        AkibanInformationSchema ais = builder.akibanInformationSchema();
        assertEquals("create table `schema`.`table`(`c1` varchar(255) CHARACTER SET utf8) engine=akibandb",
                     new DDLGenerator().createTable(ais.getTable("schema", "table")));
    }

    @Test
    public void testColumnCollation() throws Exception {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "c1", 0, "varchar", 255L, null, true, false, null, "utf8_bin");
        builder.basicSchemaIsComplete();
        AkibanInformationSchema ais = builder.akibanInformationSchema();
        assertEquals("create table `schema`.`table`(`c1` varchar(255) COLLATE utf8_bin) engine=akibandb",
                     new DDLGenerator().createTable(ais.getTable("schema", "table")));
    }

    @Test
    public void testColumnNotNull() throws Exception {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "c1", 0, "int", null, null, false, false, null, null);
        builder.basicSchemaIsComplete();
        AkibanInformationSchema ais = builder.akibanInformationSchema();
        assertEquals("create table `schema`.`table`(`c1` int NOT NULL) engine=akibandb",
                    new DDLGenerator().createTable(ais.getTable("schema", "table")));
    }

    @Test
    public void testColumnAutoIncrement() throws Exception {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "c1", 0, "int", null, null, true, true, null, null);
        builder.basicSchemaIsComplete();
        AkibanInformationSchema ais = builder.akibanInformationSchema();
        assertEquals("create table `schema`.`table`(`c1` int AUTO_INCREMENT) engine=akibandb",
                     new DDLGenerator().createTable(ais.getTable("schema", "table")));
    }
}


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
        builder.createGroup("myGroup", "some_group_schema");
        builder.addTableToGroup("myGroup", "schema", "table");
        builder.groupingIsComplete();

        AkibanInformationSchema ais = builder.akibanInformationSchema();
        DDLGenerator generator = new DDLGenerator();

        assertEquals("table",
                "create table `schema`.`table`(`col` decimal(11, 3) unsigned NULL) engine=akibandb DEFAULT CHARSET=utf8 COLLATE=utf8_bin",
                generator.createTable(ais.getUserTable("schema", "table")));
    }

    @Test
    public void testColumnCharset() throws Exception {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "c1", 0, "varchar", 255L, null, true, false, "utf-16", null);
        builder.basicSchemaIsComplete();
        AkibanInformationSchema ais = builder.akibanInformationSchema();
        assertEquals("create table `schema`.`table`(`c1` varchar(255) CHARACTER SET utf-16 NULL) engine=akibandb DEFAULT CHARSET=utf8 COLLATE=utf8_bin",
                     new DDLGenerator().createTable(ais.getTable("schema", "table")));
    }

    @Test
    public void testColumnCollation() throws Exception {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "c1", 0, "varchar", 255L, null, true, false, null, "euckr_korean_ci");
        builder.basicSchemaIsComplete();
        AkibanInformationSchema ais = builder.akibanInformationSchema();
        assertEquals("create table `schema`.`table`(`c1` varchar(255) COLLATE euckr_korean_ci NULL) engine=akibandb DEFAULT CHARSET=utf8 COLLATE=utf8_bin",
                     new DDLGenerator().createTable(ais.getTable("schema", "table")));
    }

    @Test
    public void testColumnNotNull() throws Exception {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "c1", 0, "int", null, null, false, false, null, null);
        builder.basicSchemaIsComplete();
        AkibanInformationSchema ais = builder.akibanInformationSchema();
        assertEquals("create table `schema`.`table`(`c1` int NOT NULL) engine=akibandb DEFAULT CHARSET=utf8 COLLATE=utf8_bin",
                    new DDLGenerator().createTable(ais.getTable("schema", "table")));
    }

    @Test
    public void testColumnAutoIncrement() throws Exception {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "c1", 0, "int", null, null, true, true, null, null);
        builder.basicSchemaIsComplete();
        AkibanInformationSchema ais = builder.akibanInformationSchema();
        assertEquals("create table `schema`.`table`(`c1` int NULL AUTO_INCREMENT) engine=akibandb DEFAULT CHARSET=utf8 COLLATE=utf8_bin",
                     new DDLGenerator().createTable(ais.getTable("schema", "table")));
    }

    @Test
    public void testTimestampColumn() {
        AISBuilder builder = new AISBuilder();
        builder.userTable("schema", "table");
        builder.column("schema", "table", "c1", 0, "timestamp", null, null, true, false, null, null);
        builder.basicSchemaIsComplete();
        AkibanInformationSchema ais = builder.akibanInformationSchema();
        assertEquals("create table `schema`.`table`(`c1` timestamp NULL) engine=akibandb DEFAULT CHARSET=utf8 COLLATE=utf8_bin",
                     new DDLGenerator().createTable(ais.getTable("schema", "table")));
    }
}

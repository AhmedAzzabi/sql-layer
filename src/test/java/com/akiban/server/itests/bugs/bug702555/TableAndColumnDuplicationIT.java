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

package com.akiban.server.itests.bugs.bug702555;

import com.akiban.server.InvalidOperationException;
import com.akiban.server.itests.ApiTestBase;
import org.junit.Test;

public final class TableAndColumnDuplicationIT extends ApiTestBase {

    @Test
    public void sameTableAndColumn() throws InvalidOperationException {
        doTest( "tbl1", "id1",
                "tbl1", "id1");
    }

    @Test
    public void sameTableDifferentColumn() throws InvalidOperationException {
        doTest( "tbl1", "id1",
                "tbl1", "id2");
    }

    @Test
    public void differentTableSameColumn() throws InvalidOperationException {
        doTest( "tbl1", "id1",
                "tbl2", "id1");
    }

    @Test
    public void differentTableDifferentColumn() throws InvalidOperationException {
        doTest( "tbl1", "id1",
                "tbl2", "id2");
    }

    /**
     * A potentially more subtle problem. No duplicate key exceptions are thrown, because the two tables have
     * inherently incompatible primary keys. But data gets written to the same tree, and thus a read stumbles
     * across rows it shouldn't see
     * @throws InvalidOperationException if any CRUD operation fails
     */
    @Test
    public void noDuplicateKeyButIncompatibleRows() throws InvalidOperationException {
        final int schema1Table
                = createTable("schema1", "table1", "id int key");
        final int schema2Table =
                createTable("schema2","table1", "name varchar(32) key");

        writeRows(
                createNewRow(schema1Table, 0L),
                createNewRow(schema1Table, 1L),
                createNewRow(schema1Table, 2L)
        );

        writeRows(
                createNewRow(schema2Table, "first row"),
                createNewRow(schema2Table, "second row"),
                createNewRow(schema2Table, "third row")
        );

        expectFullRows(schema1Table,
                createNewRow(schema1Table, 0L),
                createNewRow(schema1Table, 1L),
                createNewRow(schema1Table, 2L)
        );
        
        expectFullRows(schema2Table,
                createNewRow(schema2Table, "first row"),
                createNewRow(schema2Table, "second row"),
                createNewRow(schema2Table, "third row")
        );
    }

    private void doTest(String schema1TableName, String schema1TableKeyCol,
                        String schema2TableName, String schema2TableKeyCol) throws InvalidOperationException
    {
        final int schema1Table
                = createTable("schema1", schema1TableName, schema1TableKeyCol + " int key, name varchar(32)");
        final int schema2Table =
                createTable("schema2", schema2TableName, schema2TableKeyCol + " int key, name varchar(32)");

        writeRows(
                createNewRow(schema1Table, 0L, "alpha-0"),
                createNewRow(schema1Table, 1L, "alpha-1"),
                createNewRow(schema1Table, 2L, "alpha-1")
        );

        writeRows(
                createNewRow(schema2Table, 0L, "bravo-0"),
                createNewRow(schema2Table, 1L, "bravo-1"),
                createNewRow(schema2Table, 2L, "bravo-1")
        );

        expectFullRows( schema1Table,
                createNewRow(schema1Table, 0L, "alpha-0"),
                createNewRow(schema1Table, 1L, "alpha-1"),
                createNewRow(schema1Table, 2L, "alpha-1")
        );

        expectFullRows( schema2Table,
                createNewRow(schema2Table, 0L, "bravo-0"),
                createNewRow(schema2Table, 1L, "bravo-1"),
                createNewRow(schema2Table, 2L, "bravo-1")
        );
    }
}

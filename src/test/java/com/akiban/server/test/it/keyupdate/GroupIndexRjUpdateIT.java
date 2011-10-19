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
import com.akiban.server.api.dml.scan.NewRow;
import org.junit.Ignore;
import org.junit.Test;

public final class GroupIndexRjUpdateIT extends GIUpdateITBase {

    @Test
    public void placeholderNoOrphan() {
        final NewRow r1, r2;
        groupIndex("c.name, o.when");
        writeAndCheck(
                r1 = createNewRow(c, 1L, "Bergy")
        );
        writeAndCheck(
                r2 = createNewRow(o, 10L, 1L, "01-01-2001"),
                "Bergy, 01-01-2001, 1, 10 => " + depthFrom(o)
        );
        deleteAndCheck(r2);
        deleteAndCheck(r1);
    }

    @Test
    public void placeholderWithOrphan() {
        final NewRow r1, r2;
        groupIndex("c.name, o.when");
        writeAndCheck(
                r1 = createNewRow(o, 10L, 1L, "01-01-2001"),
                "null, 01-01-2001, 1, 10 => " + depthFrom(o)
        );
        writeAndCheck(
                r2 = createNewRow(c, 1L, "Bergy"),
                "Bergy, 01-01-2001, 1, 10 => " + depthFrom(o)
        );
        deleteAndCheck(
                r2,
                "null, 01-01-2001, 1, 10 => " + depthFrom(o)
        );
        deleteAndCheck(r1);
    }

    @Ignore("bug 877656")
    @Test
    public void coiNoOrphan() {
        groupIndex("c.name, o.when, i.sku");

        writeAndCheck(
                createNewRow(c, 1L, "Horton")
        );
        writeAndCheck(
                createNewRow(o, 11L, 1L, "01-01-2001")
        );
        writeAndCheck(
                createNewRow(i, 101L, 11L, 1111),
                "Horton, 01-01-2001, 1111, 1, 11, 101 => " + depthFrom(i)
        );
        writeAndCheck(
                createNewRow(i, 102L, 11L, 2222),
                "Horton, 01-01-2001, 1111, 1, 11, 101 => " + depthFrom(i),
                "Horton, 01-01-2001, 2222, 1, 11, 102 => " + depthFrom(i)
        );
        writeAndCheck(
                createNewRow(i, 103L, 11L, 3333),
                "Horton, 01-01-2001, 1111, 1, 11, 101 => " + depthFrom(i),
                "Horton, 01-01-2001, 2222, 1, 11, 102 => " + depthFrom(i),
                "Horton, 01-01-2001, 3333, 1, 11, 103 => " + depthFrom(i)
        );
        writeAndCheck(
                createNewRow(o, 12L, 1L, "02-02-2002"),
                "Horton, 01-01-2001, 1111, 1, 11, 101 => " + depthFrom(i),
                "Horton, 01-01-2001, 2222, 1, 11, 102 => " + depthFrom(i),
                "Horton, 01-01-2001, 3333, 1, 11, 103 => " + depthFrom(i)
        );

        writeAndCheck(createNewRow(a, 10001L, 1L, "Causeway"),
                "Horton, 01-01-2001, 1111, 1, 11, 101 => " + depthFrom(i),
                "Horton, 01-01-2001, 2222, 1, 11, 102 => " + depthFrom(i),
                "Horton, 01-01-2001, 3333, 1, 11, 103 => " + depthFrom(i)
        );


        // update parent
        updateAndCheck(
                createNewRow(o, 11L, 1L, "01-01-2001"),
                createNewRow(o, 11L, 1L, "01-01-1999"), // party!
                "Horton, 01-01-1999, 1111, 1, 11, 101 => " + depthFrom(i),
                "Horton, 01-01-1999, 2222, 1, 11, 102 => " + depthFrom(i),
                "Horton, 01-01-1999, 3333, 1, 11, 103 => " + depthFrom(i)
        );
        // update child
        updateAndCheck(
                createNewRow(i, 102L, 11L, 2222),
                createNewRow(i, 102L, 11L, 2442),
                "Horton, 01-01-1999, 1111, 1, 11, 101 => " + depthFrom(i),
                "Horton, 01-01-1999, 2442, 1, 11, 102 => " + depthFrom(i),
                "Horton, 01-01-1999, 3333, 1, 11, 103 => " + depthFrom(i)
        );

        // delete order
        deleteAndCheck(
                createNewRow(o, 11L, 1L, "01-01-1999"),
                "Horton, null, 1111, null, 11, 101 => " + depthFrom(i),
                "Horton, null, 3333, null, 11, 103 => " + depthFrom(i)
        );
        // delete item
        deleteAndCheck(
                createNewRow(i, 102L, 11L, 222211)
        );
    }

    @Test
    public void createGIOnFullyPopulatedTables() {
        writeRows(
                createNewRow(c, 1L, "Horton"),
                createNewRow(o, 11L, 1L, "01-01-2001"),
                createNewRow(i, 101L, 11L, 1111)
        );
        groupIndex("name_when_sku", "c.name, o.when, i.sku");
        checkIndex("name_when_sku",
                "Horton, 01-01-2001, 1111, 1, 11, 101 => " + depthFrom(i)
        );
    }

    @Test
    public void createGIOnPartiallyPopulatedTablesFromLeaf() {
        writeRows(
                createNewRow(o, 11L, 1L, "01-01-2001"),
                createNewRow(i, 101L, 11L, 1111)
        );
        groupIndex("name_when_sku", "c.name, o.when, i.sku");
        checkIndex("name_when_sku",
                "null, 01-01-2001, 1111, 1, 11, 101 => " + depthFrom(i)
        );
    }

    @Test
    public void createGiOnPartiallyPopulatedTablesFromMiddle() {
        writeRows(
                createNewRow(o, 11L, 1L, "01-01-2001"),
                createNewRow(i, 101L, 11L, 1111)
        );
        groupIndex("when_sku","o.when, i.sku");
        checkIndex("when_sku",
                "01-01-2001, 1111, 1, 11, 101 => " + depthFrom(i)
        );
    }

    @Test
    public void ihIndexNoOrphans() {
        String indexName = groupIndex("i.sku, h.handling_instructions");
        writeRows(
                createNewRow(c, 1L, "Horton"),
                createNewRow(o, 11L, 1L, "01-01-2001"),
                createNewRow(i, 101L, 11L, 1111),
                createNewRow(h, 1001L, 101L, "handle with care")
        );
        checkIndex(indexName, "1111, handle with care, 1, 11, 101, 1001 => " + depthFrom(h));

        // delete from root on up
        dml().deleteRow(session(), createNewRow(c, 1L, "Horton"));
        checkIndex(indexName, "1111, handle with care, 1, 11, 101, 1001 => " + depthFrom(h));

        dml().deleteRow(session(), createNewRow(o, 11L, 1L, "01-01-2001 => " + depthFrom(h)));
        checkIndex(indexName, "1111, handle with care, null, 11, 101, 1001 => " + depthFrom(h));

        dml().deleteRow(session(), createNewRow(i, 101L, 11L, 1111));
        checkIndex(indexName, "null, handle with care, null, null, 101, 1001 => " + depthFrom(h));

        dml().deleteRow(session(), createNewRow(h, 1001L, 101L, "handle with care"));
        checkIndex(indexName);
    }

    @Test
    public void adoptionChangesHKeyNoCustomer() {
        String indexName = groupIndex("i.sku, h.handling_instructions");
        writeRows(
                createNewRow(i, 101L, 11L, 1111),
                createNewRow(h, 1001L, 101L, "handle with care")
        );
        checkIndex(indexName,
                "1111, handle with care, null, 11, 101, 1001 => " + depthFrom(h)
        );

        // bring an o that adopts the i
        final NewRow oRow;
        writeAndCheck(
                oRow = createNewRow(o, 11L, 1L, "01-01-2001"),
                "1111, handle with care, 1, 11, 101, 1001 => " + depthFrom(h)
        );
        deleteAndCheck(
                oRow,
                "1111, handle with care, null, 11, 101, 1001 => " + depthFrom(h)
        );
    }

    @Test
    public void adoptionChangesHKeyWithC() {
        String indexName = groupIndex("i.sku, h.handling_instructions");
        writeRows(
                createNewRow(c, 1L, "Horton"),
                createNewRow(i, 101L, 11L, 1111),
                createNewRow(h, 1001L, 101L, "handle with care")
        );
        checkIndex(indexName,
                "1111, handle with care, null, 11, 101, 1001 => " + depthFrom(h)
        );

        // bring an o that adopts the i
        dml().writeRow(session(), createNewRow(o, 11L, 1L, "01-01-2001"));
        checkIndex(indexName,
                "1111, handle with care, 1, 11, 101, 1001 => " + depthFrom(h)
        );
    }
    @Test
    public void updateModifiesHKeyWithinBranch() {
        // branch is I-H, we're modifying the hkey of an H
        groupIndex("i.sku, h.handling_instructions");
        writeAndCheck(createNewRow(c, 1L, "Horton"));

        writeAndCheck(createNewRow(o, 11L, 1L, "01-01-2001"));

        writeAndCheck(createNewRow(i, 101L, 11L, "1111"));

        writeAndCheck(
                createNewRow(h, 1001L, 101L, "don't break"),
                "1111, don't break, 1, 11, 101, 1001 => " + depthFrom(h)
        );

        writeAndCheck(
                createNewRow(c, 2L, "David"),
                "1111, don't break, 1, 11, 101, 1001 => " + depthFrom(h)
        );

        writeAndCheck(
                createNewRow(o, 12L, 2L, "02-02-2002"),
                "1111, don't break, 1, 11, 101, 1001 => " + depthFrom(h)
        );

        writeAndCheck(
                createNewRow(h, 1002L, 102L, "do break"),
                "null, do break, null, null, 102, 1002 => " + depthFrom(h),
                "1111, don't break, 1, 11, 101, 1001 => " + depthFrom(h)
        );

        updateAndCheck(
                createNewRow(i, 101L, 11L, "1111"),
                createNewRow(i, 102L, 12L, "2222"),
                "null, don't break, null, null, 101, 1001 => " + depthFrom(h),
                "2222, do break, 2, 12, 102, 1002 => " + depthFrom(h)
        );
    }

    public GroupIndexRjUpdateIT() {
        super(Index.JoinType.RIGHT);
    }
}

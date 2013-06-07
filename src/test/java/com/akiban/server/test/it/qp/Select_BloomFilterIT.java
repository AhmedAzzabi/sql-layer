/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.akiban.server.test.it.qp;

import com.akiban.qp.expression.IndexBound;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.expression.RowBasedUnboundExpressions;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.qp.rowtype.UserTableRowType;
import com.akiban.server.api.dml.SetColumnSelector;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.expression.std.Comparison;
import com.akiban.server.test.ExpressionGenerators;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.akiban.qp.operator.API.*;
import static org.junit.Assert.fail;

public class Select_BloomFilterIT extends OperatorITBase
{
    @Override
    protected void setupCreateSchema()
    {
        // Tables are Driving (D) and Filtering (F). Find Filtering rows with a given test id, yielding
        // a set of (a, b) rows. Then find Driving rows matching a and b.
        d = createTable(
            "schema", "driving",
            "test_id int not null",
            "a int",
            "b int");
        f = createTable(
            "schema", "filtering",
            "test_id int not null",
            "a int",
            "b int");
        createIndex("schema", "driving", "idx_d", "test_id", "a", "b");
        createIndex("schema", "filtering", "idx_fab", "a", "b");
    }

    @Override
    protected void setupPostCreateSchema()
    {
        schema = new Schema(ais());
        dRowType = schema.userTableRowType(userTable(d));
        fRowType = schema.userTableRowType(userTable(f));
        dIndexRowType = indexType(d, "test_id", "a", "b");
        fabIndexRowType = indexType(f, "a", "b");
        adapter = newStoreAdapter(schema);
        queryContext = queryContext(adapter);
        db = new NewRow[]{
            // Test 0: No d or f rows
            // Test 1: No f rows
            createNewRow(f, 1L, 10L, 100L),
            // Test 2: No d rows
            createNewRow(f, 2L, 20L, 200L),
            // Test 3: 1 d row, no matching f rows
            createNewRow(d, 3L, 30L, 300L),
            createNewRow(f, 3L, 31L, 300L),
            createNewRow(f, 3L, 30L, 301L),
            // Test 4: 1 d row, 1 matching f rows
            createNewRow(d, 4L, 40L, 400L),
            createNewRow(f, 4L, 40L, 400L),
            createNewRow(f, 4L, 41L, 400L),
            createNewRow(f, 4L, 40L, 401L),
            // Test 5: multiple d rows, no matching f rows
            createNewRow(d, 5L, 50L, 500L),
            createNewRow(d, 5L, 51L, 501L),
            createNewRow(f, 5L, 50L, 501L),
            createNewRow(f, 5L, 51L, 500L),
            // Test 6: multiple d rows, multiple f rows, multiple matches
            createNewRow(d, 6L, 60L, 600L),
            createNewRow(d, 6L, 61L, 601L),
            createNewRow(d, 6L, 62L, 602L),
            createNewRow(d, 6L, 63L, 603L),
            createNewRow(f, 6L, 60L, 699L),
            createNewRow(f, 6L, 61L, 601L),
            createNewRow(f, 6L, 62L, 602L),
            createNewRow(f, 6L, 63L, 699L),
            // Test 7: Null columns in d
            createNewRow(d, 7L, null, null),
            // Test 8: Null columns in f
            createNewRow(f, 8L, null, null),
        };
        use(db);
    }

    // Test argument validation

    @Test
    public void testBadInputs()
    {
        try {
            using_BloomFilter(null, customerRowType, 10, 0, groupScan_Default(coi));
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            using_BloomFilter(groupScan_Default(coi), null, 10, 0, groupScan_Default(coi));
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            using_BloomFilter(groupScan_Default(coi), customerRowType, -1, 0, groupScan_Default(coi));
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            using_BloomFilter(groupScan_Default(coi), customerRowType, 10, -1, groupScan_Default(coi));
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            using_BloomFilter(groupScan_Default(coi), customerRowType, 10, -1, null);
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    // Test operator execution

    @Test
    public void test0()
    {
        Operator plan = plan(0);
        RowBase[] expected = new RowBase[] {
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test1()
    {
        Operator plan = plan(1);
        RowBase[] expected = new RowBase[] {
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test2()
    {
        Operator plan = plan(2);
        RowBase[] expected = new RowBase[] {
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test3()
    {
        Operator plan = plan(3);
        RowBase[] expected = new RowBase[] {
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test4()
    {
        Operator plan = plan(4);
        RowBase[] expected = new RowBase[] {
            row(outputRowType, 4L, 40L, 400L),
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test5()
    {
        Operator plan = plan(5);
        RowBase[] expected = new RowBase[] {
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test6()
    {
        Operator plan = plan(6);
        RowBase[] expected = new RowBase[] {
            row(outputRowType, 6L, 61L, 601L),
            row(outputRowType, 6L, 62L, 602L),
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test7()
    {
        Operator plan = plan(7);
        RowBase[] expected = new RowBase[] {
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void test8()
    {
        Operator plan = plan(8);
        RowBase[] expected = new RowBase[] {
        };
        compareRows(expected, cursor(plan, queryContext));
    }

    @Test
    public void testCursor()
    {
        Operator plan = plan(6);
        CursorLifecycleTestCase testCase = new CursorLifecycleTestCase()
        {
            @Override
            public RowBase[] firstExpectedRows()
            {
                return new RowBase[] {
                    row(outputRowType, 6L, 61L, 601L),
                    row(outputRowType, 6L, 62L, 602L),
                };
            }
        };
        testCursorLifecycle(plan, testCase);
    }

    public Operator plan(long testId)
    {
        // loadFilter loads the filter with F rows containing the given testId.
        Operator loadFilter = project_Default(
            select_HKeyOrdered(
                filter_Default(
                    groupScan_Default(group(f)),
                    Collections.singleton(fRowType)),
                fRowType,
                ExpressionGenerators.compare(
                    ExpressionGenerators.field(fRowType, 0),
                    Comparison.EQ,
                    ExpressionGenerators.literal(testId), castResolver())),
            fRowType,
            Arrays.asList(ExpressionGenerators.field(fRowType, 1),
                          ExpressionGenerators.field(fRowType, 2)));
        // For the index scan retriving rows from the D(test_id) index
        IndexBound testIdBound =
            new IndexBound(row(dIndexRowType, testId), new SetColumnSelector(0));
        IndexKeyRange dTestIdKeyRange =
            IndexKeyRange.bounded(dIndexRowType, testIdBound, true, testIdBound, true);
        // For the  index scan retrieving rows from the F(a, b) index given a D index row
        IndexBound abBound = new IndexBound(
            new RowBasedUnboundExpressions(
                loadFilter.rowType(),
                Arrays.asList(
                    ExpressionGenerators.boundField(dIndexRowType, 0, 1),
                    ExpressionGenerators.boundField(dIndexRowType, 0, 2))),
            new SetColumnSelector(0, 1));
        IndexKeyRange fabKeyRange =
            IndexKeyRange.bounded(fabIndexRowType, abBound, true, abBound, true);
        // Use a bloom filter loaded by loadFilter. Then for each input row, check the filter (projecting
        // D rows on (a, b)), and, for positives, check F using an index scan keyed by D.a and D.b.
        Operator plan =
            project_Default(
                using_BloomFilter(
                    // filterInput
                    loadFilter,
                    // filterRowType
                    loadFilter.rowType(),
                    // estimatedRowCount
                    10,
                    // filterBindingPosition
                    0,
                    // streamInput
                    select_BloomFilter(
                        // input
                        indexScan_Default(dIndexRowType, dTestIdKeyRange, new Ordering()),
                        // onPositive
                        indexScan_Default(
                            fabIndexRowType,
                            fabKeyRange,
                            new Ordering()),
                        // filterFields
                        Arrays.asList(
                            ExpressionGenerators.field(dIndexRowType, 1),
                            ExpressionGenerators.field(dIndexRowType, 2)),
                        // filterBindingPosition
                        0)),
                dIndexRowType,
                Arrays.asList(
                    ExpressionGenerators.field(dIndexRowType, 0),   // test_id
                    ExpressionGenerators.field(dIndexRowType, 1),   // a
                    ExpressionGenerators.field(dIndexRowType, 2))); // b
        outputRowType = plan.rowType();
        return plan;
    }

    private int d;
    private int f;
    private UserTableRowType dRowType;
    private UserTableRowType fRowType;
    private RowType outputRowType;
    IndexRowType dIndexRowType;
    IndexRowType fabIndexRowType;
}

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

import com.akiban.qp.expression.ExpressionRow;
import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.operator.UndefBindings;
import com.akiban.qp.row.Row;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.std.LiteralExpression;
import com.akiban.server.types.AkType;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.akiban.server.expression.std.Expressions.field;
import static com.akiban.qp.operator.API.*;

public class Sort_TreeIT extends OperatorITBase
{
    @Before
    public void before()
    {
        super.before();
        NewRow[] dbRows = new NewRow[]{
            createNewRow(customer, 1L, "northbridge"),
            createNewRow(customer, 2L, "foundation"),
            createNewRow(customer, 4L, "highland"),
            createNewRow(customer, 5L, "matrix"),
            createNewRow(order, 11L, 1L, "ori"),
            createNewRow(order, 12L, 1L, "david"),
            createNewRow(order, 21L, 2L, "david"),
            createNewRow(order, 22L, 2L, "jack"),
            createNewRow(order, 31L, 3L, "david"),
            createNewRow(order, 51L, 5L, "yuval"),
            createNewRow(item, 111L, 11L),
            createNewRow(item, 112L, 11L),
            createNewRow(item, 121L, 12L),
            createNewRow(item, 122L, 12L),
            createNewRow(item, 211L, 21L),
            createNewRow(item, 212L, 21L),
            createNewRow(item, 221L, 22L),
            createNewRow(item, 222L, 22L),
        };
        use(dbRows);
    }

    @Test
    public void testCustomerName()
    {
        Operator plan =
            sort_Tree(
                filter_Default(
                    groupScan_Default(coi),
                    Collections.singleton(customerRowType)),
                customerRowType,
                ordering(field(customerRowType, 1), true));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 2L, "foundation"),
            row(customerRowType, 4L, "highland"),
            row(customerRowType, 5L, "matrix"),
            row(customerRowType, 1L, "northbridge"),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testOrderSalesmanCid()
    {
        Operator plan =
            sort_Tree(
                filter_Default(
                    groupScan_Default(coi),
                    Collections.singleton(orderRowType)),
                orderRowType,
                ordering(field(orderRowType, 2), true, field(orderRowType, 1), false));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(orderRowType, 31L, 3L, "david"),
            row(orderRowType, 21L, 2L, "david"),
            row(orderRowType, 12L, 1L, "david"),
            row(orderRowType, 22L, 2L, "jack"),
            row(orderRowType, 11L, 1L, "ori"),
            row(orderRowType, 51L, 5L, "yuval"),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testOrderSalesman()
    {
        Operator plan =
            sort_Tree(
                filter_Default(
                    groupScan_Default(coi),
                    Collections.singleton(orderRowType)),
                orderRowType,
                ordering(field(orderRowType, 2), true));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            // Order among equals in group.
            row(orderRowType, 12L, 1L, "david"),
            row(orderRowType, 21L, 2L, "david"),
            row(orderRowType, 31L, 3L, "david"),
            row(orderRowType, 22L, 2L, "jack"),
            row(orderRowType, 11L, 1L, "ori"),
            row(orderRowType, 51L, 5L, "yuval"),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testAAA()
    {
        Operator flattenOI = flatten_HKeyOrdered(
            groupScan_Default(coi),
            orderRowType,
            itemRowType,
            JoinType.INNER_JOIN);
        RowType oiType = flattenOI.rowType();
        // flattenOI columns: oid, cid, salesman, iid, oid
        Expression cidField = field(oiType, 1);
        Expression oidField = field(oiType, 0);
        Expression iidField = field(oiType, 3);
        Operator plan =
            sort_Tree(
                filter_Default(
                    flattenOI,
                    Collections.singleton(oiType)),
                oiType,
                ordering(cidField, true, oidField, true, iidField, true));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(oiType, 11L, 1L, "ori", 111L, 11L),
            row(oiType, 11L, 1L, "ori", 112L, 11L),
            row(oiType, 12L, 1L, "david", 121L, 12L),
            row(oiType, 12L, 1L, "david", 122L, 12L),
            row(oiType, 21L, 2L, "david", 211L, 21L),
            row(oiType, 21L, 2L, "david", 212L, 21L),
            row(oiType, 22L, 2L, "jack", 221L, 22L),
            row(oiType, 22L, 2L, "jack", 222L, 22L),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testAAD()
    {
        Operator flattenOI = flatten_HKeyOrdered(
            groupScan_Default(coi),
            orderRowType,
            itemRowType,
            JoinType.INNER_JOIN);
        RowType oiType = flattenOI.rowType();
        // flattenOI columns: oid, cid, salesman, iid, oid
        Expression cidField = field(oiType, 1);
        Expression oidField = field(oiType, 0);
        Expression iidField = field(oiType, 3);
        Operator plan =
            sort_Tree(
                filter_Default(
                    flattenOI,
                    Collections.singleton(oiType)),
                oiType,
                ordering(cidField, true, oidField, true, iidField, false));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(oiType, 11L, 1L, "ori", 112L, 11L),
            row(oiType, 11L, 1L, "ori", 111L, 11L),
            row(oiType, 12L, 1L, "david", 122L, 12L),
            row(oiType, 12L, 1L, "david", 121L, 12L),
            row(oiType, 21L, 2L, "david", 212L, 21L),
            row(oiType, 21L, 2L, "david", 211L, 21L),
            row(oiType, 22L, 2L, "jack", 222L, 22L),
            row(oiType, 22L, 2L, "jack", 221L, 22L),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testADA()
    {
        Operator flattenOI = flatten_HKeyOrdered(
            groupScan_Default(coi),
            orderRowType,
            itemRowType,
            JoinType.INNER_JOIN);
        RowType oiType = flattenOI.rowType();
        // flattenOI columns: oid, cid, salesman, iid, oid
        Expression cidField = field(oiType, 1);
        Expression oidField = field(oiType, 0);
        Expression iidField = field(oiType, 3);
        Operator plan =
            sort_Tree(
                filter_Default(
                    flattenOI,
                    Collections.singleton(oiType)),
                oiType,
                ordering(cidField, true, oidField, false, iidField, true));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(oiType, 12L, 1L, "david", 121L, 12L),
            row(oiType, 12L, 1L, "david", 122L, 12L),
            row(oiType, 11L, 1L, "ori", 111L, 11L),
            row(oiType, 11L, 1L, "ori", 112L, 11L),
            row(oiType, 22L, 2L, "jack", 221L, 22L),
            row(oiType, 22L, 2L, "jack", 222L, 22L),
            row(oiType, 21L, 2L, "david", 211L, 21L),
            row(oiType, 21L, 2L, "david", 212L, 21L),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testADD()
    {
        Operator flattenOI = flatten_HKeyOrdered(
            groupScan_Default(coi),
            orderRowType,
            itemRowType,
            JoinType.INNER_JOIN);
        RowType oiType = flattenOI.rowType();
        // flattenOI columns: oid, cid, salesman, iid, oid
        Expression cidField = field(oiType, 1);
        Expression oidField = field(oiType, 0);
        Expression iidField = field(oiType, 3);
        Operator plan =
            sort_Tree(
                filter_Default(
                    flattenOI,
                    Collections.singleton(oiType)),
                oiType,
                ordering(cidField, true, oidField, false, iidField, false));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(oiType, 12L, 1L, "david", 122L, 12L),
            row(oiType, 12L, 1L, "david", 121L, 12L),
            row(oiType, 11L, 1L, "ori", 112L, 11L),
            row(oiType, 11L, 1L, "ori", 111L, 11L),
            row(oiType, 22L, 2L, "jack", 222L, 22L),
            row(oiType, 22L, 2L, "jack", 221L, 22L),
            row(oiType, 21L, 2L, "david", 212L, 21L),
            row(oiType, 21L, 2L, "david", 211L, 21L),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testDAA()
    {
        Operator flattenOI = flatten_HKeyOrdered(
            groupScan_Default(coi),
            orderRowType,
            itemRowType,
            JoinType.INNER_JOIN);
        RowType oiType = flattenOI.rowType();
        // flattenOI columns: oid, cid, salesman, iid, oid
        Expression cidField = field(oiType, 1);
        Expression oidField = field(oiType, 0);
        Expression iidField = field(oiType, 3);
        Operator plan =
            sort_Tree(
                filter_Default(
                    flattenOI,
                    Collections.singleton(oiType)),
                oiType,
                ordering(cidField, false, oidField, true, iidField, true));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(oiType, 21L, 2L, "david", 211L, 21L),
            row(oiType, 21L, 2L, "david", 212L, 21L),
            row(oiType, 22L, 2L, "jack", 221L, 22L),
            row(oiType, 22L, 2L, "jack", 222L, 22L),
            row(oiType, 11L, 1L, "ori", 111L, 11L),
            row(oiType, 11L, 1L, "ori", 112L, 11L),
            row(oiType, 12L, 1L, "david", 121L, 12L),
            row(oiType, 12L, 1L, "david", 122L, 12L),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testDAD()
    {
        Operator flattenOI = flatten_HKeyOrdered(
            groupScan_Default(coi),
            orderRowType,
            itemRowType,
            JoinType.INNER_JOIN);
        RowType oiType = flattenOI.rowType();
        // flattenOI columns: oid, cid, salesman, iid, oid
        Expression cidField = field(oiType, 1);
        Expression oidField = field(oiType, 0);
        Expression iidField = field(oiType, 3);
        Operator plan =
            sort_Tree(
                filter_Default(
                    flattenOI,
                    Collections.singleton(oiType)),
                oiType,
                ordering(cidField, false, oidField, true, iidField, false));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(oiType, 21L, 2L, "david", 212L, 21L),
            row(oiType, 21L, 2L, "david", 211L, 21L),
            row(oiType, 22L, 2L, "jack", 222L, 22L),
            row(oiType, 22L, 2L, "jack", 221L, 22L),
            row(oiType, 11L, 1L, "ori", 112L, 11L),
            row(oiType, 11L, 1L, "ori", 111L, 11L),
            row(oiType, 12L, 1L, "david", 122L, 12L),
            row(oiType, 12L, 1L, "david", 121L, 12L),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testDDA()
    {
        Operator flattenOI = flatten_HKeyOrdered(
            groupScan_Default(coi),
            orderRowType,
            itemRowType,
            JoinType.INNER_JOIN);
        RowType oiType = flattenOI.rowType();
        // flattenOI columns: oid, cid, salesman, iid, oid
        Expression cidField = field(oiType, 1);
        Expression oidField = field(oiType, 0);
        Expression iidField = field(oiType, 3);
        Operator plan =
            sort_Tree(
                filter_Default(
                    flattenOI,
                    Collections.singleton(oiType)),
                oiType,
                ordering(cidField, false, oidField, false, iidField, true));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(oiType, 22L, 2L, "jack", 221L, 22L),
            row(oiType, 22L, 2L, "jack", 222L, 22L),
            row(oiType, 21L, 2L, "david", 211L, 21L),
            row(oiType, 21L, 2L, "david", 212L, 21L),
            row(oiType, 12L, 1L, "david", 121L, 12L),
            row(oiType, 12L, 1L, "david", 122L, 12L),
            row(oiType, 11L, 1L, "ori", 111L, 11L),
            row(oiType, 11L, 1L, "ori", 112L, 11L),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testDDD()
    {
        Operator flattenOI = flatten_HKeyOrdered(
            groupScan_Default(coi),
            orderRowType,
            itemRowType,
            JoinType.INNER_JOIN);
        RowType oiType = flattenOI.rowType();
        // flattenOI columns: oid, cid, salesman, iid, oid
        Expression cidField = field(oiType, 1);
        Expression oidField = field(oiType, 0);
        Expression iidField = field(oiType, 3);
        Operator plan =
            sort_Tree(
                filter_Default(
                    flattenOI,
                    Collections.singleton(oiType)),
                oiType,
                ordering(cidField, false, oidField, false, iidField, false));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(oiType, 22L, 2L, "jack", 222L, 22L),
            row(oiType, 22L, 2L, "jack", 221L, 22L),
            row(oiType, 21L, 2L, "david", 212L, 21L),
            row(oiType, 21L, 2L, "david", 211L, 21L),
            row(oiType, 12L, 1L, "david", 122L, 12L),
            row(oiType, 12L, 1L, "david", 121L, 12L),
            row(oiType, 11L, 1L, "ori", 112L, 11L),
            row(oiType, 11L, 1L, "ori", 111L, 11L),
        };
        compareRows(expected, cursor);
    }

    // Inspired by bug 871310
    @Test
    public void testReusedSort()
    {
        Expression iidField = field(itemRowType, 0);
        Expression oidField = field(itemRowType, 1);
        Operator plan =
            sort_Tree(
                sort_Tree(
                    filter_Default(
                        groupScan_Default(coi),
                        Collections.singleton(itemRowType)),
                    itemRowType,
                    ordering(iidField, true, oidField, true)),
                itemRowType,
                ordering(oidField, false, iidField, false));
        RowBase[] expected = new RowBase[]{
            row(itemRowType, 222L, 22L),
            row(itemRowType, 221L, 22L),
            row(itemRowType, 212L, 21L),
            row(itemRowType, 211L, 21L),
            row(itemRowType, 122L, 12L),
            row(itemRowType, 121L, 12L),
            row(itemRowType, 112L, 11L),
            row(itemRowType, 111L, 11L),
        };
        for (int i = 0; i < 10; i++) {
            compareRows(expected, cursor(plan, adapter));
        }
    }

    private Ordering ordering(Object... objects)
    {
        Ordering ordering = API.ordering();
        int i = 0;
        while (i < objects.length) {
            Expression expression = (Expression) objects[i++];
            Boolean ascending = (Boolean) objects[i++];
            ordering.append(expression, ascending);
        }
        return ordering;
    }

    private Row intRow(RowType rowType, int x)
    {
        return new ExpressionRow(rowType,
                                 UndefBindings.only(), null,
                                 Arrays.asList((Expression) new LiteralExpression(AkType.INT, x)));
    }
}

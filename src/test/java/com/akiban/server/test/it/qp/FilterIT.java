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

import com.akiban.qp.physicaloperator.Cursor;
import com.akiban.qp.physicaloperator.PhysicalOperator;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.server.api.dml.scan.NewRow;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.akiban.qp.physicaloperator.API.*;

public class FilterIT extends PhysicalOperatorITBase
{
    @Before
    public void before()
    {
        super.before();
        NewRow[] dbWithOrphans = new NewRow[]{
            createNewRow(customer, 1L, "northbridge"),
            createNewRow(customer, 2L, "foundation"),
            createNewRow(address, 1001L, 1L, "111 1111 st"),
            createNewRow(address, 1002L, 1L, "111 2222 st"),
            createNewRow(address, 2001L, 2L, "222 1111 st"),
            createNewRow(address, 2002L, 2L, "222 2222 st"),
            createNewRow(order, 11L, 1L, "ori"),
            createNewRow(order, 12L, 1L, "david"),
            createNewRow(order, 21L, 2L, "tom"),
            createNewRow(order, 22L, 2L, "jack"),
            createNewRow(item, 111L, 11L),
            createNewRow(item, 112L, 11L),
            createNewRow(item, 121L, 12L),
            createNewRow(item, 122L, 12L),
            createNewRow(item, 211L, 21L),
            createNewRow(item, 212L, 21L),
            createNewRow(item, 221L, 22L),
            createNewRow(item, 222L, 22L)
        };
        use(dbWithOrphans);
    }

    // Test argument validation

    @Test(expected = IllegalArgumentException.class)
    public void testNullKeepTypes()
    {
        filter_Default(groupScan_Default(coi), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyKeepTypes()
    {
        filter_Default(groupScan_Default(coi), Collections.<RowType>emptySet());
    }

    // Test operator execution (former Cut tests)

    @Test
    public void testCutBelowCustomer()
    {
        PhysicalOperator plan = filter_Default(groupScan_Default(coi), removeDescendentTypes(customerRowType));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 1L, "northbridge"),
            row(customerRowType, 2L, "foundation")
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testCutBelowOrder()
    {
        PhysicalOperator plan = filter_Default(groupScan_Default(coi), removeDescendentTypes(orderRowType));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 1L, "northbridge"),
            row(orderRowType, 11L, 1L, "ori"),
            row(orderRowType, 12L, 1L, "david"),
            row(addressRowType, 1001L, 1L, "111 1111 st"),
            row(addressRowType, 1002L, 1L, "111 2222 st"),
            row(customerRowType, 2L, "foundation"),
            row(orderRowType, 21L, 2L, "tom"),
            row(orderRowType, 22L, 2L, "jack"),
            row(addressRowType, 2001L, 2L, "222 1111 st"),
            row(addressRowType, 2002L, 2L, "222 2222 st")
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testCutBelowItem()
    {
        PhysicalOperator plan = filter_Default(groupScan_Default(coi), removeDescendentTypes(itemRowType));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 1L, "northbridge"),
            row(orderRowType, 11L, 1L, "ori"),
            row(itemRowType, 111L, 11L),
            row(itemRowType, 112L, 11L),
            row(orderRowType, 12L, 1L, "david"),
            row(itemRowType, 121L, 12L),
            row(itemRowType, 122L, 12L),
            row(addressRowType, 1001L, 1L, "111 1111 st"),
            row(addressRowType, 1002L, 1L, "111 2222 st"),
            row(customerRowType, 2L, "foundation"),
            row(orderRowType, 21L, 2L, "tom"),
            row(itemRowType, 211L, 21L),
            row(itemRowType, 212L, 21L),
            row(orderRowType, 22L, 2L, "jack"),
            row(itemRowType, 221L, 22L),
            row(itemRowType, 222L, 22L),
            row(addressRowType, 2001L, 2L, "222 1111 st"),
            row(addressRowType, 2002L, 2L, "222 2222 st")
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testCutBelowAddress()
    {
        PhysicalOperator plan = filter_Default(groupScan_Default(coi), removeDescendentTypes(addressRowType));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 1L, "northbridge"),
            row(orderRowType, 11L, 1L, "ori"),
            row(itemRowType, 111L, 11L),
            row(itemRowType, 112L, 11L),
            row(orderRowType, 12L, 1L, "david"),
            row(itemRowType, 121L, 12L),
            row(itemRowType, 122L, 12L),
            row(addressRowType, 1001L, 1L, "111 1111 st"),
            row(addressRowType, 1002L, 1L, "111 2222 st"),
            row(customerRowType, 2L, "foundation"),
            row(orderRowType, 21L, 2L, "tom"),
            row(itemRowType, 211L, 21L),
            row(itemRowType, 212L, 21L),
            row(orderRowType, 22L, 2L, "jack"),
            row(itemRowType, 221L, 22L),
            row(itemRowType, 222L, 22L),
            row(addressRowType, 2001L, 2L, "222 1111 st"),
            row(addressRowType, 2002L, 2L, "222 2222 st")
        };
        compareRows(expected, cursor);
    }

    // Test operator execution (former extract tests)

    @Test
    public void testExtractRoot()
    {
        PhysicalOperator plan = filter_Default(groupScan_Default(coi), typeAndDescendents(customerRowType));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(customerRowType, 1L, "northbridge"),
            row(orderRowType, 11L, 1L, "ori"),
            row(itemRowType, 111L, 11L),
            row(itemRowType, 112L, 11L),
            row(orderRowType, 12L, 1L, "david"),
            row(itemRowType, 121L, 12L),
            row(itemRowType, 122L, 12L),
            row(addressRowType, 1001L, 1L, "111 1111 st"),
            row(addressRowType, 1002L, 1L, "111 2222 st"),
            row(customerRowType, 2L, "foundation"),
            row(orderRowType, 21L, 2L, "tom"),
            row(itemRowType, 211L, 21L),
            row(itemRowType, 212L, 21L),
            row(orderRowType, 22L, 2L, "jack"),
            row(itemRowType, 221L, 22L),
            row(itemRowType, 222L, 22L),
            row(addressRowType, 2001L, 2L, "222 1111 st"),
            row(addressRowType, 2002L, 2L, "222 2222 st"),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testExtractLeaf()
    {
        PhysicalOperator plan = filter_Default(groupScan_Default(coi), typeAndDescendents(itemRowType));
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(itemRowType, 111L, 11L),
            row(itemRowType, 112L, 11L),
            row(itemRowType, 121L, 12L),
            row(itemRowType, 122L, 12L),
            row(itemRowType, 211L, 21L),
            row(itemRowType, 212L, 21L),
            row(itemRowType, 221L, 22L),
            row(itemRowType, 222L, 22L),
        };
        compareRows(expected, cursor);
    }

    @Test
    public void testExtractSiblings()
    {
        Set<RowType> keepTypes = new HashSet<RowType>();
        keepTypes.addAll(typeAndDescendents(addressRowType));
        keepTypes.addAll(typeAndDescendents(orderRowType));
        PhysicalOperator plan = filter_Default(groupScan_Default(coi), keepTypes);
        Cursor cursor = cursor(plan, adapter);
        RowBase[] expected = new RowBase[]{
            row(orderRowType, 11L, 1L, "ori"),
            row(itemRowType, 111L, 11L),
            row(itemRowType, 112L, 11L),
            row(orderRowType, 12L, 1L, "david"),
            row(itemRowType, 121L, 12L),
            row(itemRowType, 122L, 12L),
            row(addressRowType, 1001L, 1L, "111 1111 st"),
            row(addressRowType, 1002L, 1L, "111 2222 st"),
            row(orderRowType, 21L, 2L, "tom"),
            row(itemRowType, 211L, 21L),
            row(itemRowType, 212L, 21L),
            row(orderRowType, 22L, 2L, "jack"),
            row(itemRowType, 221L, 22L),
            row(itemRowType, 222L, 22L),
            row(addressRowType, 2001L, 2L, "222 1111 st"),
            row(addressRowType, 2002L, 2L, "222 2222 st"),
        };
        compareRows(expected, cursor);
    }

    private Set<RowType> removeDescendentTypes(RowType type)
    {
        Set<RowType> keepTypes = type.schema().userTableTypes();
        keepTypes.removeAll(Schema.descendentTypes(type, keepTypes));
        return keepTypes;
    }

    private Set<RowType> typeAndDescendents(RowType type)
    {
        Set<RowType> keepTypes= new HashSet<RowType>();
        keepTypes.addAll(Schema.descendentTypes(type, schema.userTableTypes()));
        keepTypes.add(type);
        return keepTypes;
    }
}

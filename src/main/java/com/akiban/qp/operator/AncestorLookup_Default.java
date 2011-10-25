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

package com.akiban.qp.operator;

import com.akiban.ais.model.GroupTable;
import com.akiban.ais.model.UserTable;
import com.akiban.qp.row.HKey;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.UserTableRowType;
import com.akiban.util.ArgumentValidation;
import com.akiban.util.ShareHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class AncestorLookup_Default extends Operator
{
    // Object interface

    @Override
    public String toString()
    {
        return String.format("%s(%s -> %s)", getClass().getSimpleName(), rowType, ancestorTypes);
    }

    // Operator interface

    @Override
    public void findDerivedTypes(Set<RowType> derivedTypes)
    {
        inputOperator.findDerivedTypes(derivedTypes);
    }

    @Override
    protected Cursor cursor(StoreAdapter adapter)
    {
        return new Execution(adapter, inputOperator.cursor(adapter));
    }

    @Override
    public List<Operator> getInputOperators()
    {
        List<Operator> result = new ArrayList<Operator>(1);
        result.add(inputOperator);
        return result;
    }

    @Override
    public String describePlan()
    {
        return describePlan(inputOperator);
    }

    // AncestorLookup_Default interface

    public AncestorLookup_Default(Operator inputOperator,
                                  GroupTable groupTable,
                                  RowType rowType,
                                  Collection<? extends RowType> ancestorTypes,
                                  API.LookupOption flag)
    {
        ArgumentValidation.notEmpty("ancestorTypes", ancestorTypes);
        // Keeping index rows not currently supported
        boolean inputFromIndex = rowType instanceof IndexRowType;
        ArgumentValidation.isTrue("!inputFromIndex || flag == API.LookupOption.DISCARD_INPUT",
                                  !inputFromIndex || flag == API.LookupOption.DISCARD_INPUT);
        RowType tableRowType =
            inputFromIndex
            ? ((IndexRowType) rowType).tableType()
            : rowType;
        // Each ancestorType must be an ancestor of rowType. ancestorType = tableRowType is OK only if the input
        // is from an index. I.e., this operator can be used for an index lookup.
        for (RowType ancestorType : ancestorTypes) {
            ArgumentValidation.isTrue("inputFromIndex || ancestorType1 != tableRowType",
                                      inputFromIndex || ancestorType != tableRowType);
            ArgumentValidation.isTrue("ancestorType.ancestorOf(tableRowType)",
                                      ancestorType.ancestorOf(tableRowType));
            ArgumentValidation.isTrue("ancestorType.userTable().getGroup() == tableRowType.userTable().getGroup()",
                                      ancestorType.userTable().getGroup() == tableRowType.userTable().getGroup());
        }
        this.inputOperator = inputOperator;
        this.groupTable = groupTable;
        this.rowType = rowType;
        this.keepInput = flag == API.LookupOption.KEEP_INPUT;
        // Sort ancestor types by depth
        this.ancestorTypes = new ArrayList<RowType>(ancestorTypes);
        if (this.ancestorTypes.size() > 1) {
            Collections.sort(this.ancestorTypes,
                             new Comparator<RowType>()
                             {
                                 @Override
                                 public int compare(RowType x, RowType y)
                                 {
                                     UserTable xTable = x.userTable();
                                     UserTable yTable = y.userTable();
                                     return xTable.getDepth() - yTable.getDepth();
                                 }
                             });
        }
        this.ancestorTypeDepth = new int[ancestorTypes.size()];
        int a = 0;
        for (RowType ancestorType : this.ancestorTypes) {
            UserTable userTable = ((UserTableRowType) ancestorType).userTable();
            this.ancestorTypeDepth[a++] = userTable.getDepth() + 1;
        }
    }

    // Class state

    private static final Logger LOG = LoggerFactory.getLogger(AncestorLookup_Default.class);

    // Object state

    private final Operator inputOperator;
    private final GroupTable groupTable;
    private final RowType rowType;
    private final List<RowType> ancestorTypes;
    private final int[] ancestorTypeDepth;
    private final boolean keepInput;

    // Inner classes

    private class Execution implements Cursor
    {
        // Cursor interface

        @Override
        public void open(Bindings bindings)
        {
            input.open(bindings);
            advance();
        }

        @Override
        public Row next()
        {
            adapter.checkQueryCancelation();
            while (pending.isEmpty() && inputRow.isHolding()) {
                advance();
            }
            Row row = pending.take();
            if (LOG.isDebugEnabled()) {
                LOG.debug("AncestorLookup: {}", row == null ? null : row);
            }
            return row;
        }

        @Override
        public void close()
        {
            input.close();
            ancestorRow.release();
            pending.clear();
        }

        // For use by this class

        private void advance()
        {
            Row currentRow = input.next();
            if (currentRow != null) {
                if (currentRow.rowType() == rowType) {
                    findAncestors(currentRow);
                }
                if (keepInput) {
                    pending.add(currentRow);
                }
                inputRow.hold(currentRow);
            } else {
                inputRow.release();
            }
        }

        private void findAncestors(Row inputRow)
        {
            assert pending.isEmpty();
            HKey hKey = inputRow.hKey();
            int nSegments = hKey.segments();
            for (int i = 0; i < ancestorTypeDepth.length; i++) {
                int depth = ancestorTypeDepth[i];
                hKey.useSegments(depth);
                readAncestorRow(hKey);
                if (ancestorRow.isHolding()) {
                    ancestorRow.get().runId(inputRow.runId());
                    pending.add(ancestorRow.get());
                }
            }
            // Restore the hkey to its original state
            hKey.useSegments(nSegments);
        }

        // Execution interface

        Execution(StoreAdapter adapter, Cursor input)
        {
            this.adapter = adapter;
            this.input = input;
            // Why + 1: Because the input row (whose ancestors get discovered) also goes into pending.
            this.pending = new PendingRows(ancestorTypeDepth.length + 1);
            this.ancestorCursor = adapter.newGroupCursor(groupTable);
        }

        // For use by this class

        private void readAncestorRow(HKey hKey)
        {
            try {
                ancestorCursor.rebind(hKey, false);
                ancestorCursor.open(UndefBindings.only());
                Row retrievedRow = ancestorCursor.next();
                if (retrievedRow == null) {
                    ancestorRow.release();
                } else {
                    // Retrieved row might not actually be what we were looking for -- not all ancestors are present,
                    // (there are orphan rows).
                    ancestorRow.hold(hKey.equals(retrievedRow.hKey()) ? retrievedRow : null);
                }
            } finally {
                ancestorCursor.close();
            }
        }

        // Object state

        private final StoreAdapter adapter;
        private final Cursor input;
        private final ShareHolder<Row> inputRow = new ShareHolder<Row>();
        private final GroupCursor ancestorCursor;
        private final ShareHolder<Row> ancestorRow = new ShareHolder<Row>();
        private final PendingRows pending;
    }
}

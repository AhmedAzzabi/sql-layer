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

package com.akiban.qp.persistitadapter;

import com.akiban.ais.model.*;
import com.akiban.qp.exec.UpdatePlannable;
import com.akiban.qp.exec.UpdateResult;
import com.akiban.qp.expression.IndexBound;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.operator.*;
import com.akiban.qp.row.Row;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.UserTableRowType;
import com.akiban.qp.util.SchemaCache;
import com.akiban.server.api.dml.ColumnSelector;
import com.akiban.server.api.dml.ConstantColumnSelector;
import com.akiban.server.api.dml.scan.LegacyRowWrapper;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.error.NoRowsUpdatedException;
import com.akiban.server.error.TooManyRowsUpdatedException;
import com.akiban.server.rowdata.RowData;
import com.akiban.server.rowdata.RowDataExtractor;
import com.akiban.server.rowdata.RowDataPValueSource;
import com.akiban.server.rowdata.RowDef;
import com.akiban.server.service.config.ConfigurationService;
import com.akiban.server.service.lock.LockService;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.transaction.TransactionService;
import com.akiban.server.service.tree.TreeService;
import com.akiban.server.store.DelegatingStore;
import com.akiban.server.store.PersistitStore;
import com.akiban.server.store.SchemaManager;
import com.akiban.server.types.ToObjectValueTarget;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types3.Types3Switch;
import com.akiban.sql.optimizer.rule.PlanGenerator;
import com.akiban.util.tap.InOutTap;
import com.akiban.util.tap.PointTap;
import com.akiban.util.tap.Tap;
import com.google.inject.Inject;
import com.persistit.Exchange;
import com.persistit.exception.PersistitException;

import java.util.*;

import static com.akiban.qp.operator.API.*;

public class OperatorStore extends DelegatingStore<PersistitStore> {
    /*
     * We instantiate another PersistitAdapter for doing scans/changes with the raw
     * PersistitStore explicitly passed. We don't want step management in this sub-adapter
     * or we'll get a double increment for each row (if we are already being called with it).
     */
    private static final boolean WITH_STEPS = false;


    private PersistitAdapter createAdapter(AkibanInformationSchema ais, Session session) {
        PersistitAdapter adapter =
            new PersistitAdapter(SchemaCache.globalSchema(ais),
                                 getPersistitStore(),
                                 treeService,
                                 session,
                                 config,
                                 WITH_STEPS);
        session.put(StoreAdapter.STORE_ADAPTER_KEY, adapter);
        return adapter;
    }

    // Store interface

    @Override
    public void updateRow(Session session, RowData oldRowData, RowData newRowData, ColumnSelector columnSelector, Index[] indexes)
        throws PersistitException
    {
        if(indexes != null) {
            throw new IllegalStateException("Unexpected indexes: " + Arrays.toString(indexes));
        }
        UPDATE_TOTAL.in();
        try {
            AkibanInformationSchema ais = schemaManager.getAis(session);
            RowDef rowDef = ais.getUserTable(oldRowData.getRowDefId()).rowDef();
            UserTable userTable = rowDef.userTable();
            PersistitAdapter adapter = createAdapter(ais, session);

            if(canSkipMaintenance(userTable)) {
                // PersistitStore needs full rows and OperatorStore will look them up (unspecified behavior),
                // so keep that behavior for the places that use it (tests only?)
                if(columnSelector == null || columnSelector == ConstantColumnSelector.ALL_ON) {
                    super.updateRow(session, oldRowData, newRowData, columnSelector, indexes);
                    return;
                }
            } else if (columnSelector != null) {
                throw new RuntimeException("group index maintenance won't work with partial rows");
            }

            BitSet changedColumnPositions = changedColumnPositions(rowDef, oldRowData, newRowData);
            UpdateFunction updateFunction = new InternalUpdateFunction(adapter, rowDef, newRowData, columnSelector);

            Group group = userTable.getGroup();
            final TableIndex index = userTable.getPrimaryKeyIncludingInternal().getIndex();
            assert index != null : userTable;
            UserTableRowType tableType = adapter.schema().userTableRowType(userTable);
            IndexRowType indexType = tableType.indexRowType(index);
            ColumnSelector indexColumnSelector =
                new ColumnSelector()
                {
                    public boolean includesColumn(int columnPosition)
                    {
                        return columnPosition < index.getKeyColumns().size();
                    }
                };
            IndexBound bound =
                new IndexBound(new NewRowBackedIndexRow(tableType, new LegacyRowWrapper(userTable.rowDef(), oldRowData), index),
                               indexColumnSelector);
            IndexKeyRange range = IndexKeyRange.bounded(indexType, bound, true, bound, true);

            Operator indexScan = indexScan_Default(indexType, false, range);
            Operator scanOp;
            scanOp = ancestorLookup_Default(indexScan, group, indexType, Collections.singletonList(tableType), API.InputPreservationOption.DISCARD_INPUT);

            // MVCC will render this useless, but for now, a limit of 1 ensures we won't see the row we just updated,
            // and therefore scan through two rows -- once to update old -> new, then to update new -> copy of new
            scanOp = limit_Default(scanOp, 1);

            UpdatePlannable updateOp = update_Default(scanOp, updateFunction);

            QueryContext context = new SimpleQueryContext(adapter);
            UPDATE_MAINTENANCE.in();
            try {
                maintainGroupIndexes(session,
                                     ais,
                                     adapter,
                                     oldRowData,
                                     changedColumnPositions,
                                     OperatorStoreGIHandler.forTable(adapter, userTable),
                                     OperatorStoreGIHandler.Action.DELETE);

                runCursor(oldRowData, rowDef, updateOp, context);

                maintainGroupIndexes(session,
                                     ais,
                                     adapter,
                                     newRowData,
                                     changedColumnPositions,
                                     OperatorStoreGIHandler.forTable(adapter, userTable),
                                     OperatorStoreGIHandler.Action.STORE);
            } finally {
                UPDATE_MAINTENANCE.out();
            }
        } finally {
            UPDATE_TOTAL.out();
        }
    }

    @Override
    public void writeRow(Session session, RowData rowData) throws PersistitException {
        INSERT_TOTAL.in();
        INSERT_MAINTENANCE.in();
        try {
            if (!isBulkloading()) {
                AkibanInformationSchema ais = schemaManager.getAis(session);
                PersistitAdapter adapter = createAdapter(ais, session);
                UserTable uTable = ais.getUserTable(rowData.getRowDefId());
                super.writeRow(session, rowData);
                maintainGroupIndexes(session,
                                     ais,
                                     adapter,
                                     rowData, null,
                                     OperatorStoreGIHandler.forTable(adapter, uTable),
                                     OperatorStoreGIHandler.Action.STORE);
            } else {
                super.writeRow(session, rowData);
            }
        } finally {
            INSERT_MAINTENANCE.out();
            INSERT_TOTAL.out();
        }
    }

    @Override
    public void deleteRow(Session session, RowData rowData, boolean deleteIndexes, boolean cascadeDelete) throws PersistitException {
        DELETE_TOTAL.in();
        DELETE_MAINTENANCE.in();
        try {
            AkibanInformationSchema ais = schemaManager.getAis(session);
            PersistitAdapter adapter = createAdapter(ais, session);
            UserTable uTable = ais.getUserTable(rowData.getRowDefId());

            if (cascadeDelete) {
                cascadeDeleteMaintainGroupIndex (session, ais, adapter, rowData);
            } else { // one row, one update to group indexes
                maintainGroupIndexes(session,
                                     ais,
                                     adapter,
                                     rowData,
                                     null,
                                     OperatorStoreGIHandler.forTable(adapter, uTable),
                                     OperatorStoreGIHandler.Action.DELETE);
                super.deleteRow(session, rowData, deleteIndexes, cascadeDelete);
            }
        } finally {
            DELETE_MAINTENANCE.out();
            DELETE_TOTAL.out();
        }
    }

    @Override
    public void buildIndexes(Session session, Collection<? extends Index> indexes, boolean defer) {
        List<TableIndex> tableIndexes = new ArrayList<>();
        List<GroupIndex> groupIndexes = new ArrayList<>();
        for(Index index : indexes) {
            if(index.isTableIndex()) {
                tableIndexes.add((TableIndex)index);
            }
            else if(index.isGroupIndex()) {
                groupIndexes.add((GroupIndex)index);
            }
            else {
                throw new IllegalArgumentException("Unknown index type: " + index);
            }
        }

        AkibanInformationSchema ais = schemaManager.getAis(session);
        PersistitAdapter adapter = createAdapter(ais, session);

        if(!tableIndexes.isEmpty()) {
            super.buildIndexes(session, tableIndexes, defer);
        }

        QueryContext context = new SimpleQueryContext(adapter);
        for(GroupIndex groupIndex : groupIndexes) {
            Operator plan = OperatorStoreMaintenancePlans.groupIndexCreationPlan(adapter.schema(), groupIndex);
            runMaintenancePlan(
                    context,
                    groupIndex,
                    plan,
                    OperatorStoreGIHandler.forBuilding(adapter),
                    OperatorStoreGIHandler.Action.STORE
            );
        }
    }

    // OperatorStore interface

    @Inject
    public OperatorStore(TreeService treeService, ConfigurationService config, SchemaManager schemaManager,
                         LockService lockService, TransactionService transactionService) {
        super(new PersistitStore(false, treeService, config, schemaManager, lockService, transactionService));
        this.treeService = treeService;
        this.config = config;
        this.schemaManager = schemaManager;
    }

    @Override
    public PersistitStore getPersistitStore() {
        return super.getDelegate();
    }

    // for use by subclasses

    protected Collection<GroupIndex> optionallyOrderGroupIndexes(Collection<GroupIndex> groupIndexes) {
        return groupIndexes;
    }

    // private methods

    private void maintainGroupIndexes(
            Session session,
            AkibanInformationSchema ais,
            PersistitAdapter adapter,
            RowData rowData,
            BitSet columnDifferences,
            OperatorStoreGIHandler handler,
            OperatorStoreGIHandler.Action action)
    throws PersistitException
    {
        UserTable userTable = ais.getUserTable(rowData.getRowDefId());
        if(canSkipMaintenance(userTable)) {
            return;
        }
        Exchange hEx = adapter.takeExchange(userTable.getGroup());
        try {
            // the "false" at the end of constructHKey toggles whether the RowData should be modified to increment
            // the hidden PK field, if there is one. For PK-less rows, this field have already been incremented by now,
            // so we don't want to increment it again
            getPersistitStore().constructHKey(session, hEx, userTable.rowDef(), rowData, false);
            PersistitHKey persistitHKey = new PersistitHKey(adapter, userTable.hKey());
            persistitHKey.copyFrom(hEx.getKey());

            Collection<GroupIndex> branchIndexes = userTable.getGroupIndexes();
            for (GroupIndex groupIndex : optionallyOrderGroupIndexes(branchIndexes)) {
                assert !groupIndex.isUnique() : "unique GI: " + groupIndex;
                if (columnDifferences == null || groupIndex.columnsOverlap(userTable, columnDifferences)) {
                    OperatorStoreMaintenance plan = groupIndexCreationPlan(
                            ais,
                            groupIndex,
                            adapter.schema().userTableRowType(userTable));
                    plan.run(action, persistitHKey, rowData, adapter, handler);
                } else {
                    SKIP_MAINTENANCE.hit();
                }
            }
        } finally {
            adapter.returnExchange(hEx);
        }
    }

    private void runMaintenancePlan(
            QueryContext context,
            GroupIndex groupIndex,
            Operator rootOperator,
            OperatorStoreGIHandler handler,
            OperatorStoreGIHandler.Action action)
    {
        Cursor cursor = API.cursor(rootOperator, context);
        cursor.open();
        try {
            Row row;
            while ((row = cursor.next()) != null) {
                if (row.rowType().equals(rootOperator.rowType())) {
                    handler.handleRow(groupIndex, row, action);
                }
            }
        } finally {
            cursor.destroy();
        }
    }
    
    private OperatorStoreMaintenance groupIndexCreationPlan(
            AkibanInformationSchema ais, GroupIndex groupIndex, UserTableRowType rowType
    ) {
        return OperatorStoreMaintenancePlans.forAis(ais).forRowType(groupIndex, rowType);
    }
    
    /*
     * This does the full cascading delete, updating both the group indexes for
     * each table affected and removing the rows.  
     * It does this root to leaf order. 
     */
    private void cascadeDeleteMaintainGroupIndex (Session session,
            AkibanInformationSchema ais, 
            PersistitAdapter adapter, 
            RowData rowData)
    throws PersistitException
    {
        UserTable uTable = ais.getUserTable(rowData.getRowDefId());

        Operator plan = PlanGenerator.generateBranchPlan(ais, uTable);
        
        QueryContext queryContext = new SimpleQueryContext(adapter);
        Cursor cursor = API.cursor(plan, queryContext);

        List<Column> lookupCols = uTable.getPrimaryKeyIncludingInternal().getColumns();
        RowDataPValueSource pSource = new RowDataPValueSource();
        for (int i=0; i < lookupCols.size(); ++i) {
            Column col = lookupCols.get(i);
            pSource.bind(col.getFieldDef(), rowData);
            queryContext.setPValue(i, pSource);
        }
        try {
            Row row;
            cursor.open();
            while ((row = cursor.next()) != null) {
                UserTable table = row.rowType().userTable();
                RowData data = rowData(adapter, table.rowDef(), row, new PValueRowDataCreator());
                maintainGroupIndexes(session,
                        ais,
                        adapter,
                        data,
                        null,
                        OperatorStoreGIHandler.forTable(adapter, uTable),
                        OperatorStoreGIHandler.Action.CASCADE);
            }
            cursor.close();
        } finally {
            cursor.destroy();
        }
        super.deleteRow(session, rowData, true, true);
    }

    private <S> RowData rowData (PersistitAdapter adapter, RowDef rowDef, RowBase row, RowDataCreator<S> creator) {
        if (row instanceof PersistitGroupRow) {
            return ((PersistitGroupRow) row).rowData();
        }
        NewRow niceRow = adapter.newRow(rowDef);
        for(int i = 0; i < row.rowType().nFields(); ++i) {
            S source = creator.eval(row, i);
            creator.put(source, niceRow, rowDef.getFieldDef(i), i);
        }
        return niceRow.toRowData();
    }
        


    // private static methods

    private static void runCursor(RowData oldRowData, RowDef rowDef, UpdatePlannable plannable, QueryContext context)
    {
        final UpdateResult result  = plannable.run(context);
        if (result.rowsModified() == 0 || result.rowsTouched() == 0) {
            throw new NoRowsUpdatedException (oldRowData, rowDef);
        }
        else if(result.rowsModified() != 1 || result.rowsTouched() != 1) {
            throw new TooManyRowsUpdatedException (oldRowData, rowDef, result);
        }
    }

    private static BitSet changedColumnPositions(RowDef rowDef, RowData a, RowData b)
    {
        int fields = rowDef.getFieldCount();
        BitSet differences = new BitSet(fields);
        for (int f = 0; f < fields; f++) {
            long aloc = rowDef.fieldLocation(a, f);
            long bloc = rowDef.fieldLocation(b, f);
            differences.set(f,
                            !bytesEqual(a.getBytes(),
                                        (int) aloc,
                                        (int) (aloc >>> 32),
                                        b.getBytes(),
                                        (int) bloc,
                                        (int) (bloc >>> 32)));
        }
        return differences;
    }

    static boolean bytesEqual(byte[] a, int aoffset, int asize, byte[] b, int boffset, int bsize)
    {
        if (asize != bsize) {
            return false;
        }
        for (int i = 0; i < asize; i++) {
            if (a[i + aoffset] != b[i + boffset]) {
                return false;
            }
        }
        return true;
    }

    private static boolean canSkipMaintenance(UserTable userTable) {
       return userTable.getGroupIndexes().isEmpty();
    }

    
    // object state
    private final ConfigurationService config;
    private final TreeService treeService;
    private final SchemaManager schemaManager;


    // consts

    private static final InOutTap INSERT_TOTAL = Tap.createTimer("write: write_total");
    private static final InOutTap UPDATE_TOTAL = Tap.createTimer("write: update_total");
    private static final InOutTap DELETE_TOTAL = Tap.createTimer("write: delete_total");
    private static final InOutTap INSERT_MAINTENANCE = Tap.createTimer("write: write_maintenance");
    private static final InOutTap UPDATE_MAINTENANCE = Tap.createTimer("write: update_maintenance");
    private static final InOutTap DELETE_MAINTENANCE = Tap.createTimer("write: delete_maintenance");
    private static final PointTap SKIP_MAINTENANCE = Tap.createCount("write: skip_maintenance");


    // nested classes

    private static class InternalUpdateFunction implements UpdateFunction {
        private final PersistitAdapter adapter;
        private final RowData newRowData;
        private final ColumnSelector columnSelector;
        private final RowDef rowDef;
        private final RowDataExtractor extractor;

        private InternalUpdateFunction(PersistitAdapter adapter, RowDef rowDef, RowData newRowData, ColumnSelector columnSelector) {
            this.newRowData = newRowData;
            this.columnSelector = columnSelector;
            this.rowDef = rowDef;
            this.adapter = adapter;
            this.extractor = new RowDataExtractor(newRowData, rowDef);
        }

        @Override
        public boolean rowIsSelected(Row row) {
            return row.rowType().typeId() == rowDef.getRowDefId();
        }

        @Override
        public Row evaluate(Row original, QueryContext context) {
            // TODO
            // ideally we'd like to use an OverlayingRow, but ModifiablePersistitGroupCursor requires
            // a PersistitGroupRow if an hkey changes
//            OverlayingRow overlay = new OverlayingRow(original);
//            for (int i=0; i < rowDef.getFieldCount(); ++i) {
//                if (columnSelector == null || columnSelector.includesColumn(i)) {
//                    overlay.overlay(i, newRowData.toObject(rowDef, i));
//                }
//            }
//            return overlay;
            // null selector means all cols, so we can skip the merging and just return the new row data
            if (columnSelector == null) {
                return PersistitGroupRow.newPersistitGroupRow(adapter, newRowData);
            }
            // Note: some encodings are untested except as necessary for mtr
            NewRow newRow = adapter.newRow(rowDef);
            ToObjectValueTarget target = new ToObjectValueTarget();
            for (int i=0; i < original.rowType().nFields(); ++i) {
                if (columnSelector.includesColumn(i)) {
                    Object value = extractor.get(rowDef.getFieldDef(i));
                    newRow.put(i, value);
                }
                else {
                    ValueSource source = original.eval(i);
                    newRow.put(i, target.convertFromSource(source));
                }
            }
            return PersistitGroupRow.newPersistitGroupRow(adapter, newRow.toRowData());
        }

        @Override
        public boolean usePValues() {
            return false;
        }
    }
}

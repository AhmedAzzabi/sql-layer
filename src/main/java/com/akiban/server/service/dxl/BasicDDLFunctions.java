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

package com.akiban.server.service.dxl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.DefaultNameGenerator;
import com.akiban.ais.model.Group;
import com.akiban.ais.model.GroupIndex;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexName;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.NopVisitor;
import com.akiban.ais.model.Routine;
import com.akiban.ais.model.Sequence;
import com.akiban.ais.model.SQLJJar;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.TableIndex;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.UserTable;
import com.akiban.ais.model.View;
import com.akiban.ais.util.ChangedTableDescription;
import com.akiban.ais.util.TableChange;
import com.akiban.ais.util.TableChangeValidator;
import com.akiban.ais.util.TableChangeValidatorException;
import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.operator.QueryContextBase;
import com.akiban.qp.operator.SimpleQueryContext;
import com.akiban.qp.operator.StoreAdapter;
import com.akiban.qp.row.OverlayingRow;
import com.akiban.qp.row.ProjectedRow;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.ConstraintChecker;
import com.akiban.qp.rowtype.ProjectedUserTableRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.qp.rowtype.UserTableRowChecker;
import com.akiban.qp.util.SchemaCache;
import com.akiban.server.AccumulatorAdapter;
import com.akiban.server.AccumulatorAdapter.AccumInfo;
import com.akiban.server.error.AlterMadeNoChangeException;
import com.akiban.server.error.ErrorCode;
import com.akiban.server.error.InvalidAlterException;
import com.akiban.server.error.QueryCanceledException;
import com.akiban.server.error.QueryTimedOutException;
import com.akiban.server.error.ViewReferencesExist;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.std.FieldExpression;
import com.akiban.server.expression.std.LiteralExpression;
import com.akiban.server.rowdata.RowDef;
import com.akiban.server.api.DDLFunctions;
import com.akiban.server.api.DMLFunctions;
import com.akiban.server.api.dml.scan.Cursor;
import com.akiban.server.api.dml.scan.CursorId;
import com.akiban.server.api.dml.scan.ScanRequest;
import com.akiban.server.error.DropSequenceNotAllowedException;
import com.akiban.server.error.ForeignConstraintDDLException;
import com.akiban.server.error.InvalidOperationException;
import com.akiban.server.error.NoSuchGroupException;
import com.akiban.server.error.NoSuchIndexException;
import com.akiban.server.error.NoSuchSequenceException;
import com.akiban.server.error.NoSuchTableException;
import com.akiban.server.error.NoSuchTableIdException;
import com.akiban.server.error.ProtectedIndexException;
import com.akiban.server.error.RowDefNotFoundException;
import com.akiban.server.error.UnsupportedDropException;
import com.akiban.server.service.ServiceManager;
import com.akiban.server.service.config.ConfigurationService;
import com.akiban.server.service.lock.LockService;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.transaction.TransactionService;
import com.akiban.server.service.tree.TreeLink;
import com.akiban.server.store.PersistitStore;
import com.akiban.server.t3expressions.T3RegistryService;
import com.akiban.server.types.AkType;
import com.akiban.server.types3.TCast;
import com.akiban.server.types3.TExecutionContext;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.Types3Switch;
import com.akiban.server.types3.mcompat.mtypes.MString;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueSources;
import com.akiban.server.types3.texpressions.TCastExpression;
import com.akiban.server.types3.texpressions.TPreparedExpression;
import com.akiban.server.types3.texpressions.TPreparedField;
import com.akiban.server.types3.texpressions.TPreparedLiteral;
import com.persistit.Exchange;
import com.akiban.server.service.tree.TreeService;
import com.akiban.server.store.SchemaManager;
import com.akiban.server.store.Store;
import com.akiban.server.store.statistics.IndexStatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.akiban.ais.util.TableChangeValidator.ChangeLevel;
import static com.akiban.ais.util.TableChangeValidator.TableColumnNames;
import static com.akiban.qp.operator.API.filter_Default;
import static com.akiban.qp.operator.API.groupScan_Default;
import static com.akiban.util.Exceptions.throwAlways;

class BasicDDLFunctions extends ClientAPIBase implements DDLFunctions {
    private final static Logger logger = LoggerFactory.getLogger(BasicDDLFunctions.class);

    private final static boolean DEFER_INDEX_BUILDING = false;
    private static final boolean ALTER_AUTO_INDEX_CHANGES = true;

    private final IndexStatisticsService indexStatisticsService;
    private final ConfigurationService configService;
    private final T3RegistryService t3Registry;
    private final LockService lockService;
    private final TransactionService txnService;
    

    private static class ShimContext extends QueryContextBase {
        private final StoreAdapter adapter;
        private final QueryContext delegate;

        public ShimContext(StoreAdapter adapter, QueryContext delegate) {
            this.adapter = adapter;
            this.delegate = (delegate == null) ? new SimpleQueryContext(adapter) : delegate;
        }

        @Override
        public StoreAdapter getStore() {
            return adapter;
        }

        @Override
        public StoreAdapter getStore(UserTable table) {
            return adapter;
        }

        @Override
        public Session getSession() {
            return delegate.getSession();
        }

        @Override
        public ServiceManager getServiceManager() {
            return delegate.getServiceManager();
        }

        @Override
        public String getCurrentUser() {
            return delegate.getCurrentUser();
        }

        @Override
        public String getSessionUser() {
            return delegate.getSessionUser();
        }

        @Override
        public String getCurrentSchema() {
            return delegate.getCurrentSchema();
        }

        @Override
        public int getSessionId() {
            return delegate.getSessionId();
        }

        @Override
        public void notifyClient(NotificationLevel level, ErrorCode errorCode, String message) {
            delegate.notifyClient(level, errorCode, message);
        }

        @Override
        public long sequenceNextValue(TableName sequence) {
            return delegate.sequenceNextValue(sequence);
        }

        @Override
        public long sequenceCurrentValue(TableName sequence) {
            return delegate.sequenceCurrentValue(sequence);
        }

        @Override
        public long getQueryTimeoutMilli() {
            return delegate.getQueryTimeoutMilli();
        }
    }


    @Override
    public void createTable(Session session, UserTable table)
    {
        TableName tableName = schemaManager().createTableDefinition(session, table);
        checkCursorsForDDLModification(session, getAIS(session).getTable(tableName));
    }

    @Override
    public void renameTable(Session session, TableName currentName, TableName newName)
    {
        schemaManager().renameTable(session, currentName, newName);
        checkCursorsForDDLModification(session, getAIS(session).getTable(newName));
    }

    @Override
    public void dropTable(Session session, TableName tableName)
    {
        final int tableID;
        txnService.beginTransaction(session);
        try {
            UserTable table = getAIS(session).getUserTable(tableName);
            // Dropping a non-existing table is a no-op
            if(table == null) {
                return;
            }
            tableID = table.getTableId();
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }

        lockTables(session, Arrays.asList(tableID));
        txnService.beginTransaction(session);
        try {
            dropTableInternal(session, tableName);
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }
    }

    private void dropTableInternal(Session session, TableName tableName) {
        logger.trace("dropping table {}", tableName);

        UserTable table = getAIS(session).getUserTable(tableName);
        if(table == null) {
            return;
        }

        // May only drop leaf tables through DDL interface
        if(!table.getChildJoins().isEmpty()) {
            throw new UnsupportedDropException(table.getName());
        }

        DMLFunctions dml = new BasicDMLFunctions(middleman(), schemaManager(), store(), treeService(), this);
        if(table.isRoot()) {
            // Root table and no child tables, can delete all associated trees
            store().removeTrees(session, table);
        } else {
            dml.truncateTable(session, table.getTableId());
            store().deleteIndexes(session, table.getIndexesIncludingInternal());
            store().deleteIndexes(session, table.getGroupIndexes());

            if (table.getIdentityColumn() != null) {
                Collection<Sequence> sequences = Collections.singleton(table.getIdentityColumn().getIdentityGenerator());
                store().deleteSequences(session, sequences);
            }
        }
        schemaManager().dropTableDefinition(session, tableName.getSchemaName(), tableName.getTableName(),
                                            SchemaManager.DropBehavior.RESTRICT);
        checkCursorsForDDLModification(session, table);
    }

    private void doMetadataChange(Session session, QueryContext context, UserTable origTable, UserTable newDefinition,
                                  Collection<ChangedTableDescription> changedTables, boolean nullChange,
                                  AlterTableHelper helper) {
        helper.dropAffectedGroupIndexes(session, this, origTable);
        if(nullChange) {
            // Check new definition
            final ConstraintChecker checker = new UserTableRowChecker(newDefinition);

            // But scan old
            final AkibanInformationSchema origAIS = getAIS(session);
            final Schema oldSchema = SchemaCache.globalSchema(origAIS);
            final RowType oldSourceType = oldSchema.userTableRowType(origTable);
            // Explicitly skip OperatorStore for now
            final StoreAdapter adapter = store().getPersistitStore().createAdapter(session, oldSchema);
            final QueryContext queryContext = new ShimContext(adapter, context);

            Operator plan = filter_Default(
                    groupScan_Default(origTable.getGroup()),
                    Collections.singleton(oldSourceType)
            );
            com.akiban.qp.operator.Cursor cursor = API.cursor(plan, queryContext);

            cursor.open();
            try {
                Row oldRow;
                while((oldRow = cursor.next()) != null) {
                    checker.checkConstraints(oldRow, Types3Switch.ON);
                }
            } finally {
                cursor.close();
            }
        }
        schemaManager().alterTableDefinitions(session, changedTables);

        UserTable newTable = getUserTable(session, newDefinition.getName());
        helper.createAffectedGroupIndexes(session, this, origTable, newTable, false);
    }

    private void doIndexChange(Session session, UserTable origTable, UserTable newDefinition,
                               Collection<ChangedTableDescription> changedTables, AlterTableHelper helper) {
        helper.dropAffectedGroupIndexes(session, this, origTable);
        schemaManager().alterTableDefinitions(session, changedTables);
        UserTable newTable = getUserTable(session, newDefinition.getName());
        List<Index> indexes = helper.findNewIndexesToBuild(newTable);
        store().buildIndexes(session, indexes, DEFER_INDEX_BUILDING);
        helper.createAffectedGroupIndexes(session, this, origTable, newTable, false);
    }

    private void doTableChange(Session session, QueryContext context, TableName tableName, UserTable newDefinition,
                               Collection<ChangedTableDescription> changedTables,
                               AlterTableHelper helper, boolean groupChange) {

        final boolean usePValues = Types3Switch.ON;

        final AkibanInformationSchema origAIS = getAIS(session);
        final UserTable origTable = origAIS.getUserTable(tableName);

        helper.dropAffectedGroupIndexes(session, this, origTable);

        // Save previous state so it can be scanned
        final Schema origSchema = SchemaCache.globalSchema(origAIS);
        final RowType origTableType = origSchema.userTableRowType(origTable);

        // Alter through schemaManager to get new definitions and RowDefs
        schemaManager().alterTableDefinitions(session, changedTables);

        // Build transformation
        // Explicitly skip OperatorStore for now
        final StoreAdapter adapter = store().getPersistitStore().createAdapter(session, origSchema);
        final QueryContext queryContext = new ShimContext(adapter, context);

        final AkibanInformationSchema newAIS = getAIS(session);
        final UserTable newTable = newAIS.getUserTable(newDefinition.getName());
        final Schema newSchema = SchemaCache.globalSchema(newAIS);

        final List<Column> newColumns = newTable.getColumnsIncludingInternal();
        final List<Expression> projections;
        final List<TPreparedExpression> pProjections;
        if(Types3Switch.ON) {
            projections = null;
            pProjections = new ArrayList<>(newColumns.size());
            for(Column newCol : newColumns) {
                Integer oldPosition = helper.findOldPosition(origTable, newCol);
                TInstance newInst = newCol.tInstance();
                if(oldPosition == null) {
                    final String defaultValue = newCol.getDefaultValue();
                    final PValueSource defaultValueSource;
                    if(defaultValue == null) {
                        defaultValueSource = PValueSources.getNullSource(newInst);
                    } else {
                        PValue defaultPValue = new PValue(newInst);
                        TInstance defInstance = MString.VARCHAR.instance(defaultValue.length(), defaultValue == null);
                        TExecutionContext executionContext = new TExecutionContext(
                                Collections.singletonList(defInstance),
                                newInst,
                                queryContext
                        );
                        PValue defaultSource = new PValue(MString.varcharFor(defaultValue), defaultValue);
                        newInst.typeClass().fromObject(executionContext, defaultSource, defaultPValue);
                        defaultValueSource = defaultPValue;
                    }
                    pProjections.add(new TPreparedLiteral(newInst, defaultValueSource));
                } else {
                    Column oldCol = origTable.getColumnsIncludingInternal().get(oldPosition);
                    TInstance oldInst = oldCol.tInstance();
                    TPreparedExpression pExp = new TPreparedField(oldInst, oldPosition);
                    if(!oldInst.equalsExcludingNullable(newInst)) {
                        TCast cast = t3Registry.getCastsResolver().cast(oldInst.typeClass(), newInst.typeClass());
                        pExp = new TCastExpression(pExp, cast, newInst, queryContext);
                    }
                    pProjections.add(pExp);
                }
            }
        } else {
            projections = new ArrayList<>(newColumns.size());
            pProjections = null;
            for(Column newCol : newColumns) {
                Integer oldPosition = helper.findOldPosition(origTable, newCol);
                if(oldPosition == null) {
                    String defaultValue = newCol.getDefaultValue();
                    projections.add(new LiteralExpression(AkType.VARCHAR, defaultValue));
                } else {
                    projections.add(new FieldExpression(origTableType, oldPosition));
                }
            }
        }

        // PUTRT for constraint checking
        final ProjectedUserTableRowType newTableType = new ProjectedUserTableRowType(newSchema, newTable, projections, pProjections, !groupChange);

        Index[] oldTypeIndexes = null;
        if(!groupChange) {
            List<Index> indexesToBuild = helper.findNewIndexesToBuild(newTable);
            oldTypeIndexes = indexesToBuild.toArray(new Index[indexesToBuild.size()]);
        }

        // - For non-group change, only need to scan the table being modified.
        // - For a group change, we need to scan entire group (catch all orphans).
        //   The process of deleting a parent will update its children, and updating
        //   orphans directly covers all rows. PersistitAdapter#alterRow() does the
        //   step handling so this scan is safe (deletes at current step, writes at +1)

        final Set<RowType> filteredTypes;
        final Map<RowType,RowType> typeMap;
        if(groupChange) {
            filteredTypes = new HashSet<>();
            typeMap = new HashMap<>();
            origTable.traverseTableAndDescendants(new NopVisitor() {
                @Override
                public void visitUserTable(UserTable table) {
                    RowType oldType = origSchema.userTableRowType(table);
                    RowType newType = (table == origTable)
                            ? newTableType
                            : newSchema.userTableRowType(newAIS.getUserTable(table.getName()));
                    filteredTypes.add(oldType);
                    typeMap.put(oldType, newType);
                }
            });
        } else {
            filteredTypes = Collections.singleton(origTableType);
            typeMap = Collections.<RowType,RowType>singletonMap(origTableType, newTableType);
        }

        Operator plan = filter_Default(
                groupScan_Default(origTable.getGroup()),
                filteredTypes
        );
        com.akiban.qp.operator.Cursor cursor = API.cursor(plan, queryContext);


        int step = adapter.enterUpdateStep(true);
        cursor.open();
        try {
            Row oldRow;
            while((oldRow = cursor.next()) != null) {
                RowType oldType = oldRow.rowType();
                if(oldType == origTableType) {
                    Row newRow = new ProjectedRow(newTableType,
                                                  oldRow,
                                                  queryContext,
                                                  projections,
                                                  ProjectedRow.createTEvaluatableExpressions(pProjections),
                                                  TInstance.createTInstances(pProjections));
                    queryContext.checkConstraints(newRow, usePValues);
                    adapter.alterRow(oldRow, newRow, oldTypeIndexes, groupChange, usePValues);
                } else {
                    RowType newType = typeMap.get(oldType);
                    Row newRow = new OverlayingRow(oldRow, newType, usePValues);
                    adapter.alterRow(oldRow, newRow, null, groupChange, usePValues);
                }
            }

            // Now rebuild any group indexes, leaving out empty ones
            adapter.enterUpdateStep();
            helper.createAffectedGroupIndexes(session, this, origTable, newTable, true);
        } finally {
            adapter.leaveUpdateStep(step);
            cursor.close();
        }
    }

    @Override
    public ChangeLevel alterTable(Session session, TableName tableName, UserTable newDefinition,
                                  List<TableChange> origColChanges, List<TableChange> origIndexChanges,
                                  QueryContext context)
    {
        final Set<Integer> tableIDs = new HashSet<>();
        final List<TableChange> columnChanges = new ArrayList<>(origColChanges);
        final List<TableChange> indexChanges = new ArrayList<>(origIndexChanges);
        final TableChangeValidator validator;
        txnService.beginTransaction(session);
        try {
            UserTable origTable = getUserTable(session, tableName);
            validator = new TableChangeValidator(origTable, newDefinition, columnChanges, indexChanges,
                                                 ALTER_AUTO_INDEX_CHANGES);

            try {
                validator.compareAndThrowIfNecessary();
            } catch(TableChangeValidatorException e) {
                throw new InvalidAlterException(tableName, e.getMessage());
            }

            TableName newParentName = null;
            for(ChangedTableDescription desc : validator.getAllChangedTables()) {
                UserTable table = getUserTable(session, desc.getOldName());
                tableIDs.add(table.getTableId());
                if(desc.getOldName().equals(tableName)) {
                    newParentName = desc.getParentName();
                }
            }

            // If this is a GROUPING change, we need to lock all the way up the old and new branches
            if(validator.getFinalChangeLevel() == ChangeLevel.GROUP) {
                // Old branch. Defensive because there can't currently be old parents
                UserTable parent = origTable.parentTable();
                collectAncestorTableIDs(tableIDs, parent);
                // New branch
                if(newParentName != null) {
                    collectAncestorTableIDs(tableIDs, getUserTable(session, newParentName));
                }
            }

            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }

        lockTables(session, new ArrayList<>(tableIDs));
        final ChangeLevel level;
        txnService.beginTransaction(session);
        try {
            level = alterTableInternal(session, tableName, newDefinition, columnChanges, indexChanges, validator, context);
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }
        return level;
    }

    private ChangeLevel alterTableInternal(Session session, TableName tableName, UserTable newDefinition,
                                           List<TableChange> columnChanges, List<TableChange> indexChanges,
                                           TableChangeValidator validator,
                                           QueryContext context)
    {
        final AkibanInformationSchema origAIS = getAIS(session);
        final UserTable origTable = getUserTable(session, tableName);

        ChangeLevel changeLevel;
        boolean success = false;
        boolean oldWasRootAndIsNewGroup = false;
        List<Index> indexesToDrop = new ArrayList<>();
        List<Sequence> sequencesToDrop = new ArrayList<>();
        List<IndexName> newIndexTrees = new ArrayList<>();

        try {
            changeLevel = validator.getFinalChangeLevel();
            Map<IndexName, List<TableColumnNames>> affectedGroupIndexes = validator.getAffectedGroupIndexes();
            Collection<ChangedTableDescription> changedTables = validator.getAllChangedTables();

            // Any GroupIndex changes are entirely derived, ignore any that happen to be passed.
            if(newDefinition.getGroup() != null) {
                newDefinition.getGroup().removeIndexes(newDefinition.getGroup().getIndexes());
            }

            AlterTableHelper helper = new AlterTableHelper(columnChanges, indexChanges, affectedGroupIndexes);

            for(ChangedTableDescription desc : changedTables) {
                for(TableName name : desc.getDroppedSequences()) {
                    sequencesToDrop.add(origAIS.getSequence(name));
                }
                UserTable oldTable = origAIS.getUserTable(desc.getOldName());
                for(Index index : oldTable.getIndexesIncludingInternal()) {
                    String indexName = index.getIndexName().getName();
                    if(!desc.getPreserveIndexes().containsKey(indexName) && !index.isPrimaryKey()) {
                        indexesToDrop.add(index);
                        newIndexTrees.add(new IndexName(desc.getNewName(), indexName));
                    }
                }
            }

            for(TableChange change : indexChanges) {
                if(change.getChangeType() == TableChange.ChangeType.ADD) {
                    newIndexTrees.add(new IndexName(newDefinition.getName(), change.getNewName()));
                }
            }

            if(!changeLevel.isNoneOrMetaData()) {
                Group group = origTable.getGroup();
                // entry.getValue().isEmpty() => index going away, non-empty will get rebuilt with new tree
                for(Map.Entry<IndexName, List<TableColumnNames>> entry : affectedGroupIndexes.entrySet()) {
                    indexesToDrop.add(group.getIndex(entry.getKey().getName()));
                }
            }

            switch(changeLevel) {
                case NONE:
                    assert affectedGroupIndexes.isEmpty() : affectedGroupIndexes;
                    AlterMadeNoChangeException error = new AlterMadeNoChangeException(tableName);
                    if(context != null) {
                        context.warnClient(error);
                    } else {
                        logger.warn(error.getMessage());
                    }
                break;

                case METADATA:
                    doMetadataChange(session, context, origTable, newDefinition, changedTables, false, helper);
                break;

                case METADATA_NOT_NULL:
                    doMetadataChange(session, context, origTable, newDefinition, changedTables, true, helper);
                break;

                case INDEX:
                    doIndexChange(session, origTable, newDefinition, changedTables, helper);
                break;

                case TABLE:
                    doTableChange(session, context, tableName, newDefinition, changedTables, helper, false);
                break;

                case GROUP:
                    // PRIMARY tree *must* be preserved due to accumulators. No way to dup accum state so must do this.
                    List<Index> indexesToTruncate = new ArrayList<>();
                    for(ChangedTableDescription desc : validator.getAllChangedTables()) {
                        UserTable oldTable = origAIS.getUserTable(desc.getOldName());
                        Index index = oldTable.getPrimaryKeyIncludingInternal().getIndex();
                        indexesToTruncate.add(index);
                        if((oldTable == origTable) && oldTable.isRoot() && desc.isNewGroup()) {
                            oldWasRootAndIsNewGroup = true;
                        }
                    }
                    store().truncateIndexes(session, indexesToTruncate);
                    doTableChange(session, context, tableName, newDefinition, changedTables, helper, true);
                break;

                default:
                    throw new IllegalStateException("Unhandled ChangeLevel: " + validator.getFinalChangeLevel());
            }

            success = true;
        } catch(Exception e) {
            if(!(e instanceof InvalidOperationException)) {
                logger.error("Rethrowing exception from failed ALTER", e);
            }
            throw throwAlways(e);
        } finally {
            // Tree creation is non-transactional in Persistit. They will be empty (entirely rolled back) but
            // still present. Remove them (group and index trees) for cleanliness.
            // NB: If sequences can ever be added through alter, need to handle those too.
            AkibanInformationSchema curAIS = getAIS(session);
            if(!success && (origAIS != curAIS)) {
                // Be extra careful with null checks.. In a failure state, don't know what was created.
                List<TreeLink> links = new ArrayList<>();
                if(oldWasRootAndIsNewGroup) {
                    UserTable newTable = curAIS.getUserTable(newDefinition.getName());
                    if(newTable != null) {
                        links.add(newTable.getGroup());
                    }
                }

                for(IndexName name : newIndexTrees) {
                    UserTable table = curAIS.getUserTable(name.getFullTableName());
                    if(table != null) {
                        Index index = table.getIndexIncludingInternal(name.getName());
                        if((index != null) && (index.indexDef() != null)) {
                            links.add(index.indexDef());
                        }
                    }
                }

                store().removeTrees(session, links);
            }
        }

        // Complete: we can now get rid of any trees that shouldn't be here
        store().deleteIndexes(session, indexesToDrop);
        store().deleteSequences(session, sequencesToDrop);
        if(oldWasRootAndIsNewGroup) {
            store().removeTrees(session, Collections.singleton(origTable.getGroup()));
        }
        return changeLevel;
    }

    @Override
    public void dropSchema(Session session, String schemaName)
    {
        logger.trace("dropping schema {}", schemaName);

        List<Integer> tableIDs = new ArrayList<>();
        txnService.beginTransaction(session);
        try {
            final com.akiban.ais.model.Schema schema = getAIS(session).getSchema(schemaName);
            if(schema != null) {
                for(Table table : schema.getUserTables().values()) {
                    tableIDs.add(table.getTableId());
                }
            }
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }

        lockTables(session, tableIDs);
        txnService.beginTransaction(session);
        try {
            dropSchemaInternal(session, schemaName);
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }
    }

    private void dropSchemaInternal(Session session, String schemaName) {
        final com.akiban.ais.model.Schema schema = getAIS(session).getSchema(schemaName);
        if (schema == null)
            return; // NOT throw new NoSuchSchemaException(schemaName); adapter does it.

        List<View> viewsToDrop = new ArrayList<>();
        Set<View> seen = new HashSet<>();
        for (View view : schema.getViews().values()) {
            addView(view, viewsToDrop, seen, schema, schemaName);
        }

        // Find all groups and tables in the schema
        Set<Group> groupsToDrop = new HashSet<>();
        List<UserTable> tablesToDrop = new ArrayList<>();

        for(UserTable table : schema.getUserTables().values()) {
            groupsToDrop.add(table.getGroup());
            // Cannot drop entire group if parent is not in the same schema
            final Join parentJoin = table.getParentJoin();
            if(parentJoin != null) {
                final UserTable parentTable = parentJoin.getParent();
                if(!parentTable.getName().getSchemaName().equals(schemaName)) {
                    tablesToDrop.add(table);
                }
            }
            // All children must be in the same schema
            for(Join childJoin : table.getChildJoins()) {
                final TableName childName = childJoin.getChild().getName();
                if(!childName.getSchemaName().equals(schemaName)) {
                    throw new ForeignConstraintDDLException(table.getName(), childName);
                }
            }
        }
        List<Sequence> sequencesToDrop = new ArrayList<>();
        for (Sequence sequence : schema.getSequences().values()) {
            // Drop the sequences in this schema, but not the 
            // generator sequences, which will be dropped with the table. 
            if (!(sequence.getSequenceName().getTableName().startsWith(DefaultNameGenerator.IDENTITY_SEQUENCE_PREFIX))) {
                sequencesToDrop.add(sequence);
            }
        }
        // Remove groups that contain tables in multiple schemas
        for(UserTable table : tablesToDrop) {
            groupsToDrop.remove(table.getGroup());
        }
        // Sort table IDs so higher (i.e. children) are first
        Collections.sort(tablesToDrop, new Comparator<UserTable>() {
            @Override
            public int compare(UserTable o1, UserTable o2) {

                return o2.getTableId().compareTo(o1.getTableId());
            }
        });
        List<Routine> routinesToDrop = new ArrayList<>(schema.getRoutines().values());
        List<SQLJJar> jarsToDrop = new ArrayList<>();
        for (SQLJJar jar : schema.getSQLJJars().values()) {
            boolean anyOutside = false;
            for (Routine routine : jar.getRoutines()) {
                if (!routine.getName().getSchemaName().equals(schemaName)) {
                    anyOutside = true;
                    break;
                }
            }
            if (!anyOutside)
                jarsToDrop.add(jar);
        }
        // Do the actual dropping
        for(View view : viewsToDrop) {
            dropView(session, view.getName());
        }
        for(UserTable table : tablesToDrop) {
            dropTableInternal(session, table.getName());
        }
        for(Group group : groupsToDrop) {
            dropGroupInternal(session, group.getName());
        }
        for (Sequence sequence : sequencesToDrop) {
            dropSequence(session, sequence.getSequenceName());
        }
        for (Routine routine : routinesToDrop) {
            dropRoutine(session, routine.getName());
        }
        for (SQLJJar jar : jarsToDrop) {
            dropSQLJJar(session, jar.getName());
        }
    }

    private void addView(View view, Collection<View> into, Collection<View> seen, 
                         com.akiban.ais.model.Schema schema, String schemaName) {
        if (seen.add(view)) {
            for (TableName reference : view.getTableReferences()) {
                if (!reference.getSchemaName().equals(schemaName)) {
                    throw new ViewReferencesExist(schemaName, 
                                                  view.getName().getTableName(),
                                                  reference.getSchemaName(),
                                                  reference.getTableName());
                }
                // If reference is to another view, it must come first.
                View refView = schema.getView(reference.getTableName());
                if (refView != null) {
                    addView(view, into, seen, schema, schemaName);
                }
            }
            into.add(view);
        }
    }

    @Override
    public void dropGroup(Session session, TableName groupName)
    {
        logger.trace("dropping group {}", groupName);

        List<Integer> tableIDs = new ArrayList<>();
        txnService.beginTransaction(session);
        try {
            AkibanInformationSchema ais = getAIS(session);
            Group group = ais.getGroup(groupName);
            if(group == null) {
                return;
            }
            for(Table table : ais.getUserTables().values()) {
                if(table.getGroup() == group) {
                    tableIDs.add(table.getTableId());
                }
            }
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }

        lockTables(session, tableIDs);
        txnService.beginTransaction(session);
        try {
            dropGroupInternal(session, groupName);
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }
    }

    private void dropGroupInternal(Session session, TableName groupName) {
        final Group group = getAIS(session).getGroup(groupName);
        if(group == null) {
            return;
        }
        store().dropGroup(session, group);
        final UserTable root = group.getRoot();
        schemaManager().dropTableDefinition(session, root.getName().getSchemaName(), root.getName().getTableName(),
                                            SchemaManager.DropBehavior.CASCADE);
        checkCursorsForDDLModification(session, root);
    }

    @Override
    public AkibanInformationSchema getAIS(final Session session) {
        logger.trace("getting AIS");
        return schemaManager().getAis(session);
    }

    @Override
    public int getTableId(Session session, TableName tableName) throws NoSuchTableException {
        logger.trace("getting table ID for {}", tableName);
        Table table = getAIS(session).getTable(tableName);
        if (table == null) {
            throw new NoSuchTableException(tableName);
        }
        return table.getTableId();
    }

    @Override
    public Table getTable(Session session, int tableId) throws NoSuchTableIdException {
        logger.trace("getting AIS Table for {}", tableId);
        Table table = getAIS(session).getUserTable(tableId);
        if(table == null) {
            throw new NoSuchTableIdException(tableId);
        }
        return table;
    }

    @Override
    public Table getTable(Session session, TableName tableName) throws NoSuchTableException {
        logger.trace("getting AIS Table for {}", tableName);
        AkibanInformationSchema ais = getAIS(session);
        Table table = ais.getTable(tableName);
        if (table == null) {
            throw new NoSuchTableException(tableName);
        }
        return table;
    }

    @Override
    public UserTable getUserTable(Session session, TableName tableName) throws NoSuchTableException {
        logger.trace("getting AIS UserTable for {}", tableName);
        AkibanInformationSchema ais = getAIS(session);
        UserTable table = ais.getUserTable(tableName);
        if (table == null) {
            throw new NoSuchTableException(tableName);
        }
        return table;
    }

    @Override
    public TableName getTableName(Session session, int tableId) throws NoSuchTableException {
        logger.trace("getting table name for {}", tableId);
        return getTable(session, tableId).getName();
    }

    @Override
    public RowDef getRowDef(Session session, int tableId) throws RowDefNotFoundException {
        logger.trace("getting RowDef for {}", tableId);
        return getAIS(session).getUserTable(tableId).rowDef();
    }

    @Override
    public int getGenerationAsInt(Session session) {
        long full = getGeneration(session);
        return (int)full ^ (int)(full >>> 32);
    }

    @Override
    public long getGeneration(Session session) {
        return getAIS(session).getGeneration();
    }

    @Override
    public long getOldestActiveGeneration() {
        return schemaManager().getOldestActiveAISGeneration();
    }

    @Override
    public void createIndexes(Session session, Collection<? extends Index> indexesToAdd) {
        logger.trace("creating indexes {}", indexesToAdd);
        if (indexesToAdd.isEmpty() == true) {
            return;
        }

        List<Integer> tableIDs = new ArrayList<>(indexesToAdd.size());
        txnService.beginTransaction(session);
        try {
            AkibanInformationSchema ais = getAIS(session);
            // Cannot use Index.getAllTableIDs(), stub AIS only has to be name-correct
            for(Index index : indexesToAdd) {
                switch(index.getIndexType()) {
                    case TABLE:
                    case FULL_TEXT: // TODO: More IDs?
                        UserTable table = ais.getUserTable(index.getIndexName().getFullTableName());
                        if(table != null) {
                            tableIDs.add(table.getTableId());
                        }
                    break;
                    case GROUP:
                        collectAncestorTableIDs(tableIDs, ais.getUserTable(index.leafMostTable().getName()));
                    break;
                    default:
                        throw new IllegalStateException("Unknown index type: " + index.getIndexType());
                }
            }
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }

        lockTables(session, tableIDs);
        Collection<Index> newIndexes = null;
        txnService.beginTransaction(session);
        try {
            newIndexes = createIndexesInternal(session, indexesToAdd);
            txnService.commitTransaction(session);
            newIndexes.clear();
        } finally {
            txnService.rollbackTransactionIfOpen(session);

            // If indexes left in list, transaction was not committed and trees aren't transactional. Try to clean up.
            if((newIndexes != null) && !newIndexes.isEmpty()) {
                Collection<TreeLink> links = new ArrayList<>(newIndexes.size());
                for(Index index : newIndexes) {
                    links.add(index.indexDef());
                }
                store().removeTrees(session, links);
            }
        }
    }

    Collection<Index> createIndexesInternal(Session session, Collection<? extends Index> indexesToAdd) {
        Collection<Index> newIndexes = schemaManager().createIndexes(session, indexesToAdd, false);
        for(Index index : newIndexes) {
            checkCursorsForDDLModification(session, index.leafMostTable());
        }
        store().buildIndexes(session, newIndexes, DEFER_INDEX_BUILDING);
        return newIndexes;
    }

    @Override
    public void dropTableIndexes(Session session, TableName tableName, Collection<String> indexNamesToDrop)
    {
        logger.trace("dropping table indexes {} {}", tableName, indexNamesToDrop);
        if(indexNamesToDrop.isEmpty() == true) {
            return;
        }

        final int tableID;
        txnService.beginTransaction(session);
        try {
            tableID = getTable(session, tableName).getTableId();
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }

        lockTables(session, Arrays.asList(tableID));
        txnService.beginTransaction(session);
        try {
            final Table table = getTable(session, tableName);
            Collection<Index> indexes = new HashSet<>();
            for(String indexName : indexNamesToDrop) {
                Index index = table.getIndex(indexName);
                if(index == null
                        && !(table.isUserTable()
                                && ((index = ((UserTable)table).getFullTextIndex(indexName)) != null))) {
                        throw new NoSuchIndexException (indexName);
                }
                if(index.isPrimaryKey()) {
                    throw new ProtectedIndexException(indexName, table.getName());
                }
                indexes.add(index);
            }
            schemaManager().dropIndexes(session, indexes);
            store().deleteIndexes(session, indexes);
            checkCursorsForDDLModification(session, table);
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }
    }

    @Override
    public void dropGroupIndexes(Session session, TableName groupName, Collection<String> indexNamesToDrop) {
        logger.trace("dropping group indexes {} {}", groupName, indexNamesToDrop);
        if(indexNamesToDrop.isEmpty()) {
            return;
        }

        List<Integer> tableIDs = new ArrayList<>(3);
        txnService.beginTransaction(session);
        try {
            Group group = getAIS(session).getGroup(groupName);
            if(group == null) {
                throw new NoSuchGroupException(groupName);
            }
            for(String name : indexNamesToDrop) {
                GroupIndex index = group.getIndex(name);
                if(index == null) {
                    throw new NoSuchIndexException(name);
                }
                tableIDs.addAll(index.getAllTableIDs());
            }
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }

        lockTables(session, tableIDs);
        txnService.beginTransaction(session);
        try {
            final Group group = getAIS(session).getGroup(groupName);
            if (group == null) {
                throw new NoSuchGroupException(groupName);
            }
            Collection<Index> indexes = new HashSet<>();
            for(String indexName : indexNamesToDrop) {
                final Index index = group.getIndex(indexName);
                if(index == null) {
                    throw new NoSuchIndexException(indexName);
                }
                indexes.add(index);
            }
            schemaManager().dropIndexes(session, indexes);
            store().deleteIndexes(session, indexes);
            txnService.commitTransaction(session);
        } finally {
            txnService.rollbackTransactionIfOpen(session);
        }
    }

    @Override
    public void updateTableStatistics(Session session, TableName tableName, Collection<String> indexesToUpdate) {
        final Table table = getTable(session, tableName);
        Collection<Index> indexes = new HashSet<>();
        if (indexesToUpdate == null) {
            indexes.addAll(table.getIndexes());
            for (Index index : table.getGroup().getIndexes()) {
                if (table == index.leafMostTable())
                    indexes.add(index);
            }
        }
        else {
            for (String indexName : indexesToUpdate) {
                Index index = table.getIndex(indexName);
                if (index == null) {
                    index = table.getGroup().getIndex(indexName);
                    if (index == null)
                        throw new NoSuchIndexException(indexName);
                }
                indexes.add(index);
            }
        }
        indexStatisticsService.updateIndexStatistics(session, indexes);
    }

    @Override
    public IndexCheckSummary checkAndFixIndexes(Session session, String schemaRegex, String tableRegex) {
        long startNs = System.nanoTime();
        Pattern schemaPattern = Pattern.compile(schemaRegex);
        Pattern tablePattern = Pattern.compile(tableRegex);
        List<IndexCheckResult> results = new ArrayList<>();
        AkibanInformationSchema ais = getAIS(session);

        for (Map.Entry<TableName,UserTable> entry : ais.getUserTables().entrySet()) {
            TableName tName = entry.getKey();
            if (schemaPattern.matcher(tName.getSchemaName()).find()
                    && tablePattern.matcher(tName.getTableName()).find())
            {
                UserTable uTable = entry.getValue();
                List<Index> indexes = new ArrayList<>();
                indexes.add(uTable.getPrimaryKeyIncludingInternal().getIndex());
                for (Index gi : uTable.getGroup().getIndexes()) {
                    if (gi.leafMostTable().equals(uTable))
                        indexes.add(gi);
                }
                for (Index index : indexes) {
                    IndexCheckResult indexCheckResult = checkAndFixIndex(session, index);
                    results.add(indexCheckResult);
                }
            }
        }
        long endNs = System.nanoTime();
        return new IndexCheckSummary(results,  endNs - startNs);
    }

    private IndexCheckResult checkAndFixIndex(Session session, Index index) {
        try {
            long expected = indexStatisticsService.countEntries(session, index);
            long actual = indexStatisticsService.countEntriesManually(session, index);
            if (expected != actual) {
                PersistitStore pStore = this.store().getPersistitStore();
                if (index.isTableIndex()) {
                    index.leafMostTable().rowDef().getTableStatus().setRowCount(session, actual);
                }
                else {
                    final Exchange ex = pStore.getExchange(session, index);
                    try {
                        AccumulatorAdapter accum = new AccumulatorAdapter(AccumInfo.ROW_COUNT, ex.getTree());
                        accum.set(actual);
                    }
                    finally {
                        pStore.releaseExchange(session, ex);
                    }
                }
            }
            return new IndexCheckResult(index.getIndexName(), expected, actual, indexStatisticsService.countEntries(session, index));
        }
        catch (Exception e) {
            logger.error("while checking/fixing " + index, e);
            return new IndexCheckResult(index.getIndexName(), -1, -1, -1);
        }
    }

    @Override
    public void createView(Session session, View view)
    {
        schemaManager().createView(session, view);
    }

    @Override
    public void dropView(Session session, TableName viewName)
    {
        schemaManager().dropView(session, viewName);
    }

    @Override
    public void createRoutine(Session session, Routine routine, boolean replaceExisting)
    {
        schemaManager().createRoutine(session, routine, replaceExisting);
    }

    @Override
    public void dropRoutine(Session session, TableName routineName)
    {
        schemaManager().dropRoutine(session, routineName);
    }

    @Override
    public void createSQLJJar(Session session, SQLJJar sqljJar) {
        schemaManager().createSQLJJar(session, sqljJar);
    }
    
    @Override
    public void replaceSQLJJar(Session session, SQLJJar sqljJar) {
        schemaManager().replaceSQLJJar(session, sqljJar);
    }
    
    @Override
    public void dropSQLJJar(Session session, TableName jarName) {
        schemaManager().dropSQLJJar(session, jarName);
    }

    private void checkCursorsForDDLModification(Session session, Table table) {
        Map<CursorId,BasicDXLMiddleman.ScanData> cursorsMap = getScanDataMap(session);
        if (cursorsMap == null) {
            return;
        }

        final int tableId = table.getTableId();
        for (BasicDXLMiddleman.ScanData scanData : cursorsMap.values()) {
            Cursor cursor = scanData.getCursor();
            if (cursor.isClosed()) {
                continue;
            }
            ScanRequest request = cursor.getScanRequest();
            int scanTableId = request.getTableId();
            if (scanTableId == tableId) {
                cursor.setDDLModified();
            }
        }
    }

    private void collectAncestorTableIDs(Collection<Integer> tableIDs, UserTable table) {
        while(table != null) {
            tableIDs.add(table.getTableId());
            table = table.parentTable();
        }
    }

    private void lockTables(Session session, List<Integer> tableIDs) {
        // No locks, whatsoever, expected to be held before DDL calls this
        assert !lockService.hasAnyClaims(session, LockService.Mode.SHARED) : "Shared claims";
        assert !lockService.hasAnyClaims(session, LockService.Mode.EXCLUSIVE) : "Shared claims";

        final LockService.Mode mode = LockService.Mode.EXCLUSIVE;
        /*
         * Lock order is well defined for DDL, but DML transactions can operate in any
         * order. Current strategy is to give preference to DML that has already began so
         * that a DDL doesn't kill off a (potentially large) amount of work and avoid
         * deadlocks.
         *
         * Strategy:
         * Claim (with timeout) the first table and if there are more, only use
         * instantaneous claims and back off already acquired locks if unsuccessful.
         */
        Collections.sort(tableIDs);
        final int count = tableIDs.size();
        for(int i = 0; i < count;) {
            final int tableID = tableIDs.get(i++);
            if(i == 0) {
                try {
                    if(session.hasTimeoutAfterNanos()) {
                        final long remaining = session.getRemainingNanosBeforeTimeout();
                        if(remaining <= 0 || !lockService.tryClaimTableNanos(session, mode, tableID, remaining)) {
                            throw new QueryTimedOutException(session.getElapsedMillis());
                        }
                    } else {
                        lockService.claimTableInterruptible(session, mode, tableID);
                    }
                } catch(InterruptedException e) {
                    throw new QueryCanceledException(session);
                }
            } else {
                if(!lockService.tryClaimTable(session, mode, tableID)) {
                    // Didn't get the new lock, unwind previous ones and start over
                    for(int j = 0; j < (i - 1); ++j) {
                        lockService.releaseTable(session, mode, tableIDs.get(j));
                    }
                    i = 0;
                }
            }
        }
    }
    
    public void createSequence(Session session, Sequence sequence) {
        schemaManager().createSequence (session, sequence);
    }
   
    public void dropSequence(Session session, TableName sequenceName) {
        final Sequence sequence = getAIS(session).getSequence(sequenceName);
        
        if (sequence == null) {
            throw new NoSuchSequenceException (sequenceName);
        }

        for (UserTable table : getAIS(session).getUserTables().values()) {
            if (table.getIdentityColumn() != null && table.getIdentityColumn().getIdentityGenerator().equals(sequence)) {
                throw new DropSequenceNotAllowedException(sequence.getSequenceName().getTableName(), table.getName());
            }
        }
        store().deleteSequences(session, Collections.singleton(sequence));
        schemaManager().dropSequence(session, sequence);
    }

    BasicDDLFunctions(BasicDXLMiddleman middleman, SchemaManager schemaManager, Store store, TreeService treeService,
                      IndexStatisticsService indexStatisticsService, ConfigurationService configService,
                      T3RegistryService t3Registry, LockService lockService, TransactionService txnService) {
        super(middleman, schemaManager, store, treeService);
        this.indexStatisticsService = indexStatisticsService;
        this.configService = configService;
        this.t3Registry = t3Registry;
        this.lockService = lockService;
        this.txnService = txnService;
    }
}

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

package com.akiban.ais.model;

import com.akiban.ais.AISCloner;
import com.akiban.ais.protobuf.ProtobufWriter;
import com.akiban.ais.util.ChangedTableDescription;
import com.akiban.server.error.DuplicateIndexException;
import com.akiban.server.error.IndexLacksColumnsException;
import com.akiban.server.error.JoinColumnTypesMismatchException;
import com.akiban.server.error.NoSuchColumnException;
import com.akiban.server.error.NoSuchGroupException;
import com.akiban.server.error.NoSuchTableException;
import com.akiban.server.error.ProtectedIndexException;
import com.akiban.server.error.TableNotInGroupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akiban.ais.model.validation.AISValidations;
import com.akiban.server.error.JoinToMultipleParentsException;
import com.akiban.server.error.JoinToUnknownTableException;
import com.akiban.server.error.JoinToWrongColumnsException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AISMerge is designed to merge a single UserTable definition into an existing AIS. The merge process 
 * does not assume that UserTable.getAIS() returns a validated and complete 
 * AkibanInformationSchema object. 
 * 
 * AISMerge makes a copy of the primaryAIS (from the constructor) before performing the merge process. 
 * The final results is this copies AIS, plus new table, with the full AISValidations suite run, and 
 * frozen. If you pass a frozen AIS into the merge, the copy process unfreeze the copy.
 */
public class AISMerge {
    public enum MergeType { ADD_TABLE, MODIFY_TABLE, ADD_INDEX, OTHER }

    private static class JoinChange {
        public final Join join;
        public final TableName newParentName;
        public final Map<String,String> parentCols;
        public final TableName newChildName;
        public final Map<String,String> childCols;
        public final boolean isNewGroup;

        private JoinChange(Join join, TableName newParentName, Map<String, String> parentCols,
                           TableName newChildName, Map<String, String> childCols, boolean isNewGroup) {
            this.join = join;
            this.newParentName = newParentName;
            this.parentCols = parentCols;
            this.newChildName = newChildName;
            this.childCols = childCols;
            this.isNewGroup = isNewGroup;
        }
    }

    private static class IndexInfo {
        public final Integer id;
        public final String tree;

        private IndexInfo(Integer id, String tree) {
            this.id = id;
            this.tree = tree;
        }
    }

    private static class IdentityInfo {
        public final TableName tableName;
        public final String columnName;
        public final boolean defaultIdentity;
        public final Sequence sequence;

        public IdentityInfo(TableName tableName, String columnName, boolean defaultIdentity, Sequence sequence) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.defaultIdentity = defaultIdentity;
            this.sequence = sequence;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AISMerge.class);

    /* state */
    private final AkibanInformationSchema targetAIS;
    private final UserTable sourceTable;
    private final NameGenerator nameGenerator;
    private final MergeType mergeType;
    private final List<JoinChange> changedJoins;
    private final Map<IndexName,IndexInfo> indexesToFix;
    private final List<IdentityInfo> identityToFix;


    /** Legacy test constructor. Creates an AISMerge for adding a table with a new {@link DefaultNameGenerator}. */
    AISMerge(AkibanInformationSchema sourceAIS, UserTable newTable) {
        this(new DefaultNameGenerator(sourceAIS), copyAISForAdd(sourceAIS), newTable, MergeType.ADD_TABLE, null, null, null);
    }

    /** Create a new AISMerge to be used for adding a new table. */
    public static AISMerge newForAddTable(NameGenerator generator, AkibanInformationSchema sourceAIS, UserTable newTable) {
        return new AISMerge(generator, copyAISForAdd(sourceAIS), newTable, MergeType.ADD_TABLE, null, null, null);
    }

    /** Create a new AISMerge to be used for modifying a table. */
    public static AISMerge newForModifyTable(NameGenerator generator, AkibanInformationSchema sourceAIS,
                                             Collection<ChangedTableDescription> alteredTables) {
        List<JoinChange> changedJoins = new ArrayList<>();
        Map<IndexName,IndexInfo> indexesToFix = new HashMap<>();
        List<IdentityInfo> identityToFix = new ArrayList<>();
        AkibanInformationSchema targetAIS = copyAISForModify(sourceAIS, indexesToFix, changedJoins, identityToFix, alteredTables);
        return new AISMerge(generator, targetAIS, null, MergeType.MODIFY_TABLE, changedJoins, indexesToFix, identityToFix);
    }

    /** Create a new AISMerge to be used for adding one, or more, index to a table. Also see {@link #mergeIndex(Index)}. */
    public static AISMerge newForAddIndex(NameGenerator generator, AkibanInformationSchema sourceAIS) {
        return new AISMerge(generator, copyAISForAdd(sourceAIS), null, MergeType.ADD_INDEX, null, null, null);
    }

    public static AISMerge newForOther(NameGenerator generator, AkibanInformationSchema sourceAIS) {
        return new AISMerge(generator, copyAISForAdd(sourceAIS), null, MergeType.OTHER, null, null, null);
    }

    private AISMerge(NameGenerator nameGenerator, AkibanInformationSchema targetAIS, UserTable sourceTable,
                     MergeType mergeType, List<JoinChange> changedJoins, Map<IndexName,IndexInfo> indexesToFix,
                     List<IdentityInfo> identityToFix) {
        this.nameGenerator = nameGenerator;
        this.targetAIS = targetAIS;
        this.sourceTable = sourceTable;
        this.mergeType = mergeType;
        this.changedJoins = changedJoins;
        this.indexesToFix = indexesToFix;
        this.identityToFix = identityToFix;
    }


    public static AkibanInformationSchema copyAISForAdd(AkibanInformationSchema oldAIS) {
        return AISCloner.clone(oldAIS);
    }

    private static AkibanInformationSchema copyAISForModify(AkibanInformationSchema oldAIS,
                                                            Map<IndexName,IndexInfo> indexesToFix,
                                                            final List<JoinChange> joinsToFix,
                                                            List<IdentityInfo> identityToFix,
                                                            Collection<ChangedTableDescription> changedTables)
    {
        final Set<Sequence> excludedSequences = new HashSet<>();
        final Set<Group> excludedGroups = new HashSet<>();
        final Map<TableName,UserTable> filteredTables = new HashMap<>();
        for(ChangedTableDescription desc : changedTables) {
            // Copy tree names and IDs for pre-existing table and it's indexes
            UserTable oldTable = oldAIS.getUserTable(desc.getOldName());
            UserTable newTable = desc.getNewDefinition();

            // These don't affect final outcome and may be reset later. Needed by clone process.
            if((newTable != null) && (newTable.getGroup() != null)) {
                newTable.setOrdinal(oldTable.getOrdinal());
                newTable.setTableId(oldTable.getTableId());
                newTable.getGroup().setTreeName(oldTable.getGroup().getTreeName());
            }

            switch(desc.getParentChange()) {
                case NONE:
                    // None: Handled by cloning process
                break;
                case META:
                case UPDATE: {
                    Join join = (newTable != null) ? newTable.getParentJoin() : oldTable.getParentJoin();
                    joinsToFix.add(new JoinChange(join, desc.getParentName(), desc.getParentColNames(),
                                                  desc.getNewName(), desc.getColNames(), false));
                } break;
                case ADD:
                    if(newTable == null) {
                        throw new IllegalArgumentException("Invalid change description: " + desc);
                    }
                    joinsToFix.add(new JoinChange(null, null, desc.getParentColNames(),
                                                  desc.getNewName(), desc.getColNames(), false));
                break;
                case DROP: {
                    final Join join;
                    if(newTable != null) {
                        join = newTable.getParentJoin();
                        excludedGroups.add(newTable.getGroup());
                    } else {
                        join = oldTable.getParentJoin();
                    }
                    joinsToFix.add(new JoinChange(join, null, desc.getParentColNames(),
                                                  desc.getNewName(), desc.getColNames(), true));
                } break;
                default:
                    throw new IllegalStateException("Unhandled GroupChange: " + desc.getParentChange());
            }

            UserTable indexSearchTable = newTable;
            if(newTable == null) {
                indexSearchTable = oldTable;
            } else {
                filteredTables.put(desc.getOldName(), newTable);
            }

            // Primary key trees must always be preserved (accum state cannot be duped)
            Index oldPrimary = oldTable.getPrimaryKeyIncludingInternal().getIndex();
            Integer oldID = desc.isNewGroup() ? null : oldPrimary.getIndexId();
            indexesToFix.put(new IndexName(desc.getNewName(), Index.PRIMARY_KEY_CONSTRAINT), new IndexInfo(oldID, oldPrimary.getTreeName()));

            for(Index newIndex : indexSearchTable.getIndexesIncludingInternal()) {
                if(newIndex.isPrimaryKey()) {
                    continue;
                }
                String oldName = desc.getPreserveIndexes().get(newIndex.getIndexName().getName());
                Index oldIndex = (oldName != null) ? oldTable.getIndexIncludingInternal(oldName) : null;
                if(oldIndex != null) {
                    indexesToFix.put(newIndex.getIndexName(), new IndexInfo(oldIndex.getIndexId(), oldIndex.getTreeName()));
                } else {
                    indexesToFix.put(newIndex.getIndexName(), new IndexInfo(null, null));
                }
            }

            for(TableName name : desc.getDroppedSequences()) {
                excludedSequences.add(oldAIS.getSequence(name));
            }

            for(String name : desc.getIdentityAdded()) {
                Column col = newTable.getColumn(name);
                identityToFix.add(new IdentityInfo(desc.getNewName(), name, col.getDefaultIdentity(), col.getIdentityGenerator()));
            }
        }

        return AISCloner.clone(
                oldAIS,
                new ProtobufWriter.TableFilterSelector() {
                    @Override
                    public Columnar getSelected(Columnar columnar) {
                        if(columnar.isTable()) {
                            Columnar filtered = filteredTables.get(columnar.getName());
                            if(filtered != null) {
                                return filtered;
                            }
                        }
                        return columnar;
                    }

                    @Override
                    public boolean isSelected(Group group) {
                        return !excludedGroups.contains(group);
                    }

                    @Override
                    public boolean isSelected(Join join) {
                        for(JoinChange tnj : joinsToFix) {
                            if(tnj.join == join) {
                                return false;
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean isSelected(Sequence sequence) {
                        return !excludedSequences.contains(sequence);
                    }
                }
        );
    }

    /**
     * Returns the final, updated AkibanInformationSchema. This AIS has been fully 
     * validated and is frozen (no more changes), hence ready for update into the
     * server. 
     * @return - the primaryAIS, after merge() with the UserTable added.
     */
    public AkibanInformationSchema getAIS () {
        return targetAIS;
    }
    
    public AISMerge merge() {
        switch(mergeType) {
            case ADD_TABLE:
                doAddTableMerge();
            break;
            case MODIFY_TABLE:
                doModifyTableMerge();
            break;
            case ADD_INDEX:
                doAddIndexMerge();
            break;
            default:
                throw new IllegalStateException("Unknown MergeType: " + mergeType);
        }
        return this;
    }

    public Index mergeIndex(Index index) {
        if(index.isPrimaryKey()) {
            throw new ProtectedIndexException("PRIMARY", index.getIndexName().getFullTableName());
        }

        final IndexName indexName = index.getIndexName();
        final Index curIndex;
        final Index newIndex;
        final Group newGroup;
        switch(index.getIndexType()) {
            case TABLE:
            {
                final TableName tableName = new TableName(indexName.getSchemaName(), indexName.getTableName());
                final UserTable newTable = targetAIS.getUserTable(tableName);
                if(newTable == null) {
                    throw new NoSuchTableException(tableName);
                }
                curIndex = newTable.getIndex(indexName.getName());
                newGroup = newTable.getGroup();
                Integer newId = newIndexID(newGroup);
                newIndex = TableIndex.create(targetAIS, newTable, indexName.getName(), newId, index.isUnique(),
                                             index.getConstraint());
            }
            break;
            case GROUP:
            {
                GroupIndex gi = (GroupIndex)index;
                newGroup = targetAIS.getGroup(gi.getGroup().getName());
                if(newGroup == null) {
                    throw new NoSuchGroupException(gi.getGroup().getName());
                }
                curIndex = newGroup.getIndex(indexName.getName());
                Integer newId = newIndexID(newGroup);
                newIndex = GroupIndex.create(targetAIS, newGroup, indexName.getName(), newId, index.isUnique(),
                                             index.getConstraint(), index.getJoinType());
            }
            break;
            case FULL_TEXT:
            {
                final TableName tableName = new TableName(indexName.getSchemaName(), indexName.getTableName());
                final UserTable newTable = targetAIS.getUserTable(tableName);
                if(newTable == null) {
                    throw new NoSuchTableException(tableName);
                }
                curIndex = newTable.getFullTextIndex(indexName.getName());
                newGroup = newTable.getGroup();
                Integer newId = newIndexID(newGroup);
                newIndex = FullTextIndex.create(targetAIS, newTable, indexName.getName(), newId);
            }
            break;
            default:
                throw new IllegalArgumentException("Unknown index type: " + index);
        }

        if(index.getIndexMethod() == Index.IndexMethod.Z_ORDER_LAT_LON) {
            newIndex.markSpatial(index.firstSpatialArgument(), index.dimensions());
        }

        if(curIndex != null) {
            throw new DuplicateIndexException(indexName);
        }
        if(index.getKeyColumns().isEmpty()) {
            throw new IndexLacksColumnsException(indexName);
        }

        for(IndexColumn indexCol : index.getKeyColumns()) {
            final TableName refTableName = indexCol.getColumn().getTable().getName();
            final UserTable newRefTable = targetAIS.getUserTable(refTableName);
            if(newRefTable == null) {
                throw new NoSuchTableException(refTableName);
            }
            if(!newRefTable.getGroup().equals(newGroup)) {
                throw new TableNotInGroupException(refTableName);
            }

            final Column column = indexCol.getColumn();
            final Column newColumn = newRefTable.getColumn(column.getName());
            if(newColumn == null) {
                throw new NoSuchColumnException(column.getName());
            }
            if(!column.getType().equals(newColumn.getType())) {
                throw new JoinColumnTypesMismatchException(index.getIndexName().getFullTableName(), column.getName(),
                                                           newRefTable.getName(), newColumn.getName());
            }
            // Calls (Group)Index.addColumn(), which checks all are in same branch
            IndexColumn.create(newIndex, newColumn, indexCol, indexCol.getPosition());
        }

        newIndex.setTreeName(nameGenerator.generateIndexTreeName(newIndex));
        newIndex.freezeColumns();

        return newIndex;
    }

    private void doAddTableMerge() {
        // I should use TableSubsetWriter(new AISTarget(targetAIS))
        // but that assumes the UserTable.getAIS() is complete and valid. 
        // i.e. has a group and group table, joins are accurate, etc. 
        // this may not be true 
        // Also the tableIDs need to be assigned correctly, which 
        // TableSubsetWriter doesn't do. 
        LOG.debug("Merging new table {} into targetAIS", sourceTable.getName());

        final AISBuilder builder = new AISBuilder(targetAIS, nameGenerator);

        Group targetGroup = null;
        if (sourceTable.getParentJoin() != null) {
            String parentSchemaName = sourceTable.getParentJoin().getParent().getName().getSchemaName();
            String parentTableName = sourceTable.getParentJoin().getParent().getName().getTableName(); 
            UserTable parentTable = targetAIS.getUserTable(parentSchemaName, parentTableName);
            if (parentTable == null) {
                throw new JoinToUnknownTableException (sourceTable.getName(), new TableName(parentSchemaName, parentTableName));
            }
            targetGroup = parentTable.getGroup();
        }

        // Add the user table to the targetAIS
        addTable (builder, sourceTable, targetGroup);

        // Joins or group table?
        if (sourceTable.getParentJoin() == null) {
            LOG.debug("Table is root or lone table");
            addNewGroup(builder, sourceTable);
        } else {
            // Normally there should be only one candidate parent join.
            // But since the AIS supports multiples, so does the merge.
            // This gets flagged in JoinToOneParent validation.
            for (Join join : sourceTable.getCandidateParentJoins()) {
                addJoin(builder, join, sourceTable);
            }
        }

        if (sourceTable.getPrimaryKey() != null) {
            TableIndex index = sourceTable.getPrimaryKey().getIndex();
            final int rootTableID = (targetGroup != null) ? 
                    targetGroup.getRoot().getTableId() : 
                        builder.akibanInformationSchema().getUserTable(sourceTable.getName()).getTableId();
            IndexName indexName = index.getIndexName();
            builder.index(sourceTable.getName().getSchemaName(), 
                    sourceTable.getName().getTableName(),
                    indexName.getName(), 
                    index.isUnique(), 
                    index.getConstraint(),
                    nameGenerator.generateIndexID(rootTableID));
            for (IndexColumn col : index.getKeyColumns()) {
                    builder.indexColumn(sourceTable.getName().getSchemaName(), 
                            sourceTable.getName().getTableName(),
                            index.getIndexName().getName(),
                        col.getColumn().getName(), 
                        col.getPosition(), 
                        col.isAscending(), 
                        col.getIndexedLength());
            }
        }

        builder.basicSchemaIsComplete();
        builder.groupingIsComplete();
        
        for (TableIndex index : sourceTable.getIndexes()) {
            if (!index.isPrimaryKey()) {
                mergeIndex(index);
            }
        }
        
        for (FullTextIndex index : sourceTable.getFullTextIndexes()) {
            mergeIndex(index);
        }
        
        builder.akibanInformationSchema().validate(AISValidations.LIVE_AIS_VALIDATIONS).throwIfNecessary();
        builder.akibanInformationSchema().freeze();
    }

    private void doModifyTableMerge() {
        AISBuilder builder = new AISBuilder(targetAIS, nameGenerator);

        // Fix up groups
        for(JoinChange tnj : changedJoins) {
            final UserTable table = targetAIS.getUserTable(tnj.newChildName);
            if(tnj.isNewGroup) {
                addNewGroup(builder, table);
            } else if(tnj.newParentName != null) {
                addJoin(builder, tnj.newParentName, tnj.parentCols, tnj.join, tnj.childCols, table);
            }
        }

        builder.basicSchemaIsComplete();
        builder.groupingIsComplete();

        for(Map.Entry<IndexName,IndexInfo> entry : indexesToFix.entrySet()) {
            IndexName name = entry.getKey();
            IndexInfo info = entry.getValue();
            UserTable table = targetAIS.getUserTable(name.getSchemaName(), name.getTableName());
            Index index = table.getIndexIncludingInternal(name.getName());
            index.setIndexId((info.id != null) ? info.id : newIndexID(table.getGroup()));
            index.setTreeName((info.tree != null) ? info.tree : nameGenerator.generateIndexTreeName(index));
        }

        for(IdentityInfo info : identityToFix) {
            addIdentitySequence(builder, info.tableName.getSchemaName(), info.tableName.getTableName(), info.columnName,
                                info.defaultIdentity, info.sequence);
        }

        builder.akibanInformationSchema().validate(AISValidations.LIVE_AIS_VALIDATIONS).throwIfNecessary();
        builder.akibanInformationSchema().freeze();
    }

    private void doAddIndexMerge() {
        AISBuilder builder = new AISBuilder(targetAIS, nameGenerator);
        builder.groupingIsComplete();
        builder.akibanInformationSchema().validate(AISValidations.LIVE_AIS_VALIDATIONS).throwIfNecessary();
        builder.akibanInformationSchema().freeze();
    }

    private void addTable(AISBuilder builder, final UserTable table, final Group targetGroup) {
        
        // I should use TableSubsetWriter(new AISTarget(targetAIS)) or AISCloner.clone()
        // but both assume the UserTable.getAIS() is complete and valid. 
        // i.e. has a group and group table, and the joins point to a valid table
        // which, given the use of AISMerge, is not true. 
        
        
        final String schemaName = table.getName().getSchemaName();
        final String tableName = table.getName().getTableName();
        

        builder.userTable(schemaName, tableName);
        UserTable targetTable = targetAIS.getUserTable(schemaName, tableName); 
        targetTable.setEngine(table.getEngine());
        targetTable.setCharsetAndCollation(table.getCharsetAndCollation());
        targetTable.setPendingOSC(table.getPendingOSC());
        targetTable.setUuid(table.getUuid());
        
        // columns
        for (Column column : table.getColumns()) {
            builder.column(schemaName, tableName, 
                    column.getName(), column.getPosition(), 
                    column.getType().name(), 
                    column.getTypeParameter1(), column.getTypeParameter2(), 
                    column.getNullable(), 
                    column.getInitialAutoIncrementValue() != null, 
                    column.getCharsetAndCollation().charset(), 
                    column.getCharsetAndCollation().collation(),
                    column.getDefaultValue(), column.getDefaultFunction());
            Column newColumn = targetTable.getColumn(column.getPosition());
            newColumn.setUuid(column.getUuid());
            // if an auto-increment column, set the starting value. 
            if (column.getInitialAutoIncrementValue() != null) {
                newColumn.setInitialAutoIncrementValue(column.getInitialAutoIncrementValue());
            }
            if (column.getDefaultIdentity() != null) {
                addIdentitySequence(builder, schemaName, tableName, column.getName(),
                                    column.getDefaultIdentity(), column.getIdentityGenerator());
            }
            // Proactively cache, can go away if Column ever cleans itself up
            newColumn.getMaxStorageSize();
            newColumn.getPrefixSize();
        }
    }

    private void addIdentitySequence(AISBuilder builder, String schemaName, String tableName, String column,
                                     boolean defaultIdentity, Sequence sequence) {
        TableName sequenceName = nameGenerator.generateIdentitySequenceName(new TableName(schemaName, tableName));
        Sequence newSeq = builder.sequence(sequenceName.getSchemaName(), sequenceName.getTableName(),
                                           sequence.getStartsWith(),
                                           sequence.getIncrement(),
                                           sequence.getMinValue(),
                                           sequence.getMaxValue(),
                                           sequence.isCycle());
        builder.columnAsIdentity(schemaName, tableName, column, sequenceName.getTableName(), defaultIdentity);
        LOG.debug("Generated sequence: {}, with tree name; {}", sequenceName, newSeq.getTreeName());
    }

    private void addNewGroup (AISBuilder builder, UserTable rootTable) {
        TableName groupName = rootTable.getName();
        builder.createGroup(groupName.getTableName(), groupName.getSchemaName());
        builder.addTableToGroup(groupName,
                                rootTable.getName().getSchemaName(),
                                rootTable.getName().getTableName());
    }

    private void addJoin (AISBuilder builder, Join join, UserTable childTable) {
        Map<String,String> emptyMap = Collections.emptyMap();
        addJoin(builder, join.getParent().getName(), emptyMap, join, emptyMap, childTable);
    }

    private static String getOrDefault(Map<String, String> map, String key) {
        String val = map.get(key);
        return (val != null) ? val : key;
    }

    private void addJoin (AISBuilder builder, TableName parentName, Map<String,String> parentCols,
                          Join join, Map<String,String> childCols, UserTable childTable) {
        String parentSchemaName = parentName.getSchemaName();
        String parentTableName = parentName.getTableName();
        UserTable parentTable = targetAIS.getUserTable(parentSchemaName, parentTableName);
        if (parentTable == null) {
            throw new JoinToUnknownTableException(childTable.getName(), new TableName(parentSchemaName, parentTableName));
         }
        LOG.debug(String.format("Table is child of table %s", parentTable.getName().toString()));
        String joinName = nameGenerator.generateJoinName(parentTable.getName(),
                                                         childTable.getName(),
                                                         join.getJoinColumns());
        builder.joinTables(joinName,
                parentSchemaName,
                parentTableName,
                childTable.getName().getSchemaName(),
                childTable.getName().getTableName());

        for (JoinColumn joinColumn : join.getJoinColumns()) {
            try {
            builder.joinColumns(joinName,
                    parentSchemaName,
                    parentTableName,
                    getOrDefault(parentCols, joinColumn.getParent().getName()),
                    childTable.getName().getSchemaName(),
                    childTable.getName().getTableName(),
                    getOrDefault(childCols, joinColumn.getChild().getName()));
            } catch (AISBuilder.NoSuchObjectException ex) {
                throw new JoinToWrongColumnsException (
                        childTable.getName(), joinColumn.getChild().getName(),
                        new TableName(parentSchemaName, parentTableName),
                        joinColumn.getParent().getName());
            }
        }
        builder.basicSchemaIsComplete();
        
        try {
            builder.addJoinToGroup(parentTable.getGroup().getName(), joinName, 0);
        } catch (AISBuilder.GroupStructureException ex) {
            throw new JoinToMultipleParentsException(join.getChild().getName());
        }
    }

    private int newIndexID(Group group) {
        return newIndexID(group.getRoot().getTableId());
    }

    private int newIndexID(int rootTableID) {
        return nameGenerator.generateIndexID(rootTableID);
    }

    public static AkibanInformationSchema mergeView(AkibanInformationSchema oldAIS,
                                                    View view) {
        AkibanInformationSchema newAIS = copyAISForAdd(oldAIS);
        copyView(newAIS, view);
        newAIS.validate(AISValidations.LIVE_AIS_VALIDATIONS).throwIfNecessary();
        newAIS.freeze();
        return newAIS;
    }

    public static void copyView(AkibanInformationSchema newAIS,
                                View oldView) {
        Map<TableName,Collection<String>> newReferences = 
            new HashMap<>();
        for (Map.Entry<TableName,Collection<String>> entry : oldView.getTableColumnReferences().entrySet()) {
            newReferences.put(entry.getKey(),
                              new HashSet<>(entry.getValue()));
        }
        View newView = View.create(newAIS,
                                   oldView.getName().getSchemaName(),
                                   oldView.getName().getTableName(),
                                   oldView.getDefinition(),
                                   oldView.getDefinitionProperties(),
                                   newReferences);
        for (Column col : oldView.getColumns()) {
            Column.create(newView, col.getName(), col.getPosition(),
                          col.getType(), col.getNullable(),
                          col.getTypeParameter1(), col.getTypeParameter2(), 
                          col.getInitialAutoIncrementValue(),
                          col.getCharsetAndCollation());
        }
        newAIS.addView(newView);
    }
    
    public AkibanInformationSchema mergeSequence(Sequence sequence)
    {
        mergeSequenceInternal(sequence);
        targetAIS.validate(AISValidations.LIVE_AIS_VALIDATIONS).throwIfNecessary();
        targetAIS.freeze();
        return targetAIS;
    }

    private Sequence mergeSequenceInternal(Sequence sequence)
    {
        Sequence newSeq = Sequence.create(targetAIS, sequence);
        newSeq.setTreeName(nameGenerator.generateSequenceTreeName(newSeq));
        return newSeq;
    }

    public static AkibanInformationSchema mergeRoutine(AkibanInformationSchema oldAIS,
                                                       Routine routine) {
        AkibanInformationSchema newAIS = copyAISForAdd(oldAIS);
        newAIS.addRoutine(routine);
        newAIS.validate(AISValidations.LIVE_AIS_VALIDATIONS).throwIfNecessary();
        newAIS.freeze();
        return newAIS;
    }

    public static AkibanInformationSchema mergeSQLJJar(AkibanInformationSchema oldAIS,
                                                       SQLJJar sqljJar) {
        AkibanInformationSchema newAIS = copyAISForAdd(oldAIS);
        newAIS.addSQLJJar(sqljJar);
        newAIS.validate(AISValidations.LIVE_AIS_VALIDATIONS).throwIfNecessary();
        newAIS.freeze();
        return newAIS;
    }
}

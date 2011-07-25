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

package com.akiban.ais.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.akiban.ais.gwtutils.GwtLogger;
import com.akiban.ais.gwtutils.GwtLogging;
import com.akiban.ais.model.Join.GroupingUsage;
import com.akiban.ais.model.Join.SourceType;
import com.akiban.ais.model.validation.AISInvariants;

// AISBuilder can be used to create an AIS. The API is designed to sify the creation of an AIS during a scan
// of a dump. The user need not search the AIS and hold on to AIS objects (UserTable, Column, etc.). Instead,
// only names from the dump need be supplied. 

public class
        AISBuilder {
    GwtLogger LOG = GwtLogging.getLogger(AISBuilder.class);

    // API for creating capturing basic schema information

    public AISBuilder() {
        this(new AkibanInformationSchema(), new DefaultNameGenerator());
    }

    public AISBuilder(NameGenerator nameGenerator) {
        this(new AkibanInformationSchema(), nameGenerator);
    }

    public AISBuilder(AkibanInformationSchema ais) {
        this(ais, new DefaultNameGenerator());
    }

    public AISBuilder(AkibanInformationSchema ais, NameGenerator nameGenerator) {
        LOG.trace("creating builder");
        this.ais = ais;
        this.nameGenerator = nameGenerator;
        // this.tableIdGenerator = (int)(Math.random() * 2500);
        this.tableIdGenerator = tableGeneratorBase += 1000;
        if (ais != null) {
            Map<TableName, UserTable> userTables = ais.getUserTables();
            Map<TableName, GroupTable> groupTables = ais.getGroupTables();
            // Yuval: this next line isn't actually necessary if we initialize
            // tableIdGenerator to random, but I'm
            // keeping it in case we change that randomness.
            this.tableIdGenerator += (userTables == null ? 0 : userTables
                    .size()) + (groupTables == null ? 0 : groupTables.size());
        }
    }

    /**
     * Studio may or may not require the static incrementing tableGeneratorBase
     * that is the default behavior. Let a consumer avoid that for now.
     * @param offset New offset for tableIdGenerator
     */
    public void setTableIdOffset(int offset) {
        this.tableIdGenerator = offset;
    }

    public void userTable(String schemaName, String tableName) {
        LOG.info("userTable: " + schemaName + "." + tableName);
        UserTable.create(ais, schemaName, tableName, tableIdGenerator++);
    }

    public void userTableInitialAutoIncrement(String schemaName,
            String tableName, Long initialAutoIncrementValue) {
        LOG.info("userTableInitialAutoIncrement: " + schemaName + "."
                + tableName + " = " + initialAutoIncrementValue);
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "setting initial autoincrement value", "user table",
                concat(schemaName, tableName));
        table.setInitialAutoIncrementValue(initialAutoIncrementValue);
    }

    public void column(String schemaName, String tableName, String columnName,
            Integer position, String typeName, Long typeParameter1,
            Long typeParameter2, Boolean nullable, Boolean autoIncrement,
            String charset, String collation) {
        LOG.info("column: " + schemaName + "." + tableName + "." + columnName);
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "creating column", "user table",
                concat(schemaName, tableName));
        Type type = ais.getType(typeName);
        checkFound(type, "creating column", "type", typeName);
        Column column = Column.create(table, columnName, position, type);
        column.setNullable(nullable);
        column.setAutoIncrement(autoIncrement);
        column.setTypeParameter1(typeParameter1);
        column.setTypeParameter2(typeParameter2);
        column.setCharset(charset);
        column.setCollation(collation);
    }

    /**
     * Create a new TableIndex
     */
    public void index(String schemaName, String tableName, String indexName,
            Boolean unique, String constraint) {
        LOG.info("index: " + schemaName + "." + tableName + "." + indexName);
        Table table = ais.getTable(schemaName, tableName);
        checkFound(table, "creating index", "table",
                concat(schemaName, tableName));
        TableIndex.create(ais, table, indexName, indexIdGenerator++, unique,
                constraint);
    }

    public void groupIndex(String groupName, String indexName, Boolean unique)
    {
        LOG.info("groupIndex: " + groupName + "." + indexName);
        Group group = ais.getGroup(groupName);
        checkFound(group, "creating group index", "group", groupName);
        String constraint = unique ? Index.UNIQUE_KEY_CONSTRAINT : Index.KEY_CONSTRAINT;
        GroupIndex.create(ais, group, indexName, indexIdGenerator++, unique, constraint);
    }

    public void indexColumn(String schemaName, String tableName,
            String indexName, String columnName, Integer position,
            Boolean ascending, Integer indexedLength) {
        LOG.info("indexColumn: " + schemaName + "." + tableName + "."
                + indexName + ":" + columnName);
        Table table = ais.getTable(schemaName, tableName);
        checkFound(table, "creating index column", "table",
                concat(schemaName, tableName));
        Column column = table.getColumn(columnName);
        checkFound(column, "creating index column", "column",
                concat(schemaName, tableName, columnName));
        Index index = table.getIndex(indexName);
        checkFound(table, "creating index column", "index",
                concat(schemaName, tableName, indexName));
        index.addColumn(new IndexColumn(index, column, position, ascending,
                indexedLength));
    }

    public void groupIndexColumn(String groupName, String indexName, String schemaName, String tableName,
                                 String columnName, Integer position)
    {
        LOG.info("groupIndexColumn: " + groupName + "." + indexName + ":" + columnName);
        Group group = ais.getGroup(groupName);
        checkFound(group, "creating group index column", "group", groupName);
        Index index = group.getIndex(indexName);
        checkFound(index, "creating group index column", "index", concat(groupName, indexName));
        Table table = ais.getTable(schemaName, tableName);
        if (table.getGroup() == null) {
            throw new IllegalArgumentException("table is ungrouped: " + table);
        }
        if (!table.getGroup().getName().equals(groupName)) {
            throw new IllegalArgumentException("group name mismatch: " + groupName + " != " + table.getGroup());
        }
        checkFound(table, "creating group index column", "table", concat(schemaName, tableName));
        Column column = table.getColumn(columnName);
        checkFound(column, "creating group index column", "column", concat(schemaName, tableName, columnName));
        index.addColumn(new IndexColumn(index, column, position, true, null));
    }

    public void joinTables(String joinName, String parentSchemaName,
            String parentTableName, String childSchemaName,
            String childTableName) {
        LOG.info("joinTables: " + joinName + ": " + childSchemaName + "."
                + childTableName + " -> " + parentSchemaName + "."
                + parentTableName);
        UserTable child = ais.getUserTable(childSchemaName, childTableName);
        checkFound(child, "creating join", "child table",
                concat(childSchemaName, childTableName));
        UserTable parent = ais.getUserTable(parentSchemaName, parentTableName);
        if (parent == null) {
            TableName parentName = new TableName(parentSchemaName,
                    parentTableName);
            ForwardTableReference forwardTableReference = new ForwardTableReference(
                    joinName, parentName, child);
            forwardReferences.put(joinName, forwardTableReference);
        } else {
            Join.create(ais, joinName, parent, child);
        }
    }

    public void joinColumns(String joinName, String parentSchemaName,
            String parentTableName, String parentColumnName,
            String childSchemaName, String childTableName,
            String childColumnName)

    {
        LOG.info("joinColumns: " + joinName + ": " + childSchemaName + "."
                + childTableName + "." + childColumnName + " -> "
                + parentSchemaName + "." + parentTableName + "."
                + parentColumnName);
        // Get child info
        UserTable childTable = ais
                .getUserTable(childSchemaName, childTableName);
        checkFound(childTable, "creating join column", "child table",
                concat(childSchemaName, childTableName));
        Column childColumn = childTable.getColumn(childColumnName);
        checkFound(childColumn, "creating join column", "child column",
                concat(childSchemaName, childTableName, childColumnName));
        // Handle parent - could be a forward reference
        UserTable parentTable = ais.getUserTable(parentSchemaName,
                parentTableName);
        if (parentTable == null) {
            // forward reference
            ForwardTableReference forwardTableReference = forwardReferences
                    .get(joinName);
            forwardTableReference.addColumnReference(parentColumnName,
                    childColumn);
        } else {
            // we've seen the child table
            Column parentColumn = parentTable.getColumn(parentColumnName);
            checkFound(parentColumn, "creating join column", "parent column",
                    concat(parentSchemaName, parentTableName, parentColumnName));
            Join join = ais.getJoin(joinName);
            checkFound(
                    join,
                    "creating join column",
                    "join",
                    concat(parentSchemaName, parentTableName, parentColumnName)
                            + "/"
                            + concat(childSchemaName, childTableName,
                                    childColumnName));
            join.addJoinColumn(parentColumn, childColumn);
        }
    }

    public void basicSchemaIsComplete() {
        LOG.info("basicSchemaIsComplete");
        for (UserTable userTable : ais.getUserTables().values()) {
            userTable.endTable();
        }
        for (ForwardTableReference forwardTableReference : forwardReferences.values()) {
            UserTable childTable = forwardTableReference.childTable();
            UserTable parentTable = ais.getUserTable(forwardTableReference
                    .parentTableName().getSchemaName(), forwardTableReference
                    .parentTableName().getTableName());
            
            if (parentTable != null){
                Join join = Join.create(ais, forwardTableReference.joinName(),
                        parentTable, childTable);
                for (ForwardColumnReference forwardColumnReference : forwardTableReference
                        .forwardColumnReferences()) {
                    Column childColumn = forwardColumnReference.childColumn();
                    Column parentColumn = parentTable
                            .getColumn(forwardColumnReference.parentColumnName());
                    checkFound(childColumn, "marking basic schema complete",
                            "parent column",
                            forwardColumnReference.parentColumnName());
                    join.addJoinColumn(parentColumn, childColumn);
                }
            }
        }
        forwardReferences.clear();
    }

    private String computeTreeName(String groupSchemaName, String groupTableName) {
        String proposedName = groupSchemaName + "$$" + groupTableName;
        Collection<GroupTable> groupTables = ais.getGroupTables().values();
        int saw = 0;
        while(saw < groupTables.size()) {
            saw = 0;
            for(GroupTable table : groupTables) {
                if(table.getTreeName().equals(proposedName)) {
                    proposedName += "+";
                    break;
                }
                ++saw;
            }
        }
        return proposedName;
    }

    // API for describing groups

    public void createGroup(String groupName, String groupSchemaName,
            String groupTableName) {
        LOG.info("createGroup: " + groupName + " -> " + groupSchemaName + "."
                + groupTableName);
        String treeName = computeTreeName(groupSchemaName, groupTableName);
        GroupTable groupTable = GroupTable.create(ais, groupSchemaName, groupTableName, tableIdGenerator++);
        Group group = Group.create(ais, groupName);
        groupTable.setTreeName(treeName);
        groupTable.setGroup(group);
    }

    public void deleteGroup(String groupName) {
        LOG.info("deleteGroup: " + groupName);
        Group group = ais.getGroup(groupName);
        checkFound(group, "deleting group", "group", groupName);
        boolean groupEmpty = true;
        for (UserTable userTable : ais.getUserTables().values()) {
            if (userTable.getGroup() == group) {
                groupEmpty = false;
            }
        }
        if (groupEmpty) {
            ais.deleteGroupAndGroupTable(group);
        } else {
            throw new GroupNotEmptyException(group);
        }
    }

    public void addTableToGroup(String groupName, String schemaName,
            String tableName) {
        LOG.info("addTableToGroup: " + groupName + ": " + schemaName + "."
                + tableName);
        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "adding table to group", "group", groupName);
        // table
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "adding table to group", "table",
                concat(schemaName, tableName));
        checkGroupAddition(group, table.getGroup(),
                concat(schemaName, tableName));
        setTablesGroup(table, group);
        // group table columns
        generateGroupTableColumns(group);
    }

    // addJoinToGroup and removeJoinFromGroup identify a join based on parent
    // and child tables. This is OK for
    // removeJoinFromGroup because of the restrictions on group structure. It
    // DOES NOT WORK for addJoinToGroup,
    // because there could be multiple candidate joins between a pair of tables.

    public void addJoinToGroup(String groupName, String joinName, Integer weight) {
        LOG.info("addJoinToGroup: " + groupName + ": " + joinName);
        // join
        Join join = ais.getJoin(joinName);
        checkFound(join, "adding join to group", "join", joinName);
        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "adding join to group", "group", groupName);
        // parent
        String parentSchemaName = join.getParent().getName().getSchemaName();
        String parentTableName = join.getParent().getName().getTableName();
        UserTable parent = ais.getUserTable(parentSchemaName, parentTableName);
        checkFound(parent, "adding join to group", "parent table",
                concat(parentSchemaName, parentTableName));
        checkGroupAddition(group, parent.getGroup(),
                concat(parentSchemaName, parentTableName));
        setTablesGroup(parent, group);
        // child
        String childSchemaName = join.getChild().getName().getSchemaName();
        String childTableName = join.getChild().getName().getTableName();
        UserTable child = ais.getUserTable(childSchemaName, childTableName);
        checkFound(child, "adding join to group", "child table",
                concat(childSchemaName, childTableName));
        checkGroupAddition(group, child.getGroup(),
                concat(childSchemaName, childTableName));
        checkCycle(child, group);
        setTablesGroup(child, group);
        join.setGroup(group);
        join.setWeight(weight);
        assert join.getParent() == parent : join;
        checkGroupAddition(group, join.getGroup(), joinName);
        generateGroupTableColumns(group);
    }

    public void removeTableFromGroup(String groupName, String schemaName,
            String tableName) {
        LOG.info("removeTableFromGroup: " + groupName + ": " + schemaName + "."
                + tableName);
        // This is only valid for a single-table group.
        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "removing join from group", "group", groupName);
        // table
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "removing join from group", "table table",
                concat(schemaName, tableName));
        checkInGroup(group, table, "removing join from group", "table table");
        if (table.getParentJoin() != null || !table.getChildJoins().isEmpty()) {
            throw new GroupStructureException(
                    "Cannot remove table from a group unless "
                            + "it is the only table in the group, group "
                            + group.getName() + ", table " + table.getName());
        }
        setTablesGroup(table, null);
        generateGroupTableColumns(group);
    }

    public void removeJoinFromGroup(String groupName, String joinName) {
        LOG.info("removeJoinFromGroup: " + groupName + ": " + joinName);
        // join
        Join join = ais.getJoin(joinName);
        checkFound(join, "removing join from group", "join", joinName);
        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "removing join from group", "group", groupName);
        checkInGroup(group, join, "removing join from group", "child table");
        // parent
        String parentSchemaName = join.getParent().getName().getSchemaName();
        String parentTableName = join.getParent().getName().getTableName();
        UserTable parent = ais.getUserTable(parentSchemaName, parentTableName);
        checkFound(parent, "removing join from group", "parent table",
                concat(parentSchemaName, parentTableName));
        checkInGroup(group, parent, "removing join from group", "parent table");
        // child
        String childSchemaName = join.getChild().getName().getSchemaName();
        String childTableName = join.getChild().getName().getTableName();
        UserTable child = ais.getUserTable(childSchemaName, childTableName);
        checkFound(child, "removing join from group", "child table",
                concat(childSchemaName, childTableName));
        checkInGroup(group, child, "removing join from group", "child table");
        // Remove the join from the group
        join.setGroup(null);
        // Remove the parent from the group if it isn't involved in any other
        // joins in this group.
        if (parent.getChildJoins().size() == 0
                && parent.getParentJoin() == null) {
            setTablesGroup(parent, null);
        }
        // Same for the child (except we know that parent is null)
        assert child.getParentJoin() == null;
        if (child.getChildJoins().size() == 0) {
            setTablesGroup(child, null);
        }
        generateGroupTableColumns(group);
    }

    public void moveTreeToGroup(String schemaName, String tableName,
            String groupName, String joinName) {
        LOG.info("moveTree: " + schemaName + "." + tableName + " -> "
                + groupName + " via join " + joinName);
        // table
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "moving tree", "table", concat(schemaName, tableName));

        // group
        Group oldGroup = table.getGroup();
        Group group = ais.getGroup(groupName);
        checkFound(group, "moving tree", "group", groupName);

        // join
        Join join = ais.getJoin(joinName);
        checkFound(join, "moving tree", "join", joinName);

        // Remove table's parent join from its current group (if there is a
        // parent)
        Join parentJoin = table.getParentJoin();
        if (parentJoin != null) {
            parentJoin.setGroup(null);

            // set group usage to NEVER on old parent join
            parentJoin.setGroupingUsage(GroupingUsage.NEVER);
        }

        // Move table to group. Get the children first, because moving the table
        // to another group will cause
        // getChildJoins() to return empty.
        List<Join> children = table.getChildJoins();
        setTablesGroup(table, group);

        // Move the join to the group
        join.setGroup(group);

        // set group usage to ALWAYS on new join
        join.getSourceTypes().add(SourceType.USER);
        join.setGroupingUsage(GroupingUsage.ALWAYS);

        // update group table columns and indexes for the affected groups
        updateGroupTablesOnMove(oldGroup, group, children);

    }

    public void moveTreeToEmptyGroup(String schemaName, String tableName,
            String groupName) {
        LOG.info("moveTree: " + schemaName + "." + tableName
                + " -> empty group " + groupName);
        // table
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "moving tree", "table", concat(schemaName, tableName));

        // group
        Group oldGroup = table.getGroup();
        Group group = ais.getGroup(groupName);
        checkFound(group, "moving tree", "group", groupName);

        // Remove table's parent join from its current group (if there is a
        // parent)
        Join parentJoin = table.getParentJoin();
        if (parentJoin != null) {
            parentJoin.setGroup(null);
        }
        // find all candidate parent joins and set usage to NEVER to indicate
        // table should be ROOT
        for (Join canParentJoin : table.getCandidateParentJoins()) {
            canParentJoin.setGroupingUsage(GroupingUsage.NEVER);
        }

        // Move table to group. Get the children first (see comment in
        // moveTreeToGroup).
        List<Join> children = table.getChildJoins();
        setTablesGroup(table, group);

        // update group table columns and indexes for the affected groups
        updateGroupTablesOnMove(oldGroup, group, children);
    }

    public void moveTreeToNoGroup(String schemaName, String tableName) {
        LOG.info("moveTree: " + schemaName + "." + tableName + " -> no group ");
        // table
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "moving tree", "table", concat(schemaName, tableName));

        // group
        Group oldGroup = table.getGroup();
        Group group = null;

        // Remove table's parent join from its current group (if there is a
        // parent)
        Join parentJoin = table.getParentJoin();
        if (parentJoin != null) {
            parentJoin.setGroup(null);
        }

        // Move table to group. Get the children first (see comment in
        // moveTreeToGroup).
        List<Join> children = table.getChildJoins();
        setTablesGroup(table, group);

        // update group table columns and indexes for the affected groups
        updateGroupTablesOnMove(oldGroup, group, children);
    }

    private void updateGroupTablesOnMove(Group oldGroup, Group newGroup,
            List<Join> moveJoins) {

        moveTree(moveJoins, newGroup);

        // update columns in old and new groups
        if (oldGroup != null)
            generateGroupTableColumns(oldGroup);
        if (newGroup != null)
            generateGroupTableColumns(newGroup);

        // update indexes in old and new groups
        if (oldGroup != null)
            generateGroupTableIndexes(oldGroup);
        if (newGroup != null)
            generateGroupTableIndexes(newGroup);

    }

    public void generateGroupTableIndexes(Group group) {
        LOG.debug("generating group table indexes for group " + group);

        GroupTable groupTable = group.getGroupTable();
        if (groupTable != null) {
            UserTable root = groupTable.getRoot();
            if (root != null) {
                groupTable.clearIndexes();
                generateGroupTableIndexes(groupTable, root);
            }
        }
    }

    private void generateGroupTableIndexes(GroupTable groupTable, UserTable userTable) {
        LOG.debug("generating group table indexes for group table "
                + groupTable + " and user table " + userTable);

        for (TableIndex userIndex : userTable.getIndexesIncludingInternal()) {
            String indexName = nameGenerator.generateGroupIndexName(userIndex);

            // Check if the index we're about to add is already in the table.
            // This can happen if the user alters one or more groups, then
            // calls groupingIsComplete again (or just calls it twice in a row)
            // but this assumes that indexName == index Definition
            // TODO: Need to check definition, not just name.
            if (AISInvariants.isIndexInTable(groupTable, indexName)) {
                continue;
            }
            TableIndex groupIndex = TableIndex.create(ais, groupTable, indexName, userIndex.getIndexId(),
                                                      false, Index.KEY_CONSTRAINT);
            groupIndex.setTreeName(userIndex.getTreeName());

            int position = 0;
            for (IndexColumn userIndexColumn : userIndex.getColumns()) {
                this.checkFound(userIndexColumn, "building group indexes", "userIndexColumn", "NONE");
                this.checkFound(userIndexColumn.getColumn().getGroupColumn(), "building group indexes",
                                "group column", userIndexColumn.getColumn().getName());
                IndexColumn groupIndexColumn = new IndexColumn(
                        groupIndex,
                        userIndexColumn.getColumn().getGroupColumn(),
                        position++,
                        userIndexColumn.isAscending(),
                        userIndexColumn.getIndexedLength());
                groupIndex.addColumn(groupIndexColumn);
            }
        }

        for (Join join : userTable.getChildJoins()) {
            generateGroupTableIndexes(groupTable, join.getChild());
        }
    }

    public void groupingIsComplete() {
        LOG.info("groupingIsComplete");
        
        // make sure the groups have all the correct columns
        // including the hidden PK columns. 
        for (Group group : ais.getGroups().values()) {
            generateGroupTableColumns(group);
        }
        // Create group table indexes for each user table index
        for (UserTable userTable : ais.getUserTables().values()) {
            Group group = userTable.getGroup();
            if (group != null) {
                generateGroupTableIndexes(group);
            }
        }
    }

    public void clearGroupings() {
        LOG.info("clear groupings");
        ais.getGroups().clear();
        ais.getGroupTables().clear();
        for (UserTable table : ais.getUserTables().values()) {
            setTablesGroup(table, null);
            for (Column column : table.getColumnsIncludingInternal()) {
                column.setGroupColumn(null);
            }
        }
        for (Join join : ais.getJoins().values()) {
            join.setGroup(null);
        }
    }

    // API for getting the created AIS

    public AkibanInformationSchema akibanInformationSchema() {
        LOG.info("getting AIS");
        return ais;
    }

    public void generateGroupTableColumns(Group group) {
        LOG.debug("generating group table columns for group " + group);
        // Only generate columns if the group is connected, i.e., there is only
        // one root. Multiple roots means
        // that there are disconnected pieces, which is not a valid final state.
        boolean multipleRoots = false;
        UserTable root = null;
        for (UserTable userTable : ais.getUserTables().values()) {
            if (userTable.getGroup() == group) {
                if (userTable.getParentJoin() == null) {
                    if (root == null) {
                        root = userTable;
                    } else {
                        multipleRoots = true;
                    }
                }
            }
        }
        GroupTable groupTable = group.getGroupTable();
        groupTable.dropColumns();
        if (root != null && !multipleRoots) {
            generateGroupTableColumns(groupTable, root);
        }
    }

    private void generateGroupTableColumns(GroupTable groupTable,
            UserTable userTable) {
        LOG.debug("generating group table columns for group table "
                + groupTable + " and user table " + userTable);
        for (Column userColumn : userTable.getColumnsIncludingInternal()) {
            String groupColumnName = nameGenerator.generateColumnName(userColumn);
            Column groupColumn = Column.create(groupTable,
                                               groupColumnName,
                                               groupTable.getColumns().size(),
                                               userColumn.getType());
            groupColumn.setNullable(userColumn.getNullable());
            int nTypeParameters = userColumn.getType().nTypeParameters();
            if (nTypeParameters >= 1) {
                groupColumn.setTypeParameter1(userColumn.getTypeParameter1());
                if (nTypeParameters >= 2) {
                    groupColumn.setTypeParameter2(userColumn
                            .getTypeParameter2());
                }
            }
            groupColumn.setCharsetAndCollation(userColumn
                    .getCharsetAndCollation());
            userColumn.setGroupColumn(groupColumn);
            groupColumn.setUserColumn(userColumn);
        }
        for (Join join : userTable.getChildJoins()) {
            generateGroupTableColumns(groupTable, join.getChild());
        }
    }

    private void moveTree(List<Join> joins, Group group) {
        LOG.debug("moving tree " + joins + " to group " + group);
        for (Join join : joins) {
            List<Join> children = join.getChild().getChildJoins();
            setTablesGroup(join.getChild(), group);
            join.setGroup(group);
            moveTree(children, group);
        }
    }

    private void checkFound(Object object, String action, String needed,
            String name) {
        if (object == null) {
            throw new NoSuchObjectException(action, needed, name);
        }
    }

    private void checkGroupAddition(Group group, Group existingGroup,
            String name) {
        if (existingGroup != null && existingGroup != group) {
            throw new GroupStructureException(group, existingGroup, name);
        }
    }

    private void checkInGroup(Group group, HasGroup object, String action,
            String objectDescription) {
        if (object.getGroup() != group) {
            throw new NotInGroupException(group, object, action,
                    objectDescription);
        }
    }

    private void checkCycle(UserTable table, Group group) {
        if (table.getGroup() == group) {
            String exception = table + " is already in " + group
                    + ". Group must be acyclic";
            throw new GroupStructureException(exception);
        }
    }

    private String concat(String... strings) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < strings.length; i++) {
            if (i > 0) {
                buffer.append(".");
            }
            buffer.append(strings[i]);
        }
        return buffer.toString();
    }

    private void setTablesGroup(Table table, Group group) {
        table.setGroup(group);
        table.setTreeName(group != null ? group.getGroupTable().getTreeName() : "");
    }

    // State
    static final class ColumnName {
        private final TableName table;
        private final String columnName;

        public ColumnName(TableName table, String columnName) {
            this.table = table;
            this.columnName = columnName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((table == null) ? 0 : table.hashCode());
            result = prime * result
                    + ((columnName == null) ? 0 : columnName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object object) {
            if (object == this)
                return true;
            if (!(object instanceof ColumnName))
                return false;
            ColumnName other = (ColumnName) object;
            if (this.table == null && other.table != null)
                return false;
            if (!this.table.equals(other.table))
                return false;
            return (this.columnName == null) ? other.columnName == null
                    : this.columnName.equals(other.columnName);
        }
    }

    public final static int MAX_COLUMN_NAME_LENGTH = 64;
    private static int tableGeneratorBase = 25000;

    private final AkibanInformationSchema ais;
    private Map<String, ForwardTableReference> forwardReferences = // join name
                                                                   // ->
                                                                   // ForwardTableReference
    new LinkedHashMap<String, ForwardTableReference>();
    private NameGenerator nameGenerator;
    // This is temporary. We need unique ids generated here until the
    // chunkserver assigns them.
    private int tableIdGenerator = 0;
    private int indexIdGenerator = 0;

    // Inner classes

    private class ForwardTableReference {
        public ForwardTableReference(String joinName,
                TableName parentTableName, UserTable childTable) {
            this.joinName = joinName;
            this.parentTableName = parentTableName;
            this.childTable = childTable;
        }

        public String joinName() {
            return joinName;
        }

        public TableName parentTableName() {
            return parentTableName;
        }

        public UserTable childTable() {
            return childTable;
        }

        public void addColumnReference(String parentColumnName,
                Column childColumn) {
            forwardColumnReferences.add(new ForwardColumnReference(
                    parentColumnName, childColumn));
        }

        public List<ForwardColumnReference> forwardColumnReferences() {
            return forwardColumnReferences;
        }

        private final String joinName;
        private final UserTable childTable;
        private final TableName parentTableName;
        private final List<ForwardColumnReference> forwardColumnReferences = new ArrayList<ForwardColumnReference>();
    }

    private class ForwardColumnReference {
        public ForwardColumnReference(String parentColumnName,
                Column childColumn) {
            this.parentColumnName = parentColumnName;
            this.childColumn = childColumn;
        }

        public String parentColumnName() {
            return parentColumnName;
        }

        public Column childColumn() {
            return childColumn;
        }

        private final String parentColumnName;
        private final Column childColumn;
    }

    public static class NoSuchObjectException extends RuntimeException {
        @SuppressWarnings("unused")
        // GWT
        private NoSuchObjectException() {
        }

        public NoSuchObjectException(String action, String needed, String name) {
            // XXX: GWT issue - was using String.format
            super("While " + action + ", could not find " + needed + " " + name);
        }
    }

    public static class GroupStructureException extends RuntimeException {
        @SuppressWarnings("unused")
        // GWT
        private GroupStructureException() {
        }

        public GroupStructureException(Group group, Group existingGroup,
                String name) {
            // XXX: GWT issue - was using String.format
            super(name + " already belongs to group " + existingGroup.getName()
                    + " so it cannot be associated with group "
                    + group.getName());
        }

        public GroupStructureException(String message) {
            super(message);
        }
    }

    public static class GroupNotEmptyException extends RuntimeException {
        @SuppressWarnings("unused")
        // GWT
        private GroupNotEmptyException() {
        }

        public GroupNotEmptyException(Group group) {
            super(
                    "Group "
                            + group.getName()
                            + " cannot be deleted because it contains at least one user table.");
        }
    }

    public class NotInGroupException extends RuntimeException {
        @SuppressWarnings("unused")
        // GWT
        private NotInGroupException() {
        }

        public NotInGroupException(Group group, HasGroup object, String action,
                String objectDescription) {
            super("While " + action + ", found " + objectDescription
                    + " not in " + group + ", but in " + object.getGroup()
                    + " instead.");
        }
    }
}

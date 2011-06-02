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

package com.akiban.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.akiban.ais.model.TableIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.GroupTable;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.IndexName;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.JoinColumn;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.UserTable;
import com.akiban.server.util.RowDefNotFoundException;

/**
 * Caches RowDef instances. In this incarnation, this class also constructs
 * RowDef objects from the AkibanInformationSchema. The translation is done in
 * the {@link #setAIS(AkibanInformationSchema)} method.
 * 
 * @author peter
 */
public class RowDefCache {

    // TODO: For debugging - remove this
    private static volatile RowDefCache LATEST;

    private static final Logger LOG = LoggerFactory.getLogger(RowDefCache.class
            .getName());

    private final Map<Integer, RowDef> cacheMap = new TreeMap<Integer, RowDef>();

    private final Map<String, Integer> nameMap = new TreeMap<String, Integer>();
    
    private TableStatusCache tableStatusCache;

    private AkibanInformationSchema ais;

    {
        LATEST = this;
    }

    public RowDefCache(final TableStatusCache tableStatusCache) {
        this.tableStatusCache = tableStatusCache;
    }

    public static RowDefCache latest() {
        return LATEST;
    }

    public synchronized boolean contains(final int rowDefId) {
        return cacheMap.containsKey(Integer.valueOf(rowDefId));
    }
    
    /**
     * Look up and return a RowDef for a supplied rowDefId value.
     * 
     * @param rowDefId
     * @return the corresponding RowDef
     * @throws RowDefNotFoundException if there is no such RowDef.
     */
    public synchronized RowDef getRowDef(final int rowDefId) throws RowDefNotFoundException {
        RowDef rowDef = rowDef(rowDefId);
        if (rowDef == null) {
            throw new RowDefNotFoundException(rowDefId);
        }
        return rowDef;
    }
    
    /**
     * @param rowDefId
     * @return  the corresponding RowDef object, or <code>null</code> if
     * there is RowDef defined with the specified id
     */
    public synchronized RowDef rowDef(final int rowDefId) {
        return cacheMap.get(Integer.valueOf(rowDefId));
    }

    public synchronized List<RowDef> getRowDefs() {
        return new ArrayList<RowDef>(cacheMap.values());
    }

    public synchronized RowDef getRowDef(final String tableName) throws RowDefNotFoundException {
        final Integer key = nameMap.get(tableName);
        if (key == null) {
            return null;
        }
        return getRowDef(key.intValue());
    }

    /**
     * Given a schema and table name, gets a string that uniquely identifies a
     * table. This string can then be passed to {@link #getRowDef(String)}.
     * 
     * @param schema
     *            the schema
     * @param table
     *            the table name
     * @return a unique form
     */
    public static String nameOf(String schema, String table) {
        assert schema != null;
        assert table != null;
        return schema + "." + table;
    }

    public synchronized void clear() {
        cacheMap.clear();
        nameMap.clear();
    }

    /**
     * Receive an instance of the AkibanInformationSchema, crack it and produce
     * the RowDef instances it defines.
     * 
     * @param ais
     */
    public synchronized void setAIS(final AkibanInformationSchema ais) {
        this.ais = ais;
        
        for (final UserTable table : ais.getUserTables().values()) {
            putRowDef(createUserTableRowDef(table));
        }

        for (final GroupTable table : ais.getGroupTables().values()) {
            putRowDef(createGroupTableRowDef(table));
        }

        analyzeAll();
        
        if (LOG.isDebugEnabled()) {
            LOG.debug(toString());
        }
    }

    public AkibanInformationSchema ais()
    {
        return ais;
    }

    /**
     * Assign "ordinal" values to user table RowDef instances. An ordinal the
     * integer used to identify a user table subtree within an hkey. This method
     * Assigned unique integers where needed to any tables that have not already
     * received non-zero ordinal values. Once a table is populated, its ordinal
     * is written as part of the TableStatus record, and on subsequent server
     * start-ups, that value is loaded and reused from the status tree.
     * @return Map of Table->Ordinal for all Tables/RowDefs in the RowDefCache
     */
    protected Map<Table,Integer> fixUpOrdinals() {
        Map<Table,Integer> ordinalMap = new HashMap<Table,Integer>();
        for (final RowDef groupRowDef : getRowDefs()) {
            if (groupRowDef.isGroupTable()) {
                // groupTable has no ordinal but it should be in the map
                ordinalMap.put(groupRowDef.table(), 0);
                final HashSet<Integer> assigned = new HashSet<Integer>();
                // First pass: merge already assigned values
                for (final RowDef userRowDef : groupRowDef.getUserTableRowDefs()) {
                    final TableStatus tableStatus = userRowDef.getTableStatus();
                    int ordinal = tableStatus.getOrdinal();
                    if (ordinal != 0 && !assigned.add(ordinal)) {
                        throw new IllegalStateException(String.format(
                                "Non-unique ordinal value %s added to %s",
                                ordinal, assigned));
                    }
                }
                int nextOrdinal = 1;
                for (final RowDef userRowDef : groupRowDef.getUserTableRowDefs()) {
                    int ordinal = userRowDef.getOrdinal();
                    if (ordinal == 0) {
                        // find an unassigned value. Here we could try to optimize layout
                        // by assigning "bushy" values in some optimal pattern
                        // (if we knew what that was...)
                        while(assigned.contains(nextOrdinal)) {
                            ++nextOrdinal;
                        }
                        ordinal = nextOrdinal;
                        tableStatusCache.setOrdinal(userRowDef.getRowDefId(), ordinal);
                    }
                    assigned.add(ordinal);
                    ordinalMap.put(userRowDef.table(), ordinal);
                }
                if (assigned.size() != groupRowDef.getUserTableRowDefs().length) {
                    throw new IllegalStateException("Inconsistent ordinal number assignments: " + assigned);
                }
            }
        }
        return ordinalMap;
    }

    private static String getTreeName(GroupTable groupTable) {
        return groupTable.getName().toString();
    }
    
    private static String getTreeName(String groupName, TableIndex index) {
        IndexName iname = index.getIndexName();
        String schemaName = iname.getSchemaName();
        String tableName = iname.getTableName();
        String indexName = iname.getName();

        // Tree names for identical indexes on the group and user table must match.
        // Check if this index originally came from a user table and, if so, use their
        // names instead.
        if (index.getTable().isGroupTable()) {
            Column c = index.getColumns().get(0).getColumn().getUserColumn();
            if (c != null) {
                UserTable table = c.getUserTable();
                for(Index i : table.getIndexes()) {
                    if(i.getIndexId().equals(index.getIndexId())) {
                        tableName = table.getName().getTableName();
                        indexName = i.getIndexName().getName();
                        break;
                    }
                }
            }
        }

        return String.format("%s$$%s$$%s$$%s", groupName, schemaName, tableName, indexName);
    }

    private RowDef createUserTableRowDef(UserTable table) {
        RowDef rowDef = new RowDef(table, tableStatusCache.getTableStatus(table.getTableId()));
        // parentRowDef
        int[] parentJoinFields;
        if (table.getParentJoin() != null) {
            final Join join = table.getParentJoin();
            //
            // parentJoinFields - TODO - not sure this is right.
            //
            parentJoinFields = new int[join.getJoinColumns().size()];
            for (int index = 0; index < join.getJoinColumns().size(); index++) {
                final JoinColumn joinColumn = join.getJoinColumns().get(index);
                parentJoinFields[index] = joinColumn.getChild().getPosition();
            }
        } else {
            parentJoinFields = new int[0];
        }

        // root table
        UserTable root = table;
        while (root.getParentJoin() != null) {
            root = root.getParentJoin().getParent();
        }

        // group table name
        final GroupTable groupTable = root.getGroup().getGroupTable();
        final String groupTableName = groupTable.getName().getTableName();
        final String groupTableTreeName = getTreeName(groupTable);
        
        assert groupTableName != null : root;
        assert groupTableTreeName != null : root;

        // Secondary indexes
        List<IndexDef> indexDefList = new ArrayList<IndexDef>();
        for (TableIndex index : table.getIndexesIncludingInternal()) {
            List<IndexColumn> indexColumns = index.getColumns();
            if (!indexColumns.isEmpty()) {
                String treeName = getTreeName(groupTableName, index);
                IndexDef indexDef = new IndexDef(treeName, rowDef, index);
                if (index.isPrimaryKey()) {
                    indexDefList.add(0, indexDef);
                } else {
                    indexDefList.add(indexDef);
                }
            } // else: Don't create an index for an artificial IndexDef that has
              // no fields.
        }
        rowDef.setTreeName(groupTableTreeName);
        rowDef.setParentJoinFields(parentJoinFields);
        rowDef.setIndexDefs(indexDefList.toArray(new IndexDef[indexDefList.size()]));
        return rowDef;

    }
    
    private RowDef createGroupTableRowDef(GroupTable table) {
        RowDef rowDef = new RowDef(table, tableStatusCache.getTableStatus(table.getTableId()));
        List<Integer> userTableRowDefIds = new ArrayList<Integer>();
        for (Column column : table.getColumnsIncludingInternal()) {
            Column userColumn = column.getUserColumn();
            if (userColumn.getPosition() == 0) {
                int userRowDefId = userColumn.getTable().getTableId();
                userTableRowDefIds.add(userRowDefId);
                RowDef userRowDef = cacheMap.get(userRowDefId);
                userRowDef.setColumnOffset(column.getPosition());
            }
        }
        RowDef[] userTableRowDefs = new RowDef[userTableRowDefIds.size()];
        int i = 0;
        for (Integer userTableRowDefId : userTableRowDefIds) {
            userTableRowDefs[i++] = cacheMap.get(userTableRowDefId);
        }
        final String groupTableName = table.getName().getTableName();
        final String groupTableTreeName = getTreeName(table);
        // Secondary indexes
        final List<IndexDef> indexDefList = new ArrayList<IndexDef>();
        for (TableIndex index : table.getIndexes()) {
            List<IndexColumn> indexColumns = index.getColumns();
            if (!indexColumns.isEmpty()) {
                String treeName = getTreeName(groupTableName, index);
                IndexDef indexDef = new IndexDef(treeName, rowDef, index);
                indexDefList.add(indexDef);
            } // else: Don't create a group table index for an artificial
              // IndeDef that has no fields.
        }
        rowDef.setTreeName(groupTableTreeName);
        rowDef.setUserTableRowDefs(userTableRowDefs);
        rowDef.setIndexDefs(indexDefList.toArray(new IndexDef[indexDefList.size()]));
        return rowDef;
    }
    
    private synchronized void putRowDef(final RowDef rowDef) {
        final Integer key = rowDef.getRowDefId();
        final String name = nameOf(rowDef.getSchemaName(), rowDef.getTableName());
        if (cacheMap.containsKey(key)) {
            throw new IllegalStateException("Duplicate RowDefID (" + key + ") for RowDef: " + rowDef);
        }
        if (nameMap.containsKey(name)) {
            throw new IllegalStateException("Duplicate name (" + name + ") for RowDef: " + rowDef);
        }
        cacheMap.put(key, rowDef);
        nameMap.put(name, key);
    }
    
    private void analyzeAll() throws RowDefNotFoundException {
        Map<Table,Integer> ordinalMap = fixUpOrdinals();
        for (final RowDef rowDef : cacheMap.values()) {
            rowDef.computeFieldAssociations(ordinalMap);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("\n");
        for (Map.Entry<String, Integer> entry : nameMap.entrySet()) {
            final RowDef rowDef = cacheMap.get(entry.getValue());
            sb.append("   ");
            sb.append(rowDef);
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(!(o instanceof RowDefCache)) {
            return false;
        }
        RowDefCache that = (RowDefCache) o;
        if(cacheMap == null) {
            return that.cacheMap == null;
        }
        return cacheMap.equals(that.cacheMap);
    }

    @Override
    public int hashCode() {
        return cacheMap.hashCode();
    }
}

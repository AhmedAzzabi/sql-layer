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

import java.util.HashMap;

public class AISSchemaChanger implements Visitor
{
    private final String from;
    private final String to;
    private final HashMap<String,TableName> tableNameMap;
    
    public AISSchemaChanger(String from, String to)
    {
        this.from = from;
        this.to = to;
        this.tableNameMap = new HashMap<>();
    }
    
    private void updateTableName(Table table)
    {
        TableName tableName = table.getName();
        if (!tableName.getSchemaName().equals(from)) {
            return;
        }
        TableName newName = tableNameMap.get(tableName.getTableName());
        if (newName == null) {
            newName = new TableName(to, tableName.getTableName());
            tableNameMap.put(tableName.getTableName(), newName);
        }
        table.setTableName(newName);
    }
    
    @Override
    public void visitColumn(Column column)
    {
        updateTableName( column.getTable() );
    }

    @Override
    public void visitGroup(Group group) 
    {
    }

    @Override
    public void visitIndex(Index index)
    {
        IndexName indexName = index.getIndexName();
        if (!indexName.getSchemaName().equals(from)) {
            return;
        }
        IndexName newName = new IndexName(new TableName(indexName.getSchemaName(), indexName.getTableName()),
                                          indexName.getName());
        index.setIndexName(newName);
    }

    @Override
    public void visitIndexColumn(IndexColumn indexColumn)
    {
        visitColumn( indexColumn.getColumn() );
        visitIndex( indexColumn.getIndex() );
    }

    @Override
    public void visitJoin(Join join)
    {
    }

    @Override
    public void visitJoinColumn(JoinColumn joinColumn)
    {
    }

    @Override
    public void visitType(Type type)
    {
    }

    @Override
    public void visitUserTable(UserTable userTable)
    {
        updateTableName( userTable );
    }
}

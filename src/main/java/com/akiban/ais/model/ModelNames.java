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

public interface ModelNames
{
    String version = "version";
    // type
    String type = "type";
    String type_name = "name";
    String type_parameters = "parameters";
    String type_fixedSize = "fixedSize";
    String type_maxSizeBytes = "maxSizeBytes";
    String type_encoding = "encoding";
    // group
    String group = "group";
    String group_name = "groupName";
    // table
    String table = "table";
    String table_schemaName = "schemaName";
    String table_tableName = "tableName";
    String table_tableType = "tableType";
    String table_tableId = "tableId";
    String table_groupName = "groupName";
    String table_migrationUsage = "migrationUsage";
    // column
    String column = "column";
    String column_schemaName = "schemaName";
    String column_tableName = "tableName";
    String column_columnName = "columnName";
    String column_position = "position";
    String column_typename = "typename";
    String column_typeParam1 = "typeParam1";
    String column_typeParam2 = "typeParam2";
    String column_nullable = "nullable";
    String column_initialAutoIncrementValue = "initialAutoIncrementValue";
    String column_groupSchemaName = "groupSchemaName";
    String column_groupTableName = "groupTableName";
    String column_groupColumnName = "groupColumnName";
    String column_maxStorageSize = "maxStorageSize";
    String column_prefixSize = "prefixSize";
    String column_charset = "charset";
    String column_collation = "collation";
    // join
    String join = "join";
    String join_joinName = "joinName";
    String join_parentSchemaName = "parentSchemaName";
    String join_parentTableName = "parentTableName";
    String join_childSchemaName = "childSchemaName";
    String join_childTableName = "childTableName";
    String join_groupName = "groupName";
    String join_joinWeight = "joinWeight";
    String join_groupingUsage = "groupingUsage";
    String join_sourceTypes = "sourceTypes";
    // joinColumn
    String joinColumn = "joinColumn";
    String joinColumn_joinName = "joinName";
    String joinColumn_parentSchemaName = "parentSchemaName";
    String joinColumn_parentTableName = "parentTableName";
    String joinColumn_parentColumnName = "parentColumnName";
    String joinColumn_childSchemaName = "childSchemaName";
    String joinColumn_childTableName = "childTableName";
    String joinColumn_childColumnName = "childColumnName";
    // index
    String index = "index";
    String index_schemaName = "schemaName";
    String index_tableName = "tableName";
    String index_indexName = "indexName";
    String index_indexId = "indexId";
    String index_constraint = "constraint";
    String index_unique = "unique";
    // indexColumn
    String indexColumn = "indexColumn";
    String indexColumn_schemaName = "schemaName";
    String indexColumn_tableName = "tableName";
    String indexColumn_indexName = "indexName";
    String indexColumn_columnName = "columnName";
    String indexColumn_position = "position";
    String indexColumn_ascending = "ascending";
    String indexColumn_indexedLength= "indexedLength";
}

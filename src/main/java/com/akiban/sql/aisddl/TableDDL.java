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

package com.akiban.sql.aisddl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.akiban.server.api.DDLFunctions;
import com.akiban.server.error.NoSuchTableException;
import com.akiban.server.error.UnsupportedCheckConstraintException;
import com.akiban.server.error.UnsupportedCreateSelectException;
import com.akiban.server.error.UnsupportedDataTypeException;
import com.akiban.server.error.UnsupportedFKIndexException;
import com.akiban.server.error.UnsupportedSQLException;
import com.akiban.server.service.session.Session;
import com.akiban.sql.parser.ColumnDefinitionNode;
import com.akiban.sql.parser.ConstraintDefinitionNode;
import com.akiban.sql.parser.CreateTableNode;
import com.akiban.sql.parser.DropTableNode;
import com.akiban.sql.parser.FKConstraintDefinitionNode;
import com.akiban.sql.parser.RenameNode;
import com.akiban.sql.parser.ResultColumn;
import com.akiban.sql.parser.TableElementNode;

import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.sql.types.TypeId;

import com.akiban.ais.model.AISBuilder;
import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.DefaultNameGenerator;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.NameGenerator;
import com.akiban.ais.model.Type;
import com.akiban.ais.model.UserTable;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.Types;

/** DDL operations on Tables */
public class TableDDL
{
    //private final static Logger logger = LoggerFactory.getLogger(TableDDL.class);
    private TableDDL() {
    }

    public static void dropTable (DDLFunctions ddlFunctions,
                                  Session session, 
                                  String defaultSchemaName,
                                  DropTableNode dropTable) {
        com.akiban.sql.parser.TableName parserName = dropTable.getObjectName();
        
        String schemaName = parserName.hasSchema() ? parserName.getSchemaName() : defaultSchemaName;
        TableName tableName = TableName.create(schemaName, parserName.getTableName());
        
        if (ddlFunctions.getAIS(session).getUserTable(tableName) == null && 
                ddlFunctions.getAIS(session).getGroupTable(tableName) == null) {
            throw new NoSuchTableException (tableName.getSchemaName(), tableName.getTableName());
        }
        ddlFunctions.dropTable(session, tableName);
    }
    
    public static void renameTable (DDLFunctions ddlFunctions,
                                    Session session,
                                    String defaultSchemaName,
                                    RenameNode renameTable) {
        throw new UnsupportedSQLException (renameTable.statementToString(), renameTable);
        //ddlFunctions.renameTable(session, currentName, newName);
    }

    public static void createTable(DDLFunctions ddlFunctions,
                                   Session session,
                                   String defaultSchemaName,
                                   CreateTableNode createTable) {
        if (createTable.getQueryExpression() != null)
            throw new UnsupportedCreateSelectException();

        com.akiban.sql.parser.TableName parserName = createTable.getObjectName();
        String schemaName = parserName.hasSchema() ? parserName.getSchemaName() : defaultSchemaName;
        String tableName = parserName.getTableName();
        
        AISBuilder builder = new AISBuilder();
        
        builder.userTable(schemaName, tableName);

        int colpos = 0;
        // first loop through table elements, add the columns
        for (TableElementNode tableElement : createTable.getTableElementList()) {
            if (tableElement instanceof ColumnDefinitionNode) {
                addColumn (builder, (ColumnDefinitionNode)tableElement, schemaName, tableName, colpos++);
            }
        }
        // second pass get the constraints (primary, FKs, and other keys)
        // This needs to be done in two passes as the parser may put the 
        // constraint before the column definition. For example:
        // CREATE TABLE t1 (c1 INT PRIMARY KEY) produces such a result. 
        // The Builder complains if you try to do such a thing. 
        for (TableElementNode tableElement : createTable.getTableElementList()) {
            if (tableElement instanceof FKConstraintDefinitionNode) {
                FKConstraintDefinitionNode fkdn = (FKConstraintDefinitionNode)tableElement;
                if (fkdn.isGrouping()) {
                    addParentTable(builder, ddlFunctions.getAIS(session), fkdn, schemaName);
                    addJoin (builder, fkdn, schemaName, tableName);
                } else {
                    throw new UnsupportedFKIndexException();
                }
            }
            else if (tableElement instanceof ConstraintDefinitionNode) {
                addIndex (builder, (ConstraintDefinitionNode)tableElement, schemaName, tableName);
            }
        }
        builder.basicSchemaIsComplete();
        builder.groupingIsComplete();
        UserTable table = builder.akibanInformationSchema().getUserTable(schemaName, tableName);
        
        ddlFunctions.createTable(session, table);
    }
    
    private static void addColumn (final AISBuilder builder, final ColumnDefinitionNode cdn, 
            final String schemaName, final String tableName, int colpos) {
        DataTypeDescriptor type = cdn.getType();
        Long typeParameter1 = null, typeParameter2 = null;
        
        Type builderType = typeMap.get(type.getTypeId());
        if (builderType == null) {
            throw new UnsupportedDataTypeException (new TableName(schemaName, tableName), cdn.getColumnName(), type.getTypeName());
        }
        
        if (builderType.nTypeParameters() == 1) {
            typeParameter1 = (long)type.getMaximumWidth();
        } else if (builderType.nTypeParameters() == 2) {
            typeParameter1 = (long)type.getPrecision();
            typeParameter2 = (long)type.getScale();
        }
        
        builder.column(schemaName, tableName, 
                cdn.getColumnName(), 
                Integer.valueOf(colpos), 
                builderType.name(), 
                typeParameter1, typeParameter2, 
                type.isNullable(), 
                cdn.isAutoincrementColumn(),
                null, null);
        if (cdn.isAutoincrementColumn()) {
            builder.userTableInitialAutoIncrement(schemaName, tableName, 
                    cdn.getAutoincrementStart());
        }
    }

    public static void addIndex (final AISBuilder builder, final ConstraintDefinitionNode cdn, 
            final String schemaName, final String tableName)  {

        NameGenerator namer = new DefaultNameGenerator();
        String constraint = null;
        String indexName = null;
        
        if (cdn.getConstraintType() == ConstraintDefinitionNode.ConstraintType.CHECK) {
            throw new UnsupportedCheckConstraintException ();
        }
        else if (cdn.getConstraintType() == ConstraintDefinitionNode.ConstraintType.PRIMARY_KEY) {
            constraint = Index.PRIMARY_KEY_CONSTRAINT;
        }
        else if (cdn.getConstraintType() == ConstraintDefinitionNode.ConstraintType.UNIQUE) {
            constraint = Index.UNIQUE_KEY_CONSTRAINT;
        }
        indexName = namer.generateIndexName(cdn.getName(), cdn.getColumnList().get(0).getName(), constraint);
        
        builder.index(schemaName, tableName, indexName, true, constraint);
        
        int colPos = 0;
        for (ResultColumn col : cdn.getColumnList()) {
            builder.indexColumn(schemaName, tableName, indexName, col.getName(), colPos++, true, null);
        }
    }
    
    private static void addJoin(final AISBuilder builder, final FKConstraintDefinitionNode fkdn, 
            final String schemaName, final String tableName)  {

 
        String parentSchemaName = fkdn.getRefTableName().hasSchema() ?
                fkdn.getRefTableName().getSchemaName() : schemaName;
        String parentTableName = fkdn.getRefTableName().getTableName();
        String groupName = parentTableName;
        
        String joinName = String.format("%s/%s/%s/%s",
                parentSchemaName, parentTableName, 
                schemaName, tableName);

        UserTable table = builder.akibanInformationSchema().getUserTable(parentSchemaName, parentTableName);
        
        builder.joinTables(joinName, parentSchemaName, parentTableName, schemaName, tableName);
        
        int colpos = 0;
        for (ResultColumn column : fkdn.getColumnList()) {
            String columnName = column.getName();
            builder.joinColumns(joinName, 
                    parentSchemaName, parentTableName, table.getColumn(colpos).getName(), 
                    schemaName, tableName, columnName);
            colpos++;
        }
        builder.addJoinToGroup(groupName, joinName, 0);
        //builder.groupingIsComplete();
    }
    
    /**
     * Add a minimal parent table (PK) with group to the builder based upon the AIS. 
     * 
     * @param builder
     * @param ais
     * @param fkdn
     */
    private static void addParentTable(final AISBuilder builder, 
            final AkibanInformationSchema ais, final FKConstraintDefinitionNode fkdn, 
            final String schemaName) {

        String parentSchemaName = fkdn.getRefTableName().hasSchema() ?
                fkdn.getRefTableName().getSchemaName() : schemaName;
        String parentTableName = fkdn.getRefTableName().getTableName();
        

        UserTable parentTable = ais.getUserTable(parentSchemaName, parentTableName);
        if (parentTable == null) {
            throw new NoSuchTableException (parentSchemaName, parentTableName);
        }
        
        builder.userTable(parentSchemaName, parentTableName);
        
        builder.index(parentSchemaName, parentTableName, Index.PRIMARY_KEY_CONSTRAINT, true, Index.PRIMARY_KEY_CONSTRAINT);
        int colpos = 0;
        for (Column column : parentTable.getPrimaryKeyIncludingInternal().getColumns()) {
            builder.column(parentSchemaName, parentTableName,
                    column.getName(),
                    colpos,
                    column.getType().name(),
                    column.getTypeParameter1(),
                    column.getTypeParameter2(),
                    column.getNullable(),
                    false, //column.getInitialAutoIncrementValue() != 0,
                    column.getCharsetAndCollation() != null ? column.getCharsetAndCollation().charset() : null,
                    column.getCharsetAndCollation() != null ? column.getCharsetAndCollation().collation() : null);
            builder.indexColumn(parentSchemaName, parentTableName, Index.PRIMARY_KEY_CONSTRAINT, 
                    column.getName(), colpos++, true, 0);
        }
        builder.createGroup(parentTableName, parentSchemaName, "_akiban_" + parentTableName);
        builder.addTableToGroup(parentTableName, parentSchemaName, parentTableName);
        //builder.groupingIsComplete();
    }
    
    private final static Map<TypeId, Type> typeMap  = typeMapping();
    
    private static Map<TypeId, Type> typeMapping() {
        HashMap<TypeId, Type> types = new HashMap<TypeId, Type>();
        types.put(TypeId.BOOLEAN_ID, Types.TINYINT);
        types.put(TypeId.TINYINT_ID, Types.TINYINT);
        types.put(TypeId.SMALLINT_ID, Types.SMALLINT);
        types.put(TypeId.INTEGER_ID, Types.INT);
        types.put(TypeId.BIGINT_ID, Types.BIGINT);
        
        types.put(TypeId.TINYINT_UNSIGNED_ID, Types.U_TINYINT);
        types.put(TypeId.SMALLINT_UNSIGNED_ID, Types.U_SMALLINT);
        types.put(TypeId.INTEGER_UNSIGNED_ID, Types.U_INT);
        types.put(TypeId.BIGINT_UNSIGNED_ID, Types.U_BIGINT);
        
        types.put(TypeId.REAL_ID, Types.FLOAT);
        types.put(TypeId.DOUBLE_ID, Types.DOUBLE);
        types.put(TypeId.DECIMAL_ID, Types.DECIMAL);
        types.put(TypeId.NUMERIC_ID, Types.DECIMAL);
        
        types.put(TypeId.REAL_UNSIGNED_ID, Types.U_FLOAT);
        types.put(TypeId.DOUBLE_UNSIGNED_ID, Types.U_DOUBLE);
        types.put(TypeId.DECIMAL_UNSIGNED_ID, Types.U_DECIMAL);
        types.put(TypeId.NUMERIC_UNSIGNED_ID, Types.U_DECIMAL);
        
        types.put(TypeId.CHAR_ID, Types.CHAR);
        types.put(TypeId.VARCHAR_ID, Types.VARCHAR);
        types.put(TypeId.LONGVARCHAR_ID, Types.VARCHAR);
        types.put(TypeId.BIT_ID, Types.BINARY);
        types.put(TypeId.VARBIT_ID, Types.VARBINARY);
        types.put(TypeId.LONGVARBIT_ID, Types.VARBINARY);
        
        types.put(TypeId.DATE_ID, Types.DATE);
        types.put(TypeId.TIME_ID, Types.TIME);
        types.put(TypeId.TIMESTAMP_ID, Types.DATETIME); // TODO: Types.TIMESTAMP?
        types.put(TypeId.DATETIME_ID, Types.DATETIME);
        types.put(TypeId.YEAR_ID, Types.YEAR);
        
        types.put(TypeId.BLOB_ID, Types.LONGBLOB);
        types.put(TypeId.CLOB_ID, Types.LONGTEXT);
        return Collections.unmodifiableMap(types);
        
    }        
}
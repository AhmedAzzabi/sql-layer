/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.sql.aisddl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akiban.ais.model.DefaultIndexNameGenerator;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.IndexNameGenerator;
import com.akiban.ais.model.PrimaryKey;
import com.akiban.server.api.DDLFunctions;
import com.akiban.server.error.*;
import com.akiban.server.service.session.Session;
import com.akiban.sql.parser.ColumnDefinitionNode;
import com.akiban.sql.parser.ConstantNode;
import com.akiban.sql.parser.ConstraintDefinitionNode;
import com.akiban.sql.parser.CreateTableNode;
import com.akiban.sql.parser.DefaultNode;
import com.akiban.sql.parser.DropGroupNode;
import com.akiban.sql.parser.DropTableNode;
import com.akiban.sql.parser.FKConstraintDefinitionNode;
import com.akiban.sql.parser.IndexColumnList;
import com.akiban.sql.parser.IndexConstraintDefinitionNode;
import com.akiban.sql.parser.IndexDefinition;
import com.akiban.sql.parser.RenameNode;
import com.akiban.sql.parser.ResultColumn;
import com.akiban.sql.parser.ResultColumnList;
import com.akiban.sql.parser.TableElementNode;

import com.akiban.sql.parser.ValueNode;
import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.sql.types.TypeId;

import com.akiban.ais.model.AISBuilder;
import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.Group;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.Type;
import com.akiban.ais.model.UserTable;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.Types;
import com.akiban.server.error.DuplicateTableNameException;
import com.akiban.sql.parser.ExistenceCheck;
import com.akiban.qp.operator.QueryContext;

import static com.akiban.sql.aisddl.DDLHelper.convertName;

/** DDL operations on Tables */
public class TableDDL
{
    //private final static Logger logger = LoggerFactory.getLogger(TableDDL.class);
    private TableDDL() {
    }

    public static void dropTable (DDLFunctions ddlFunctions,
                                  Session session, 
                                  String defaultSchemaName,
                                  DropTableNode dropTable,
                                  QueryContext context) {
        TableName tableName = convertName(defaultSchemaName, dropTable.getObjectName());
        ExistenceCheck existenceCheck = dropTable.getExistenceCheck();

        AkibanInformationSchema ais = ddlFunctions.getAIS(session);
        
        if (ais.getUserTable(tableName) == null) {
            if (existenceCheck == ExistenceCheck.IF_EXISTS)
            {
                if (context != null)
                    context.warnClient(new NoSuchTableException (tableName.getSchemaName(), tableName.getTableName()));
                return;
            }
            throw new NoSuchTableException (tableName.getSchemaName(), tableName.getTableName());
        }
        ViewDDL.checkDropTable(ddlFunctions, session, tableName);
        ddlFunctions.dropTable(session, tableName);
    }

    public static void dropGroup (DDLFunctions ddlFunctions,
                                    Session session,
                                    String defaultSchemaName,
                                    DropGroupNode dropGroup,
                                    QueryContext context)
    {
        TableName tableName = convertName(defaultSchemaName, dropGroup.getObjectName());
        ExistenceCheck existenceCheck = dropGroup.getExistenceCheck();
        AkibanInformationSchema ais = ddlFunctions.getAIS(session);
        
        if (ais.getUserTable(tableName) == null) {
            if (existenceCheck == ExistenceCheck.IF_EXISTS) {
                if (context != null) {
                    context.warnClient(new NoSuchTableException (tableName));
                }
                return;
            }
            throw new NoSuchTableException (tableName);
        } 
        if (!ais.getUserTable(tableName).isRoot()) {
            throw new DropGroupNotRootException (tableName);
        }
        
        final Group root = ais.getUserTable(tableName).getGroup();
        for (UserTable table : ais.getUserTables().values()) {
            if (table.getGroup() == root) {
                ViewDDL.checkDropTable(ddlFunctions, session, table.getName());
            }
        }
        ddlFunctions.dropGroup(session, root.getName());
    }
    
    public static void renameTable (DDLFunctions ddlFunctions,
                                    Session session,
                                    String defaultSchemaName,
                                    RenameNode renameTable) {
        TableName oldName = convertName(defaultSchemaName, renameTable.getObjectName());
        TableName newName = convertName(defaultSchemaName, renameTable.getNewTableName());
        ddlFunctions.renameTable(session, oldName, newName);
    }

    public static void createTable(DDLFunctions ddlFunctions,
                                   Session session,
                                   String defaultSchemaName,
                                   CreateTableNode createTable,
                                   QueryContext context) {
        if (createTable.getQueryExpression() != null)
            throw new UnsupportedCreateSelectException();

        com.akiban.sql.parser.TableName parserName = createTable.getObjectName();
        String schemaName = parserName.hasSchema() ? parserName.getSchemaName() : defaultSchemaName;
        String tableName = parserName.getTableName();
        ExistenceCheck condition = createTable.getExistenceCheck();

        AkibanInformationSchema ais = ddlFunctions.getAIS(session);

        if (ais.getUserTable(schemaName, tableName) != null)
            switch(condition)
            {
                case IF_NOT_EXISTS:
                    // table already exists. does nothing
                    if (context != null)
                        context.warnClient(new DuplicateTableNameException(schemaName, tableName));
                    return;
                case NO_CONDITION:
                    throw new DuplicateTableNameException(schemaName, tableName);
                default:
                    throw new IllegalStateException("Unexpected condition: " + condition);
            }

        AISBuilder builder = new AISBuilder();
        builder.userTable(schemaName, tableName);
        UserTable table = builder.akibanInformationSchema().getUserTable(schemaName, tableName);
        IndexNameGenerator namer = DefaultIndexNameGenerator.forTable(table);

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
                    addJoin (builder, fkdn, schemaName, schemaName, tableName);
                } else {
                    throw new UnsupportedFKIndexException();
                }
            }
            else if (tableElement instanceof ConstraintDefinitionNode) {
                addIndex (namer, builder, (ConstraintDefinitionNode)tableElement, schemaName, tableName);
            }
        }
        builder.basicSchemaIsComplete();
        builder.groupingIsComplete();
        
        ddlFunctions.createTable(session, table);
    }
    
    static void addColumn (final AISBuilder builder, final ColumnDefinitionNode cdn,
                           final String schemaName, final String tableName, int colpos) {

        // Special handling for the "SERIAL" column type -> which is transformed to 
        // BIGINT NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1) UNIQUE
        if (cdn.getType().getTypeName().equals("serial")) {
            // BIGINT NOT NULL 
            DataTypeDescriptor bigint = new DataTypeDescriptor (TypeId.BIGINT_ID, false);
            addColumn (builder, schemaName, tableName, cdn.getColumnName(), colpos,
                    bigint, false, true, getColumnDefault(cdn));
            // GENERATED BY DEFAULT AS IDENTITY 
            setAutoIncrement (builder, schemaName, tableName, cdn.getColumnName(),
                    true, 1, 1);
            // UNIQUE (KEY)
            String constraint = Index.UNIQUE_KEY_CONSTRAINT;
            builder.index(schemaName, tableName, cdn.getColumnName(), true, constraint);
            builder.indexColumn(schemaName, tableName, cdn.getColumnName(), cdn.getColumnName(), 0, true, null);
        } else {
            boolean autoIncrement = cdn.isAutoincrementColumn();
            
            addColumn(builder, schemaName, tableName, cdn.getColumnName(), colpos,
                      cdn.getType(), cdn.getType().isNullable(), autoIncrement, getColumnDefault(cdn));
           
            if (autoIncrement) {
                // if the cdn has a default node-> GENERATE BY DEFAULT
                // if no default node -> GENERATE ALWAYS
                Boolean defaultIdentity = cdn.getDefaultNode() != null;
                setAutoIncrement (builder, schemaName, tableName, cdn.getColumnName(),
                        defaultIdentity, cdn.getAutoincrementStart(), cdn.getAutoincrementIncrement());
            }
        }
    }

    static void setAutoIncrement (final AISBuilder builder, 
            String schemaName, String tableName, String columnName, boolean defaultIdentity, 
            long start, long increment) {
        // The merge process will generate a real sequence name
        final String sequenceName = "temp-sequence-1"; 
        builder.sequence(schemaName, sequenceName, 
                start, increment,  
                Long.MIN_VALUE, Long.MAX_VALUE, 
                false);
        // make the column an identity column 
        builder.columnAsIdentity(schemaName, tableName, columnName, sequenceName, defaultIdentity);
        builder.userTableInitialAutoIncrement(schemaName, tableName, start);
    }
    
    static String getColumnDefault(ColumnDefinitionNode cdn) {
        String defaultStr = null;
        DefaultNode defNode = (cdn != null) ? cdn.getDefaultNode() : null;
        if(defNode != null) {
            // TODO: This seems plausible, but also fragile. Better way to derive this?
            ValueNode valueNode = defNode.getDefaultTree();
            if(valueNode instanceof ConstantNode) {
                defaultStr = ((ConstantNode)valueNode).getValue().toString();
            } else {
                defaultStr = defNode.getDefaultText();
            }
        }
        return defaultStr;
    }

    static void addColumn(final AISBuilder builder,
                          final String schemaName, final String tableName, final String columnName,
                          int colpos, DataTypeDescriptor type, boolean nullable, boolean autoIncrement,
                          final String defaultValue) {
        Long[] typeParameters = new Long[2];
        Type builderType = columnType(type, typeParameters, schemaName, tableName, columnName);
        String charset = null, collation = null;
        if (type.getCharacterAttributes() != null) {
            charset = type.getCharacterAttributes().getCharacterSet();
            collation = type.getCharacterAttributes().getCollation();
        }
        builder.column(schemaName, tableName, columnName, 
                       colpos,
                       builderType.name(), typeParameters[0], typeParameters[1],
                       nullable,
                       autoIncrement,
                       charset, collation,
                       defaultValue);
    }

    static Type columnType(DataTypeDescriptor type, Long[] typeParameters,
                           String schemaName, String tableName, String columnName) {
        Type builderType = typeMap.get(type.getTypeId());
        if (builderType == null) {
            throw new UnsupportedDataTypeException(new TableName(schemaName, tableName), columnName, type.getTypeName());
        }
        
        if (builderType.nTypeParameters() == 1) {
            typeParameters[0] = (long)type.getMaximumWidth();
            typeParameters[1] = null;
        } else if (builderType.nTypeParameters() == 2) {
            typeParameters[0] = (long)type.getPrecision();
            typeParameters[1] = (long)type.getScale();
        } else {
            typeParameters[0] = typeParameters[1] = null;
        }
        return builderType;
    }

    private static final Logger logger = LoggerFactory.getLogger(TableDDL.class);


    public static String addIndex(IndexNameGenerator namer, AISBuilder builder, ConstraintDefinitionNode cdn,
                                  String schemaName, String tableName)  {
        // We don't (yet) have a constraint representation so override any provided
        UserTable table = builder.akibanInformationSchema().getUserTable(schemaName, tableName);
        final String constraint;
        String indexName = cdn.getName();
        int colPos = 0;

        if (cdn.getConstraintType() == ConstraintDefinitionNode.ConstraintType.CHECK) {
            throw new UnsupportedCheckConstraintException ();
        }
        else if (cdn.getConstraintType() == ConstraintDefinitionNode.ConstraintType.PRIMARY_KEY) {
            indexName = constraint = Index.PRIMARY_KEY_CONSTRAINT;
        }
        else if (cdn.getConstraintType() == ConstraintDefinitionNode.ConstraintType.UNIQUE) {
            constraint = Index.UNIQUE_KEY_CONSTRAINT;
        } 
        // Indexes do things a little differently because they need to support Group indexes, Full Text and Geospacial
        else if (cdn.getConstraintType() == ConstraintDefinitionNode.ConstraintType.INDEX) {
            return generateTableIndex(namer, builder, cdn, table);
        } else {
            throw new UnsupportedCheckConstraintException ();
        }

        if(indexName == null) {
            indexName = namer.generateIndexName(null, cdn.getColumnList().get(0).getName(), constraint);
        }
        
        builder.index(schemaName, tableName, indexName, true, constraint);
        
        for (ResultColumn col : cdn.getColumnList()) {
            if(table.getColumn(col.getName()) == null) {
                throw new NoSuchColumnException(col.getName());
            }
            builder.indexColumn(schemaName, tableName, indexName, col.getName(), colPos++, true, null);
        }
        return indexName;
    }

    public static TableName getReferencedName(String schemaName, FKConstraintDefinitionNode fkdn) {
        return convertName(schemaName, fkdn.getRefTableName());
    }

    public static void addJoin(final AISBuilder builder, final FKConstraintDefinitionNode fkdn,
                               final String defaultSchemaName, final String schemaName, final String tableName)  {
        TableName parentName = getReferencedName(defaultSchemaName, fkdn);
        String joinName = String.format("%s/%s/%s/%s",
                                        parentName.getSchemaName(),
                                        parentName.getTableName(),
                                        schemaName, tableName);

        AkibanInformationSchema ais = builder.akibanInformationSchema();
        // Check parent table exists
        UserTable parentTable = ais.getUserTable(parentName);
        if (parentTable == null) {
            throw new JoinToUnknownTableException(new TableName(schemaName, tableName), parentName);
        }
        // Check child table exists
        UserTable childTable = ais.getUserTable(schemaName, tableName);
        if (childTable == null) {
            throw new NoSuchTableException(schemaName, tableName);
        }
        // Check that fk list and pk list are the same size
        String[] fkColumns = columnNamesFromListOrPK(fkdn.getColumnList(), null); // No defaults for child table
        String[] pkColumns = columnNamesFromListOrPK(fkdn.getRefResultColumnList(), parentTable.getPrimaryKey());

        int actualPkColCount = parentTable.getPrimaryKeyIncludingInternal().getColumns().size();
        if ((fkColumns.length != actualPkColCount) || (pkColumns.length != actualPkColCount)) {
            throw new JoinColumnMismatchException(fkdn.getColumnList().size(),
                                                  new TableName(schemaName, tableName),
                                                  parentName,
                                                  parentTable.getPrimaryKeyIncludingInternal().getColumns().size());
        }

        int colPos = 0;
        while((colPos < fkColumns.length) && (colPos < pkColumns.length)) {
            String fkColumn = fkColumns[colPos];
            String pkColumn = pkColumns[colPos];
            if (childTable.getColumn(fkColumn) == null) {
                throw new NoSuchColumnException(String.format("%s.%s.%s", schemaName, tableName, fkColumn));
            }
            if (parentTable.getColumn(pkColumn) == null) {
                throw new JoinToWrongColumnsException(new TableName(schemaName, tableName),
                                                      fkColumn,
                                                      parentName,
                                                      pkColumn);
            }
            ++colPos;
        }

        builder.joinTables(joinName, parentName.getSchemaName(), parentName.getTableName(), schemaName, tableName);

        colPos = 0;
        while(colPos < fkColumns.length) {
            builder.joinColumns(joinName,
                                parentName.getSchemaName(), parentName.getTableName(), pkColumns[colPos],
                                schemaName, tableName, fkColumns[colPos]);
            ++colPos;
        }
        builder.addJoinToGroup(parentTable.getGroup().getName(), joinName, 0);
    }
    
    /**
     * Add a minimal parent table (PK) with group to the builder based upon the AIS.
     */
    public static void addParentTable(final AISBuilder builder, final AkibanInformationSchema ais,
                                      final FKConstraintDefinitionNode fkdn, final String schemaName) {

        TableName parentName = getReferencedName(schemaName, fkdn);
        UserTable parentTable = ais.getUserTable(parentName);
        if (parentTable == null) {
            throw new NoSuchTableException (parentName);
        }

        builder.userTable(parentName.getSchemaName(), parentName.getTableName());
        
        builder.index(parentName.getSchemaName(), parentName.getTableName(), Index.PRIMARY_KEY_CONSTRAINT, true,
                      Index.PRIMARY_KEY_CONSTRAINT);
        int colpos = 0;
        for (Column column : parentTable.getPrimaryKeyIncludingInternal().getColumns()) {
            builder.column(parentName.getSchemaName(), parentName.getTableName(),
                    column.getName(),
                    colpos,
                    column.getType().name(),
                    column.getTypeParameter1(),
                    column.getTypeParameter2(),
                    column.getNullable(),
                    false, //column.getInitialAutoIncrementValue() != 0,
                    column.getCharsetAndCollation() != null ? column.getCharsetAndCollation().charset() : null,
                    column.getCharsetAndCollation() != null ? column.getCharsetAndCollation().collation() : null);
            builder.indexColumn(parentName.getSchemaName(), parentName.getTableName(), Index.PRIMARY_KEY_CONSTRAINT,
                    column.getName(), colpos++, true, 0);
        }
        final TableName groupName;
        if(parentTable.getGroup() == null) {
            groupName = parentName;
        } else {
            groupName = parentTable.getGroup().getName();
        }
        builder.createGroup(groupName.getTableName(), groupName.getSchemaName());
        builder.addTableToGroup(groupName, parentName.getSchemaName(), parentName.getTableName());
    }


    private static String[] columnNamesFromListOrPK(ResultColumnList list, PrimaryKey pk) {
        String[] names = (list == null) ? null: list.getColumnNames();
        if(((names == null) || (names.length == 0)) && (pk != null)) {
            Index index = pk.getIndex();
            names = new String[index.getKeyColumns().size()];
            int i = 0;
            for(IndexColumn iCol : index.getKeyColumns()) {
                names[i++] = iCol.getColumn().getName();
            }
        }
        if(names == null) {
            names = new String[0];
        }
        return names;
    }
    
    private static String generateTableIndex(IndexNameGenerator namer, 
            AISBuilder builder, 
            ConstraintDefinitionNode cdn, 
            Table table) {
        IndexDefinition id = ((IndexConstraintDefinitionNode)cdn);
        IndexColumnList columnList = id.getIndexColumnList();
        Index tableIndex;
        String indexName = ((IndexConstraintDefinitionNode)cdn).getIndexName();
        if(indexName == null) {
            indexName = namer.generateIndexName(null, columnList.get(0).getColumnName(), Index.KEY_CONSTRAINT);
        }

        if (columnList.functionType() == IndexColumnList.FunctionType.FULL_TEXT) {
            logger.debug ("Building Full text index on table {}", table.getName()) ;
            tableIndex = IndexDDL.buildFullTextIndex (builder, table.getName(), indexName, id);
        } else if (IndexDDL.checkIndexType (id, table.getName()) == Index.IndexType.TABLE) {
            logger.debug ("Building Table index on table {}", table.getName()) ;
            tableIndex = IndexDDL.buildTableIndex (builder, table.getName(), indexName, id);
        } else {
            logger.debug ("Building Group index on table {}", table.getName());
            tableIndex = IndexDDL.buildGroupIndex (builder, table.getName(), indexName, id);
        }

        boolean indexIsSpatial = columnList.functionType() == IndexColumnList.FunctionType.Z_ORDER_LAT_LON;
        // Can't check isSpatialCompatible before the index columns have been added.
        if (indexIsSpatial && !Index.isSpatialCompatible(tableIndex)) {
            throw new BadSpatialIndexException(tableIndex.getIndexName().getTableName(), null);
        }
        return tableIndex.getIndexName().getName();
    }

    private final static Map<TypeId, Type> typeMap  = typeMapping();
    
    private static Map<TypeId, Type> typeMapping() {
        HashMap<TypeId, Type> types = new HashMap<>();
        types.put(TypeId.BOOLEAN_ID, Types.TINYINT);
        types.put(TypeId.TINYINT_ID, Types.TINYINT);
        types.put(TypeId.SMALLINT_ID, Types.SMALLINT);
        types.put(TypeId.INTEGER_ID, Types.INT);
        types.put(TypeId.MEDIUMINT_ID, Types.MEDIUMINT);
        types.put(TypeId.BIGINT_ID, Types.BIGINT);
        
        types.put(TypeId.TINYINT_UNSIGNED_ID, Types.U_TINYINT);
        types.put(TypeId.SMALLINT_UNSIGNED_ID, Types.U_SMALLINT);
        types.put(TypeId.MEDIUMINT_UNSIGNED_ID, Types.U_MEDIUMINT);
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

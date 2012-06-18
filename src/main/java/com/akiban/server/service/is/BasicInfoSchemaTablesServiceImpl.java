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

package com.akiban.server.service.is;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.CharsetAndCollation;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.GroupIndex;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.JoinColumn;
import com.akiban.ais.model.Schema;
import com.akiban.ais.model.TableIndex;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.UserTable;
import com.akiban.ais.model.aisb2.AISBBasedBuilder;
import com.akiban.ais.model.aisb2.NewAISBuilder;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.memoryadapter.MemoryAdapter;
import com.akiban.qp.memoryadapter.MemoryGroupCursor;
import com.akiban.qp.memoryadapter.MemoryTableFactory;
import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.IndexScanSelector;
import com.akiban.qp.row.Row;
import com.akiban.qp.row.ValuesRow;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.service.Service;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.session.SessionService;
import com.akiban.server.store.AisHolder;
import com.akiban.server.store.SchemaManager;
import com.akiban.server.store.statistics.IndexStatistics;
import com.akiban.server.types.AkType;
import com.google.inject.Inject;

import java.util.Iterator;

import static com.akiban.qp.memoryadapter.MemoryGroupCursor.GroupScan;

public class BasicInfoSchemaTablesServiceImpl implements Service<BasicInfoSchemaTablesService>, BasicInfoSchemaTablesService {
    private static final String SCHEMA_NAME = TableName.INFORMATION_SCHEMA;
    static final TableName SCHEMATA = new TableName(SCHEMA_NAME, "schemata");
    static final TableName TABLES = new TableName(SCHEMA_NAME, "tables");
    static final TableName COLUMNS = new TableName(SCHEMA_NAME, "columns");
    static final TableName TABLE_CONSTRAINTS = new TableName(SCHEMA_NAME, "table_constraints");
    static final TableName REFERENTIAL_CONSTRAINTS = new TableName(SCHEMA_NAME, "referential_constraints");
    static final TableName GROUPING_CONSTRAINTS = new TableName(SCHEMA_NAME, "grouping_constraints");
    static final TableName KEY_COLUMN_USAGE = new TableName(SCHEMA_NAME, "key_column_usage");
    static final TableName INDEXES = new TableName(SCHEMA_NAME, "indexes");
    static final TableName INDEX_COLUMNS = new TableName(SCHEMA_NAME, "index_columns");

    private static final String CHARSET_SCHEMA = SCHEMA_NAME;
    private static final String COLLATION_SCHEMA = SCHEMA_NAME;

    private final AisHolder aisHolder;
    private final SchemaManager schemaManager;

    @Inject
    public BasicInfoSchemaTablesServiceImpl(AisHolder aisHolder, SchemaManager schemaManager) {
        this.aisHolder = aisHolder;
        this.schemaManager = schemaManager;
    }

    @Override
    public BasicInfoSchemaTablesServiceImpl cast() {
        return this;
    }

    @Override
    public Class<BasicInfoSchemaTablesService> castClass() {
        return BasicInfoSchemaTablesService.class;
    }

    @Override
    public void start() {
        AkibanInformationSchema ais = createTablesToRegister();
        attachFactories(ais, true);
    }

    @Override
    public void stop() {
        // Nothing
    }

    @Override
    public void crash() {
        // Nothing
    }

    private abstract class BasicFactoryBase implements MemoryTableFactory {
        private final UserTable sourceTable;

        public BasicFactoryBase(UserTable sourceTable) {
            this.sourceTable = sourceTable;
        }

        @Override
        public TableName getName() {
            return sourceTable.getName();
        }

        @Override
        public UserTable getTableDefinition() {
            return sourceTable;
        }

        @Override
        public Cursor getIndexCursor(Index index, Session session, IndexKeyRange keyRange, API.Ordering ordering, IndexScanSelector scanSelector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IndexStatistics computeIndexStatistics(Session session, Index index) {
            throw new UnsupportedOperationException();
        }

        protected RowType getRowType(MemoryAdapter adapter) {
            return adapter.schema().userTableRowType(adapter.schema().ais().getUserTable(sourceTable.getName()));
        }
    }

    private class SchemataFactory extends BasicFactoryBase {
        public SchemataFactory(UserTable sourceTable) {
            super(sourceTable);
        }

        @Override
        public GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            return aisHolder.getAis().getSchemas().size();
        }

        private class Scan implements GroupScan {
            final RowType rowType;
            final Iterator<Schema> it = aisHolder.getAis().getSchemas().values().iterator();
            int rowCounter;

            public Scan(RowType rowType) {
                this.rowType = rowType;
            }

            @Override
            public Row next() {
                if(!it.hasNext()) {
                    return null;
                }
                Schema schema = it.next();
                return new ValuesRow(rowType,
                                     schema.getName(),
                                     null, // owner
                                     null, // charset
                                     null, // collation
                                     ++rowCounter /*hidden pk*/);

            }

            @Override
            public void close() {
            }
        }
    }

    private class TablesFactory extends BasicFactoryBase {
        public TablesFactory(UserTable sourceTable) {
            super(sourceTable);
        }

        @Override
        public GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            return aisHolder.getAis().getUserTables().size();
        }

        private class Scan implements GroupScan {
            final RowType rowType;
            final Iterator<UserTable> it = aisHolder.getAis().getUserTables().values().iterator();
            int rowCounter;

            public Scan(RowType rowType) {
                this.rowType = rowType;
            }

            @Override
            public Row next() {
                if(!it.hasNext()) {
                    return null;
                }
                UserTable table = it.next();
                final String tableType = table.hasMemoryTableFactory() ? "DICTIONARY VIEW" : "TABLE";
                return new ValuesRow(rowType,
                                     table.getName().getSchemaName(),
                                     table.getName().getTableName(),
                                     tableType,
                                     table.getTableId(),
                                     CHARSET_SCHEMA,
                                     table.getCharsetAndCollation().charset(),
                                     COLLATION_SCHEMA,
                                     table.getCharsetAndCollation().collation(),
                                     ++rowCounter /* hidden pk */);
            }

            @Override
            public void close() {
            }
        }
    }

    private class ColumnsFactory extends BasicFactoryBase {
        public ColumnsFactory(UserTable sourceTable) {
            super(sourceTable);
        }

        @Override
        public GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            long count = 0;
            for(UserTable table : aisHolder.getAis().getUserTables().values()) {
                count += table.getColumns().size();
            }
            return count;
        }

        private class Scan implements GroupScan {
            final RowType rowType;
            final Iterator<UserTable> tableIt = aisHolder.getAis().getUserTables().values().iterator();
            Iterator<Column> columnIt;
            int rowCounter;

            public Scan(RowType rowType) {
                this.rowType = rowType;
            }

            @Override
            public Row next() {
                if(columnIt == null || !columnIt.hasNext()) {
                    if(tableIt.hasNext()) {
                        columnIt = tableIt.next().getColumns().iterator();
                    } else {
                        return null;
                    }
                }
                Column column = columnIt.next();

                Integer scale = null;
                Integer precision = null;
                if(column.getType().akType() == AkType.DECIMAL) {
                    scale = column.getTypeParameter1().intValue();
                    precision = column.getTypeParameter2().intValue();
                }
                final Long length;
                if(column.getType().fixedSize()) {
                    length = column.getMaxStorageSize();
                } else {
                    length = column.getTypeParameter1();
                }

                // TODO: This should come from type attributes when new types go in
                CharsetAndCollation charAndColl = null;
                if(column.getType().akType() == AkType.VARCHAR || column.getType().akType() == AkType.TEXT) {
                    charAndColl = column.getCharsetAndCollation();
                }

                return new ValuesRow(rowType,
                                     column.getTable().getName().getSchemaName(),
                                     column.getTable().getName().getTableName(),
                                     column.getName(),
                                     column.getPosition(),
                                     column.getType().name(),
                                     column.getNullable() ? "YES" : "NO",
                                     length,
                                     scale,
                                     precision,
                                     column.getPrefixSize(),
                                     column.getInitialAutoIncrementValue(),
                                     charAndColl != null ? CHARSET_SCHEMA : null,
                                     charAndColl != null ? charAndColl.charset() : null,
                                     charAndColl != null ? COLLATION_SCHEMA : null,
                                     charAndColl != null ? charAndColl.collation() : null,
                                     ++rowCounter /* hidden pk */);
            }

            @Override
            public void close() {
            }
        }
    }

    private class TableConstraintsFactory extends BasicFactoryBase {
        public TableConstraintsFactory(UserTable sourceTable) {
            super(sourceTable);
        }

        @Override
        public GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            long count = 0;
            TableConstraintsIteration it = new TableConstraintsIteration(aisHolder.getAis().getUserTables().values().iterator());
            while(it.next()) {
                ++count;
            }
            return count;
        }

        private class Scan implements GroupScan {
            final RowType rowType;
            final TableConstraintsIteration it = new TableConstraintsIteration(aisHolder.getAis().getUserTables().values().iterator());
            int rowCounter;

            public Scan(RowType rowType) {
                this.rowType = rowType;
            }

            @Override
            public Row next() {
                if(!it.next()) {
                    return null;
                }
                return new ValuesRow(rowType,
                                     it.getTable().getName().getSchemaName(),
                                     it.getTable().getName().getTableName(),
                                     it.getName(),
                                     it.getType(),
                                     ++rowCounter /* hidden pk */);
            }

            @Override
            public void close() {
            }
        }
    }

    private class ReferentialConstraintsFactory extends BasicFactoryBase {
        public ReferentialConstraintsFactory(UserTable sourceTable) {
            super(sourceTable);
        }

        @Override
        public GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            return 0;
        }

        private class Scan implements GroupScan {
            final RowType rowType;

            public Scan(RowType rowType) {
                this.rowType = rowType;
            }

            @Override
            public Row next() {
                return null;
            }

            @Override
            public void close() {
            }
        }
    }

    private class GroupingConstraintsFactory extends BasicFactoryBase {
        public GroupingConstraintsFactory(UserTable sourceTable) {
            super(sourceTable);
        }

        @Override
        public GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            int count = 0;
            for(UserTable userTable : aisHolder.getAis().getUserTables().values()) {
                if(userTable.getParentJoin() != null) {
                    ++count;
                }
            }
            return count;
        }

        private class Scan implements GroupScan {
            final RowType rowType;
            final Iterator<UserTable> tableIt = aisHolder.getAis().getUserTables().values().iterator();
            int rowCounter;

            public Scan(RowType rowType) {
                this.rowType = rowType;
            }

            private UserTable advance() {
                while(tableIt.hasNext()) {
                    UserTable table = tableIt.next();
                    if(table.getParentJoin() != null) {
                        return table;
                    }
                }
                return null;
            }

            @Override
            public Row next() {
                UserTable table = advance();
                if(table == null) {
                    return null;
                }
                Join join = table.getParentJoin();
                return new ValuesRow(rowType,
                                     table.getName().getSchemaName(),
                                     table.getName().getTableName(),
                                     join.getName(),
                                     join.getParent().getName().getSchemaName(),
                                     join.getParent().getName().getTableName(),
                                     ++rowCounter /* hidden pk */);
            }

            @Override
            public void close() {
            }
        }
    }

    private class KeyColumnUsageFactory extends BasicFactoryBase {
        public KeyColumnUsageFactory(UserTable sourceTable) {
            super(sourceTable);
        }

        @Override
        public GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            int count = 0;
            TableConstraintsIteration it = new TableConstraintsIteration(aisHolder.getAis().getUserTables().values().iterator());
            while(it.next()) {
                ++count;
            }
            return count;
        }

        private class Scan implements GroupScan {
            final RowType rowType;
            final TableConstraintsIteration it = new TableConstraintsIteration(aisHolder.getAis().getUserTables().values().iterator());
            Iterator<IndexColumn> indexColIt;
            Iterator<JoinColumn> joinColIt;
            String colName;
            int colPos;
            Integer posInUnique;
            int rowCounter;

            public Scan(RowType rowType) {
                this.rowType = rowType;
            }

            // Find position in parents PK
            private Integer findPosInIndex(Column column, Index index) {
                // Find position in the parents pk
                for(IndexColumn indexCol : index.getKeyColumns()) {
                    if(column == indexCol.getColumn()) {
                        return indexCol.getPosition();
                    }
                }
                return null;
            }

            public boolean advance() {
                posInUnique = null;
                if(joinColIt != null && joinColIt.hasNext()) {
                    JoinColumn joinColumn = joinColIt.next();
                    colName = joinColumn.getChild().getName();
                    posInUnique = findPosInIndex(joinColumn.getParent(), joinColumn.getParent().getUserTable().getPrimaryKey().getIndex());
                } else if(indexColIt != null && indexColIt.hasNext()) {
                    IndexColumn indexColumn = indexColIt.next();
                    colName = indexColumn.getColumn().getName();
                } else if(it.next()) {
                    joinColIt = null;
                    indexColIt = null;
                    if(it.isGrouping()) {
                        joinColIt = it.getTable().getParentJoin().getJoinColumns().iterator();
                    } else {
                        indexColIt = it.getIndex().getKeyColumns().iterator();
                    }
                    colPos = -1;
                    return advance();
                } else {
                    return false;
                }
                ++colPos;
                return true;
            }

            @Override
            public Row next() {
                if(!advance()) {
                    return null;
                }
                return new ValuesRow(rowType,
                                     it.getTable().getName().getSchemaName(),
                                     it.getTable().getName().getTableName(),
                                     it.getName(),
                                     colName,
                                     colPos,
                                     posInUnique,
                                     ++rowCounter /* hidden pk */);
            }

            @Override
            public void close() {
            }
        }
    }

    private class IndexesFactory extends BasicFactoryBase {
        public IndexesFactory(UserTable sourceTable) {
            super(sourceTable);
        }

        @Override
        public GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            long count = 0;
            for(UserTable table : aisHolder.getAis().getUserTables().values()) {
                count += table.getIndexes().size();
            }
            return count;
        }

        private class Scan implements GroupScan {
            final RowType rowType;
            final IndexIteration indexIt = new IndexIteration(aisHolder.getAis().getUserTables().values().iterator());
            int rowCounter;

            public Scan(RowType rowType) {
                this.rowType = rowType;
            }

            @Override
            public Row next() {
                Index index = indexIt.next();
                if(index == null) {
                    return null;
                }
                final String indexType;
                String constraintName = null;
                if(index.isPrimaryKey()) {
                    indexType = constraintName = Index.PRIMARY_KEY_CONSTRAINT;
                } else if(index.isUnique()) {
                    constraintName = index.getIndexName().getName();
                    indexType = Index.UNIQUE_KEY_CONSTRAINT;
                } else {
                    indexType = "INDEX";
                }
                return new ValuesRow(rowType,
                                     indexIt.getTable().getName().getSchemaName(),
                                     indexIt.getTable().getName().getTableName(),
                                     index.getIndexName().getName(),
                                     constraintName,
                                     index.getIndexId(),
                                     indexType,
                                     index.isUnique() ? "YES" : "NO",
                                     index.isGroupIndex() ? index.getJoinType().name() : null,
                                     ++rowCounter /*hidden pk*/);
            }

            @Override
            public void close() {
            }
        }
    }

    private class IndexColumnsFactory extends BasicFactoryBase {
        public IndexColumnsFactory(UserTable sourceTable) {
            super(sourceTable);
        }

        @Override
        public MemoryGroupCursor.GroupScan getGroupScan(MemoryAdapter adapter) {
            return new Scan(getRowType(adapter));
        }

        @Override
        public long rowCount() {
            IndexIteration indexIt = new IndexIteration(aisHolder.getAis().getUserTables().values().iterator());
            long count = 0;
            Index index;
            while((index = indexIt.next()) != null) {
                count += index.getKeyColumns().size();
            }
            return count;
        }

        private class Scan implements GroupScan {
            final RowType rowType;
            final IndexIteration indexIt = new IndexIteration(aisHolder.getAis().getUserTables().values().iterator());
            Iterator<IndexColumn> indexColumnIt;
            int rowCounter;

            public Scan(RowType rowType) {
                this.rowType = rowType;
            }

            private IndexColumn advance() {
                while(indexColumnIt == null || !indexColumnIt.hasNext()) {
                    Index index = indexIt.next();
                    if(index == null) {
                        return null;
                    }
                    indexColumnIt = index.getKeyColumns().iterator(); // Always at least 1
                }
                return indexColumnIt.next();
            }

            @Override
            public Row next() {
                IndexColumn indexColumn = advance();
                if(indexColumn == null) {
                    return null;
                }
                return new ValuesRow(rowType,
                                     indexIt.getTable().getName().getSchemaName(),
                                     indexColumn.getIndex().getIndexName().getName(),
                                     indexIt.getTable().getName().getTableName(),
                                     indexColumn.getColumn().getTable().getName().getTableName(),
                                     indexColumn.getColumn().getName(),
                                     indexColumn.getPosition(),
                                     indexColumn.isAscending() ? "YES" : "NO",
                                     indexColumn.getIndexedLength(),
                                     ++rowCounter /*hidden pk*/);
            }

            @Override
            public void close() {
            }
        }
    }

    private static class TableConstraintsIteration {
        private final Iterator<UserTable> tableIt;
        private Iterator<? extends Index> indexIt;
        private UserTable curTable;
        private Index curIndex;
        private String name;
        private String type;

        public TableConstraintsIteration(Iterator<UserTable> tableIt) {
            this.tableIt = tableIt;
        }

        public boolean next() {
            while(indexIt != null || tableIt.hasNext()) {
                if(curTable == null) {
                    curTable = tableIt.next();
                    if(curTable.getParentJoin() != null) {
                        name = curTable.getParentJoin().getName(); // TODO: Need a real constraint name here
                        type = "GROUPING";
                        return true;
                    }
                }
                if(indexIt == null) {
                    indexIt = curTable.getIndexes().iterator();
                }
                while(indexIt.hasNext()) {
                    curIndex = indexIt.next();
                    if(curIndex.isUnique()) {
                        name = curIndex.getIndexName().getName();
                        type = curIndex.isPrimaryKey() ? "PRIMARY KEY" : curIndex.getConstraint();
                        return true;
                    }
                }
                indexIt = null;
                curIndex = null;
                curTable = null;
            }
            return false;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public UserTable getTable() {
            return curTable;
        }

        public Index getIndex() {
            return curIndex;
        }

        public boolean isGrouping() {
            return indexIt == null;
        }
    }

    private static class IndexIteration {
        private final Iterator<UserTable> tableIt;
        Iterator<TableIndex> tableIndexIt;
        Iterator<GroupIndex> groupIndexIt;
        UserTable curTable;

        public IndexIteration(Iterator<UserTable> tableIt) {
            this.tableIt = tableIt;
        }

        Index next() {
            while(tableIndexIt == null || !tableIndexIt.hasNext()) {
                while(groupIndexIt != null && groupIndexIt.hasNext()) {
                    GroupIndex index = groupIndexIt.next();
                    if(index.leafMostTable() == curTable) {
                        return index;
                    }
                }
                if(tableIt.hasNext()) {
                    curTable = tableIt.next();
                    tableIndexIt = curTable.getIndexes().iterator();
                    groupIndexIt = curTable.getGroup().getIndexes().iterator();
                } else {
                    return null;
                }
            }
            return tableIndexIt.next();
        }

        UserTable getTable() {
            return curTable;
        }
    }

    //
    // Package, for testing
    //

    static AkibanInformationSchema createTablesToRegister() {
        NewAISBuilder builder = AISBBasedBuilder.create();
        builder.userTable(SCHEMATA)
                .colString("schema_name", 128, false)
                .colString("schema_owner", 128, true)
                .colString("default_character_set_name", 128, true)
                .colString("default_collation_name", 128, true);
        //.pk("schema_name")
        builder.userTable(TABLES)
                .colString("table_schema", 128, false)
                .colString("table_name", 128, false)
                .colString("table_type", 128, false)
                .colBigInt("table_id", false)
                .colString("character_set_schema", 128, true)
                .colString("character_set_name", 128, true)
                .colString("collation_schema", 128, true)
                .colString("collation_name", 128, true);
        //primary key (schema_name, table_name)
        //foreign_key (schema_name) references SCHEMATA (schema_name)
        //foreign key (character_set_schema, character_set_name) references CHARACTER_SETS
        //foreign key (collations_schema, collation_name) references COLLATIONS
        builder.userTable(COLUMNS)
                .colString("schema_name", 128, false)
                .colString("table_name", 128, false)
                .colString("column_name", 128, false)
                .colBigInt("position", false)
                .colString("type", 32, false)
                .colString("nullable", 3, false)
                .colBigInt("length", false)
                .colBigInt("precision", true)
                .colBigInt("scale", true)
                .colBigInt("prefix_size", true)
                .colBigInt("identity_start", true)
                .colString("character_set_schema", 128, true)
                .colString("character_set_name", 128, true)
                .colString("collation_schema", 128, true)
                .colString("collation_name", 128, true);
        //primary key(schema_name, table_name, column_name)
        //foreign key(schema_name, table_name) references TABLES (schema_name, table_name)
        //foreign key (type) references TYPES (type_name)
        //foreign key (character_set_schema, character_set_name) references CHARACTER_SETS
        //foreign key (collation_schema, collation_name) references COLLATIONS
        builder.userTable(TABLE_CONSTRAINTS)
                .colString("schema_name", 128, false)
                .colString("table_name", 128, false)
                .colString("constraint_name", 128, false)
                .colString("constraint_type", 32, false);
        //primary key (schema_name, table_name, constraint_name)
        //foreign key (schema_name, table_name) references TABLES
        builder.userTable(REFERENTIAL_CONSTRAINTS)
            .colString("constraint_schema_name", 128, false)
            .colString("constraint_table_name", 128, false)
            .colString("constraint_name", 128, false)
            .colString("unique_schema_name", 128, false)
            .colString("unique_table_name", 128, false)
            .colString("unique_constraint_name", 128, false)
            .colString("update_rule", 32, false)
            .colString("delete_rule", 32, false);
        //foreign key (schema_name, table_name, constraint_name)
        //    references TABLE_CONSTRAINTS (schema_name, table_name, constraint_name)
        builder.userTable(GROUPING_CONSTRAINTS)
            .colString("constraint_schema_name", 128, false)
            .colString("constraint_table_name", 128, false)
            .colString("constraint_name", 128, false)
            .colString("unique_schema_name", 128, false)
            .colString("unique_table_name", 128, false);
        //foreign key (schema_name, table_name, constraint_name)
        //    references TABLE_CONSTRAINTS (schema_name, table_name, constraint_name)
        builder.userTable(KEY_COLUMN_USAGE)
            .colString("schema_name", 128, true)
            .colString("table_name", 128, true)
            .colString("constraint_name", 128, true)
            .colString("column_name", 128, true)
            .colBigInt("ordinal_position", false)
            .colBigInt("position_in_unique_constraint", true);
        //primary key  (schema_name, table_name, constraint_name, column_name),
        //foreign key (schema_name, table_name, constraint_name) references TABLE_CONSTRAINTS
        builder.userTable(INDEXES)
                .colString("schema_name", 128, false)
                .colString("table_name", 128, false)
                .colString("index_name", 128, false)
                .colString("constraint_name", 128, true)
                .colBigInt("index_id", false)
                .colString("index_type", 128, false)
                .colString("is_unique", 3, false)
                .colString("join_type", 32, true);
        //primary key(schema_name, table_name, index_name)
        //foreign key (schema_name, table_name, constraint_name)
        //    references TABLE_CONSTRAINTS (schema_name, table_name, constraint_name)
        //foreign key (schema_name, table_name) references TABLES (schema_name, table_name)
        builder.userTable(INDEX_COLUMNS)
                .colString("schema_name", 128, false)
                .colString("index_name", 128, false)
                .colString("index_table_name", 128, false)
                .colString("column_table_name", 128, false)
                .colString("column_name", 128, false)
                .colBigInt("ordinal_position", false)
                .colString("is_ascending", 128, false)
                .colBigInt("indexed_length", true);
        //primary key(schema_name, index_name, index_table_name, column_table_name, column_name)
        //foreign key(schema_name, index_table_name, index_name)
        //    references INDEXES (schema_name, table_name, index_name)
        //foreign key (schema_name, column_table_name, column_name)
        //    references COLUMNS (schema_name, table_name, column_name)

        return builder.ais(false);
    }

    private void attach(AkibanInformationSchema ais, boolean doRegister, TableName name, Class<? extends BasicFactoryBase> clazz) {
        UserTable table = ais.getUserTable(name);
        final BasicFactoryBase factory;
        try {
            factory = clazz.getConstructor(BasicInfoSchemaTablesServiceImpl.class, UserTable.class).newInstance(this, table);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        if(doRegister) {
            schemaManager.registerMemoryInformationSchemaTable(table, factory);
        } else {
            table.setMemoryTableFactory(factory);
        }
    }

    void attachFactories(AkibanInformationSchema ais, boolean doRegister) {
        attach(ais, doRegister, SCHEMATA, SchemataFactory.class);
        attach(ais, doRegister, TABLES, TablesFactory.class);
        attach(ais, doRegister, COLUMNS, ColumnsFactory.class);
        attach(ais, doRegister, TABLE_CONSTRAINTS, TableConstraintsFactory.class);
        attach(ais, doRegister, REFERENTIAL_CONSTRAINTS, ReferentialConstraintsFactory.class);
        attach(ais, doRegister, GROUPING_CONSTRAINTS, GroupingConstraintsFactory.class);
        attach(ais, doRegister, KEY_COLUMN_USAGE, KeyColumnUsageFactory.class);
        attach(ais, doRegister, INDEXES, IndexesFactory.class);
        attach(ais, doRegister, INDEX_COLUMNS, IndexColumnsFactory.class);
    }
}

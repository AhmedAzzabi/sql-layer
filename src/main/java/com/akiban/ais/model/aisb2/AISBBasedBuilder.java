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

package com.akiban.ais.model.aisb2;

import com.akiban.ais.model.AISBuilder;
import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.DefaultNameGenerator;
import com.akiban.ais.model.Group;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.NameGenerator;
import com.akiban.ais.model.Parameter;
import com.akiban.ais.model.Routine;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.UserTable;
import com.akiban.ais.model.View;
import com.akiban.ais.model.validation.AISInvariants;
import com.akiban.ais.model.validation.AISValidationResults;
import com.akiban.ais.model.validation.AISValidations;
import com.akiban.server.error.InvalidSQLJJarURLException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

public class AISBBasedBuilder
{
    public static NewAISBuilder create() {
        return new ActualBuilder();
    }

    public static NewAISBuilder create(String defaultSchema) {
        return new ActualBuilder().defaultSchema(defaultSchema);
    }

    private static class ActualBuilder implements NewAISBuilder, NewViewBuilder, NewAkibanJoinBuilder, NewRoutineBuilder, NewSQLJJarBuilder {

        // NewAISProvider interface

        @Override
        public AkibanInformationSchema ais() {
            return ais(true);
        }

        @Override
        public AkibanInformationSchema ais(boolean freezeAIS) {
            usable = false;
            aisb.basicSchemaIsComplete();
            aisb.groupingIsComplete();
            AISValidationResults results = aisb.akibanInformationSchema().validate(AISValidations.LIVE_AIS_VALIDATIONS);
            results.throwIfNecessary();
            if (freezeAIS) {
                aisb.akibanInformationSchema().freeze();
            }
            return aisb.akibanInformationSchema();
        }
        
        @Override
        public AkibanInformationSchema unvalidatedAIS() {
            aisb.basicSchemaIsComplete();
            aisb.groupingIsComplete();
            return aisb.akibanInformationSchema();
        }
        // NewAISBuilder interface

        @Override
        public NewAISBuilder defaultSchema(String schema) {
            this.defaultSchema = schema;
            return this;
        }

        @Override
        public NewUserTableBuilder userTable(String table) {
            return userTable(defaultSchema, table);
        }

        @Override
        public NewUserTableBuilder userTable(String schema, String table) {
            checkUsable();
            AISInvariants.checkDuplicateTables(aisb.akibanInformationSchema(), schema, table);
            this.schema = schema;
            this.userTable = table;
            TableName tableName= new TableName (schema, table);
            aisb.userTable(schema, table);
            aisb.createGroup(table, schema);
            aisb.addTableToGroup(tableName, schema, table);
            tablesToGroups.put(TableName.create(schema, table), tableName);
            uTableColumnPos = 0;
            return this;
        }

        @Override
        public NewAISBuilder sequence (String name) {
            return sequence (name, 1, 1, false);
        }
        
        @Override
        public NewAISBuilder sequence (String name, long start, long increment, boolean isCycle) {
            checkUsable();
            AISInvariants.checkDuplicateSequence(aisb.akibanInformationSchema(), defaultSchema, name);
            aisb.sequence(defaultSchema, name, start, increment, Long.MIN_VALUE, Long.MAX_VALUE, isCycle);
            return this;
        }

        @Override
        public NewUserTableBuilder userTable(TableName tableName) {
            return userTable(tableName.getSchemaName(), tableName.getTableName());
        }

        @Override
        public NewViewBuilder view(String view) {
            return view(defaultSchema, view);
        }

        @Override
        public NewViewBuilder view(String schema, String view) {
            checkUsable();
            AISInvariants.checkDuplicateTables(aisb.akibanInformationSchema(), schema, view);
            this.schema = schema;
            this.userTable = view;
            return this;
        }

        @Override
        public NewViewBuilder view(TableName viewName) {
            return view(viewName.getSchemaName(), viewName.getTableName());
        }

        @Override
        public NewRoutineBuilder procedure(String procedure) {
            return procedure(defaultSchema, procedure);
        }

        @Override
        public NewRoutineBuilder procedure(String schema, String procedure) {
            checkUsable();
            AISInvariants.checkDuplicateRoutine(aisb.akibanInformationSchema(), schema, procedure);
            this.schema = schema;
            this.userTable = procedure;
            return this;
        }

        @Override
        public NewRoutineBuilder procedure(TableName procedureName) {
            return procedure(procedureName.getSchemaName(), procedureName.getTableName());
        }

        @Override
        public NewSQLJJarBuilder sqljJar(String jarName) {
            return sqljJar(defaultSchema, jarName);
        }

        @Override
        public NewSQLJJarBuilder sqljJar(String schema, String jarName) {
            checkUsable();
            AISInvariants.checkDuplicateSQLJJar(aisb.akibanInformationSchema(), schema, jarName);
            this.schema = schema;
            this.userTable = jarName;
            return this;
        }

        @Override
        public NewSQLJJarBuilder sqljJar(TableName name) {
            return sqljJar(name.getSchemaName(), name.getTableName());
        }

        @Override
        public NewAISGroupIndexStarter groupIndex(String indexName) {
            return groupIndex(indexName, null);
        }

        @Override
        public NewAISGroupIndexStarter groupIndex(String indexName, Index.JoinType joinType) {
            ActualGroupIndexBuilder actual  = new ActualGroupIndexBuilder(aisb, defaultSchema);
            return actual.groupIndex(indexName, joinType);
        }

        // NewUserTableBuilder interface

        @Override
        public NewUserTableBuilder colLong(String name) {
            return colLong(name, false, NULLABLE_DEFAULT, null);
        }

        @Override
        public NewUserTableBuilder colLong(String name, boolean nullable) {
            return colLong(name, nullable, false, null);
        }

        @Override
        public NewUserTableBuilder autoIncLong(String name, int initialValue) {
            return colLong(name, false, true, initialValue);
        }

        private NewUserTableBuilder colLong(String name, boolean nullable, boolean autoIncrement, Integer initialAutoInc) {
            checkUsable();
            aisb.column(schema, userTable, name, uTableColumnPos++, "INT", 10L, null, nullable, autoIncrement, null, null);
            if (initialAutoInc != null) {
                String sequenceName = "temp-seq-" + userTable + "-" + name;
                long initValue = initialAutoInc.longValue();
                aisb.sequence(schema, sequenceName, 
                              initValue, 1L, Long.MIN_VALUE, Long.MAX_VALUE, 
                              false);
                aisb.columnAsIdentity(schema, userTable, name, sequenceName, true);
                aisb.akibanInformationSchema().
                     getUserTable(schema, userTable).
                     getColumn(name).
                     setInitialAutoIncrementValue(initValue);
            }
            return this;
        }

        @Override
        public NewUserTableBuilder colString(String name, int length) {
            return colString(name, length, NULLABLE_DEFAULT);
        }

        @Override
        public NewUserTableBuilder colString(String name, int length, boolean nullable) {
            return colString(name, length, nullable, CHARSET_DEFAULT);
        }

        @Override
        public NewUserTableBuilder colString(String name, int length, boolean nullable, String charset) {
            checkUsable();
            aisb.column(schema, userTable, name, uTableColumnPos++, "VARCHAR", (long)length, null, nullable, false, charset, null);
            return this;
        }

        @Override
        public NewUserTableBuilder colDouble(String name) {
            return colDouble(name, NULLABLE_DEFAULT);
        }

        @Override
        public NewUserTableBuilder colDouble(String name, boolean nullable) {
            checkUsable();
            aisb.column(schema, userTable, name, uTableColumnPos++, "DOUBLE", null, null, nullable, false, null, null);
            return this;
        }

        @Override
        public NewUserTableBuilder colTimestamp(String name) {
            return colTimestamp(name, NULLABLE_DEFAULT);
        }

        @Override
        public NewUserTableBuilder colTimestamp(String name, boolean nullable) {
            aisb.column(schema, userTable, name, uTableColumnPos++, "TIMESTAMP", null, null, nullable, false, null, null);
            return this;
        }

        @Override
        public NewUserTableBuilder colBigInt(String name) {
            return colBigInt(name, NULLABLE_DEFAULT);
        }

        @Override
        public NewUserTableBuilder colBigInt(String name, boolean nullable) {
            aisb.column(schema, userTable, name, uTableColumnPos++, "BIGINT", null, null, nullable, false, null, null);
            return this;
        }

        @Override
        public NewUserTableBuilder colVarBinary(String name, int length) {
            return colVarBinary(name, length, NULLABLE_DEFAULT);
        }

        @Override
        public NewUserTableBuilder colVarBinary(String name, int length, boolean nullable) {
            aisb.column(schema, userTable, name, uTableColumnPos++, "VARBINARY", (long)length, null, nullable, false, null, null);
            return this;
        }

        @Override
        public NewUserTableBuilder colText(String name) {
            return colText(name, NULLABLE_DEFAULT);
        }

        @Override
        public NewUserTableBuilder colText(String name, boolean nullable) {
            aisb.column(schema, userTable, name, uTableColumnPos++, "TEXT", null, null, nullable, false, null, null);
            return this;
        }

        @Override
        public NewUserTableBuilder pk(String... columns) {
            return key(PRIMARY, columns, true, Index.PRIMARY_KEY_CONSTRAINT);
        }

        @Override
        public NewUserTableBuilder uniqueKey(String indexName, String... columns) {
            return key(indexName, columns, true, Index.UNIQUE_KEY_CONSTRAINT);
        }

        @Override
        public NewUserTableBuilder key(String indexName, String... columns) {
            return key(indexName, columns, false, Index.KEY_CONSTRAINT);
        }

        private NewUserTableBuilder key(String indexName, String[] columns, boolean unique, String constraint) {
            checkUsable();
            aisb.index(schema, userTable, indexName, unique, constraint);
            for (int i=0; i < columns.length; ++i) {
                aisb.indexColumn(schema, userTable, indexName, columns[i], i, true, null);
            }
            return this;
        }

        @Override
        public NewAkibanJoinBuilder joinTo(String table) {
            return joinTo(schema, table);
        }

        @Override
        public NewAkibanJoinBuilder joinTo(TableName name) {
            return joinTo(name.getSchemaName(), name.getTableName());
        }

        @Override
        public NewAkibanJoinBuilder joinTo(String schema, String table) {
            String generated = "autogenerated_"+userTable+"_references_" + table;
            return joinTo(schema, table, generated);
        }

        @Override
        public NewAkibanJoinBuilder joinTo(String schema, String table, String fkName) {
            checkUsable();
            this.fkIndexName = "__akiban_" + fkName;
            this.fkJoinName = "join_" + fkIndexName;
            this.fkIndexPos = 0;
            this.referencesSchema = schema;
            this.referencesTable = table;

            Group oldGroup = aisb.akibanInformationSchema().getUserTable(this.schema, this.userTable).getGroup();

            aisb.index(this.schema, this.userTable, fkIndexName, false, Index.FOREIGN_KEY_CONSTRAINT);
            aisb.joinTables(fkJoinName, schema, table, this.schema, this.userTable);

            TableName fkGroupName = tablesToGroups.get(TableName.create(referencesSchema, referencesTable));
            aisb.moveTreeToGroup(this.schema, this.userTable, fkGroupName, fkJoinName);
            aisb.akibanInformationSchema().removeGroup(oldGroup);
            TableName oldGroupName = tablesToGroups.put(TableName.create(this.schema, this.userTable), fkGroupName);
            assert oldGroup.getName().equals(oldGroupName) : oldGroup.getName() + " != " + oldGroupName;
            return this;
        }

        // NewAkibanJoinBuilder

        @Override
        public NewAkibanJoinBuilder on(String childColumn, String parentColumn) {
            checkUsable();
            aisb.indexColumn(schema, userTable, fkIndexName, childColumn, fkIndexPos, true, null);
            aisb.joinColumns(fkJoinName, referencesSchema, referencesTable, parentColumn, schema, userTable, childColumn);
            return this;
        }

        @Override
        public NewAkibanJoinBuilder and(String childColumn, String parentColumn) {
            return on(childColumn, parentColumn);
        }

        // NewViewBuilder

        @Override
        public NewViewBuilder definition(String definition) {
            Properties properties = new Properties();
            properties.put("database", schema);
            return definition(definition, properties);
        }

        @Override
        public NewViewBuilder definition(String definition, Properties properties) {
            aisb.view(schema, userTable,
                      definition, properties, 
                      new HashMap<TableName,Collection<String>>());
            return this;
        }

        @Override
        public NewViewBuilder references(String table) {
            return references(schema, table);
        }

        @Override
        public NewViewBuilder references(String schema, String table, String... columns) {
            checkUsable();
            View view = aisb.akibanInformationSchema().getView(this.schema, this.userTable);
            TableName tableName = TableName.create(schema, table);
            Collection<String> entry = view.getTableColumnReferences().get(tableName);
            if (entry == null) {
                entry = new HashSet<>();
                view.getTableColumnReferences().put(tableName, entry);
            }
            for (String colname : columns) {
                entry.add(colname);
            }
            return this;
        }

        // NewRoutineBuilder

        @Override
        public NewRoutineBuilder language(String language, Routine.CallingConvention callingConvention) {
            aisb.routine(schema, userTable, language, callingConvention);
            return this;
        }
    
        @Override
        public NewRoutineBuilder paramLongIn(String name) {
            aisb.parameter(schema, userTable, name, Parameter.Direction.IN, "BIGINT", null, null);
            return this;
        }

        @Override
        public NewRoutineBuilder paramStringIn(String name, int length) {
            aisb.parameter(schema, userTable, name, Parameter.Direction.IN, "VARCHAR", (long)length, null);
            return this;
        }

        @Override
        public NewRoutineBuilder paramDoubleIn(String name) {
            aisb.parameter(schema, userTable, name, Parameter.Direction.IN, "DOUBLE", null, null);
            return this;
        }

        @Override
        public NewRoutineBuilder paramLongOut(String name) {
            aisb.parameter(schema, userTable, name, Parameter.Direction.OUT, "BIGINT", null, null);
            return this;
        }

        @Override
        public NewRoutineBuilder paramStringOut(String name, int length) {
            aisb.parameter(schema, userTable, name, Parameter.Direction.OUT, "VARCHAR", (long)length, null);
            return this;
        }

        @Override
        public NewRoutineBuilder paramDoubleOut(String name) {
            aisb.parameter(schema, userTable, name, Parameter.Direction.OUT, "DOUBLE", null, null);
            return this;
        }

        @Override
        public NewRoutineBuilder externalName(String className) {
            return externalName(className, null);
        }

        @Override
        public NewRoutineBuilder externalName(String className, String methodName) {
            return externalName(null, className, methodName);
        }

        @Override
        public NewRoutineBuilder externalName(String jarName,
                                              String className, String methodName) {
            return externalName(defaultSchema, jarName, className, methodName);
        }

        @Override
        public NewRoutineBuilder externalName(String jarSchema, String jarName, 
                                              String className, String methodName) {
            aisb.routineExternalName(schema, userTable, 
                                     jarSchema, jarName, 
                                     className, methodName);
            return this;
        }

        @Override
        public NewRoutineBuilder procDef(String definition) {
            aisb.routineDefinition(schema, userTable, definition);
            return this;
        }

        @Override
        public NewRoutineBuilder sqlAllowed(Routine.SQLAllowed sqlAllowed) {
            aisb.routineSQLAllowed(schema, userTable, sqlAllowed);
            return this;
        }

        @Override
        public NewRoutineBuilder dynamicResultSets(int dynamicResultSets) {
            aisb.routineDynamicResultSets(schema, userTable, dynamicResultSets);
            return this;
        }

        @Override
        public NewRoutineBuilder deterministic(boolean deterministic) {
            aisb.routineDeterministic(schema, userTable, deterministic);
            return this;
        }

        @Override
        public NewRoutineBuilder calledOnNullInput(boolean calledOnNullInput) {
            aisb.routineCalledOnNullInput(schema, userTable, calledOnNullInput);
            return this;
        }

        // NewSQLJJarBuilder

        @Override
        public NewSQLJJarBuilder url(String value, boolean checkReadable) {
            URL url;
            try {
                url = new URL(value);
            }
            catch (MalformedURLException ex1) {
                File file = new File(value);
                try {
                    url = file.toURL();
                }
                catch (MalformedURLException ex2) {
                    // Report original failure.
                    throw new InvalidSQLJJarURLException(schema, userTable, ex1);
                }
                if (checkReadable && file.canRead())
                    checkReadable = false; // Can tell quickly.
            }
            if (checkReadable) {
                InputStream istr = null;
                try {
                    istr = url.openStream(); // Must be able to load it.
                }
                catch (IOException ex) {
                    throw new InvalidSQLJJarURLException(schema, userTable, ex);
                }
                finally {
                    if (istr != null) {
                        try {
                            istr.close();
                        }
                        catch (IOException ex) {
                        }
                    }
                }
            }
            aisb.sqljJar(schema, userTable, url);
            return this;
        }

        // ActualBuilder interface

        public ActualBuilder() {
            aisb = new AISBuilder();
            usable = true;
            tablesToGroups = new HashMap<>();
        }

        // private

        private void checkUsable() {
            if (!usable) {
                throw new IllegalStateException("AIS has already been retrieved; can't reuse");
            }
        }

        // object state

        private final AISBuilder aisb;
        private String defaultSchema;
        private String schema;
        private String userTable;

        private int uTableColumnPos;

        private String fkIndexName;
        private String fkJoinName;
        private int fkIndexPos;
        private String referencesSchema;
        private String referencesTable;

        private boolean usable;

        private final Map<TableName,TableName> tablesToGroups;
        // constants

        private static final boolean NULLABLE_DEFAULT = false;
        private static final String CHARSET_DEFAULT = "UTF-8";
        private static final String PRIMARY = "PRIMARY";
    }

    private static class ActualGroupIndexBuilder implements NewAISGroupIndexStarter, NewAISGroupIndexBuilder {

        // NewAISProvider interface

        @Override
        public AkibanInformationSchema ais(boolean freezeAIS) {
            return ais();
        }

        @Override
        public AkibanInformationSchema ais() {
            if (unstartedIndex()) {
                throw new IllegalStateException("a groupIndex was started but not given any columns: " + indexName);
            }
            return aisb.akibanInformationSchema();
        }

        @Override
        public AkibanInformationSchema unvalidatedAIS() {
            return aisb.akibanInformationSchema();
        }
        // NewAISGroupIndexBuilder interface

        @Override
        public NewAISGroupIndexStarter groupIndex(String indexName) {
            return groupIndex(indexName, null);
        }

        @Override
        public NewAISGroupIndexStarter groupIndex(String indexName, Index.JoinType joinType) {
            this.indexName = indexName;
            this.groupName = null;
            this.position = -1;
            this.joinType = joinType;
            return this;
        }

        @Override
        public NewAISGroupIndexBuilder on(String table, String column) {
            return on(defaultSchema, table, column);
        }

        @Override
        public NewAISGroupIndexBuilder on(String schema, String table, String column) {
            UserTable userTable = aisb.akibanInformationSchema().getUserTable(schema, table);
            if (userTable == null) {
                throw new NoSuchElementException("no table " + schema + '.' + table);
            }
            if (userTable.getGroup() == null) {
                throw new IllegalStateException("ungrouped table: " + schema + '.' + table);
            }
            TableName localGroupName = userTable.getGroup().getName();
            if (localGroupName == null) {
                throw new IllegalStateException("unnamed group for " + schema + '.' + table);
            }
            this.groupName = localGroupName;
            this.position = 0;
            aisb.groupIndex(this.groupName, this.indexName, false, joinType);
            return and(schema, table, column);
        }

        @Override
        public NewAISGroupIndexBuilder and(String table, String column) {
            return and(defaultSchema, table, column);
        }

        @Override
        public NewAISGroupIndexBuilder and(String schema, String table, String column) {
            if (unstartedIndex()) {
                throw new IllegalStateException("never called on(table,column) for " + indexName);
            }
            aisb.groupIndexColumn(groupName, indexName, schema, table, column, position++);
            return this;
        }

        // ActualFinisher interface

        public ActualGroupIndexBuilder(AISBuilder aisb, String defaultSchema) {
            this.aisb = aisb;
            this.defaultSchema = defaultSchema;
        }

        // private methods

        private boolean unstartedIndex() {
            // indexName is assigned as soon as groupIndex is invoked, but groupName is only resolved
            // by on.
            boolean hasUnstarted = (indexName != null) && (groupName == null);
            assert hasUnstarted == (position < 0) : String.format("%s but %d", hasUnstarted, position);
            return hasUnstarted;
        }

        // object states

        private final AISBuilder aisb;
        private final String defaultSchema;
        private int position;
        private Index.JoinType joinType;
        private String indexName;
        private TableName groupName;
    }
}

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

package com.akiban.direct;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.akiban.sql.embedded.JDBCResultSet;
import com.akiban.sql.server.ServerJavaValues;

public abstract class AbstractDirectObject implements DirectObject {

    private final static Map<Connection, Map<BitSet, PreparedStatement>> updateStatementCache = new WeakHashMap<>();
    private final static Map<Connection, Map<BitSet, PreparedStatement>> insertStatementCache = new WeakHashMap<>();

    /*
     * 1. schema_name 2. tableName 3. comma-separated list of column names, 4.
     * comma-separated list of '?' symbols.
     */
    private final static String INSERT_STATEMENT = "insert into \"%s\".\"%s\" (%s) values (%s) returning *";

    /*
     * 1. schema name 2. table name 3. comma-separated list of column_name=?
     * pairs 4. predicate: pkcolumn=?, ...
     */
    private final static String UPDATE_STATEMENT = "update \"%s\".\"%s\" set %s where %s";

    private final static Object NOT_SET = new Object() {
        @Override
        public String toString() {
            return "NOT_SET";
        }
    };

    private static Column[] columns;
    private static String schemaName;
    private static String tableName;

    protected static class Column implements DirectColumn {

        final int columnIndex;
        final String columnName;
        final String propertyName;
        final String propertyType;
        final int primaryKeyFieldIndex;
        final int parentJoinFieldIndex;

        protected Column(final int columnIndex, final String columnName, final String propertyName,
                final String propertyType, final int primaryKeyFieldIndex, final int parentJoinFieldIndex) {
            this.columnIndex = columnIndex;
            this.columnName = columnName;
            this.propertyName = propertyName;
            this.propertyType = propertyType;
            this.primaryKeyFieldIndex = primaryKeyFieldIndex;
            this.parentJoinFieldIndex = parentJoinFieldIndex;
        }

        public int getColumnIndex() {
            return columnIndex;
        }

        public String getColumnName() {
            return columnName;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getPropertyType() {
            return propertyType;
        }
    }

    /**
     * Static initializer of subclass passes a string declaring the columns.
     * Format is columnName:propertyName:propertyType:primaryKeyFieldIndex:
     * parentjoinField,...
     * 
     * @param columnSpecs
     */
    protected static void __init(final String sName, final String tName, final String columnSpecs) {
        try {
            schemaName = sName;
            tableName = tName;
            String[] columnArray = columnSpecs.split(",");
            columns = new Column[columnArray.length];
            for (int index = 0; index < columnArray.length; index++) {
                String[] v = columnArray[index].split(":");
                columns[index] = new Column(index, v[0], v[1], v[2], Integer.parseInt(v[3]), Integer.parseInt(v[4]));
            }
        } catch (Exception e) {
            throw new DirectException(e);
        }
    }

    private Object[] updates;
    private ServerJavaValues values;
    private DirectResultSet rs;

    public void setResults(ServerJavaValues values, DirectResultSet rs) {
        if (updates != null) {
            throw new IllegalStateException("Updates not saved: " + updates);
        }
        this.values = values;
        this.rs = rs;
        updates = null;
    }

    private ServerJavaValues values() {
        if (rs.hasRow()) {
            return values;
        }
        throw new IllegalStateException("No more rows");
    }

    private Object[] updates() {
        if (updates == null) {
            updates = new Object[columns.length];
            Arrays.fill(updates, NOT_SET);
        }
        return updates;
    }

    /**
     * Instantiates (update) values for parent join fields. This method is
     * invoked from {@link DirectIterableImpl#newInstance()}.
     * 
     * @param parent
     */
    void populateJoinFields(DirectObject parent) {
        if (parent instanceof AbstractDirectObject) {
            AbstractDirectObject ado = (AbstractDirectObject) parent;
            if (parent != null) {
                for (int index = 0; index < columns.length; index++) {
                    Column c = columns[index];
                    if (c.parentJoinFieldIndex >= 0) {
                        updates()[index] = ado.__getObject(c.parentJoinFieldIndex);
                    }
                }
            }
        }
    }

    protected boolean __getBOOL(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getBoolean(p);
        } else {
            return (boolean) updates[p];
        }
    }

    protected void __setBOOL(int p, boolean v) {
        updates()[p] = v;
    }

    protected Date __getDATE(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getDate(p);
        } else {
            return (Date) updates[p];
        }
    }

    protected void __setDATE(int p, Date v) {
        updates()[p] = v;
    }

    protected Timestamp __getDATETIME(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getTimestamp(p);
        } else {
            return (Timestamp) updates[p];
        }
    }

    protected void __setDATETIME(int p, Timestamp v) {
        updates()[p] = v;
    }

    protected BigDecimal __getDECIMAL(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getBigDecimal(p);
        } else {
            return (BigDecimal) updates[p];
        }
    }

    protected void __setDECIMAL(int p, BigDecimal v) {
        updates()[p] = v;
    }

    protected double __getDOUBLE(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getDouble(p);
        } else {
            return (double) updates[p];
        }
    }

    protected void __setDOUBLE(int p, double v) {
        updates()[p] = v;
    }

    protected float __getFLOAT(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getFloat(p);
        } else {
            return (float) updates[p];
        }
    }

    protected void __setFLOAT(int p, float v) {
        updates()[p] = v;
    }

    protected int __getINT(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getInt(p);
        } else {
            return (int) updates[p];
        }
    }

    protected void __setINT(int p, int v) {
        updates()[p] = v;
    }

    protected int __getINTERVAL_MILLIS(int p) {
        throw new UnsupportedOperationException("Don't know how to convert an INTERVAL_MILLIS from a ValueSource");
    }

    protected void __setINTERVAL_MILLIS(int p, int v) {
        throw new UnsupportedOperationException("Don't know how to store an INTERVAL_MILLIS");
    }

    protected int __getINTERVAL_MONTH(int p) {
        throw new UnsupportedOperationException("Don't know how to convert an INTERVAL_MONTH from a ValueSource");
    }

    protected void __setINTERVAL_MONTH(int p, int v) {
        throw new UnsupportedOperationException("Don't know how to store an INTERVAL_MONTH");
    }

    protected long __getLONG(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getLong(p);
        } else {
            return (long) updates[p];
        }
    }

    protected void __setLONG(int p, long v) {
        updates()[p] = v;
    }

    protected String __getTEXT(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getString(p);
        } else {
            return (String) updates[p];
        }
    }

    protected void __setTEXT(int p, String v) {
        updates()[p] = v;
    }

    protected Time __getTIME(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getTime(p);
        } else {
            return (Time) updates[p];
        }
    }

    protected void __setTIME(int p, Time v) {
        updates()[p] = v;
    }

    protected Timestamp __getTIMESTAMP(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getTimestamp(p);
        } else {
            return (Timestamp) updates[p];
        }
    }

    protected Object __getObject(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getObject(p);
        } else {
            return updates[p];
        }
    }

    protected void __setTIMESTAMP(int p, Timestamp v) {
        updates()[p] = v;
    }

    protected long __getU_INT(int p) {
        throw new UnsupportedOperationException("Don't know how to convert a U_INT from a ValueSource");
    }

    protected void __setU_INT(int p, long v) {
        throw new UnsupportedOperationException("Don't know how to store a U_INT");
    }

    protected BigInteger __getU_BIGINT(int p) {
        throw new UnsupportedOperationException("Don't know how to convert a U_BIGINT from a ValueSource");
    }

    protected void __setU_BIGINT(int p, BigInteger v) {
        throw new UnsupportedOperationException("Don't know how to store a U_BIGINT");
    }

    protected BigDecimal __getU_DOUBLE(int p) {
        throw new UnsupportedOperationException("Don't know how to convert a U_DOUBLE from a ValueSource");
    }

    protected void __setU_DOUBLE(int p, BigDecimal v) {
        throw new UnsupportedOperationException("Don't know how to store a U_DOUBLE");
    }

    protected double __getU_FLOAT(int p) {
        throw new UnsupportedOperationException("Don't know how to convert a U_FLOAT from a ValueSource");
    }

    protected void __setU_FLOAT(int p, double v) {
        throw new UnsupportedOperationException("Don't know how to store a U_FLOAT");
    }

    protected String __getVARCHAR(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getString(p);
        } else {
            return (String) updates[p];
        }
    }

    protected void __setVARCHAR(int p, String v) {
        updates()[p] = v;
    }

    protected byte[] __getVARBINARY(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getBytes(p);
        } else {
            return (byte[]) updates[p];
        }
    }

    protected void __setVARBINARY(int p, byte[] v) {
        updates()[p] = v;
    }

    protected int __getYEAR(int p) {
        if (updates == null || updates[p] == NOT_SET) {
            return values().getInt(p);
        } else {
            return (int) updates[p];
        }
    }

    protected void __setYEAR(int p, int v) {
        updates()[p] = v;
    }

    protected DirectObject copyInstance(final Class<?> clazz) {
        final AbstractDirectObject newInstance = Direct.newInstance(clazz);
        if (rs != null) {
            newInstance.rs = new JDBCResultSet((JDBCResultSet)rs) {
                @Override
                public boolean next() throws SQLException {
                    throw new SQLException("Copy is frozen");
                }
                
                private JDBCResultSet copyValues(AbstractDirectObject ado) {
                    ado.values = getValues();
                    return this;
                }
            }.copyValues(newInstance);
            
        }
        if (updates != null) {
            newInstance.updates = new Object[updates.length];
            System.arraycopy(updates, 0, newInstance.updates, 0, updates.length);
        }
        return newInstance;

    }

    /**
     * Issue either an INSERT or an UPDATE statement depending on whether this
     * instance is bound to a result set.
     */
    public void save() {
        try {
            /*
             * If rs == null then this instance was created via the
             * DirectIterable#newInstance method and the intention is to INSERT
             * it. If rs is not null, then this instance was selected from an
             * existing table and the intention is to UPDATE it.
             */
            if (rs == null) {
                PreparedStatement stmt = __insertStatement();
                stmt.execute();
                JDBCResultSet returningResultSet = (JDBCResultSet)stmt.getGeneratedKeys();
                try {
                    if (returningResultSet.next()) {
                        rs = new JDBCResultSet((JDBCResultSet)returningResultSet) {
                            @Override
                            public boolean next() throws SQLException {
                                throw new SQLException("Copy is frozen");
                            }
                            
                            private JDBCResultSet copyValues() {
                                values = getValues();
                                return this;
                            }
                        }.copyValues();
                    }
                } catch (SQLException e) {
                    throw new DirectException(e);
                }

                updates = null;
            } else {
                PreparedStatement stmt = __updateStatement();
                stmt.execute();
                updates = null;
                
            }

        } catch (SQLException e) {
            throw new DirectException(e);
        }
    }

    private PreparedStatement __insertStatement() throws SQLException {
        assert updates != null : "No updates to save";
        BitSet bs = new BitSet(columns.length);
        for (int index = 0; index < updates.length; index++) {
            if (updates[index] != NOT_SET) {
                bs.set(index);
            }
        }
        Connection conn = Direct.getContext().getConnection();
        Map<BitSet, PreparedStatement> map = insertStatementCache.get(conn);
        PreparedStatement stmt = null;
        if (map == null) {
            map = new HashMap<>();
            insertStatementCache.put(conn, map);
        } else {
            stmt = map.get(bs);
        }
        if (stmt == null) {
            StringBuilder updateColumns = new StringBuilder();
            StringBuilder updateValues = new StringBuilder();

            for (int index = 0; index < columns.length; index++) {
                if (updates[index] != NOT_SET) {
                    if (updateColumns.length() > 0) {
                        updateColumns.append(',');
                        updateValues.append(',');
                    }
                    updateColumns.append(columns[index].columnName);
                    updateValues.append('?');
                }
            }
            final String sql = String.format(INSERT_STATEMENT, schemaName, tableName, updateColumns, updateValues);
            stmt = conn.prepareStatement(sql);
            map.put(bs, stmt);
        } else {
            // Just in case
            stmt.clearParameters();
        }
        int statementIndex = 1;
        for (int index = 0; index < columns.length; index++) {
            if (updates[index] != NOT_SET) {
                stmt.setObject(statementIndex, updates[index]);
                statementIndex++;
            }
        }
        return stmt;
    }

    private PreparedStatement __updateStatement() throws SQLException {
        assert updates != null : "No updates to save";
        BitSet bs = new BitSet(columns.length);
        for (int index = 0; index < updates.length; index++) {
            if (updates[index] != NOT_SET) {
                bs.set(index);
            }
        }
        Connection conn = Direct.getContext().getConnection();

        Map<BitSet, PreparedStatement> map = updateStatementCache.get(conn);
        synchronized (this) {
            if (map == null) {
                map = new HashMap<>();
                updateStatementCache.put(conn, map);
            }
        }

        PreparedStatement stmt = map.get(bs);
        if (stmt == null) {
            StringBuilder updateColumns = new StringBuilder();
            StringBuilder pkColumns = new StringBuilder();

            for (int index = 0; index < columns.length; index++) {
                if (columns[index].parentJoinFieldIndex >= 0 || columns[index].primaryKeyFieldIndex >= 0) {
                    if (pkColumns.length() > 0) {
                        pkColumns.append(" and ");
                    }
                    pkColumns.append(columns[index].columnName).append("=?");
                }
                if (updates[index] != NOT_SET) {
                    if (updateColumns.length() > 0) {
                        updateColumns.append(',');
                    }
                    updateColumns.append(columns[index].getColumnName()).append("=?");
                }
            }
            final String sql = String.format(UPDATE_STATEMENT, schemaName, tableName, updateColumns, pkColumns);
            stmt = conn.prepareStatement(sql);
            map.put(bs, stmt);
        } else {
            // Just in case
            stmt.clearParameters();
        }
        int statementIndex = 1;
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < columns.length; index++) {
                if (pass == 0) {
                    if (updates[index] != NOT_SET) {
                        stmt.setObject(statementIndex, updates[index]);
                        statementIndex++;
                    }
                }
                if (pass == 1) {
                    if (columns[index].parentJoinFieldIndex >= 0 || columns[index].primaryKeyFieldIndex >= 0) {
                        stmt.setObject(statementIndex, __getObject(index));
                        statementIndex++;
                    }
                }
            }
        }
        return stmt;
    }

}

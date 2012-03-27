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

package com.akiban.server.test.it.store;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.TableName;
import com.akiban.server.api.DMLFunctions;
import com.akiban.server.api.dml.scan.LegacyRowWrapper;
import com.akiban.server.rowdata.RowData;
import com.akiban.server.rowdata.RowDef;
import com.akiban.server.rowdata.RowDefCache;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.akiban.ais.model.UserTable;
import com.akiban.server.AkServerUtil;
import com.akiban.server.store.RowCollector;
import com.akiban.server.test.it.ITSuiteBase;
import com.akiban.util.ByteBufferFactory;

public abstract class AbstractScanBase extends ITSuiteBase {

    protected final static String DDL_FILE_NAME = "scan_rows_test.ddl";

    protected final static String SCHEMA = "scan_rows_test";

    protected final static boolean VERBOSE = false;
    
    protected static SortedMap<TableName, UserTable> tableMap = new TreeMap<TableName, UserTable>();

    protected List<RowData> result = new ArrayList<RowData>();

    @BeforeClass
    public static void baseSetUpSuite() throws Exception {
        loadDDLFromResource(SCHEMA, DDL_FILE_NAME);

        final AkibanInformationSchema ais = serviceManager.getDXL().ddlFunctions().getAIS(session);
        for (UserTable table : ais.getUserTables().values()) {
            if (table.getName().getTableName().startsWith("a")) {
                tableMap.put(RowDefCache.nameOf(table.getName().getSchemaName(), table.getName().getTableName()), table);
            }
        }
        
        populateTables(serviceManager.getDXL().dmlFunctions());
    }

    @AfterClass
    public static void baseTearDownSuite() throws Exception {
        tableMap.clear();
    }

    protected static void populateTables(DMLFunctions dml) throws Exception {
        final RowData rowData = new RowData(new byte[256]);
        // Create the tables in alphabetical order. Because of the
        // way the tables are defined, this also creates all parents before
        // their children.
        // PrintStream output = new PrintStream(new FileOutputStream(new File("/tmp/srt.out")));
        for (TableName name : tableMap.keySet()) {
            final RowDef rowDef = rowDefCache.getRowDef(name);
            final LegacyRowWrapper rowWrapper = new LegacyRowWrapper(rowDef);
            final int level = name.getTableName().length();
            int k = (int) Math.pow(10, level);
            for (int i = 0; i < k; i++) {
                rowData.createRow(rowDef, new Object[] { (i / 10), i, 7, 8, i + "X" });
                rowWrapper.setRowData(rowData);
                // output.println(rowData.toString(rowDefCache));
                dml.writeRow(session, rowWrapper);
            }
        }
        // output.close();
    }

    protected RowDef rowDef(final String name) {
        return rowDefCache.getRowDef(RowDefCache.nameOf(SCHEMA, name));
    }

    protected int scanAllRows(final String test, final RowData start,
            final RowData end, final byte[] columnBitMap, final int indexId)
            throws Exception {
        return scanAllRows(test, start.getRowDefId(), 0, start, end,
                columnBitMap, indexId);
    }

    protected int scanAllRows(final String test, final int rowDefId,
            final int scanFlags, final RowData start, final RowData end,
            final byte[] columnBitMap, final int indexId) throws Exception {
        int scanCount = 0;
        result.clear();
        final RowCollector rc = store.newRowCollector(session, rowDefId, indexId,
                scanFlags, start, end, columnBitMap, null);
        if (VERBOSE) {
            System.out.println("Test " + test);
        }
        rc.open();
        while (rc.hasMore()) {
            final ByteBuffer payload = ByteBufferFactory.allocate(65536);
            while (rc.collectNextRow(payload))
                ;
            payload.flip();
            for (int p = payload.position(); p < payload.limit();) {
                RowData rowData = new RowData(payload.array(),
                        payload.position(), payload.limit());
                rowData.prepareRow(p);
                scanCount++;
                result.add(rowData);
                if (VERBOSE) {
                    System.out.println(String.format("%5d ", scanCount)
                            + rowData.toString(rowDefCache));
                }
                p = rowData.getRowEnd();
            }
        }
        rc.close();
        if (VERBOSE) {
            System.out.println();
        }
        return scanCount;
    }

    protected int findIndexId(final RowDef rowDef, final String name) {
        for (Index index : rowDef.getIndexes()) {
            if (index.getIndexName().getName().startsWith(name)) {
                return index.getIndexId();
            }
        }
        return -1;
    }

    protected void assertOk(final int a, final int b) {
        System.out.println("AssertOk expected and got " + a + "," + b);
    }

    protected int findFieldIndex(final RowDef rowDef, final String name) {
        for (int index = 0; index < rowDef.getFieldCount(); index++) {
            if (rowDef.getFieldDef(index).getName().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    protected int columnOffset(final RowDef userRowDef, final RowDef groupRowDef) {
        for (int i = groupRowDef.getUserTableRowDefs().length; --i >= 0;) {
            if (groupRowDef.getUserTableRowDefs()[i] == userRowDef) {
                return groupRowDef.getUserTableRowDefs()[i].getColumnOffset();
            }
        }
        return -1;
    }

    protected byte[] bitsToRoot(final RowDef rowDef, final RowDef groupRowDef) {
        final byte[] bits = new byte[(groupRowDef.getFieldCount() + 7) / 8];
        for (RowDef rd = rowDef; rd != null;) {
            int level = -1;
            for (int i = 0; i < groupRowDef.getUserTableRowDefs().length; i++) {
                if (groupRowDef.getUserTableRowDefs()[i] == rd) {
                    level = i;
                    break;
                }
            }
            // Set the bits for all columns of the user table selected by level
            RowDef userTableRowDef = groupRowDef.getUserTableRowDefs()[level];
            UserTable userTable = userTableRowDef.userTable();
            for (Column column : userTable.getColumns()) {
                int groupColumnPosition = userTableRowDef.getColumnOffset() + column.getPosition();
                bits[groupColumnPosition / 8] |= 1 << (groupColumnPosition % 8);
            }
            if (rd.getParentRowDefId() == 0) {
                break;
            } else {
                rd = rowDefCache.getRowDef(rd.getParentRowDefId());
            }
        }
        return bits;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final RowData rowData : result) {
            sb.append(rowData.toString(rowDefCache));
            sb.append(AkServerUtil.NEW_LINE);
        }
        return sb.toString();
    }
}

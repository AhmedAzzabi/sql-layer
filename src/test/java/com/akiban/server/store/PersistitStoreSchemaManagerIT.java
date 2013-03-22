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

package com.akiban.server.store;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.TableName;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.transaction.TransactionService;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.CyclicBarrier;

import static com.akiban.server.store.PersistitStoreSchemaManager.SerializationType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PersistitStoreSchemaManagerIT extends PersistitStoreSchemaManagerITBase {
    private final static String SCHEMA = "my_schema";
    private final static String T1_NAME = "t1";
    private final static String T1_DDL = "id int NOT NULL, PRIMARY KEY(id)";
    private static final TableName TABLE_NAME = new TableName(SCHEMA, T1_NAME);
    private static final int ROW_COUNT = 10;

    private int tid;
    private NewRow[] rows = new NewRow[ROW_COUNT];

    private void createAndLoad() {
        tid = createTable(SCHEMA, T1_NAME, T1_DDL);
        for(int i = 0; i < ROW_COUNT; ++i) {
            rows[i] = createNewRow(tid, i+1L);
        }
        writeRows(rows);
    }


    @Test
    public void newDataSetReadAndSavedAsProtobuf() throws Exception {
        createTable(SCHEMA, T1_NAME, T1_DDL);
        assertEquals("Saved as PROTOBUF", SerializationType.PROTOBUF, pssm.getSerializationType());

        safeRestart();

        assertEquals("Saw PROTOBUF on load", SerializationType.PROTOBUF, pssm.getSerializationType());
    }

    @Test
    public void groupAndIndexTreeDelayedRemoval() throws Exception {
        createAndLoad();

        String groupTreeName = getUserTable(tid).getGroup().getTreeName();
        String pkTreeName = getUserTable(tid).getPrimaryKey().getIndex().getTreeName();
        Set<String> treeNames = pssm.getTreeNames();
        assertEquals("Group tree is in set before drop", true, treeNames.contains(groupTreeName));
        assertEquals("PK tree is in set before drop", true, treeNames.contains(pkTreeName));

        ddl().dropTable(session(), TABLE_NAME);

        treeNames = pssm.getTreeNames();
        assertEquals("Group tree is in set after drop", true, treeNames.contains(groupTreeName));
        assertEquals("PK tree is in set after drop", true, treeNames.contains(pkTreeName));

        safeRestart();

        treeNames = pssm.getTreeNames();
        assertEquals("Group tree is in set after restart", false, treeNames.contains(groupTreeName));
        assertEquals("PK tree is in set after restart", false, treeNames.contains(pkTreeName));
        assertEquals("Group tree exist after restart", false, treeService().treeExists(SCHEMA, groupTreeName));
        assertEquals("PK tree exists after restart", false, treeService().treeExists(SCHEMA, pkTreeName));
    }

    @Test
    public void createDropCreateRestart() throws Exception {
        createAndLoad();
        expectFullRows(tid, rows);
        ddl().dropTable(session(), TABLE_NAME);

        // Make sure second table gets new trees that don't get removed on restart
        createAndLoad();
        expectFullRows(tid, rows);
        safeRestart();
        expectFullRows(tid, rows);
    }

    @Test
    public void delayedTreeRemovalRollbackSafe() throws Exception {
        final String EX_MSG = "Intentional";
        createAndLoad();

        // This is a bit of a hack, but only makes minor assumptions.
        // DDL.dropTable() performs 2 transactions, first to get table ID to lock and then second to do DDL.
        // Set up a hook for the end of the first that adds another hook for pre-commit of the second to cause a failure.

        final TransactionService.Callback preCommitCB = new TransactionService.Callback() {
            @Override
            public void run(Session session, long timestamp) {
                throw new RuntimeException(EX_MSG);
            }
        };
        final TransactionService.Callback firstEndCB = new TransactionService.Callback() {
            @Override
            public void run(Session session, long timestamp) {
                txnService().addCallbackOnInactive(session, TransactionService.CallbackType.PRE_COMMIT, preCommitCB);
            }
        };
        txnService().addCallbackOnInactive(session(), TransactionService.CallbackType.END, firstEndCB);

        try {
            ddl().dropTable(session(), TABLE_NAME);
            fail("Expected exception");
        } catch(RuntimeException e) {
            assertEquals("Correct exception (message)", EX_MSG, e.getMessage());
        }

        safeRestart();
        expectFullRows(tid, rows);
    }

    @Test
    public void aisCanBeReloaded() {
        createAndLoad();
        pssm.clearAISMap();
        expectFullRows(tid, rows);
    }

    @Test
    public void aisMapCleanup() {
        final int COUNT = 10;
        // Create a number of versions
        for(int i = 0; i < COUNT; ++i) {
            createTable(SCHEMA, T1_NAME+i, T1_DDL);
        }
        // Should be fully cleared after queue is cleared
        pssm.waitForQueueToEmpty(5000);
        assertEquals("AIS map size", 1, pssm.getAISMapSize());
        pssm.clearUnreferencedAISMap();
        assertEquals("AIS map size after clearing", 1, pssm.getAISMapSize());
    }

    @Test
    public void clearUnreferencedAndOpenTransaction() throws Exception {
        final int expectedTableCount = ais().getUserTables().size();
        createTable(SCHEMA, T1_NAME+1, T1_DDL);
        createTable(SCHEMA, T1_NAME+2, T1_DDL);

        // Construct this sequence:
        // Session 1: CREATE t1,t2                  BEGIN,CREATE t3,getAIS(),cleanup(),COMMIT
        // Session 2:               BEGIN,getAIS()                                             COMMIT
        CyclicBarrier b1 = new CyclicBarrier(2);
        CyclicBarrier b2 = new CyclicBarrier(2);
        Thread thread2 = new Thread(new AISReader(b1, b2, expectedTableCount + 2), "TestThread2");
        thread2.start();
        b1.await();
        createTable(SCHEMA, T1_NAME + 3, T1_DDL);
        txnService().beginTransaction(session());
        try {
            AkibanInformationSchema ais = ddl().getAIS(session());
            assertEquals("Table count after creates", expectedTableCount + 3, ais.getUserTables().size());
            pssm.clearUnreferencedAISMap();
            assertEquals("AIS map size after clearing", 2, pssm.getAISMapSize());
        } finally {
            txnService().commitTransaction(session());
        }
        b2.await();
        thread2.join();
    }


    private class AISReader implements Runnable {
        private final CyclicBarrier b1, b2;
        private final int tableCount;

        public AISReader(CyclicBarrier b1, CyclicBarrier b2, int expectedCount) {
            this.b1 = b1;
            this.b2 = b2;
            this.tableCount = expectedCount;
        }

        @Override
        public void run() {
            Session session = createNewSession();
            try {
                txnService().beginTransaction(session);
                AkibanInformationSchema ais = ddl().getAIS(session);
                b1.await();
                assertEquals("Table count (session 2)", tableCount, ais.getUserTables().size());
                b2.await();
                txnService().commitTransaction(session);
            } catch(Exception e) {
                throw new RuntimeException(e);
            } finally {
                txnService().rollbackTransactionIfOpen(session);
                session.close();
            }
        }
    }
}

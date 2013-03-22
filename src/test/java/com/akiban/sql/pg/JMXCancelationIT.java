
package com.akiban.sql.pg;

import com.akiban.server.error.ErrorCode;

import org.junit.Before;
import org.junit.Test;
import static junit.framework.Assert.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class JMXCancelationIT extends PostgresServerITBase
{
    private static final int SERVER_JMX_PORT = 8082;
    private static final String SERVER_ADDRESS = "localhost";
    private static final int N = 1000;

    @Before
    public void loadDB() throws Exception {
        Statement statement = getConnection().createStatement();
        statement.execute("CREATE TABLE t(id INTEGER NOT NULL PRIMARY KEY)");
        for (int id = 0; id < N; id++) {
            statement.execute(String.format("INSERT INTO t VALUES(%s)", id));
        }
        statement.close();
    }

    @Test
    public void testCancel() throws Exception {
        test("cancelQuery", false);
    }

    @Test
    public void testKillConnection() throws Exception {
        test("killConnection", true);
    }

    private void test(String method, boolean forKill) throws Exception {
        JMXInterpreter jmx = null;
        try {
            jmx = new JMXInterpreter(false);

            Integer[] sessions = (Integer[])
                jmx.makeBeanCall(SERVER_ADDRESS, SERVER_JMX_PORT,
                                 "com.akiban:type=PostgresServer",
                                 "CurrentSessions", null, "get");
            List<Integer> before = Arrays.asList(sessions);

            CountDownLatch latch = new CountDownLatch(1);
            Thread queryThread = startQueryThread(forKill, latch);
            latch.await();

            // Connection is open, so (unique) session should exist.
            sessions = (Integer[])
                jmx.makeBeanCall(SERVER_ADDRESS, SERVER_JMX_PORT,
                                 "com.akiban:type=PostgresServer",
                                 "CurrentSessions", null, "get");
            List<Integer> after = Arrays.asList(sessions);
            after = new ArrayList<>(after);
            after.removeAll(before);

            assertEquals(1, after.size());
            Integer session = after.get(0);

            // Still need to wait for session to have a query in progress.
            while (true) {
                String sql = (String)
                    jmx.makeBeanCall(SERVER_ADDRESS, SERVER_JMX_PORT,
                                     "com.akiban:type=PostgresServer",
                                     "getSqlString", new Object[] { session }, "method");
                if (sql != null) 
                    break;
                Thread.sleep(50);
            }

            jmx.makeBeanCall(SERVER_ADDRESS, SERVER_JMX_PORT,
                             "com.akiban:type=PostgresServer",
                             method, new Object[] { session }, "method");

            queryThread.join();
        }
        finally {
            if (jmx != null) {
                jmx.close();
            }
        }
    }

    private Thread startQueryThread(final boolean forKill, final CountDownLatch latch) throws Exception {
        Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Connection connection = null;
                    Statement statement = null;
                    try {
                        connection = openConnection();
                        latch.countDown();
                        statement = connection.createStatement();
                        statement.execute("SELECT COUNT(*) FROM t t1, t t2, t t3");
                        fail("Query should not complete.");
                    }
                    catch (SQLException ex) {
                        String sqlState = ex.getSQLState();
                        if (forKill) {
                            // Kill case can also see connection close (PSQLState.CONNECTION_FAILURE).
                            if (!"08006".equals(sqlState))
                                assertEquals(ErrorCode.CONNECTION_TERMINATED.getFormattedValue(), sqlState);
                        }
                        else {
                            assertEquals(ErrorCode.QUERY_CANCELED.getFormattedValue(), sqlState);
                        }
                    }
                    catch (Exception ex) {
                        fail(ex.toString());
                    }
                    finally {
                        try {
                            if (statement != null)
                                statement.close();
                            closeConnection(connection);
                        }
                        catch (Exception ex) {
                        }
                    }
                }
            });
        thread.setDaemon(false);
        thread.start();
        return thread;
    }

}

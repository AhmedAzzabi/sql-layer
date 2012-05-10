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

package com.akiban.qp.persistitadapter;

import com.akiban.ais.model.GroupTable;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.PrimaryKey;
import com.akiban.ais.model.UserTable;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.operator.*;
import com.akiban.qp.row.HKey;
import com.akiban.qp.row.Row;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.dml.scan.NiceRow;
import com.akiban.server.error.PersistitAdapterException;
import com.akiban.server.error.QueryCanceledException;
import com.akiban.server.error.QueryTimedOutException;
import com.akiban.server.rowdata.RowData;
import com.akiban.server.rowdata.RowDef;
import com.akiban.server.service.config.ConfigurationService;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.tree.TreeService;
import com.akiban.server.store.PersistitStore;
import com.akiban.server.types.ToObjectValueTarget;
import com.akiban.server.types.ValueSource;
import com.akiban.util.tap.InOutTap;
import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Transaction;
import com.persistit.exception.PersistitException;
import com.persistit.exception.PersistitInterruptedException;

import java.io.InterruptedIOException;

public class PersistitAdapter extends StoreAdapter
{
    // StoreAdapter interface

    @Override
    public GroupCursor newGroupCursor(GroupTable groupTable)
    {
        GroupCursor cursor;
        try {
            cursor = new PersistitGroupCursor(this, groupTable);
        } catch (PersistitException e) {
            handlePersistitException(e);
            throw new AssertionError();
        }
        return cursor;
    }

    @Override
    public Cursor newIndexCursor(QueryContext context, Index index, IndexKeyRange keyRange, API.Ordering ordering, IndexScanSelector selector)
    {
        Cursor cursor;
        try {
            cursor = new PersistitIndexCursor(context, schema.indexRowType(index), keyRange, ordering, selector);
        } catch (PersistitException e) {
            handlePersistitException(e);
            throw new AssertionError();
        }
        return cursor;
    }

    @Override
    public Cursor sort(QueryContext context,
                       Cursor input,
                       RowType rowType,
                       API.Ordering ordering,
                       API.SortOption sortOption,
                       InOutTap loadTap)
    {
        return new SorterToCursorAdapter(this, context, input, rowType, ordering, sortOption, loadTap);
    }

    @Override
    public void checkQueryCancelation(long queryStartMsec)
    {
        if (session.isCurrentQueryCanceled()) {
            throw new QueryCanceledException(session);
        }
        long queryTimeoutSec = config.queryTimeoutSec();
        if (queryTimeoutSec >= 0) {
            long runningTimeMsec = System.currentTimeMillis() - queryStartMsec;
            if (runningTimeMsec > queryTimeoutSec * 1000) {
                throw new QueryTimedOutException(runningTimeMsec);
            }
        }
    }

    @Override
    public HKey newHKey(com.akiban.ais.model.HKey hKeyMetadata)
    {
        return new PersistitHKey(this, hKeyMetadata);
    }

    @Override
    public void updateRow(Row oldRow, Row newRow) {
        RowDef rowDef = oldRow.rowType().userTable().rowDef();
        RowDef rowDefNewRow = newRow.rowType().userTable().rowDef();
        if (rowDef != rowDefNewRow) {
            throw new IllegalArgumentException(String.format("%s != %s", rowDef, rowDefNewRow));
        }

        RowData oldRowData = rowData(rowDef, oldRow);
        RowData newRowData = rowData(rowDef, newRow);
        int oldStep = enterUpdateStep();
        try {
            persistit.updateRow(session, oldRowData, newRowData, null);
        } catch (PersistitException e) {
            handlePersistitException(e);
            assert false;
        }
        finally {
            leaveUpdateStep(oldStep);
        }
    }
    @Override
    public void writeRow (Row newRow) {
        RowDef rowDef = newRow.rowType().userTable().rowDef();
        RowData newRowData = rowData (rowDef, newRow);
        int oldStep = enterUpdateStep();
        try {
            persistit.writeRow(session, newRowData);
        } catch (PersistitException e) {
            handlePersistitException(e);
            assert false;
        }
        finally {
            leaveUpdateStep(oldStep);
        }
    }
    
    @Override
    public void deleteRow (Row oldRow) {
        RowDef rowDef = oldRow.rowType().userTable().rowDef();
        RowData oldRowData = rowData(rowDef, oldRow);
        int oldStep = enterUpdateStep();
        try {
            persistit.deleteRow(session, oldRowData);
        } catch (PersistitException e) {
            handlePersistitException(e);
            assert false;
        }
        finally {
            leaveUpdateStep(oldStep);
        }
    }

    @Override
    public long rowCount(RowType tableType) {
        RowDef rowDef = tableType.userTable().rowDef();
        try {
            return rowDef.getTableStatus().getRowCount();
        } catch(PersistitInterruptedException e) {
            throw new QueryCanceledException(session);
        }
    }

    // PersistitAdapter interface

    public Session session()
    {
        return session;
    }

    public PersistitStore persistit()
    {
        return persistit;
    }

    public RowDef rowDef(int tableId)
    {
        return persistit.getRowDefCache().getRowDef(tableId);
    }

    public NewRow newRow(RowDef rowDef)
    {
        NiceRow row = new NiceRow(rowDef.getRowDefId(), rowDef);
        UserTable table = rowDef.userTable();
        PrimaryKey primaryKey = table.getPrimaryKeyIncludingInternal();
        if (primaryKey != null && table.getPrimaryKey() == null) {
            // Akiban-generated PK. Initialize its value to a dummy value, which will be replaced later. The
            // important thing is that the value be non-null.
            row.put(table.getColumnsIncludingInternal().size() - 1, -1L);
        }
        return row;
    }

    public RowData rowData(RowDef rowDef, RowBase row)
    {
        if (row instanceof PersistitGroupRow) {
            return ((PersistitGroupRow) row).rowData();
        }
        ToObjectValueTarget target = new ToObjectValueTarget();
        NewRow niceRow = newRow(rowDef);
        for(int i = 0; i < row.rowType().nFields(); ++i) {
            ValueSource source = row.eval(i);
            niceRow.put(i, target.convertFromSource(source));
        }
        return niceRow.toRowData();
    }

    public PersistitGroupRow newGroupRow()
    {
        return PersistitGroupRow.newPersistitGroupRow(this);
    }

    public PersistitIndexRow newIndexRow(IndexRowType indexRowType) throws PersistitException
    {
        return
            indexRowType.index().isTableIndex()
            ? PersistitIndexRow.tableIndexRow(this, indexRowType)
            : PersistitIndexRow.groupIndexRow(this, indexRowType);
    }


    public Exchange takeExchange(GroupTable table) throws PersistitException
    {
        return persistit.getExchange(session, table.rowDef());
    }

    public Exchange takeExchange(Index index)
    {
        return persistit.getExchange(session, index);
    }

    public Key newKey()
    {
        return new Key(persistit.getDb());
    }

    public void handlePersistitException(PersistitException e)
    {
        handlePersistitException(session, e);
    }

    public static void handlePersistitException(Session session, PersistitException e)
    {
        assert e != null;
        Throwable cause = e.getCause();
        if (e instanceof PersistitInterruptedException ||
            cause != null && (cause instanceof InterruptedIOException || cause instanceof InterruptedException)) {
            throw new QueryCanceledException(session);
        } else {
            throw new PersistitAdapterException(e);
        }
    }

    public void returnExchange(Exchange exchange)
    {
        persistit.releaseExchange(session, exchange);
    }
    
    public Transaction transaction() {
        return treeService.getTransaction(session);
    }

    public int enterUpdateStep()
    {
        Transaction transaction = transaction();
        int step = transaction.getStep();
        if (step > 0)
            transaction.incrementStep();
        return step;
    }

    public void leaveUpdateStep(int step) {
        transaction().setStep(step);
    }

    public PersistitAdapter(Schema schema,
                            PersistitStore persistit,
                            TreeService treeService,
                            Session session,
                            ConfigurationService config)
    {
        super(schema);
        this.config = config;
        this.persistit = persistit;
        this.session = session;
        this.treeService = treeService;
    }
    
    // Object state

    private final TreeService treeService;
    private final ConfigurationService config;
    private final PersistitStore persistit;
    private final Session session;
}

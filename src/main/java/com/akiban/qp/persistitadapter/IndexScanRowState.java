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

import com.akiban.qp.persistitadapter.indexcursor.IterationHelper;
import com.akiban.qp.persistitadapter.indexrow.PersistitIndexRow;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.util.ShareHolder;
import com.persistit.Exchange;
import com.persistit.exception.PersistitException;

public class IndexScanRowState implements IterationHelper
{
    @Override
    public Row row() throws PersistitException
    {
        unsharedRow().get().copyFromExchange(exchange);
        return row.get();
    }

    @Override
    public void openIteration()
    {
        if (exchange == null) {
            exchange = adapter.takeExchange(indexRowType.index());
        }
    }

    @Override
    public void closeIteration()
    {
        if (row.isHolding()) {
            if (!row.isShared())
                adapter.returnIndexRow(row.get());
            row.release();
        }
        if (exchange != null) {
            adapter.returnExchange(exchange);
            exchange = null;
        }
    }

    @Override
    public Exchange exchange()
    {
        return exchange;
    }

    // IndexScanRowState interface

    public IndexScanRowState(PersistitAdapter adapter, IndexRowType indexRowType)
    {
        this.adapter = adapter;
        this.indexRowType = indexRowType.physicalRowType(); // In case we have a spatial index
        this.row = new ShareHolder<>(adapter.takeIndexRow(this.indexRowType));
    }

    // For use by this class

    private ShareHolder<PersistitIndexRow> unsharedRow() throws PersistitException
    {
        if (row.isEmpty() || row.isShared()) {
            row.hold(adapter.takeIndexRow(indexRowType));
        }
        return row;
    }

    // Object state

    private final PersistitAdapter adapter;
    private final IndexRowType indexRowType;
    private final ShareHolder<PersistitIndexRow> row;
    private Exchange exchange;
}

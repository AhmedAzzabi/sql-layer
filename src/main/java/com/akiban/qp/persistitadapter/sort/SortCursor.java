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

package com.akiban.qp.persistitadapter.sort;

import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.qp.row.Row;
import com.akiban.util.tap.PointTap;
import com.akiban.util.tap.Tap;
import com.persistit.Exchange;
import com.persistit.exception.PersistitException;

public abstract class SortCursor implements Cursor
{
    // TODO: TEMPORARY

    @Override
    public void destroy()
    {
        assert false;
    }

    @Override
    public boolean isIdle()
    {
        assert false;
        return false;
    }

    @Override
    public boolean isActive()
    {
        assert false;
        return false;
    }

    @Override
    public boolean isDestroyed()
    {
        assert false;
        return false;
    }

    // TODO: END TEMPORARY

    // Cursor interface

    @Override
    public final void close()
    {
        iterationHelper.close();
    }

    // SortCursor interface

    public static SortCursor create(QueryContext context,
                                    IndexKeyRange keyRange,
                                    API.Ordering ordering,
                                    IterationHelper iterationHelper)
    {
        return
            ordering.allAscending() || ordering.allDescending()
            ? (keyRange != null && keyRange.lexicographic()
               ? SortCursorUnidirectionalLexicographic.create(context, iterationHelper, keyRange, ordering)
               : SortCursorUnidirectional.create(context, iterationHelper, keyRange, ordering))
            : SortCursorMixedOrder.create(context, iterationHelper, keyRange, ordering);
    }

    // For use by subclasses

    protected SortCursor(QueryContext context, IterationHelper iterationHelper)
    {
        this.context = context;
        this.adapter = (PersistitAdapter)context.getStore();
        this.iterationHelper = iterationHelper;
        this.exchange = iterationHelper.exchange();
    }

    protected Row row() throws PersistitException
    {
        return iterationHelper.row();
    }

    // Object state

    protected final QueryContext context;
    protected final PersistitAdapter adapter;
    protected final Exchange exchange;
    protected final IterationHelper iterationHelper;
    
    static final PointTap SORT_TRAVERSE = Tap.createCount("traverse_sort");
}

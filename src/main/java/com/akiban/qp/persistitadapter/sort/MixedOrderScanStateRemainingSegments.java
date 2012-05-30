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

import com.persistit.Key;
import com.persistit.exception.PersistitException;

import static com.akiban.qp.persistitadapter.sort.SortCursor.SORT_TRAVERSE;

class MixedOrderScanStateRemainingSegments extends MixedOrderScanState
{
    @Override
    public boolean startScan() throws PersistitException
    {
        if (subtreeRootKey == null) {
            subtreeRootKey = new Key(cursor.exchange.getKey());
        } else {
            cursor.exchange.getKey().copyTo(subtreeRootKey);
        }
        SORT_TRAVERSE.hit();
        return cursor.exchange.traverse(Key.GT, true);
    }

    @Override
    public boolean advance() throws PersistitException
    {
        SORT_TRAVERSE.hit();
        boolean more = ascending ? cursor.exchange.next(true) : cursor.exchange.previous(true);
        if (more) {
            more = cursor.exchange.getKey().firstUniqueByteIndex(subtreeRootKey) >= subtreeRootKey.getEncodedSize();
        }
        if (!more) {
            // Restore exchange key to where it was before exploring this subtree. But also attach one
            // more key segment since SortCursorMixedOrder is going to cut one.
            subtreeRootKey.copyTo(cursor.exchange.getKey());
            cursor.exchange.getKey().append(Key.BEFORE);
        }
        return more;
    }

    public MixedOrderScanStateRemainingSegments(SortCursorMixedOrder sortCursor, int field) throws PersistitException
    {
        super(sortCursor, field, true);
    }

    private Key subtreeRootKey;
}

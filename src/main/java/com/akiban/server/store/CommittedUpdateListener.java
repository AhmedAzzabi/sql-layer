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

package com.akiban.server.store;

import com.akiban.server.rowdata.RowData;
import com.akiban.server.rowdata.RowDef;
import com.persistit.KeyState;

/**
 * Listener interface for database update events. These methods are called from
 * within the Transaction commit logic of Persistit. In normal operation they
 * must not throw exceptions and should complete quickly without blocking.
 * 
 * @author peter
 * 
 */
public interface CommittedUpdateListener {

    /**
     * Invoked when a newly inserted row has been committed.
     * 
     * @param keyState
     * @param rowDef
     * @param rowData
     */
    void inserted(final KeyState keyState, final RowDef rowDef,
            final RowData rowData);

    /**
     * Invoked when a row update has been committed to a table.
     * 
     * @param keyState
     * @param rowDef
     * @param oldRowData
     * @param newRowData
     */
    void updated(final KeyState keyState, final RowDef rowDef,
            final RowData oldRowData, final RowData newRowData);

    /**
     * Invoked when deletion of a row has been committed.
     * 
     * @param keyState
     * @param rowDef
     * @param rowData
     */
    void deleted(final KeyState keyState, final RowDef rowDef,
            final RowData rowData);

}

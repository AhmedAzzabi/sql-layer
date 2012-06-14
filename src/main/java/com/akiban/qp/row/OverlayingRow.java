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

package com.akiban.qp.row;

import com.akiban.qp.rowtype.RowType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.FromObjectValueSource;
import com.akiban.server.types.util.ValueHolder;

public final class OverlayingRow extends AbstractRow {
    private final Row underlying;
    private final ValueHolder[] overlays;

    public OverlayingRow(Row underlying) {
        this.underlying = underlying;
        this.overlays = new ValueHolder[underlying.rowType().nFields()];
    }

    public OverlayingRow overlay(int index, ValueSource object) {
        if (object == null) {
            overlays[index] = null;
        }
        else {
            if (overlays[index] == null)
                overlays[index] = new ValueHolder();
            overlays[index].copyFrom(object);
        }
        return this;
    }

    public OverlayingRow overlay(int index, Object object) {
        return overlay(index, valueSource.setExplicitly(object, underlying.rowType().typeAt(index)));
    }

    @Override
    public RowType rowType() {
        return underlying.rowType();
    }

    @Override
    public ValueSource eval(int i) {
        return overlays[i] == null ? underlying.eval(i) : overlays[i];
    }

    @Override
    public HKey hKey() {
        return underlying.hKey();
    }

    private final FromObjectValueSource valueSource = new FromObjectValueSource();
}

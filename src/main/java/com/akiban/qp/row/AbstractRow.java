/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.qp.row;

import com.akiban.qp.rowtype.RowType;
import com.akiban.server.Quote;
import com.akiban.util.AkibanAppender;

public abstract class AbstractRow implements Row
{
    // Row interface

    @Override
    public abstract RowType rowType();

    @Override
    public abstract HKey hKey();

    @Override
    public final boolean ancestorOf(RowBase that)
    {
        return this.hKey().prefixOf(that.hKey());
    }

    @Override
    public final int runId()
    {
        return runId;
    }

    @Override
    public void runId(int runId)
    {
        this.runId = runId;
    }

    @Override
    public Row subRow(RowType subRowType)
    {
        return rowType() == subRowType ? this : null;
    }

    // ManagedRow interface

    @Override
    public final void share()
    {
        assert references >= 0 : this;
        references++;
    }

    @Override
    public final boolean isShared()
    {
        assert references >= 0 : this;
        return references > 0;
    }

    @Override
    public final void release()
    {
        assert references >= 0 : this;
        if (references == 0) {
            throw new IllegalStateException("can't release unshared row");
        }
        references--;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.getClass().getSimpleName()).append('[');
        final int fieldsCount = rowType().nFields();
        AkibanAppender appender = AkibanAppender.of(builder);
        for (int i=0; i < fieldsCount; ++i) {
            eval(i).appendAsString(appender, Quote.SINGLE_QUOTE);
            if(i+1 < fieldsCount) {
                builder.append(',').append(' ');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    // Object state

    private int references = 0;
    // runId is set for rows coming out of an IndexScan and then propagated through rows created by other operators.
    // For a row from a GroupScan, rowId is left at -1, indicating that run boundaries have to be determined by
    // hkey comparisons.
    private int runId = -1;
}

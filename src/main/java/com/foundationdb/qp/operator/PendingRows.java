/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
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

package com.foundationdb.qp.operator;

import com.foundationdb.qp.row.Row;
import com.foundationdb.util.ShareHolder;

import java.util.ArrayList;
import java.util.List;

class PendingRows
{
    public void add(Row row)
    {
        assert !full();
        row(end, row);
        end = next(end);
    }

    public Row take()
    {
        Row row = null;
        if (!empty()) {
            row = row(start);
            row(start, null);
            start = next(start);
        }
        return row;
    }

    public void clear()
    {
        for (int i = 0; i <= capacity; i++) {
            row(i, null);
        }
        start = 0;
        end = 0;
    }

    public boolean isEmpty()
    {
        return start == end;
    }

    public PendingRows(int capacity)
    {
        this.capacity = capacity;
        queue = new ArrayList<>(capacity+1);
        for (int i = 0; i <= capacity; i++) {
            queue.add(new ShareHolder<Row>());
        }
        this.start = 0;
        this.end = 0;
    }

    // For use by this class

    private Row row(int i)
    {
        return queue.get(i).get();
    }

    private void row(int i, Row row)
    {
        queue.get(i).hold(row);
    }

    private boolean empty()
    {
        return end == start;
    }

    private boolean full()
    {
        return next(end) == start;
    }

    private int next(int x)
    {
        if (x++ == capacity) {
            x = 0;
        }
        return x;
    }

    // Object state

    private final int capacity;
    private final List<ShareHolder<Row>> queue;
    private int start;
    private int end;
}

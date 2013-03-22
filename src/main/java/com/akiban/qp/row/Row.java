
package com.akiban.qp.row;

// Row and RowHolder implement a reference-counting scheme for rows. Operators should hold onto
// Rows using a RowHolder. Assignments to RowHolder (using RowHolder.set) cause reference counting
// to be done, via Row.share() and release(). isShared() is used by data sources (a btree cursor)
// when the row filled in by the cursor is about to be changed. If isShared() is true, then the cursor
// allocates a new row and writes into it, otherwise the existing row is reused. (isShared() is true iff the reference
// count is > 1. If the reference count is = 1, then presumably the reference is from the btree cursor itself.)
//
// This implementation is NOT threadsafe. It assumes that all access to a Row is within one thread.
// E.g., when a Row is returned from an operator's next(), it is often not in a RowHolder owned by that operator,
// and the reference count could be zero. As long as we're in one thread, the cursor can't be writing new data into
// the row while this is happening.

import com.akiban.util.Shareable;

public interface Row extends RowBase, Shareable
{
}

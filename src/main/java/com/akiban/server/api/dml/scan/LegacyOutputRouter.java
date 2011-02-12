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

package com.akiban.server.api.dml.scan;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for routing a legacy RowData to zero or more handlers. You can use this to route the data to just a single
 * handler (i.e., perform a conversion) or to multiple handlers (in which case it acts as a splitter).
 *
 * <p>This class requires a ByteBuffer to accept each legacy row as it comes in. You can provide one, or have this
 * class allocate one of a given size. If you provide a ByteBuffer, its position at the time that you instantiate this
 * class is considered its base position. After each row is written and routed to all handlers, this class can
 * optionally reset the backing ByteBuffer's position to the initial position. If you use that functionality, a single
 * router can be used to process as many messages as you like, as long as each one fits within the buffer. Otherwise,
 * the buffer will keep filling. You can use this to efficiently split a LegacyRowData, using one of the outputs
 * as the backing ByteBuffer.</p>
 */
public class LegacyOutputRouter extends WrappingRowOutput {
    public interface Handler {
        void handleRow(byte[] bytes, int offset, int length) throws RowOutputException;
    }

    private int lastPosition;
    private final boolean resetPosition;
    private final List<Handler> handlers = new ArrayList<Handler>();

    public LegacyOutputRouter(int capacity, boolean resetPosition) {
        this( ByteBuffer.allocate(capacity), resetPosition);
    }

    public LegacyOutputRouter(ByteBuffer buffer, boolean resetPosition) {
        super(buffer);
        if (!buffer.hasArray()) {
            throw new RuntimeException("Buffer needs an array");
        }
        this.resetPosition = resetPosition;
        lastPosition = buffer.position();
    }

    /**
     * Adds a handler to this router. This method returns the handler back to you, as a convenience for when
     * you want to add a new handler and also keep a reference to it.
     * @param handler the handler to add to this router
     * @param <T> the handler's type
     * @return the handler you passed in
     */
    public <T extends Handler> T addHandler(T handler) {
        handlers.add(handler);
        return handler;
    }

    @Override
    protected void postWroteRow() throws RowOutputException {
        super.postWroteRow();
        final byte[] bytes = wrapped.array();
        final int incomingPosition = wrapped.position();
        final int length = incomingPosition - lastPosition;
        for (Handler handler : handlers) {
            handler.handleRow(bytes, lastPosition, length);
        }
        if (resetPosition) {
            wrapped.position(lastPosition);
        }
        else {
            lastPosition = incomingPosition;
        }
    }
}

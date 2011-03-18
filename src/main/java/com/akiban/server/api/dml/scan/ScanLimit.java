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

import com.akiban.server.RowData;

public interface ScanLimit {

    /**
     * A singleton that represents no scan limit.
     */
    public static final ScanLimit NONE = new NoScanLimit();

    /**
     * Whether the limit has been reached; a {@code false} value indicates that the scan should continue. This method
     * is invoked directly after the row is collected, and before it's outputted; if this method returns {@code false},
     * the method will not be outputted
     *
     * @param row the row that has just been collected
     * @return whether scanning should stop
     */
    boolean limitReached(RowData row);
}

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

package com.akiban.util;

import java.util.List;
import java.util.ListIterator;

public final class ListUtils {

    /**
     * Ensures that there are no more than {@code size} elements in the given {@code list}. If the list already has
     * {@code size} elements or fewer, this doesn't do anything; otherwise, it'll remove enough elements from the
     * list to ensure that {@code list.size() == size}. Either way, by the end of this invocation,
     * {@code list.size() <= size}.
     * @param list the incoming list.
     * @param size the maximum number of elements to keep in {@code list}
     * @throws IllegalArgumentException if {@code size < 0}
     * @throws NullPointerException if {@code list} is {@code null}
     * @throws UnsupportedOperationException if the list doesn't support removal
     */
    public static void truncate(List<?> list, int size) {
        ArgumentValidation.isGTE("truncate size", size, 0);

        int rowsToRemove = list.size() - size;
        if (rowsToRemove <= 0) {
            return;
        }
        ListIterator<?> iterator = list.listIterator(list.size());
        while (rowsToRemove-- > 0) {
            iterator.previous();
            iterator.remove();
        }
    }
}

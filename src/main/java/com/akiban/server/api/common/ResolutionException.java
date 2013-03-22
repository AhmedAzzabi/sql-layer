/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
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

package com.akiban.server.api.common;


import com.akiban.ais.model.TableName;

public final class ResolutionException extends RuntimeException {
    public ResolutionException(Integer tableId, TableName tableName) {
        super(message(tableId, tableName, null));
    }

    public ResolutionException(Integer tableId, TableName tableName, String extraMessage) {
        super(message(tableId, tableName, extraMessage));
    }

    private static String message(Integer tableId, TableName tableName, String extraMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("id: ").append(tableId).append(", name: ").append(tableName);
        if (extraMessage != null) {
            sb.append(", message: ").append(extraMessage);
        }
        return sb.toString();
    }
}

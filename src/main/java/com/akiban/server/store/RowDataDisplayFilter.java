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

/**
 * 
 */
package com.akiban.server.store;

import com.akiban.server.AkServerUtil;
import com.akiban.server.RowData;
import com.akiban.server.RowDef;
import com.akiban.server.service.tree.TreeService;
import com.persistit.Exchange;
import com.persistit.Management.DisplayFilter;
import com.persistit.Value;

class RowDataDisplayFilter implements DisplayFilter {

    private final static String[] PROTECTED_VOLUME_NAMES = { "akiban_system",
            "akiban_txn" };

    private final static String[] PROTECTED_TREE_NAMES = { "_status_",
            "_schema_", "_txn_" };
    private final PersistitStore persistitStore;
    private final TreeService treeService;
    private DisplayFilter defaultFilter;

    public RowDataDisplayFilter(PersistitStore store, TreeService treeService,
            final DisplayFilter filter) {
        this.persistitStore = store;
        this.treeService = treeService;
        this.defaultFilter = filter;
    }

    public String toKeyDisplayString(final Exchange exchange) {
        return defaultFilter.toKeyDisplayString(exchange);
    }

    public String toValueDisplayString(final Exchange exchange) {
        final String treeName = exchange.getTree().getName();
        final String volumeName = exchange.getVolume().getName();
        boolean protectedTree = treeName.contains("$$");
        if (!protectedTree) {
            for (final String s : PROTECTED_VOLUME_NAMES) {
                if (volumeName.equals(s)) {
                    protectedTree = true;
                    break;
                }
            }
        }
        if (!protectedTree) {
            for (final String s : PROTECTED_TREE_NAMES) {
                if (treeName.equals(s)) {
                    protectedTree = true;
                    break;
                }
            }
        }
        try {
            if (!protectedTree) {
                final Value value = exchange.getValue();
                int rowDefId = AkServerUtil.getInt(value.getEncodedBytes(),
                                                   RowData.O_ROW_DEF_ID - RowData.LEFT_ENVELOPE_SIZE);
                rowDefId = treeService.storeToAis(exchange.getVolume(), rowDefId);
                final RowDef rowDef = persistitStore.getRowDefCache().getRowDef(rowDefId);
                final int size = value.getEncodedSize() + RowData.ENVELOPE_SIZE;
                final byte[] bytes = new byte[size];
                final RowData rowData = new RowData(bytes);
                persistitStore.expandRowData(exchange, rowData);
                return rowData.toString(rowDef);
            }
        } catch (Exception e) {
            // fall through
        }
        return defaultFilter.toValueDisplayString(exchange);

    }
}
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

package com.akiban.server;

import static com.akiban.server.service.tree.TreeService.STATUS_TREE_NAME;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.akiban.server.service.ServiceManagerImpl;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.tree.TreeLink;
import com.akiban.server.service.tree.TreeService;
import com.akiban.server.service.tree.TreeVisitor;
import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Persistit;
import com.persistit.TransactionalCache;
import com.persistit.exception.PersistitException;

public class TableStatusCache extends TransactionalCache {

    private final static int INITIAL_MAP_SIZE = 1000;

    private final TreeService treeService;

    public TableStatusCache(final Persistit db, final TreeService treeService) {
        super(db);
        this.treeService = treeService;
    }
    
    private TableStatusCache(final TableStatusCache tsc) {
        super(tsc);
        treeService = tsc.treeService;
        for (final Entry<Integer, TableStatus> entry : tsc.tableStatusMap
                .entrySet()) {
            tableStatusMap.put(entry.getKey(),
                    new TableStatus(entry.getValue()));
        }

    }

    private static final long serialVersionUID = 2823468378367226075L;

    private final static byte INCREMENT = 1;
    private final static byte DECREMENT = 2;
    private final static byte TRUNCATE = 3;
    private final static byte AUTO_INCREMENT = 4;
    private final static byte UNIQUEID = 5;
    private final static byte DROP = 6;
    private final static byte ASSIGN_ORDINAL = 100;

    private final Map<Integer, TableStatus> tableStatusMap = new HashMap<Integer, TableStatus>(
            INITIAL_MAP_SIZE);

    static class IncrementRowCount extends UpdateInt {

        IncrementRowCount() {
            super(INCREMENT);
        }

        IncrementRowCount(final int tableId) {
            this();
            _arg = tableId;
        }

        @Override
        public void apply(final TransactionalCache tc) {
            TableStatusCache tsc = (TableStatusCache) tc;
            final TableStatus ts = tsc.getTableStatus(_arg);
            ts.incrementRowCount(1);
        }

        @Override
        public boolean cancel(final Update update) {
            if (update instanceof DecrementRowCount) {
                DecrementRowCount drc = (DecrementRowCount) update;
                if (drc.getTableId() == _arg) {
                    return true;
                }
            }
            return false;
        }

        int getTableId() {
            return _arg;
        }

        @Override
        public String toString() {
            return String.format("<IncrementRowCount:%d>", _arg);
        }
    }

    static class DecrementRowCount extends UpdateInt {

        DecrementRowCount() {
            super(DECREMENT);
        }

        DecrementRowCount(final int tableId) {
            this();
            _arg = tableId;
        }

        @Override
        public void apply(final TransactionalCache tc) {
            TableStatusCache tsc = (TableStatusCache) tc;
            final TableStatus ts = tsc.getTableStatus(_arg);
            ts.incrementRowCount(-1);
        }

        @Override
        public boolean cancel(final Update update) {
            if (update instanceof IncrementRowCount) {
                IncrementRowCount irc = (IncrementRowCount) update;
                if (irc.getTableId() == _arg) {
                    return true;
                }
            }
            return false;
        }

        int getTableId() {
            return _arg;
        }

        @Override
        public String toString() {
            return String.format("<DecrementRowCount:%d>", _arg);
        }
    }

    static class Truncate extends UpdateInt {

        Truncate() {
            super(TRUNCATE);
        }

        Truncate(final int tableId) {
            this();
            _arg = tableId;
        }

        @Override
        public void apply(final TransactionalCache tc) {
            TableStatusCache tsc = (TableStatusCache) tc;
            final TableStatus ts = tsc.getTableStatus(_arg);
            ts.truncate();
        }

        @Override
        public String toString() {
            return String.format("<Truncate:%d>", _arg);
        }
    }
    
    static class Drop extends UpdateInt {
        
        Drop() {
            super(DROP);
        }
        
        Drop(final int tableId) {
            this();
            _arg = tableId;
        }
        
        @Override
        public void apply(final TransactionalCache tc) {
            TableStatusCache tsc = (TableStatusCache) tc;
            tsc.tableStatusMap.remove(_arg);
        }

        @Override
        public String toString() {
            return String.format("<" +
            		"<Drop:%d>", _arg);
        }
    }

    static class AutoIncrementUpdate extends UpdateIntLong {

        AutoIncrementUpdate() {
            super(AUTO_INCREMENT);
        }

        AutoIncrementUpdate(final int tableId, final long value) {
            this();
            this._arg1 = tableId;
            this._arg2 = value;
        }

        @Override
        protected void apply(TransactionalCache tc) {
            final TableStatusCache tsc = (TableStatusCache) tc;
            final TableStatus ts = tsc.getTableStatus(_arg1);
            ts.updateAutoIncrementValue(_arg2);
        }

        @Override
        protected boolean combine(final Update update) {
            if (update instanceof AutoIncrementUpdate) {
                AutoIncrementUpdate au = (AutoIncrementUpdate) update;
                if (au._arg1 == _arg1) {
                    if (_arg2 > au._arg2) {
                        au._arg2 = _arg2;
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String
                    .format("<UpdateAutoIncrement:%d(%d)>", _arg1, _arg2);
        }
    }

    static class UniqueIdUpdate extends UpdateIntLong {

        UniqueIdUpdate() {
            super(UNIQUEID);
        }

        UniqueIdUpdate(final int tableId, final long value) {
            this();
            this._arg1 = tableId;
            this._arg2 = value;
        }

        @Override
        protected void apply(TransactionalCache tc) {
            final TableStatusCache tsc = (TableStatusCache) tc;
            final TableStatus ts = tsc.getTableStatus(_arg1);
            ts.updateUniqueIdValue(_arg2);
        }

        @Override
        protected boolean combine(final Update update) {
            if (update instanceof UniqueIdUpdate) {
                UniqueIdUpdate uiu = (UniqueIdUpdate) update;
                if (uiu._arg1 == _arg1) {
                    if (_arg2 > uiu._arg2) {
                        uiu._arg2 = _arg2;
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("<UniqueIdUpdate:%d(%d)>", _arg1, _arg2);
        }
    }

    static class AssignOrdinalUpdate extends UpdateIntLong {

        AssignOrdinalUpdate() {
            super(ASSIGN_ORDINAL);
        }

        AssignOrdinalUpdate(final int tableId, final long value) {
            this();
            this._arg1 = tableId;
            this._arg2 = value;
        }

        @Override
        protected void apply(TransactionalCache tc) {
            final TableStatusCache tsc = (TableStatusCache) tc;
            final TableStatus ts = tsc.getTableStatus(_arg1);
            ts.setOrdinal((int) _arg2);
        }

        @Override
        public String toString() {
            return String.format("<UniqueIdUpdate:%d(%d)>", _arg1, _arg2);
        }
    }

    @Override
    protected Update createUpdate(byte opCode) {
        switch (opCode) {
        case INCREMENT:
            return new IncrementRowCount();
        case DECREMENT:
            return new DecrementRowCount();
        case TRUNCATE:
            return new Truncate();
        case AUTO_INCREMENT:
            return new AutoIncrementUpdate();
        case UNIQUEID:
            return new UniqueIdUpdate();
        case DROP:
            return new Drop();
        case ASSIGN_ORDINAL:
            return new AssignOrdinalUpdate();

        default:
            throw new IllegalArgumentException("Invalid opCode: " + opCode);
        }
    }

    @Override
    protected long cacheId() {
        return serialVersionUID;
    }

    @Override
    public TableStatusCache copy() {
        return new TableStatusCache(this);
    }

    @Override
    public void save() throws PersistitException {
        final Session session = ServiceManagerImpl.newSession();
        try {
            removeAll(session);
            for (final TableStatus ts : tableStatusMap.values()) {
                final RowDef rowDef = ts.getRowDef();
                if (rowDef != null) {
                    final TreeLink link = treeService.treeLink(
                            rowDef.getSchemaName(), STATUS_TREE_NAME);
                    final Exchange exchange = treeService
                            .getExchange(session, link);
                    try {
                        final int tableId = treeService.aisToStore(link,
                                rowDef.getRowDefId());
                        exchange.clear().append(tableId);
                        ts.put(exchange.getValue());
                        exchange.store();
                    } finally {
                        treeService.releaseExchange(session, exchange);
                    }
                }
            }
        } finally {
             session.close();
        }
    }

    @Override
    public void load() throws PersistitException {
        final Session session = ServiceManagerImpl.newSession();
        try {
            treeService.visitStorage(session, new TreeVisitor() {

                @Override
                public void visit(Exchange exchange) throws Exception {
                    loadOneVolume(exchange);
                }

            }, STATUS_TREE_NAME);
        } catch (PersistitException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            session.close();
        }
    }

    public void loadOneVolume(final Exchange exchange) throws PersistitException {
        exchange.append(Key.BEFORE);
        while (exchange.next()) {
            final int storedTableId = exchange.getKey().reset().decodeInt();
            int tableId = treeService.storeToAis(exchange.getVolume(),
                    storedTableId);
            if (exchange.getValue().isDefined()) {
                TableStatus ts = tableStatusMap.get(tableId);
                if (ts != null && ts.getCreationTime() != 0) {
                    throw new IllegalStateException("TableID " + tableId
                            + " already loaded");
                }
                ts = new TableStatus(tableId);
                ts.get(exchange.getValue());
                tableStatusMap.put(tableId, ts);
            }
        }
    }

    private void removeAll(final Session session) throws PersistitException {
        try {
            treeService.visitStorage(session, new TreeVisitor() {

                @Override
                public void visit(Exchange exchange) throws Exception {
                    exchange.removeAll();
                }

            }, STATUS_TREE_NAME);
        } catch (PersistitException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ====
    // Public API methods of this TableStatusCache.

    public void incrementRowCount(final int tableId) {
        update(new IncrementRowCount(tableId));
    }

    public void decrementRowCount(final int tableId) {
        update(new DecrementRowCount(tableId));
    }

    public void truncate(final int tableId) {
        update(new Truncate(tableId));
    }
    
    public void drop(final int tableId) {
        update(new Drop(tableId));
    }

    public void updateAutoIncrementValue(final int tableId, final long value) {
        update(new AutoIncrementUpdate(tableId, value));
    }

    public void updateUniqueIdValue(final int tableId, final long value) {
        update(new UniqueIdUpdate(tableId, value));
    }

    public void setOrdinal(final int tableId, final int ordinal) {
        update(new AssignOrdinalUpdate(tableId, ordinal));
    }

    public synchronized TableStatus getTableStatus(final int tableId) {
        TableStatus ts = tableStatusMap.get(Integer.valueOf(tableId));
        if (ts == null) {
            ts = new TableStatus(tableId);
            tableStatusMap.put(Integer.valueOf(tableId), ts);
        }
        return ts;
    }
    
    public synchronized void detachAIS() {
        TableStatusCache tsc = this;
        while (tsc != null) {
            for (final TableStatus ts : tsc.tableStatusMap.values()) {
                ts.setRowDef(null);
            }
            tsc = (TableStatusCache)tsc._previousVersion;
        }
    }

    @Override
    public String toString() {
        return String.format("TableStatusCache %s", tableStatusMap);
    }

}

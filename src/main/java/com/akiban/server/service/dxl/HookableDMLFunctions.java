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

package com.akiban.server.service.dxl;

import com.akiban.server.RowData;
import com.akiban.server.TableStatistics;
import com.akiban.server.api.DMLFunctions;
import com.akiban.server.api.dml.ColumnSelector;
import com.akiban.server.api.dml.scan.BufferFullException;
import com.akiban.server.api.dml.scan.CursorId;
import com.akiban.server.api.dml.scan.CursorState;
import com.akiban.server.api.dml.scan.LegacyRowOutput;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.dml.scan.RowOutput;
import com.akiban.server.api.dml.scan.ScanRequest;
import com.akiban.server.service.ServiceManagerImpl;
import com.akiban.server.service.dxl.DXLFunctionsHook.DXLFunction;
import com.akiban.server.service.session.Session;

import java.util.List;
import java.util.Set;

import static com.akiban.util.Exceptions.throwAlways;

public final class HookableDMLFunctions implements DMLFunctions {

    private final DMLFunctions delegate;
    private final DXLFunctionsHook hook;

    public HookableDMLFunctions(DMLFunctions delegate, List<DXLFunctionsHook> hooks) {
        this.delegate = delegate;
        this.hook = hooks.size() == 1 ? hooks.get(0) : new CompositeHook(hooks);
    }

    @Override
    public TableStatistics getTableStatistics(Session session, int tableId, boolean updateFirst) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS);
            return delegate.getTableStatistics(session, tableId, updateFirst);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, thrown);
        }
    }

    @Override
    public CursorId openCursor(Session session, int knownAIS, ScanRequest request) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunction.OPEN_CURSOR);
            return delegate.openCursor(session, knownAIS, request);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunction.OPEN_CURSOR, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.OPEN_CURSOR, thrown);
        }
    }

    @Override
    public CursorState getCursorState(Session session, CursorId cursorId) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunctionsHook.DXLFunction.GET_CURSOR_STATE);
            return delegate.getCursorState(session, cursorId);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_CURSOR_STATE, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.GET_CURSOR_STATE, thrown);
        }
    }

    @Override
    public void scanSome(Session session, CursorId cursorId, LegacyRowOutput output) throws BufferFullException {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunctionsHook.DXLFunction.SCAN_SOME);
            delegate.scanSome(session, cursorId, output);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (BufferFullException ex) {
            thrown = ex; 
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, ex);
            throw ex;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.SCAN_SOME, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.SCAN_SOME, thrown);
        }
    }

    @Override
    public void scanSome(Session session, CursorId cursorId, RowOutput output) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunctionsHook.DXLFunction.SCAN_SOME);
            delegate.scanSome(session, cursorId, output);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunction.SCAN_SOME, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.SCAN_SOME, thrown);
        }
    }

    @Override
    public void closeCursor(Session session, CursorId cursorId) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunction.CLOSE_CURSOR);
            delegate.closeCursor(session, cursorId);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.CLOSE_CURSOR, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunction.CLOSE_CURSOR, thrown);
        }
    }

    @Override
    public Set<CursorId> getCursors(Session session) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunction.GET_CURSORS);
            return delegate.getCursors(session);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunction.GET_CURSORS, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunction.GET_CURSORS, thrown);
        }
    }

    @Override
    public RowData convertNewRow(NewRow row){
        Session session = ServiceManagerImpl.newSession();
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunctionsHook.DXLFunction.CONVERT_NEW_ROW);
            return delegate.convertNewRow(row);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.CONVERT_NEW_ROW, t);
            throw throwAlways(t);
        } finally {
            try {
                hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.CONVERT_NEW_ROW, thrown);
            } finally {
                session.close();
            }
        }
    }

    @Override
    public NewRow convertRowData(RowData rowData) {
        Session session = ServiceManagerImpl.newSession();
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunction.CONVERT_ROW_DATA);
            return delegate.convertRowData(rowData);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.CONVERT_ROW_DATA, t);
            throw throwAlways(t);
        } finally {
            try {
                hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.CONVERT_ROW_DATA, thrown);
            } finally {
                session.close();
            }
        }
    }

    @Override
    public List<NewRow> convertRowDatas(List<RowData> rowDatas) {
        Session session = ServiceManagerImpl.newSession();
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunction.CONVERT_ROW_DATAS);
            return delegate.convertRowDatas(rowDatas);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.CONVERT_ROW_DATAS, t);
            throw throwAlways(t);
        } finally {
            try {
                hook.hookFunctionFinally(session, DXLFunction.CONVERT_ROW_DATAS, thrown);
            } finally {
                session.close();
            }
        }
    }

    @Override
    public Long writeRow(Session session, NewRow row) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunction.WRITE_ROW);
            return delegate.writeRow(session, row);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunction.WRITE_ROW, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.WRITE_ROW, thrown);
        }
    }

    @Override
    public void deleteRow(Session session, NewRow row) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunctionsHook.DXLFunction.DELETE_ROW);
            delegate.deleteRow(session, row);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.DELETE_ROW, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.DELETE_ROW, thrown);
        }
    }

    @Override
    public void updateRow(Session session, NewRow oldRow, NewRow newRow, ColumnSelector columnSelector) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunctionsHook.DXLFunction.UPDATE_ROW);
            delegate.updateRow(session, oldRow, newRow, columnSelector);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunction.UPDATE_ROW, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunctionsHook.DXLFunction.UPDATE_ROW, thrown);
        }
    }

    @Override
    public void truncateTable(final Session session, final int tableId) {
        Throwable thrown = null;
        try {
            hook.hookFunctionIn(session, DXLFunctionsHook.DXLFunction.TRUNCATE_TABLE);
            delegate.truncateTable(session, tableId);
        } catch (RuntimeException t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.GET_TABLE_STATISTICS, t);
            throw t;
        } catch (Throwable t) {
            thrown = t;
            hook.hookFunctionCatch(session, DXLFunctionsHook.DXLFunction.TRUNCATE_TABLE, t);
            throw throwAlways(t);
        } finally {
            hook.hookFunctionFinally(session, DXLFunction.TRUNCATE_TABLE, thrown);
        }
    }
}

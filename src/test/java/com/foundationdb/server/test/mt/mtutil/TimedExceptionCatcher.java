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

package com.foundationdb.server.test.mt.mtutil;

import com.foundationdb.server.service.session.Session;

public abstract class TimedExceptionCatcher extends TimedCallable<Throwable> {

    protected abstract void doOrThrow(TimePoints timePoints, Session session) throws Exception;

    @Override
    protected final Throwable doCall(TimePoints timePoints, Session session) throws Exception {
        Throwable t = null;
        try {
            doOrThrow(timePoints, session);
        } catch (Throwable caught) {
            t = caught;
            handleCaught(timePoints, session, t);
        }
        return t;
    }

    protected void handleCaught(TimePoints timePoints, Session session, Throwable t) {
        // nothing
    }

    public static <T extends Throwable> void throwIfThrown(TimedResult<T> timedResult) throws T {
        T throwable = timedResult.getItem();
        if (throwable != null) {
            throw throwable;
        }
    }
}

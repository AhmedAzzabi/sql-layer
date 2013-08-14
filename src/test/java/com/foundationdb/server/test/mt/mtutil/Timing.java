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

package com.akiban.server.test.mt.mtutil;

public class Timing {
    private final static Timing instance = new Timing();

    Timing() {
        // nothing
    }

    /**
     * Sleeps in a way that gets around code instrumentation which may end up shortening Thread.sleep.
     * If Thread.sleep throws an exception, this will invoke {@code Thread.currentThread().interrupt()} to propagate it.
     * @param millis how long to sleep
     */
    public static void sleep(long millis) {
        instance.doSleepLoop(millis);
    }

    final void doSleepLoop(long millis) {
        if (millis <= 0) {
            return;
        }
        final long start = System.currentTimeMillis();
        final long end = start + millis;
        long remaining = millis;
        do {
            doSleep(remaining);
            remaining = end - System.currentTimeMillis();
        } while (remaining > 0);
    }

    protected void doSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

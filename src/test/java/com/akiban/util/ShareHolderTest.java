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

package com.akiban.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ShareHolderTest {

    @Test
    public void totallyEmpty() {
        assertEquals("shared state", false, new ShareHolder<>().isHolding());
    }

    @Test
    public void testSharing() {
        Shareable shareable = new DummyShareable();
        shareable.acquire();

        ShareHolder<Shareable> holder = new ShareHolder<>();
        assertEquals("shared state A", false, holder.isHolding());
        assertEquals("shareable.isShared", false, shareable.isShared());

        holder.hold(shareable);
        assertEquals("shared state B", true, holder.isHolding());
        assertEquals("shareable.isShared", true, shareable.isShared());

        holder.release();
        assertEquals("shared state A", false, holder.isHolding());
        assertEquals("shareable.isShared", false, shareable.isShared());
    }

    @Test
    public void releaseWhenNotHeld() {
        new ShareHolder<>().release();
    }

    @Test
    public void holdNull() {
        ShareHolder<Shareable> holder = new ShareHolder<>();
        holder.hold(null);
    }

    /**
     * A dummy class which can be shared or released, but which only counts as being shared as long as
     * the number of shares is >= the number given in its constructor. For instance, if that number is 0,
     * this object is shared by default. If it's 2, you'll have to call {@linkplain #acquire} twice for it to
     * be shared.
     */
    private static class DummyShareable implements Shareable {

        @Override
        public void acquire() {
            ++ownedBy;
        }

        @Override
        public boolean isShared() {
            return ownedBy > 1;
        }

        @Override
        public void release() {
            assert ownedBy >= 0 : ownedBy;
            if (ownedBy > 0)
                --ownedBy;
        }

        private int ownedBy = 0;
    }
}

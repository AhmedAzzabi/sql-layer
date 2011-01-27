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

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.junit.Test;

public final class MultiCloserTest
{
    private static class Thrower implements Closeable
    {
        private final IOException ioe;
        private final NullPointerException npe;

        private Thrower(IOException ioe, NullPointerException npe)
        {
            this.ioe = ioe;
            this.npe = npe;
        }

        public Thrower()
        {
            this(null,null);
        }


        public Thrower(IOException e)
        {
            this(e, null);
        }

        public Thrower(NullPointerException e)
        {
            this(null, e);
        }

        @Override
        public void close() throws IOException
        {
            if (ioe != null)
                throw ioe;
            if (npe != null)
                throw npe;
        }
    }

    @Test
    public void nothingThrown() throws IOException
    {
        MultiCloser closer = new MultiCloser();
        // the unused reference is to demonstrate that generics work :)
        @SuppressWarnings("unused") Thrower thrower = closer.register( new Thrower() );
        closer.close();
    }

    @Test
    public void oneIoeThrown()
    {
        MultiCloser closer = new MultiCloser();
        IOException expected = new IOException();

        closer.register( new Thrower() );
        closer.register( new Thrower(expected) );
        try
        {
            closer.close();
        }
        catch(IOException e)
        {
            Assert.assertEquals("exception message", "one IOException caught", e.getMessage());
            Assert.assertSame("exception cause", expected, e.getCause());
            return;
        }
        Assert.fail("expected IOException");
    }

    @Test
    public void oneNpeThrown() throws IOException
    {
        MultiCloser closer = new MultiCloser();
        NullPointerException expected = new NullPointerException();

        closer.register( new Thrower() );
        closer.register( new Thrower(expected) );
        try
        {
            closer.close();
        }
        catch(RuntimeException e)
        {
            Assert.assertEquals("exception message", "one unchecked exception caught", e.getMessage());
            Assert.assertSame("exception cause", expected, e.getCause());
            return;
        }
        Assert.fail("expected IOException");
    }

    @Test
    public void multipleThrownButNoIOE() throws IOException
    {
        MultiCloser closer = new MultiCloser();
        closer.setPrintingOfExtraThrowables(false);
        NullPointerException expected = new NullPointerException("one");

        closer.register( new Thrower() );
        closer.register( new Thrower(expected) );
        closer.register( new Thrower( new NullPointerException("two") ));
        try
        {
            closer.close();
        }
        catch(RuntimeException e)
        {
            Assert.assertEquals("exception message",
                    "2 items caught; first unchecked exception is listed as this exception's cause",
                    e.getMessage());
            Assert.assertSame("exception cause", expected, e.getCause());
            return;
        }
        Assert.fail("expected IOException");
    }

    @Test
    public void multipleThrownIncludingIOE()
    {
        MultiCloser closer = new MultiCloser();
        closer.setPrintingOfExtraThrowables(false);
        IOException expected = new IOException();

        closer.register( new Thrower( new NullPointerException("one") ));
        closer.register( new Thrower() );
        closer.register( new Thrower(expected) );
        closer.register( new Thrower( new NullPointerException("two") ));
        try
        {
            closer.close();
        }
        catch(IOException e)
        {
            Assert.assertEquals("exception message",
                    "3 items caught; first IOException is listed as this exception's cause",
                    e.getMessage());
            Assert.assertSame("exception cause", expected, e.getCause());
            return;
        }
        Assert.fail("expected IOException");
    }

    @Test
    public void throwMyOwn() throws IOException
    {
        MultiCloser closer = new MultiCloser();
        closer.register( new Thrower( new IOException()) );
        Throwable t = new IllegalStateException();
        closer.throwException(t);
        closer.setPrintingOfExtraThrowables(false);
        try
        {
            closer.close();
        }
        catch (RuntimeException e)
        {
            Assert.assertEquals("exception message",
                    "2 items caught; first unchecked exception is listed as this exception's cause", e.getMessage());
            Assert.assertSame("cause", t, e.getCause());
            return;
        }
        Assert.fail("expected a RuntimeException");
    }

    @Test
    public void testCloseableIterables() throws IOException
    {
        final AtomicInteger i = new AtomicInteger();

        class MyCloser implements Closeable
        {
            @Override
            public void close() throws IOException
            {
                i.incrementAndGet();
            }
        }

        MultiCloser closer = new MultiCloser();
        List<MyCloser> closeables = new LinkedList<MyCloser>();
        closer.registerIterable( closeables );

        closer.close();
        Assert.assertEquals(0, i.get());

        closeables.add( new MyCloser() );
        closeables.add( new MyCloser() );
        closer.close();
        Assert.assertEquals(2, i.get());

        closeables.add( new MyCloser() );
        closer.close();
        Assert.assertEquals(5, i.get()); // previous 2, plus those two again, plus this latest

        closeables.clear();
        closer.close();
        Assert.assertEquals(5, i.get());        
    }

    @Test(expected=IllegalArgumentException.class)
    public void closerClosesItself()
    {
        MultiCloser closer = new MultiCloser();
        closer.register(closer);
    }

    private static void assertCustomCloseable(MultiCloser.CustomCloseable closeable, Class<? extends Throwable>... chain)
    {
        Throwable thrown = null;
        try
        {
            closeable.close();
        }
        catch(Throwable t)
        {
            thrown = t;
        }

        int i = 0;
        for (Class<? extends Throwable> throwable : chain)
        {
            Assert.assertNotNull("expected " + throwable + " but found null", thrown);
            Assert.assertEquals("at level " + i, throwable, thrown.getClass());
            thrown = thrown.getCause();
            ++i;
        }

        Assert.assertNull("extra cause found: " + thrown, thrown);
    }

    @Test
    public void customCloserNoException()
    {
        MultiCloser.CustomCloseable closeable = new MultiCloser.CustomCloseable()
        {
            @Override
            protected void customClose() throws Exception
            {
                // nothing
            }
        };
        assertCustomCloseable(closeable);
    }

    @Test
    public void customCloserNPE()
    {
        MultiCloser.CustomCloseable closeable = new MultiCloser.CustomCloseable()
        {
            @Override
            protected void customClose() throws Exception
            {
                throw new NullPointerException();
            }
        };
        assertCustomCloseable(closeable, RuntimeException.class, NullPointerException.class);
    }

    @Test
    public void customCloserException()
    {
        MultiCloser.CustomCloseable closeable = new MultiCloser.CustomCloseable()
        {
            @Override
            protected void customClose() throws Exception
            {
                throw new Exception();
            }
        };
        assertCustomCloseable(closeable, IOException.class, Exception.class);
    }


    @Test
    public void customCloserIOException()
    {
        MultiCloser.CustomCloseable closeable = new MultiCloser.CustomCloseable()
        {
            @Override
            protected void customClose() throws Exception
            {
                throw new IOException();
            }
        };
        assertCustomCloseable(closeable, IOException.class);
    }
}

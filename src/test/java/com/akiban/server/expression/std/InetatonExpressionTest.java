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

package com.akiban.server.expression.std;

import com.akiban.server.types.AkType;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InetatonExpressionTest  extends ComposedExpressionTestBase
{
    private final CompositionTestInfo testInfo = new CompositionTestInfo(1, AkType.LONG, true);

    @Test
    public void test4Quads ()
    {
        test("255.0.9.5", 4278192389L);
    }

     @Test
    public void test3Quads ()
    {
        test("255.1.1", 4278255617L); // equivalent to 255.1.0.1
    }

    @Test
    public void test2Quads ()
    {
        test("127.1", 2130706433); // equivalent to 127.0.0.1
    }

    @Test
    public void test1Quad()
    {
        test("127", 127); // equivalent to 0.0.0.127
    }

    @Test
    public void testZeroQuad()
    {
        testExpectNull(new LiteralExpression(AkType.VARCHAR, ""));
    }

    @Test
    public void test5Quads() // do not accept IPv6 or anything other than Ipv4
    {
        testExpectNull(new LiteralExpression(AkType.VARCHAR,"1.2.3.4.5"));
    }

    @Test
    public void testNull ()
    {
        testExpectNull(new LiteralExpression(AkType.NULL, null));
    }

    @Test
    public void testBadFormatString ()
    {
        testExpectNull(new LiteralExpression(AkType.VARCHAR, "12sdfa"));
    }

    @Test
    public void testNonNumeric ()
    {        
        testExpectNull(new LiteralExpression(AkType.VARCHAR, "a.b.c.d"));
    }

    @Test
    public void testNeg ()
    {
        testExpectNull(new LiteralExpression(AkType.VARCHAR, "-127.0.0.1"));
    }

    @Test
    public void testNumberOutofRange ()
    {
        testExpectNull(new LiteralExpression(AkType.VARCHAR, "256.0.1.0"));
    }
    
    @Override
    protected CompositionTestInfo getTestInfo()
    {
        return testInfo;
    }

    @Override
    protected ExpressionComposer getComposer()
    {
        return InetatonExpression.COMPOSER;
    }

    //------------ private methods-------------------------
    private void testExpectNull (Expression arg)
    {
        assertTrue((new InetatonExpression(arg)).evaluation().eval().isNull());
    }
    private void test (String ip, long expected)
    {
        assertEquals(expected,
                (new InetatonExpression(new LiteralExpression(AkType.VARCHAR, ip))).evaluation().eval().getLong());
    }

    @Override
    public boolean alreadyExc()
    {
        return false;
    }
}

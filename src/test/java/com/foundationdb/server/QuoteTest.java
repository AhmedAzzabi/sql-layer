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

package com.akiban.server;

import com.akiban.util.AkibanAppender;
import com.akiban.util.ArgumentValidation;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

public class QuoteTest {
    private static final String TEST_STRING = "world\\ isn't this \" a quote?\u0001";
    @Test
    public void testNoneEncoding() {
        StringBuilder sb = new StringBuilder();
        Quote.NONE.append(AkibanAppender.of(sb), TEST_STRING);

        assertEquals("encoded string", TEST_STRING, sb.toString());

    }

    @Test
    public void testDoubleEncoding() {
        StringBuilder sb = new StringBuilder();
        Quote.DOUBLE_QUOTE.append(AkibanAppender.of(sb), TEST_STRING);

        assertEquals("encoded string", "world\\\\ isn't this \\\" a quote?\u0001", sb.toString());
    }

    @Test
    public void testSingleEncoding() {
        StringBuilder sb = new StringBuilder();
        Quote.SINGLE_QUOTE.append(AkibanAppender.of(sb), TEST_STRING);

        assertEquals("encoded string", "world\\\\ isn\\'t this \" a quote?\u0001", sb.toString());
    }

    @Test
    public void testJSONEncoding() {
        StringBuilder sb = new StringBuilder();
        Quote.JSON_QUOTE.append(AkibanAppender.of(sb), TEST_STRING);

        assertEquals("encoded string", "world\\\\ isn't this \\\" a quote?\\u0001", sb.toString());
    }

    @Test
    public void writeBytesBasicASCII_NONE() {
        doWriteBytesTest("very basic string", "US-ASCII", "very basic string", Quote.NONE);
    }

    @Test
    public void writeBytesBasicUTF8_NONE() {
        doWriteBytesTest("very basic string", "UTF-8", "very basic string", Quote.NONE);
    }
    @Test
    public void writeBytesBasicUTF8_DOUBLE() {
        doWriteBytesTest("very basic string", "UTF-8", "very basic string", Quote.DOUBLE_QUOTE);
    }

    @Test(expected=IllegalArgumentException.class)
    public void writeBytesBasicBadEncoding() throws UnsupportedEncodingException {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintWriter pr = new PrintWriter(os);
        AkibanAppender appender = AkibanAppender.of(os, pr, "UTF-16");
        byte[] bytes = "some string".getBytes("UTF-16");
        Quote.writeBytes(appender, bytes, 0, bytes.length, Charset.forName("UTF-16"), Quote.NONE);
    }

    @Test
    public void writeBytesWithSnowman_JSON() {
        doWriteBytesTest("very wintery ☃ string", "UTF-8", "very wintery \\u2603 string", Quote.JSON_QUOTE);
    }

    @Test
    public void writeBytesJSONControlChars() {
        doWriteBytesTest("very newline \n string", "UTF-8", "very newline \\n string", Quote.JSON_QUOTE);
    }

    @Test
    public void snowmanWithPrepend() {
        doWriteBytesTest("very wintery ☃ string", "UTF-8", 15, 0, "very wintery ☃ string", Quote.NONE);
    }

    @Test
    public void snowmanWithSuffix() {
        doWriteBytesTest("very wintery ☃ string", "UTF-8", 0, 13, "very wintery ☃ string", Quote.NONE);
    }

    @Test
    public void snowmanWithBoth() {
        doWriteBytesTest("very wintery ☃ string", "UTF-8", 15, 13, "very wintery ☃ string", Quote.NONE);
    }

    public void doWriteBytesTest(String testString, String charsetName, String expectedString, Quote quote) {
        doWriteBytesTest(testString, charsetName, 0, 0, expectedString, quote);
    }

    public void doWriteBytesTest(String testString, String charsetName, int preBytes, int postBytes,
                                 String expectedString, Quote quote) {
        ArgumentValidation.isGTE("prependBytes", preBytes, 0);

        Charset charset = Charset.forName(charsetName);
        byte[] testBytes = testString.getBytes(charset);

        if (postBytes > 0) {
            assertFalse("last byte was already 0!", testBytes[testBytes.length - 1] == 0);
            byte[] tmp = new byte[testBytes.length + postBytes];
            System.arraycopy(testBytes, 0, tmp, 0, testBytes.length);
            testBytes = tmp;
            assertEquals("last byte", 0, testBytes[testBytes.length - 1]);
        }

        if (preBytes > 0) {
            assertFalse("first byte was already 0!", testBytes[0] == 0);
            byte[] tmp = new byte[testBytes.length + preBytes];
            System.arraycopy(testBytes, 0, tmp, preBytes, testBytes.length);
            testBytes = tmp;
            assertEquals("first byte", 0, testBytes[0]);
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintWriter pr;
        try {
          pr = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
        }
        catch (UnsupportedEncodingException ex) {
          throw new RuntimeException(ex);
        }
        AkibanAppender appender = AkibanAppender.of(os, pr, "UTF-8");
        Quote.writeBytes(appender, testBytes, preBytes, testBytes.length - preBytes - postBytes, charset, quote);
        pr.flush();

        String actualString = new String(os.toByteArray(), charset);
        assertEquals("written string", expectedString, actualString);
        assertArrayEquals("written bytes", expectedString.getBytes(charset), os.toByteArray());
    }
}

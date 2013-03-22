
package com.akiban.sql.pg;

import com.akiban.sql.pg.YamlTester.Regexp;

import static org.junit.Assert.fail;
import org.junit.Test;

/** Test the YamlTester.Regexp class. */

public class YamlTesterRegexpTest {

    @Test(expected=NullPointerException.class)
    public void testConstructorNull() {
	new Regexp(null);
    }

    @Test
    public void testSimple() {
	test("", "", true);
	test("null", "null", true);
	test("null", null, true);
	test("abc", "abc", true);
	test("abc", "ab", false);
	test("abc", "abcd", false);
	test("1", new Integer(1), true);
	test("1", new Integer(0), false);
	test("1.0", new Double(1.0), true);
	test("1.0", new Double(-1.0), false);
    }

    @Test
    public void testFeatures() {
	test("ab\\\\c", "ab\\c", true);
	test("a.c", "axc", true);
	test("a[bc]d", "abd", true);
	test("a[bc]d", "acd", true);
	test("a[bc]d", "aed", false);
	test("(ab|cd)ef", "abef", true);
	test("(ab|cd)ef", "cdef", true);
	test("(ab|cd)ef", "adef", false);
	test("ab*c", "ac", true);
	test("ab*c", "abc", true);
	test("ab*c", "abbbc", true);
	test("ab*c", "aabc", false);
	test("ab?c", "ac", true);
	test("ab?c", "abc", true);
	test("ab?c", "abbc", false);
	test("ab+c", "ac", false);
	test("ab+c", "abc", true);
	test("ab+c", "abbbbc", true);
	test("ab+c", "abbbbcc", false);
    }

    @Test
    public void testCaptureReference() {
	test("a b c \\{d e f\\}", "a b c {d e f}", true);
	test("([ab]+)([cd]+){1}{2}", "badcbadc", true);
	test("([ab]+)([cd]+){1}{2}", "badcabcd", false);
	test("\\{abc\\}", "{abc}", true);
	test("\\{123\\}", "{123}", true);
    }

    private static void test(String pattern, Object output, boolean match) {
	boolean result = new Regexp(pattern).compareExpected(output);
	if (result != match) {
	    fail("Expected pattern '" + pattern + "' and output '" + output +
		 "' to " + (match ? "" : "not ") + "match");
	}
    }
}

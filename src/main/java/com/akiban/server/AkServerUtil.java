/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.server;

import com.akiban.server.rowdata.FieldDef;
import com.akiban.util.AkibanAppender;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.management.RuntimeMXBean;
import java.math.BigInteger;
import java.nio.ByteBuffer;

public class AkServerUtil {

    private final static char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    public final static String NEW_LINE = System.getProperty("line.separator");

    private static String UNEXPECTED_SIGNED_WIDTH_MSG = "Width must be 0,1,2,3,4 or 8 but was: ";
    private static String UNEXPECTED_UNSIGNED_WIDTH_MSG = "Width must be 0,1,2,3, or 4 but was: ";


    public static long getSignedIntegerByWidth(final byte[] bytes, final int index, final int width) {
        switch (width) {
            case 0: return 0;
            case 1: return bytes[index];
            case 2: return getShort(bytes, index);
            case 3: return getMediumInt(bytes, index);
            case 4: return getInt(bytes, index);
            case 8: return getLong(bytes, index);
        }

        throw new IllegalArgumentException(UNEXPECTED_SIGNED_WIDTH_MSG + width);
    }

    public static long getUnsignedIntegerByWidth(final byte[] bytes, final int index, final int width) {
        switch (width) {
            case 0: return 0;
            case 1: return getUByte(bytes, index);
            case 2: return getUShort(bytes, index);
            case 3: return getUMediumInt(bytes, index);
            case 4: return getUInt(bytes, index);
            //case 8: return getULong(bytes, index); // Returns BigInteger, must call directly
        }
        
        throw new IllegalArgumentException(UNEXPECTED_UNSIGNED_WIDTH_MSG + width);
    }

    /**
     * Write an integer into a byte array.
     * @param destination Byte buffer to write into
     * @param destinationIndex Position in destination to write at
     * @param width Width of integer (= number of bytes to write)
     * @param value Value to write
     * @return The number of bytes written
     */
    public static int putIntegerByWidth(byte[] destination, int destinationIndex, int width, long value) {
        switch (width) {
            case 0: break;
            case 1: putByte(destination, destinationIndex, (byte)value);     break;
            case 2: putShort(destination, destinationIndex, (short)value);   break;
            case 3: putMediumInt(destination, destinationIndex, (int)value); break;
            case 4: putInt(destination, destinationIndex, (int)value);       break;
            case 8: putLong(destination, destinationIndex, value);           break;
            default:
                throw new IllegalArgumentException(UNEXPECTED_SIGNED_WIDTH_MSG + width);
        }
        return width;
    }

    public static byte getByte(byte[] bytes, int index) {
        return bytes[index];
    }

    public static short getUByte(byte[] bytes, int index) {
        return (short) (bytes[index] & 0xFF);
    }

    public static short getShort(byte[] bytes, int index) {
        return (short) ((bytes[index] & 0xFF) | (bytes[index+1] & 0xFF) << 8);
    }

    public static int getUShort(byte[] bytes, int index) {
        return getShort(bytes, index) & 0xFFFF;
    }

    public static int getMediumInt(byte[] bytes, int index) {
        final int value = getUMediumInt(bytes, index);
        // Negative values have bit 23 set so the sign extension promotes to 32bit representation
        return (value << 8) >> 8;
    }

    public static int getUMediumInt(byte[] bytes, int index) {
        return (bytes[index] & 0xFF)
                | (bytes[index + 1] & 0xFF) << 8
                | (bytes[index + 2] & 0xFF) << 16;
    }

    public static int getInt(byte[] bytes, int index) {
        return (bytes[index] & 0xFF)
                | (bytes[index + 1] & 0xFF) << 8
                | (bytes[index + 2] & 0xFF) << 16
                | (bytes[index + 3] & 0xFF) << 24;
    }

    public static long getUInt(byte[] bytes, int index) {
        return getInt(bytes, index) & 0xFFFFFFFFL;
    }

    public static long getLong(byte[] bytes, int index) {
        return (bytes[index] & 0xFFL)
                | (bytes[index + 1] & 0xFFL) << 8
                | (bytes[index + 2] & 0xFFL) << 16
                | (bytes[index + 3] & 0xFFL) << 24
                | (bytes[index + 4] & 0xFFL) << 32
                | (bytes[index + 5] & 0xFFL) << 40
                | (bytes[index + 6] & 0xFFL) << 48
                | (bytes[index + 7] & 0xFFL) << 56;
    }

    public static BigInteger getULong(byte[] bytes, int index) {
        byte longBytes[] = {bytes[index+7],
                            bytes[index+6], 
                            bytes[index+5],
                            bytes[index+4],
                            bytes[index+3],
                            bytes[index+2],
                            bytes[index+1],
                            bytes[index]};
        return new BigInteger(1, longBytes);
    }

    public static void putByte(byte[] bytes, int index, int value) {
        bytes[index] = (byte) (value);
    }

    public static void putShort(byte[] bytes, int index, int value) {
        bytes[index]     = (byte) (value);
        bytes[index + 1] = (byte) (value >>> 8);
    }

    public static void putMediumInt(byte[] bytes, int index, int value) {
        bytes[index]     = (byte) (value);
        bytes[index + 1] = (byte) (value >>> 8);
        bytes[index + 2] = (byte) (value >>> 16);
    }

    public static void putInt(byte[] bytes, int index, int value) {
        bytes[index]     = (byte) (value);
        bytes[index + 1] = (byte) (value >>> 8);
        bytes[index + 2] = (byte) (value >>> 16);
        bytes[index + 3] = (byte) (value >>> 24);
    }

    public static void putLong(byte[] bytes, int index, long value) {
        bytes[index]     = (byte) (value);
        bytes[index + 1] = (byte) (value >>> 8);
        bytes[index + 2] = (byte) (value >>> 16);
        bytes[index + 3] = (byte) (value >>> 24);
        bytes[index + 4] = (byte) (value >>> 32);
        bytes[index + 5] = (byte) (value >>> 40);
        bytes[index + 6] = (byte) (value >>> 48);
        bytes[index + 7] = (byte) (value >>> 56);
    }

    public static String dump(byte[] b, int offset, int size) {
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        for (int m = 0; m < size; m += 16) {
            sb2.setLength(0);
            hex(sb1, m, 4);
            sb1.append(":");
            for (int i = 0; i < 16; i++) {
                sb1.append(" ");
                if (i % 8 == 0)
                    sb1.append(" ");
                int j = m + i;
                if (j < size) {
                    hex(sb1, b[j + offset], 2);
                    final char c = (char) (b[j + offset] & 0xFF);
                    sb2.append(c > 32 && c < 127 ? c : '.');
                } else
                    sb1.append("  ");
            }
            sb1.append("  ");
            sb1.append(sb2.toString());
            sb1.append(NEW_LINE);
        }
        return sb1.toString();
    }

    public static String hex(byte[] bytes, int start, int length) {
        final StringBuilder sb = new StringBuilder(length * 2);
        hex(AkibanAppender.of(sb), bytes, start, length);
        return sb.toString();
    }

    public static StringBuilder hex(StringBuilder sb, long value, int length) {
        for (int i = length - 1; i >= 0; i--) {
            sb.append(HEX_DIGITS[(int) (value >> (i * 4)) & 0xF]);
        }
        return sb;
    }

    public static void hex(AkibanAppender sb, byte[] bytes, int start,
            int length) {
        for (int i = start; i < start + length; i++) {
            sb.append(HEX_DIGITS[(bytes[i] & 0xF0) >>> 4]);
            sb.append(HEX_DIGITS[(bytes[i] & 0x0F)]);
        }
    }

    public static void printRuntimeInfo() {
        System.out.println();
        RuntimeMXBean m = java.lang.management.ManagementFactory
                .getRuntimeMXBean();
        System.out.println("BootClassPath = " + m.getBootClassPath());
        System.out.println("ClassPath = " + m.getClassPath());
        System.out.println("LibraryPath = " + m.getLibraryPath());
        System.out.println("ManagementSpecVersion = "
                + m.getManagementSpecVersion());
        System.out.println("Name = " + m.getName());
        System.out.println("SpecName = " + m.getSpecName());
        System.out.println("SpecVendor = " + m.getSpecVendor());
        System.out.println("SpecVersion = " + m.getSpecVersion());
        System.out.println("UpTime = " + m.getUptime());
        System.out.println("VmName = " + m.getVmName());
        System.out.println("VmVendor = " + m.getVmVendor());
        System.out.println("VmVersion = " + m.getVmVersion());
        System.out.println("InputArguments = " + m.getInputArguments());
        System.out.println("BootClassPathSupported = "
                + m.isBootClassPathSupported());
        System.out.println("---all properties--");
        System.out.println("SystemProperties = " + m.getSystemProperties());
        System.out.println("---");
        System.out.println();
    }

    public final static void cleanUpDirectory(final File file) {
        if (!file.exists()) {
            file.mkdirs();
            return;
        } else if (file.isFile()) {
            throw new IllegalStateException(file + " must be a directory");
        } else {
            final File[] files = file.listFiles();
            if (files != null) {
                cleanUpFiles(files);
            }
        }
    }

    public final static void cleanUpFiles(final File[] files) {
        for (final File file : files) {
            if (file.isDirectory()) {
                cleanUpDirectory(file);
            }
            file.delete();
        }
    }

    /**
     * Cracks the MySQL variable-length format. Interprets 0, 1, 2 or 3 prefix
     * bytes as a little-endian string size and constructs a string from the
     * remaining bytes.
     * 
     * @param bytes Byte array to read string from
     * @param offset Position within bytes to start at
     * @param width Number of available bytes 
     * @param fieldDef Corresponding field
     * @return The decoded string.
     */
    public static String decodeMySQLString(byte[] bytes, final int offset,
            final int width, final FieldDef fieldDef) {
        ByteBuffer buff = byteBufferForMySQLString(bytes, offset, width, fieldDef);
        return decodeString(buff, fieldDef.column().getCharsetAndCollation().charset());
    }

    /**
     * Convert the bytes in a given buffer to a string, using a given charset.
     * @param buffer array of bytes encoded using the given charset
     * @param charset name of valid and supported character set
     * @return string representation of the buffer
     * @throws RuntimeException if the charset is not supported
     */
    static String decodeString(ByteBuffer buffer, String charset) {
        if (buffer == null) {
            return null;
        }
        if (charset == null) {
            throw new IllegalArgumentException("charset");
        }
        // Note: String(.., Charset) has *very* different behavior than String(.., "charset")
        // Think carefully, and read the String docs, before changing.
        try {
            return new String(buffer.array(), buffer.position(), buffer.limit() - buffer.position(), charset);
        } catch(UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static ByteBuffer byteBufferForMySQLString(byte[] bytes, final int offset,
                                           final int width, final FieldDef fieldDef) {
        if (width == 0) {
            return null;
        }

        final int prefixSize = fieldDef.getPrefixSize();
        int length = (int) getUnsignedIntegerByWidth(bytes, offset, prefixSize);
        
        if (length > width) {
            throw new IllegalArgumentException(String.format(
                    "String is wider than available bytes: %d > %d", length, width));
        }

        return ByteBuffer.wrap(bytes, offset + prefixSize, length);
    }

    public static int varWidth(final int length) {
        return length == 0 ? 0 : length < 0x100 ? 1 : length < 0x10000 ? 2
                : length < 0x1000000 ? 3 : 4;
    }
    
    public static boolean equals(final Object a, final Object b) {
        return a == null ? b == null : a.equals(b);
    }
    
    public static int hashCode(final Object o) {
        return o == null ? Integer.MIN_VALUE : o.hashCode();
    }
}

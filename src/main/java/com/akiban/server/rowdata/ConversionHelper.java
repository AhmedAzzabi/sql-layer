
package com.akiban.server.rowdata;

import com.akiban.ais.model.TableName;
import com.akiban.server.AkServerUtil;
import com.akiban.server.error.UnsupportedCharsetException;

import java.io.UnsupportedEncodingException;

final class ConversionHelper {
    // "public" methods

    /**
     * Writes a VARCHAR or CHAR: inserts the correct-sized PREFIX for MySQL
     * VARCHAR. Assumes US-ASCII encoding, for now. Can be used temporarily for
     * the BLOB types as well.
     *
     * @param string Othe string to put in
     * @param bytes Buffer to write bytes into
     * @param offset Position within bytes to write at
     * @param fieldDef Corresponding field
     * @return How many positions in bytes were used
     */
    public static int encodeString(String string, final byte[] bytes, final int offset, final FieldDef fieldDef) {
        assert string != null;
        final byte[] b;
        String charsetName = fieldDef.column().getCharsetAndCollation().charset();
        try {
            b = string.getBytes(charsetName);
        } catch (UnsupportedEncodingException e) {
            TableName table = fieldDef.column().getTable().getName();
            throw new UnsupportedCharsetException(table.getSchemaName(), table.getTableName(), charsetName);
        }
        return putByteArray(b, 0, b.length, bytes, offset, fieldDef);
    }

    public static int putByteArray(final byte[] src, final int srcOffset, final int srcLength,
                                   final byte[] dst, final int dstOffset, final FieldDef fieldDef) {
        int prefixSize = fieldDef.getPrefixSize();
        AkServerUtil.putIntegerByWidth(dst, dstOffset, prefixSize, srcLength);
        System.arraycopy(src, srcOffset, dst, dstOffset + prefixSize, srcLength);
        return prefixSize + srcLength;

    }

    // for use in this class

    private ConversionHelper() {}
}

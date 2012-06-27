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

package com.akiban.sql.server;

import com.akiban.server.Quote;
import com.akiban.server.error.UnsupportedCharsetException;
import com.akiban.server.error.ZeroDateTimeException;
import com.akiban.server.types.AkType;
import com.akiban.server.types.FromObjectValueSource;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.extract.Extractors;
import com.akiban.server.types3.mcompat.mtypes.MDatetimes;
import com.akiban.server.types3.mcompat.mtypes.MString;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.util.AkibanAppender;
import com.akiban.util.ByteSource;

import java.io.*;

public class ServerValueEncoder
{
    public static enum ZeroDateTimeBehavior {
        NONE(null),
        EXCEPTION("exception"),
        ROUND("round"),
        CONVERT_TO_NULL("convertToNull");

        private String propertyName;

        ZeroDateTimeBehavior(String propertyName) {
            this.propertyName = propertyName;
        }
        
        public static ZeroDateTimeBehavior fromProperty(String name) {
            if (name == null) return NONE;
            for (ZeroDateTimeBehavior zdtb : values()) {
                if (name.equals(zdtb.propertyName))
                    return zdtb;
            }
            throw new IllegalArgumentException(name);
        }
    }

    public static final String ROUND_ZERO_DATETIME = "0001-01-01 00:00:00";
    public static final String ROUND_ZERO_DATE = "0001-01-01";

    private String encoding;
    private ZeroDateTimeBehavior zeroDateTimeBehavior;
    private ByteArrayOutputStream byteStream;
    private PrintWriter printWriter;
    private AkibanAppender appender;
    private FromObjectValueSource objectSource;
    private PValue pSource;

    public ServerValueEncoder(String encoding) {
        this.encoding = encoding;

        byteStream = new ByteArrayOutputStream();
        try {
            printWriter = new PrintWriter(new OutputStreamWriter(byteStream, encoding));
        }
        catch (UnsupportedEncodingException ex) {
            throw new UnsupportedCharsetException("", "", encoding);
        }
        // If the target encoding is UTF-8, we can support
        // canAppendBytes() for properly encoded source strings.
        if ("UTF-8".equals(encoding))
            appender = AkibanAppender.of(byteStream, printWriter);
        else
            appender = AkibanAppender.of(printWriter);
    }

    public ServerValueEncoder(String encoding, ZeroDateTimeBehavior zeroDateTimeBehavior) {
        this(encoding);
        this.zeroDateTimeBehavior = zeroDateTimeBehavior;
    }

    public ByteArrayOutputStream getByteStream() {
        printWriter.flush();
        return byteStream;
    }

    public AkibanAppender getAppender() {
        return appender;
    }

    /** Encode the given value into a stream that can then be passed
     * to <code>writeByteStream</code>.
     */
    public ByteArrayOutputStream encodeValue(ValueSource value, ServerType type, 
                                             boolean binary) throws IOException {
        if (value.isNull())
            return null;
        if ((zeroDateTimeBehavior != ZeroDateTimeBehavior.NONE) &&
            (((type.getAkType() == AkType.DATE) &&
              (value.getDate() == 0)) ||
             ((type.getAkType() == AkType.DATETIME) &&
              (value.getDateTime() == 0)))) {
            switch (zeroDateTimeBehavior) {
            case EXCEPTION:
                throw new ZeroDateTimeException();
            case ROUND:
                if (objectSource == null)
                    objectSource = new FromObjectValueSource();
                objectSource.setExplicitly((type.getAkType() == AkType.DATETIME) ?
                                           ROUND_ZERO_DATETIME : ROUND_ZERO_DATE,
                                           AkType.VARCHAR);
                value = objectSource;
                break;
            case CONVERT_TO_NULL:
                return null;
            }
        }
        reset();
        appendValue(value, type, binary);
        return getByteStream();
    }
    
        /** Encode the given value into a stream that can then be passed
     * to <code>writeByteStream</code>.
     */
    public ByteArrayOutputStream encodePValue(PValueSource value, ServerType type, 
                                             boolean binary) throws IOException {
        if (value.isNull())
            return null;
        if ((zeroDateTimeBehavior != ZeroDateTimeBehavior.NONE) &&
            (((type.getInstance().typeClass() == MDatetimes.DATE) &&
              (value.getInt32() == 0)) ||
             ((type.getInstance().typeClass() == MDatetimes.DATETIME) &&
              (value.getInt64() == 0)))) {
            switch (zeroDateTimeBehavior) {
            case EXCEPTION:
                throw new ZeroDateTimeException();
            case ROUND:
                if (pSource == null)
                    pSource = new PValue(type.getInstance().typeClass().underlyingType());
                else {
                    if (type.getInstance().typeClass() == MDatetimes.DATETIME)
                        pSource.putInt64(00010101000000);
                    else
                        pSource.putInt32(00010101);
                }   
                break;
            case CONVERT_TO_NULL:
                return null;
            }
        }
        reset();
        appendPValue(value, type, binary);
        return getByteStream();
    }

    /** Encode the given direct value. */
    public ByteArrayOutputStream encodeObject(Object value, ServerType type, 
                                              boolean binary) throws IOException {
        if (value == null)
            return null;
        reset();
        appendObject(value, type, binary);
        return getByteStream();
    }

    /** Reset the contents of the buffer. */
    public void reset() {
        getByteStream().reset();
    }
    
    /** Append the given value to the buffer. */
    public void appendValue(ValueSource value, ServerType type, boolean binary) 
            throws IOException {
        if (type.getAkType() == AkType.VARBINARY) {
            ByteSource bs = Extractors.getByteSourceExtractor().getObject(value);
            byte[] ba = bs.byteArray();
            int offset = bs.byteArrayOffset();
            int length = bs.byteArrayLength();
            if (binary)
                getByteStream().write(ba, offset, length);
            else {
                for (int i = 0; i < length; i++) {
                    printWriter.format("\\%03o", ba[offset+i]);
                }
            }
        }
        else {
            assert !binary;
            value.appendAsString(appender, Quote.NONE);
        }
    }
    
        /** Append the given value to the buffer. */
    public void appendPValue(PValueSource value, ServerType type, boolean binary) 
            throws IOException {
        if (type.getInstance().typeClass() == MString.VARBINARY) {
            ByteSource bs = (ByteSource) value.getObject();
            byte[] ba = bs.byteArray();
            int offset = bs.byteArrayOffset();
            int length = bs.byteArrayLength();
            if (binary)
                getByteStream().write(ba, offset, length);
            else {
                for (int i = 0; i < length; i++) {
                    printWriter.format("\\%03o", ba[offset+i]);
                }
            }
        }
        else {
            assert !binary;
            String input = (String) value.getObject();
            String curr = (String) pSource.getObject();
            pSource.putObject(curr.concat(input));
        }
    }
    
    /** Append the given direct object to the buffer. */
    public void appendObject(Object value, ServerType type, boolean binary) 
            throws IOException {
        AkType akType = type.getAkType();
        if ((akType == AkType.VARCHAR) && (value instanceof String)) {
            // Optimize the common case of directly encoding a string.
            printWriter.write((String)value);
            return;
        }
        if (objectSource == null)
            objectSource = new FromObjectValueSource();
        objectSource.setExplicitly(value, akType);
        appendValue(objectSource, type, binary);
    }

    public void appendString(String string) throws IOException {
        printWriter.write(string);
    }

}

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

package com.akiban.server.types3.common.types;

import com.akiban.server.collation.AkCollator;
import com.akiban.server.collation.AkCollatorFactory;
import com.akiban.server.error.InvalidArgumentTypeException;
import com.akiban.server.error.UnsupportedCharsetException;
import com.akiban.server.expression.std.ExpressionTypes;
import com.akiban.server.types3.TBundle;
import com.akiban.server.types3.TCast;
import com.akiban.server.types3.TClass;
import com.akiban.server.types3.TClassFormatter;
import com.akiban.server.types3.TInputSet;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.TInstanceAdjuster;
import com.akiban.server.types3.TInstanceBuilder;
import com.akiban.server.types3.TInstanceNormalizer;
import com.akiban.server.types3.aksql.AkCategory;
import com.akiban.server.types3.pvalue.PBasicValueSource;
import com.akiban.server.types3.pvalue.PBasicValueTarget;
import com.akiban.server.types3.pvalue.PUnderlying;
import com.akiban.server.types3.pvalue.PValueCacher;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.texpressions.TValidatedOverload;
import com.akiban.sql.types.CharacterTypeAttributes;
import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.sql.types.TypeId;
import com.akiban.util.AkibanAppender;
import com.akiban.util.ByteSource;
import com.akiban.util.Strings;
import com.akiban.util.WrappingByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Formatter;

/**
 * Base types for VARCHAR types. Its base type is PUnderlying.STRING. Its cached object can either be a String
 * (representing a collated string with a lossy collation) or a ByteSource wrapping the string's bytes.
 */
public abstract class TString extends TClass
{
    protected TString (TypeId typeId, TBundle bundle, String name, int serialisationSize)
    {
        this(typeId, bundle, name, serialisationSize, -1);
    }

    protected TString (TypeId typeId, TBundle bundle, String name, int serialisationSize, int fixedLength)
    {
        super(bundle.id(),
                name,
                AkCategory.STRING_CHAR,
                StringAttribute.class,
                FORMAT.STRING,
                1,
                1,
                serialisationSize,
                PUnderlying.STRING);
        this.fixedLength = fixedLength;
        this.typeId = typeId;
    }
    
    private static enum FORMAT implements TClassFormatter {
        STRING {
            @Override
            public void format(TInstance instance, PValueSource source, AkibanAppender out) {
                if (source.hasCacheValue() && out.canAppendBytes()) {
                    Object cached = source.getObject();
                    if (cached instanceof ByteSource) {
                        String tInstanceCharset = StringAttribute.charsetName(instance);
                        Charset appenderCharset = out.appendBytesAs();
                        if (Strings.equalCharsets(appenderCharset, tInstanceCharset)) {
                            out.appendBytes((ByteSource) cached);
                            return;
                        }
                    }
                    else {
                        logger.warn("couldn't append TString directly; bad cached object. {}", source);
                    }
                }
                out.append(source.getString());
            }

            @Override
            public void formatAsLiteral(TInstance instance, PValueSource source, AkibanAppender out) {
                formatQuoted(source, out, '\'', '\'', false);
            }

            @Override
            public void formatAsJson(TInstance instance, PValueSource source, AkibanAppender out) {
                formatQuoted(source, out, '"', '\\', true);
            }

            private boolean needsEscaping(int ch) {
                // Anything other than printing ASCII.
                return (ch >= 0200) || Character.isISOControl(ch);
            }

            private static final String SIMPLY_ESCAPED = "\r\n\t";
            private static final String SIMPLY_ESCAPES = "rnt";

            protected void formatQuoted(PValueSource source, AkibanAppender out,
                                        char quote, char escape, boolean escapeControlChars) {
                String value = source.getString();
                out.append(quote);
                if (!escapeControlChars && (value.indexOf(quote) < 0))
                    out.append(value);
                else {
                    for (int i = 0; i < value.length(); i++) {
                        int ch = value.charAt(i);
                        if (escapeControlChars && needsEscaping(ch)) {
                            int idx = SIMPLY_ESCAPED.indexOf(ch);
                            if (idx < 0) {
                                new Formatter(out.getAppendable()).format("\\u%04x", (int)ch);
                            }
                            else {
                                out.append(escape);
                                out.append(SIMPLY_ESCAPES.charAt(idx));
                            }
                        }
                        else {
                            if ((ch == quote) || (ch == escape))
                                out.append(escape);
                            out.append((char)ch);
                        }
                    }
                }
                out.append(quote);
            }
        }
    }

    public int getFixedLength() {
        return fixedLength;
    }

    @Override
    public Object formatCachedForNiceRow(PValueSource source) {
        return StringCacher.getString((ByteSource)source.getObject(), source.tInstance());
    }

    @Override
    protected int doCompare(TInstance instanceA, PValueSource sourceA, TInstance instanceB, PValueSource sourceB) {
        CharacterTypeAttributes aAttrs = StringAttribute.characterTypeAttributes(instanceA);
        CharacterTypeAttributes bAttrs = StringAttribute.characterTypeAttributes(instanceB);
        AkCollator collator = ExpressionTypes.mergeAkCollators(aAttrs, bAttrs);
        if (collator == null)
            // TODO in the future, we may want to use some default collator. For now, just use native comparison
            return sourceA.getString().compareTo(sourceB.getString());
        return collator.compare(sourceA, sourceB);
    }

    @Override
    public boolean attributeIsPhysical(int attributeIndex) {
        return attributeIndex != StringAttribute.MAX_LENGTH.ordinal();
    }

    @Override
    public void attributeToString(int attributeIndex, long value, StringBuilder output) {
        StringAttribute attribute = StringAttribute.values()[attributeIndex];
        switch (attribute) {
        case MAX_LENGTH:
            output.append(value);
            break;
        case CHARSET:
            StringFactory.Charset[] charsets = StringFactory.Charset.values();
            if (value < 0 || value >= charsets.length) {
                logger.warn("charset value out of range: {}", value);
                output.append(value);
            }
            else {
                output.append(charsets[(int)value]);
            }
            break;
        case COLLATION:
            AkCollator collator = AkCollatorFactory.getAkCollator((int)value);
            if (collator == null) {
                if (value == StringFactory.NULL_COLLATION_ID) {
                    output.append("NONE");
                }
                else {
                    logger.warn("unknown collator for id " + value + " (" + ((int)value) + ')');
                    output.append(value);
                }
            }
            else {
                output.append(collator.getName());
            }
            break;
        }
    }

    @Override
    public Object attributeToObject(int attributeIndex, int value) {
        StringAttribute attribute = StringAttribute.values()[attributeIndex];
        switch (attribute) {
        case MAX_LENGTH:
            return value;
        case CHARSET:
            StringFactory.Charset[] charsets = StringFactory.Charset.values();
            if (value < 0 || value >= charsets.length) {
                logger.warn("charset value out of range: {}", value);
                return value;
            }
            else {
                return charsets[value].name();
            }
        case COLLATION:
            AkCollator collator = AkCollatorFactory.getAkCollator((int)value);
            if (collator == null) {
                if (value == StringFactory.NULL_COLLATION_ID) {
                    return "NONE";
                }
                else {
                    logger.warn("unknown collator for id " + value + " (" + ((int)value) + ')');
                    return value;
                }
            }
            else {
                return collator.getName();
            }
        default:
            throw new IllegalArgumentException("illegal attribute index: " + attributeIndex);
        }
    }

    public AkCollator getCollator(TInstance instance) {
        return AkCollatorFactory.getAkCollator((int)instance.attribute(StringAttribute.COLLATION));
    }

    @Override
    public PValueCacher cacher() {
        return cacher;
    }

    @Override
    protected DataTypeDescriptor dataTypeDescriptor(TInstance instance) {
        return new DataTypeDescriptor(
                typeId, instance.nullability(), instance.attribute(StringAttribute.MAX_LENGTH), 
                StringAttribute.characterTypeAttributes(instance));
    }

    @Override
    public TInstance instance (int length, int charsetId, boolean nullable) {
        return instance (fixedLength >= 0 ? fixedLength : length, charsetId, StringFactory.DEFAULT_COLLATION_ID, nullable);
    }

    @Override
    public TInstance instance(boolean nullable)
    {
        return instance(fixedLength >= 0 ? fixedLength : StringFactory.DEFAULT_LENGTH,
                        StringFactory.DEFAULT_CHARSET.ordinal(),
                        StringFactory.DEFAULT_COLLATION_ID,
                        nullable);
    }

    @Override
    public TInstance instance(int length, boolean nullable)
    {
        return instance(length < 0 ? 0 : length, 
                        StringFactory.DEFAULT_CHARSET.ordinal(),
                        StringFactory.DEFAULT_COLLATION_ID,
                        nullable);
    }

    @Override
    protected TInstance doPickInstance(TInstance left, TInstance right, boolean suggestedNullability) {
        return doPickInstance(left, right, false, suggestedNullability);
    }

    @Override
    protected void validate(TInstance instance) {
        int length = instance.attribute(StringAttribute.MAX_LENGTH);
        int charsetId = instance.attribute(StringAttribute.CHARSET);
        int collaitonid = instance.attribute(StringAttribute.COLLATION);
        // TODO
    }

    @Override
    public TCast castToVarchar() {
        return null;
    }

    @Override
    public TCast castFromVarchar() {
        return null;
    }

    private TInstance doPickInstance(TInstance left, TInstance right, boolean useRightLength, boolean nullable) {
        final int pickLen, pickCharset, pickCollation;

        int aCharset = left.attribute(StringAttribute.CHARSET);
        int bCharset = right.attribute(StringAttribute.CHARSET);
        if (aCharset == bCharset)
            pickCharset = aCharset;
        else
            throw new InvalidArgumentTypeException("can't combine strings " + left + " and " + right);
        int aCollation = left.attribute(StringAttribute.COLLATION);
        int bCollation = right.attribute(StringAttribute.COLLATION);
        if (aCollation == bCollation) {
            pickCollation = aCollation;
        }
        else {
            CharacterTypeAttributes aAttrs = StringAttribute.characterTypeAttributes(left);
            CharacterTypeAttributes bAttrs = StringAttribute.characterTypeAttributes(right);
            AkCollator collator = ExpressionTypes.mergeAkCollators(aAttrs, bAttrs);
            pickCollation = (collator == null) ? -1 : collator.getCollationId();
        }
        int leftLen = left.attribute(StringAttribute.MAX_LENGTH);
        int rightLen = right.attribute(StringAttribute.MAX_LENGTH);
        if (useRightLength) {
            pickLen = rightLen;
        }
        else {
            pickLen = Math.max(leftLen,rightLen);
        }
        return instance(pickLen, pickCharset, pickCollation, nullable);
    }

    private final int fixedLength;
    private final TypeId typeId;
    private static final Logger logger = LoggerFactory.getLogger(TString.class);

    private static final PValueCacher cacher = new StringCacher();

    private static class StringCacher implements PValueCacher {
        @Override
        public void cacheToValue(Object cached, TInstance tInstance, PBasicValueTarget target) {
            String asString = getString((ByteSource) cached, tInstance);
            target.putString(asString, null);
        }

        @Override
        public Object valueToCache(PBasicValueSource value, TInstance tInstance) {
            String charsetName = StringAttribute.charsetName(tInstance);
            try {
                return new WrappingByteSource(value.getString().getBytes(charsetName));
            } catch (UnsupportedEncodingException e) {
                throw new UnsupportedCharsetException("<unknown>", "<unknown>", charsetName);
            }
        }

        @Override
        public Object sanitize(Object object) {
            return String.valueOf(object);
        }

        static String getString(ByteSource bs, TInstance tInstance) {
            String charsetName = StringAttribute.charsetName(tInstance);
            String asString;
            try {
                asString = new String(bs.byteArray(), bs.byteArrayOffset(), bs.byteArrayLength(), charsetName);
            } catch (UnsupportedEncodingException e) {
                throw new UnsupportedCharsetException("<unknown>", "<unknown>", charsetName);
            }
            return asString;
        }

        @Override
        public boolean canConvertToValue(Object cached) {
            return cached instanceof ByteSource;
        }
    }

    public final TInstanceNormalizer PICK_RIGHT_LENGTH = new TInstanceNormalizer() {
        @Override
        public void apply(TInstanceAdjuster adapter, TValidatedOverload overload, TInputSet inputSet, int max) {
            TInstance result = null;
            boolean nullable = false;
            for (int i = overload.firstInput(inputSet); i >= 0; i = overload.nextInput(inputSet, i+1, max)) {
                TInstance input = adapter.get(i);
                nullable |= input.nullability();
                result = (result == null)
                        ? input
                        : doPickInstance(result, input, true, nullable);
            }
            assert result != null;
            int resultCharset = result.attribute(StringAttribute.CHARSET);
            int resultCollation = result.attribute(StringAttribute.COLLATION);
            for (int i = overload.firstInput(inputSet); i >= 0; i = overload.nextInput(inputSet, i+1, max)) {
                TInstance input = adapter.get(i);
                int inputCharset = input.attribute(StringAttribute.CHARSET);
                int inputCollation = input.attribute(StringAttribute.COLLATION);
                if ( (inputCharset != resultCharset) || (inputCollation != resultCollation)) {
                    TInstanceBuilder adjusted = adapter.adjust(i);
                    adjusted.setAttribute(StringAttribute.CHARSET, resultCharset);
                    adjusted.setAttribute(StringAttribute.COLLATION, resultCollation);
                }
            }
        }
    };
}

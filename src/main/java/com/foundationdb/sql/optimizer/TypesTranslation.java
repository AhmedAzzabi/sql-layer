/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
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

package com.foundationdb.sql.optimizer;

import com.foundationdb.ais.model.Column;
import com.foundationdb.ais.model.Type;
import com.foundationdb.ais.model.Types;
import com.foundationdb.server.error.AkibanInternalException;
import com.foundationdb.server.error.UnknownDataTypeException;
import com.foundationdb.server.expression.ExpressionType;
import com.foundationdb.server.expression.std.ExpressionTypes;
import com.foundationdb.server.types.AkType;
import com.foundationdb.server.types3.TInstance;
import com.foundationdb.server.types3.aksql.aktypes.AkBool;
import com.foundationdb.server.types3.aksql.aktypes.AkInterval;
import com.foundationdb.server.types3.aksql.aktypes.AkResultSet;
import com.foundationdb.server.types3.common.types.StringFactory.Charset;
import com.foundationdb.server.types3.common.types.StringFactory;
import com.foundationdb.server.types3.common.types.TString;
import com.foundationdb.server.types3.mcompat.mtypes.MApproximateNumber;
import com.foundationdb.server.types3.mcompat.mtypes.MBinary;
import com.foundationdb.server.types3.mcompat.mtypes.MDatetimes;
import com.foundationdb.server.types3.mcompat.mtypes.MNumeric;
import com.foundationdb.server.types3.mcompat.mtypes.MString;
import com.foundationdb.sql.StandardException;
import com.foundationdb.sql.types.CharacterTypeAttributes;
import com.foundationdb.sql.types.DataTypeDescriptor;
import com.foundationdb.sql.types.TypeId;
import java.util.ArrayList;
import java.util.List;

/** Yet another translator between type regimes. */
public final class TypesTranslation {
    public static AkType sqlTypeToAkType(DataTypeDescriptor descriptor) {
        TypeId typeId = descriptor.getTypeId();
        switch (typeId.getTypeFormatId()) {
        case TypeId.FormatIds.BOOLEAN_TYPE_ID:
            return AkType.BOOL;
        case TypeId.FormatIds.CHAR_TYPE_ID:
        case TypeId.FormatIds.VARCHAR_TYPE_ID:
            return AkType.VARCHAR;
        case TypeId.FormatIds.MEDIUMINT_ID:
        case TypeId.FormatIds.INT_TYPE_ID:
        case TypeId.FormatIds.SMALLINT_TYPE_ID:
        case TypeId.FormatIds.TINYINT_TYPE_ID:
            if (typeId == TypeId.YEAR_ID)
                return AkType.YEAR;
            if (typeId.isUnsigned())
                return AkType.U_INT;
            return AkType.LONG; // Not INT.
        case TypeId.FormatIds.LONGINT_TYPE_ID:
            if (typeId.isUnsigned())
                return AkType.U_BIGINT;
            return AkType.LONG;
        case TypeId.FormatIds.DECIMAL_TYPE_ID:
        case TypeId.FormatIds.NUMERIC_TYPE_ID:
            return AkType.DECIMAL;
        case TypeId.FormatIds.DOUBLE_TYPE_ID:
            if (typeId.isUnsigned())
                return AkType.U_DOUBLE;
            return AkType.DOUBLE;
        case TypeId.FormatIds.REAL_TYPE_ID:
            if (typeId.isUnsigned())
                return AkType.U_FLOAT;
            return AkType.FLOAT;
        case TypeId.FormatIds.DATE_TYPE_ID:
            return AkType.DATE;
        case TypeId.FormatIds.TIME_TYPE_ID:
            return AkType.TIME;
        case TypeId.FormatIds.TIMESTAMP_TYPE_ID:
            if (typeId == TypeId.DATETIME_ID)
                return AkType.DATETIME;
            return AkType.TIMESTAMP;
        case TypeId.FormatIds.BIT_TYPE_ID:
        case TypeId.FormatIds.VARBIT_TYPE_ID:
        case TypeId.FormatIds.LONGVARBIT_TYPE_ID:
        case TypeId.FormatIds.BLOB_TYPE_ID:
            return AkType.VARBINARY;
        case TypeId.FormatIds.LONGVARCHAR_TYPE_ID:
        case TypeId.FormatIds.CLOB_TYPE_ID:
            return AkType.TEXT;
        case TypeId.FormatIds.INTERVAL_YEAR_MONTH_ID:
            return AkType.INTERVAL_MONTH;
        case TypeId.FormatIds.INTERVAL_DAY_SECOND_ID:
            return AkType.INTERVAL_MILLIS;
        case TypeId.FormatIds.ROW_MULTISET_TYPE_ID_IMPL:
            return AkType.RESULT_SET;
        }

        String name = descriptor.getFullSQLTypeName();
        for (com.foundationdb.ais.model.Type aisType : Types.types()) {
            if (aisType.name().equalsIgnoreCase(name)) {
                return aisType.akType();
            }
        }
        try {
            return AkType.valueOf(name.toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            throw new UnsupportedOperationException(
                    "unsupported type id: " + typeId + " (" + name + ')'
            );
        }
    }

    public static ExpressionType toExpressionType(DataTypeDescriptor sqlType) {
        if (sqlType == null)
            return null;
        TypeId typeId = sqlType.getTypeId();
        switch (typeId.getTypeFormatId()) {
        case TypeId.FormatIds.BOOLEAN_TYPE_ID:
            return ExpressionTypes.BOOL;
        case TypeId.FormatIds.DATE_TYPE_ID:
            return ExpressionTypes.DATE;
        case TypeId.FormatIds.DECIMAL_TYPE_ID:
        case TypeId.FormatIds.NUMERIC_TYPE_ID:
            return ExpressionTypes.decimal(sqlType.getPrecision(),
                                           sqlType.getScale());
        case TypeId.FormatIds.DOUBLE_TYPE_ID:
            if (typeId.isUnsigned())
                return ExpressionTypes.U_DOUBLE;
            else
                return ExpressionTypes.DOUBLE;
        case TypeId.FormatIds.SMALLINT_TYPE_ID:
            if (typeId == TypeId.YEAR_ID)
                return ExpressionTypes.YEAR;
            /* else falls through */
        case TypeId.FormatIds.TINYINT_TYPE_ID:
        case TypeId.FormatIds.INT_TYPE_ID:
            if (typeId.isUnsigned())
                return ExpressionTypes.U_INT;
            else
                return ExpressionTypes.INT;
        case TypeId.FormatIds.LONGINT_TYPE_ID:
            if (typeId.isUnsigned())
                return ExpressionTypes.U_BIGINT;
            else
                return ExpressionTypes.LONG;
        case TypeId.FormatIds.LONGVARBIT_TYPE_ID:
        case TypeId.FormatIds.LONGVARCHAR_TYPE_ID:
        case TypeId.FormatIds.BLOB_TYPE_ID:
        case TypeId.FormatIds.CLOB_TYPE_ID:
        case TypeId.FormatIds.XML_TYPE_ID:
            return ExpressionTypes.TEXT;
        case TypeId.FormatIds.REAL_TYPE_ID:
            if (typeId.isUnsigned())
                return ExpressionTypes.U_FLOAT;
            else
                return ExpressionTypes.FLOAT;
        case TypeId.FormatIds.TIME_TYPE_ID:
            return ExpressionTypes.TIME;
        case TypeId.FormatIds.TIMESTAMP_TYPE_ID:
            if (typeId == TypeId.DATETIME_ID)
                return ExpressionTypes.DATETIME;
            else
                return ExpressionTypes.TIMESTAMP;
        case TypeId.FormatIds.BIT_TYPE_ID:
        case TypeId.FormatIds.VARBIT_TYPE_ID:
            return ExpressionTypes.varbinary(sqlType.getMaximumWidth());
        case TypeId.FormatIds.CHAR_TYPE_ID:
        case TypeId.FormatIds.VARCHAR_TYPE_ID:
            return ExpressionTypes.varchar(sqlType.getMaximumWidth(),
                                           sqlType.getCharacterAttributes());
        case TypeId.FormatIds.INTERVAL_DAY_SECOND_ID:
            return ExpressionTypes.INTERVAL_MILLIS;
        case TypeId.FormatIds.INTERVAL_YEAR_MONTH_ID:
            return ExpressionTypes.INTERVAL_MONTH;
        case TypeId.FormatIds.USERDEFINED_TYPE_ID:
            {
                AkType akType = null;
                String name = sqlType.getFullSQLTypeName();
                for (com.foundationdb.ais.model.Type aisType : Types.types()) {
                    if (aisType.name().equalsIgnoreCase(name)) {
                        akType = aisType.akType();
                        break;
                    }
                }
                if (akType == null) {
                    try {
                        akType = AkType.valueOf(name.toUpperCase());
                    }
                    catch (IllegalArgumentException ex) {
                    }
                }
                if (akType != null)
                    return ExpressionTypes.newType(akType, 
                                                   sqlType.getPrecision(), sqlType.getScale());
                else
                    return null;
            }
        default:
            return null;
        }
    }

    public static DataTypeDescriptor fromExpressionType(ExpressionType resultType) {
        return fromExpressionType (resultType, true);
    }

    public static DataTypeDescriptor fromExpressionType(ExpressionType resultType, boolean isNullable) {
        switch (resultType.getType()) {
        case BOOL:
            return new DataTypeDescriptor(TypeId.BOOLEAN_ID, isNullable);
        case INT:
            return new DataTypeDescriptor(TypeId.INTEGER_ID, isNullable);
        case LONG:
            return new DataTypeDescriptor(TypeId.BIGINT_ID, isNullable);
        case DOUBLE:
            return new DataTypeDescriptor(TypeId.DOUBLE_ID, isNullable);
        case FLOAT:
            return new DataTypeDescriptor(TypeId.REAL_ID, isNullable);
        case U_INT:
            return new DataTypeDescriptor(TypeId.INTEGER_UNSIGNED_ID, isNullable);
        case U_BIGINT:
            return new DataTypeDescriptor(TypeId.BIGINT_UNSIGNED_ID, isNullable);
        case U_FLOAT:
            return new DataTypeDescriptor(TypeId.REAL_UNSIGNED_ID, isNullable);
        case U_DOUBLE:
            return new DataTypeDescriptor(TypeId.DOUBLE_UNSIGNED_ID, isNullable);
        case DATE:
            return new DataTypeDescriptor(TypeId.DATE_ID, isNullable);
        case TIME:
            return new DataTypeDescriptor(TypeId.TIME_ID, isNullable);
        case TIMESTAMP:
            return new DataTypeDescriptor(TypeId.TIMESTAMP_ID, isNullable);
        case VARCHAR:
            {
                DataTypeDescriptor dtd = new DataTypeDescriptor(TypeId.VARCHAR_ID, isNullable,
                                                                resultType.getPrecision());
                if (resultType.getCharacterAttributes() != null)
                    dtd = new DataTypeDescriptor(dtd, resultType.getCharacterAttributes());
                return dtd;
            }
        case DECIMAL:
            {
                int precision = resultType.getPrecision();
                int scale = resultType.getScale();
                return new DataTypeDescriptor(TypeId.DECIMAL_ID, precision, scale, isNullable,
                                              DataTypeDescriptor.computeMaxWidth(precision, scale));
            }
        case TEXT:
            return new DataTypeDescriptor(TypeId.LONGVARCHAR_ID, isNullable);
        case VARBINARY:
            return new DataTypeDescriptor(TypeId.VARBIT_ID, isNullable);
        case NULL:
            return null;
        case DATETIME:
            return new DataTypeDescriptor(TypeId.DATETIME_ID, isNullable);
        case YEAR:
            return new DataTypeDescriptor(TypeId.YEAR_ID, isNullable);
        case INTERVAL_MILLIS:
            return new DataTypeDescriptor(TypeId.INTERVAL_SECOND_ID, isNullable);
        case INTERVAL_MONTH:
            return new DataTypeDescriptor(TypeId.INTERVAL_MONTH_ID, isNullable);
        default:
            try {
                return new DataTypeDescriptor(TypeId.getUserDefinedTypeId(null,
                                                                          resultType.getType().name(),
                                                                          null),
                                              isNullable);
            }
            catch (StandardException ex) {
                throw new AkibanInternalException("Cannot make type for " + resultType,
                                                  ex);
            }
        }
    }

    public static ExpressionType castType(ExpressionType fromType, AkType toType,
                                          DataTypeDescriptor sqlType) {
        switch (toType) {
        case BOOL:
            return ExpressionTypes.BOOL;
        case INT:
            return ExpressionTypes.INT;
        case YEAR:
            return ExpressionTypes.YEAR;
        case LONG:
            return ExpressionTypes.LONG;
        case DOUBLE:
            return ExpressionTypes.DOUBLE;
        case FLOAT:
            return ExpressionTypes.FLOAT;
        case U_INT:
            return ExpressionTypes.U_INT;
        case U_BIGINT:
            return ExpressionTypes.U_BIGINT;
        case U_FLOAT:
            return ExpressionTypes.U_FLOAT;
        case U_DOUBLE:
            return ExpressionTypes.U_DOUBLE;
        case DATE:
            return ExpressionTypes.DATE;
        case TIME:
            return ExpressionTypes.TIME;
        case DATETIME:
            return ExpressionTypes.DATETIME;
        case TIMESTAMP:
            return ExpressionTypes.TIMESTAMP;
        case TEXT:
            return ExpressionTypes.TEXT;
        case VARCHAR:
            if (sqlType != null)
                return ExpressionTypes.varchar(sqlType.getMaximumWidth(),
                                               sqlType.getCharacterAttributes());
            else
                return ExpressionTypes.varchar(TypeId.VARCHAR_ID.getMaximumMaximumWidth(), null);
        case VARBINARY:
            if (sqlType != null)
                return ExpressionTypes.varbinary(sqlType.getMaximumWidth());
            else
                return ExpressionTypes.varbinary(TypeId.VARBIT_ID.getMaximumMaximumWidth());
        case DECIMAL:
            if (sqlType != null) {
                TypeId typeId = sqlType.getTypeId();
                if (typeId.isNumericTypeId())
                    return ExpressionTypes.decimal(sqlType.getPrecision(),
                                                   sqlType.getScale());
                else
                    return ExpressionTypes.decimal(typeId.getMaximumPrecision(),
                                                   typeId.getMaximumScale());
            }
            else
                return ExpressionTypes.decimal(TypeId.DECIMAL_ID.getMaximumPrecision(),
                                               TypeId.DECIMAL_ID.getMaximumScale());
        case INTERVAL_MILLIS:
            return ExpressionTypes.INTERVAL_MILLIS;
        case INTERVAL_MONTH:
            return ExpressionTypes.INTERVAL_MONTH;
        default:
            return ExpressionTypes.newType(toType, 0, 0);
        }
    }

    public static TInstance toTInstance(DataTypeDescriptor sqlType) {
        TInstance tInstance;
        if (sqlType == null) return null;
        TypeId typeId = sqlType.getTypeId();
        typeIdSwitch:
        switch (typeId.getTypeFormatId()) {
        case TypeId.FormatIds.INTERVAL_DAY_SECOND_ID:
            tInstance = AkInterval.SECONDS.tInstanceFrom(sqlType);
            break;
        case TypeId.FormatIds.INTERVAL_YEAR_MONTH_ID:
            tInstance = AkInterval.MONTHS.tInstanceFrom(sqlType);
            break;
        case TypeId.FormatIds.BIT_TYPE_ID:
            tInstance = MBinary.VARBINARY.instance(sqlType.getMaximumWidth(), sqlType.isNullable());
            break;
        case TypeId.FormatIds.BOOLEAN_TYPE_ID:
            tInstance = AkBool.INSTANCE.instance(sqlType.isNullable());
            break;
        case TypeId.FormatIds.CHAR_TYPE_ID:
            tInstance = charTInstance(sqlType, MString.VARCHAR);
            break;
        case TypeId.FormatIds.DATE_TYPE_ID:
            tInstance = MDatetimes.DATE.instance(sqlType.isNullable());
            break;
        case TypeId.FormatIds.DECIMAL_TYPE_ID:
        case TypeId.FormatIds.NUMERIC_TYPE_ID:
            tInstance = MNumeric.DECIMAL.instance(sqlType.getPrecision(), sqlType.getScale(), sqlType.isNullable());
            break;
        case TypeId.FormatIds.DOUBLE_TYPE_ID:
            if (typeId.isUnsigned()) {
                tInstance = MApproximateNumber.DOUBLE_UNSIGNED.instance(sqlType.isNullable());
            }
            else {
                tInstance = MApproximateNumber.DOUBLE.instance(sqlType.isNullable());
            }
            break;
        case TypeId.FormatIds.INT_TYPE_ID:
            if (typeId.isUnsigned()) {
                tInstance = MNumeric.INT_UNSIGNED.instance(sqlType.isNullable());
            }
            else {
                tInstance = MNumeric.INT.instance(sqlType.isNullable());
            }
            break;
        case TypeId.FormatIds.LONGINT_TYPE_ID:
            if (typeId.isUnsigned()) {
                tInstance = MNumeric.BIGINT_UNSIGNED.instance(sqlType.isNullable());
            }
            else {
                tInstance = MNumeric.BIGINT.instance(sqlType.isNullable());
            }
            break;
        case TypeId.FormatIds.LONGVARBIT_TYPE_ID:
            tInstance = charTInstance(sqlType, MString.TEXT);
            break;
        case TypeId.FormatIds.LONGVARCHAR_TYPE_ID:
            tInstance = charTInstance(sqlType, MString.TEXT);
            break;
        case TypeId.FormatIds.REAL_TYPE_ID:
            if (typeId.isUnsigned()) {
                tInstance = MApproximateNumber.FLOAT_UNSIGNED.instance(sqlType.isNullable());
            }
            else {
                tInstance = MApproximateNumber.FLOAT.instance(sqlType.isNullable());
            }
            break;
        case TypeId.FormatIds.SMALLINT_TYPE_ID:
            if (typeId == TypeId.YEAR_ID) {
                tInstance = MDatetimes.YEAR.instance(sqlType.isNullable());
            }
            else if (typeId.isUnsigned()) {
                tInstance = MNumeric.SMALLINT_UNSIGNED.instance(sqlType.isNullable());
            }
            else {
                tInstance = MNumeric.SMALLINT.instance(sqlType.isNullable());
            }
            break;
        case TypeId.FormatIds.TIME_TYPE_ID:
            tInstance = MDatetimes.TIME.instance(sqlType.isNullable());
            break;
        case TypeId.FormatIds.TIMESTAMP_TYPE_ID:
            if (typeId == TypeId.DATETIME_ID) {
                tInstance = MDatetimes.DATETIME.instance(sqlType.isNullable());
            }
            else {
                tInstance = MDatetimes.TIMESTAMP.instance(sqlType.isNullable());
            }
            break;
        case TypeId.FormatIds.TINYINT_TYPE_ID:
            if (typeId.isUnsigned()) {
                tInstance = MNumeric.TINYINT_UNSIGNED.instance(sqlType.isNullable());
            }
            else {
                tInstance = MNumeric.TINYINT.instance(sqlType.isNullable());
            }
            break;
        case TypeId.FormatIds.VARBIT_TYPE_ID:
            tInstance = MBinary.VARBINARY.instance(sqlType.getMaximumWidth(), sqlType.isNullable());
            break;
        case TypeId.FormatIds.BLOB_TYPE_ID:
            tInstance = MBinary.VARBINARY.instance(sqlType.getMaximumWidth(), sqlType.isNullable());
            break;
        case TypeId.FormatIds.VARCHAR_TYPE_ID:
            tInstance = charTInstance(sqlType, MString.VARCHAR);
            break;
        case TypeId.FormatIds.CLOB_TYPE_ID:
            tInstance = charTInstance(sqlType, MString.TEXT);
            break;
        case TypeId.FormatIds.XML_TYPE_ID:
            tInstance = charTInstance(sqlType, MString.TEXT);
            break;
        case TypeId.FormatIds.ROW_MULTISET_TYPE_ID_IMPL:
            {
                TypeId.RowMultiSetTypeId rmsTypeId = 
                    (TypeId.RowMultiSetTypeId)typeId;
                String[] columnNames = rmsTypeId.getColumnNames();
                DataTypeDescriptor[] columnTypes = rmsTypeId.getColumnTypes();
                List<AkResultSet.Column> columns = new ArrayList<>(columnNames.length);
                for (int i = 0; i < columnNames.length; i++) {
                    columns.add(new AkResultSet.Column(columnNames[i],
                                                       toTInstance(columnTypes[i])));
                }
                tInstance = AkResultSet.INSTANCE.instance(columns);
            }
            break;
        case TypeId.FormatIds.USERDEFINED_TYPE_ID:
            {
                String name = typeId.getSQLTypeName();
                for (Type aisType : Types.types()) {
                    if (aisType.name().equalsIgnoreCase(name)) {
                        tInstance = Column.generateTInstance(null, aisType, null, null, false);
                        break typeIdSwitch;
                    }
                }
            }
            /* falls through */
        default:
            throw new UnknownDataTypeException(sqlType.toString());
        }
        return tInstance;
    }

    private static TInstance charTInstance(DataTypeDescriptor type, TString tClass) {
        CharacterTypeAttributes typeAttributes = type.getCharacterAttributes();
        int charsetId = (typeAttributes == null)
                ? StringFactory.DEFAULT_CHARSET.ordinal()
                : Charset.of(typeAttributes.getCharacterSet()).ordinal();
        return tClass.instance(type.getMaximumWidth(), charsetId, type.isNullable());
    }

    private TypesTranslation() {}
}

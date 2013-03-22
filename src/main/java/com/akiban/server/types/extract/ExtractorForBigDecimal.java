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

package com.akiban.server.types.extract;

import com.akiban.server.error.InvalidCharToNumException;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;

import java.math.BigDecimal;

final class ExtractorForBigDecimal extends ObjectExtractor<BigDecimal> {
    @Override
    public BigDecimal getObject(ValueSource source) {
        try
        {
            AkType type = source.getConversionType();
            switch (type) {
            case DECIMAL:   return source.getDecimal();
            case TEXT:      return new BigDecimal(source.getText());
            case VARCHAR:   return new BigDecimal(source.getString());
            case LONG:      return BigDecimal.valueOf(source.getLong());
            case INT:       return BigDecimal.valueOf(source.getInt());
            case U_INT:     return BigDecimal.valueOf(source.getUInt());
            case FLOAT:     return BigDecimal.valueOf(source.getFloat());
            case U_FLOAT:   return BigDecimal.valueOf(source.getUFloat());
            case DOUBLE:    return BigDecimal.valueOf(source.getDouble());
            case U_DOUBLE:  return BigDecimal.valueOf(source.getUDouble());
            case INTERVAL_MILLIS:  return new BigDecimal(source.getInterval_Millis());
            case INTERVAL_MONTH:   return new BigDecimal(source.getInterval_Month());
            case U_BIGINT:  return new BigDecimal(source.getUBigInt());
            case DATE:      return new BigDecimal(source.getDate());
            case DATETIME:  return new BigDecimal(source.getDateTime());
            case TIME:      return new BigDecimal(source.getTime());
            case TIMESTAMP: return new BigDecimal(source.getTimestamp());
            case YEAR:      return new BigDecimal(source.getYear());
            default: throw unsupportedConversion(type);
            }
        }
        catch (NumberFormatException e)
        {
            throw new InvalidCharToNumException(e.getMessage());
        }
    }

    @Override
    public BigDecimal getObject(String string) {
        return new BigDecimal(string);
    }

    ExtractorForBigDecimal() {
        super(AkType.DECIMAL);
    }
}

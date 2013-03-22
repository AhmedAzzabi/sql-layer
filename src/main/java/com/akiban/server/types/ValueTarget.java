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

package com.akiban.server.types;

import com.akiban.qp.operator.Cursor;
import com.akiban.util.ByteSource;

import java.math.BigDecimal;
import java.math.BigInteger;

public interface ValueTarget {
    void putNull();
    void putDate(long value);
    void putDateTime(long value);
    void putDecimal(BigDecimal value);
    void putDouble(double value);
    void putFloat(float value);
    void putInt(long value);
    void putLong(long value);
    void putString(String value);
    void putText(String value);
    void putTime(long value);
    void putTimestamp(long value);
    void putInterval_Millis(long value);
    void putInterval_Month(long value);
    void putUBigInt(BigInteger value);
    void putUDouble(double value);
    void putUFloat(float value);
    void putUInt(long value);
    void putVarBinary(ByteSource value);
    void putYear(long value);
    void putBool(boolean value);
    void putResultSet(Cursor value);
    AkType getConversionType();
}

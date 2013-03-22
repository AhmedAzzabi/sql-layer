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

package com.akiban.server.types.conversion;

import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.ValueTarget;
import com.akiban.server.types.extract.BooleanExtractor;
import com.akiban.server.types.extract.Extractors;

public final class ConverterForBool extends AbstractConverter {
    public static final ConverterForBool INSTANCE = new ConverterForBool();

    @Override
    protected void doConvert(ValueSource source, ValueTarget target) {
        target.putBool(extractor.getBoolean(source, null));
    }

    @Override
    protected AkType targetConversionType() {
        return AkType.BOOL;
    }

    private final BooleanExtractor extractor = Extractors.getBooleanExtractor();
}

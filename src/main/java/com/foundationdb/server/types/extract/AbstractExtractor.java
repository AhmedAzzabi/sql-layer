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

package com.foundationdb.server.types.extract;

import com.foundationdb.server.error.InconvertibleTypesException;
import com.foundationdb.server.error.InvalidOperationException;
import com.foundationdb.server.types.AkType;
import com.foundationdb.util.ArgumentValidation;

public abstract class AbstractExtractor {

    public final AkType targetConversionType() {
        return targetConversionType;
    }
    // for use by subclasses

    protected InvalidOperationException unsupportedConversion(AkType sourceType) {
        throw new InconvertibleTypesException(sourceType, targetConversionType());
    }

    @Override
    public String toString() {
        return '(' + targetConversionType.name() + " Extractor)";
    }

    // for use in this package

    AbstractExtractor(AkType targetConversionType) {
        ArgumentValidation.notNull("target conversion type", targetConversionType);
        this.targetConversionType = targetConversionType;
    }

    private final AkType targetConversionType;
}

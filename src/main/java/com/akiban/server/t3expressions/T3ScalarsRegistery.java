/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.t3expressions;

import com.akiban.server.types3.TCast;
import com.akiban.server.types3.TClass;

import java.util.List;

public interface T3ScalarsRegistery {
    OverladResolutionResult get(String name, List<? extends TClass> inputClasses);
    TCast cast(TClass source, TClass target);

    /**
     * Returns the common of the two types. For either argument, a <tt>null</tt> value is interpreted as any type.
     * @param one the first type class
     * @param two the other type class
     * @return a wrapper that represents the common class, no common class, or <tt>ANY</tt> (the latter only if both
     * inputs are <tt>null</tt>)
     */
    TClassPossibility commonTClass(TClass one, TClass two);
}

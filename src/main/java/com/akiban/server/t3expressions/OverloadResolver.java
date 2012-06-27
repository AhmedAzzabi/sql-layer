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

import com.akiban.server.error.NoSuchFunctionException;
import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.types3.TCast;
import com.akiban.server.types3.TClass;
import com.akiban.server.types3.TInputSet;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.TPreptimeValue;
import com.akiban.server.types3.texpressions.TValidatedOverload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public final class OverloadResolver {
    OverloadResult get(String name, List<? extends TPreptimeValue> inputs) {
        Collection<? extends TValidatedOverload> namedOverloads = null;//registry.get(name);
        if (namedOverloads.isEmpty())
            throw new NoSuchFunctionException(name);

        OverloadResult result = null;

        if (namedOverloads.size() == 1) {
            result = defaultResolution(inputs, namedOverloads);
        }
        if (result == null) {
            result = inputBasedResolution(inputs, namedOverloads);
        }
        return result;
    }

    private OverloadResult inputBasedResolution(List<? extends TPreptimeValue> inputs,
                                                Collection<? extends TValidatedOverload> namedOverloads) {
        // Input-based resolution
        List<TValidatedOverload> candidates = new ArrayList<TValidatedOverload>(namedOverloads.size());
        for (TValidatedOverload overload : namedOverloads) {
            if ( isCandidate(overload, inputs)) {
                candidates.add(overload);
            }
        }
        // TODO find the most specific
        return null;
    }

    private OverloadResult defaultResolution(List<? extends TPreptimeValue> inputs,
                                                 Collection<? extends TValidatedOverload> namedOverloads) {
        TValidatedOverload resolvedOverload;TClass pickingClass;
        int nInputs = inputs.size();
        resolvedOverload = namedOverloads.iterator().next();
        // throwing an exception here isn't strictly required, but it gives the user a more specific error
        if (!resolvedOverload.coversNInputs(nInputs))
            throw new WrongExpressionArityException(resolvedOverload.positionalInputs(), nInputs);
        pickingClass = pickingClass(resolvedOverload, inputs);
        return new OverloadResult(resolvedOverload, pickingClass);
    }

    public boolean isCandidate(TValidatedOverload overload, List<? extends TPreptimeValue> inputs) {
        if (!overload.coversNInputs(inputs.size()))
            return false;
        for (int i = 0, inputsSize = inputs.size(); i < inputsSize; i++) {
            TInstance inputInstance = inputs.get(i).instance();
            // allow this input if...
            // ... input's type it NULL or ?
            if (inputInstance == null)       // input
                continue;
            // ... input set takes ANY
            TInputSet inputSet = overload.inputSetAt(i);
            if (inputSet.targetType() == null)
                continue;
            // ... input can be strongly cast to input set
            TCast cast = registry.cast(inputInstance.typeClass(), inputSet.targetType());
            if (cast != null && cast.isAutomatic())
                continue;
            // This input precludes the use of the overload
            return false;
        }
        // no inputs have precluded this overload
        return true;
    }

    private TClass pickingClass(TValidatedOverload overload,  List<? extends TPreptimeValue> inputs) {
        TInputSet pickingSet = overload.pickingInputSet();
        if (pickingSet == null)
            return null;
        TClass common = null;
        for (int i = pickingSet.firstPosition(); i >=0 ; i = pickingSet.nextPosition(i)) {
            common = registry.commonTClass(common, inputs.get(i).instance().typeClass()).get();
            if (common == T3ScalarsRegistry.NO_COMMON)
                return common;
        }
        if (pickingSet.coversRemaining()) {
            for (int i = overload.firstVarargInput(), last = inputs.size(); i < last; ++i) {
                common = registry.commonTClass(common, inputs.get(i).instance().typeClass()).get();
                if (common == T3ScalarsRegistry.NO_COMMON)
                    return common;
            }
        }
        return common;
    }


    private T3ScalarsRegistry registry;

    public static class OverloadResult {
        private TValidatedOverload overload;
        private TClass pickingClass;

        public OverloadResult(TValidatedOverload overload, TClass pickingClass) {
            this.overload = overload;
            this.pickingClass = pickingClass;
        }
    }
}

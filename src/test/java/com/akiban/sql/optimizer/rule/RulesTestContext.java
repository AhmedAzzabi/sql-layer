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

package com.akiban.sql.optimizer.rule;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.server.aggregation.DummyAggregatorRegistry;
import com.akiban.server.service.functions.FunctionsRegistryImpl;

import java.util.List;

public class RulesTestContext extends SchemaRulesContext
{
    public RulesTestContext(AkibanInformationSchema ais, List<BaseRule> rules) {
        super(ais, 
              new FunctionsRegistryImpl(), new DummyAggregatorRegistry(),
              rules);
        RulesTestHelper.ensureRowDefs(ais);
    }
}

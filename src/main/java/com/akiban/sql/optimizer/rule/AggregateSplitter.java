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

import com.akiban.sql.optimizer.rule.AggregateMapper.AggregateSourceFinder;

import com.akiban.server.error.UnsupportedSQLException;

import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.sql.types.TypeId;

import com.akiban.sql.optimizer.plan.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Aggregates need to be split into a Project and the aggregation
 * proper, so that the project can go into a nested loop Map. */
public class AggregateSplitter extends BaseRule
{
    private static final Logger logger = LoggerFactory.getLogger(AggregateSplitter.class);

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void apply(PlanContext plan) {
        List<AggregateSource> sources = new AggregateSourceFinder().find(plan.getPlan());
        for (AggregateSource source : sources) {
            split(source);
        }
    }

    protected void split(AggregateSource source) {
        assert !source.isProjectSplitOff();
        // TODO: This is only to support the old Count_Default operator while we have it.
        if (!source.hasGroupBy() && source.getAggregates().size() == 1) {
            AggregateFunctionExpression aggr1 = source.getAggregates().get(0);
            if ((aggr1.getOperand() == null) &&
                (aggr1.getFunction().equals("COUNT"))) {
                return;
            }
        }
        // Another way to do this would be to have a different class
        // for AggregateSource in the split-off state. Doing that
        // would require replacing expression references to the old
        // one as a ColumnSource.
        List<ExpressionNode> fields = source.splitOffProject();
        PlanNode input = source.getInput();
        source.replaceInput(input, new Project(input, fields));
    }

}

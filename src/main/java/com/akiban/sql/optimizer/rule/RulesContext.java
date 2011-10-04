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

import com.akiban.sql.optimizer.plan.PlanContext;

import org.slf4j.Logger;

import java.util.List;

/** The context / owner of a {@link PlanContext}, shared among several of them. */
public class RulesContext
{
    // TODO: Need more much sophisticated invocation mechanism.
    private List<BaseRule> rules;

    public RulesContext(List<BaseRule> rules) {
        this.rules = rules;
    }

    public void applyRules(PlanContext plan) {
        boolean logged = false;
        for (BaseRule rule : rules) {
            Logger logger = rule.getLogger();
            boolean debug = logger.isDebugEnabled();
            if (debug && !logged) {
                logger.debug("Before {}:\n{}", rule.getName(), plan.getPlan());
            }
            beginRule(rule);
            try {
                rule.apply(plan);
            }
            finally {
                endRule(rule);
            }
            if (debug) {
                logger.debug("After {}:\n{}", rule.getName(), plan.getPlan());
            }
            logged = debug;
        }
    }

    /** Extend this to implement tracing, etc. */
    public void beginRule(BaseRule rule) {
    }
    public void endRule(BaseRule rule) {
    }
}

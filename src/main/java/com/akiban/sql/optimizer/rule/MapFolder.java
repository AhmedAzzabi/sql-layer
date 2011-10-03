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

import com.akiban.sql.optimizer.plan.*;

import com.akiban.sql.optimizer.plan.JoinNode.JoinType;

import com.akiban.server.error.UnsupportedSQLException;

import java.util.*;

/** Take a map join node and push enough into the inner loop that the
 * bindings can be evaluated properly.
 */
public class MapFolder extends BaseRule
{
    static class MapJoinsFinder implements PlanVisitor, ExpressionVisitor {
        List<MapJoin> result = new ArrayList<MapJoin>();

        public List<MapJoin> find(PlanNode root) {
            root.accept(this);
            return result;
        }

        @Override
        public boolean visitEnter(PlanNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(PlanNode n) {
            return true;
        }

        @Override
        public boolean visit(PlanNode n) {
            if (n instanceof MapJoin) {
              result.add((MapJoin)n);
            }
            return true;
        }

        @Override
        public boolean visitEnter(ExpressionNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(ExpressionNode n) {
            return true;
        }

        @Override
        public boolean visit(ExpressionNode n) {
            return true;
        }
    }

    @Override
    public void apply(PlanContext planContext) {
        BaseQuery query = (BaseQuery)planContext.getPlan();
        List<MapJoin> maps = new MapJoinsFinder().find(query);
        for (MapJoin map : maps)
            fold(map);
    }

    protected void fold(MapJoin map) {
        if (map.getJoinType() != JoinType.INNER)
            throw new UnsupportedSQLException("non-INNER complex join", null);

        PlanWithInput parent = map;
        PlanNode child;
        do {
          child = parent;
          parent = child.getOutput();
        } while (!((parent instanceof ResultSet) ||
                   (parent instanceof AggregateSource) ||
                   (child instanceof Project)));
        if (child != map) {
          map.getOutput().replaceInput(map, map.getInner());
          parent.replaceInput(child, map);
          map.setInner(child);
        }

        map.setJoinType(null);  // No longer special.
    }

}

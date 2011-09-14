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

package com.akiban.sql.optimizer.plan;

/** A join to an actual table. */
public class TableSource extends BaseJoinable implements ColumnSource
{
    private TableNode table;
    private TableGroup group;
    private TableGroupJoin parentJoin;
    // TODO: Add conditions, correlation name?, ...

    public TableSource(TableNode table) {
        this.table = table;
        table.addUse(this);
    }

    public TableNode getTable() {
        return table;
    }

    public TableGroup getGroup() {
        return group;
    }
    protected void setGroup(TableGroup group) {
        this.group = group;
    }

    public TableGroupJoin getParentJoin() {
        return parentJoin;
    }
    protected void setParentJoin(TableGroupJoin parentJoin) {
        this.parentJoin = parentJoin;
        this.group = parentJoin.getGroup();
    }

    public TableSource getParentTable() {
        if (parentJoin == null)
            return null;
        else
            return parentJoin.getParent();
    }

    @Override
    public String getName() {
        return table.getTable().getName().toString();
    }

    @Override
    public boolean isTable() {
        return true;
    }

    @Override
    public boolean accept(PlanVisitor v) {
        return v.visit(this);
    }
    
    @Override
    public String summaryString() {
        StringBuilder str = new StringBuilder(super.summaryString());
        str.append("(");
        str.append(table.toString());
        if (parentJoin != null) {
            str.append(" - ");
            str.append(parentJoin);
        }
        else if (group != null) {
            str.append(" - ");
            str.append(group);
        }
        str.append(")");
        return str.toString();
    }

    @Override
    protected boolean maintainInDuplicateMap() {
        return true;
    }

    @Override
    protected void deepCopy(DuplicateMap map) {
        super.deepCopy(map);
        table = map.duplicate(table);
        table.addUse(this);
    }

}

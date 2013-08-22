/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
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

package com.foundationdb.sql.optimizer.rule;

import com.foundationdb.sql.optimizer.plan.*;

import com.foundationdb.ais.model.Column;
import com.foundationdb.ais.model.IndexColumn;
import com.foundationdb.ais.model.Join;
import com.foundationdb.ais.model.JoinColumn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Identify queries that are susceptible to the Halloween problem.
 * <ul>
 * <li>Updating a primary or grouping foreign key, 
 * which can change hkeys and so group navigation.</li>
 * <li>Updating a field of an index that is scanned.</li>
 * <li>A <em>second</em> access to the target table.</li>
 * </ul>
 */
public class HalloweenRecognizer extends BaseRule
{
    private static final Logger logger = LoggerFactory.getLogger(HalloweenRecognizer.class);

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void apply(PlanContext plan) {
        if (plan.getPlan() instanceof DMLStatement) {
            DMLStatement stmt = (DMLStatement)plan.getPlan();
            TableNode targetTable = stmt.getTargetTable();
            boolean requireStepIsolation = false;
            Set<Column> updateColumns = new HashSet<>();
            
            if (stmt.getType() == BaseUpdateStatement.StatementType.UPDATE) {
                BasePlanWithInput node = stmt;
                do {
                    node = (BasePlanWithInput) node.getInput();
                } while (node != null && !(node instanceof UpdateStatement));
                assert node != null;
                
                UpdateStatement updateStmt = (UpdateStatement)node;
                
                update: { 
                    for (UpdateStatement.UpdateColumn updateColumn : updateStmt.getUpdateColumns()) {
                        updateColumns.add(updateColumn.getColumn());
                    }
                    for (Column pkColumn : targetTable.getTable().getPrimaryKeyIncludingInternal().getColumns()) {
                        if (updateColumns.contains(pkColumn)) {
                            requireStepIsolation = true;
                            break update;
                        }
                    }
                    Join parentJoin = targetTable.getTable().getParentJoin();
                    if (parentJoin != null) {
                        for (JoinColumn joinColumn : parentJoin.getJoinColumns()) {
                            if (updateColumns.contains(joinColumn.getChild())) {
                                requireStepIsolation = true;
                                break update;
                            }
                        }
                    }
                }
            }
            if (!requireStepIsolation) {
                Checker checker = new Checker(targetTable,
                                              (stmt.getType() == BaseUpdateStatement.StatementType.INSERT) ? 0 : 1,
                                              updateColumns);
                requireStepIsolation = checker.check(stmt.getQuery());
            }
            stmt.setRequireStepIsolation(requireStepIsolation);
        }
    }

    static class Checker implements PlanVisitor, ExpressionVisitor {
        private TableNode targetTable;
        private int targetMaxUses;
        private Set<Column> updateColumns;
        private boolean requireStepIsolation;

        public Checker(TableNode targetTable, int targetMaxUses, Set<Column> updateColumns) {
            this.targetTable = targetTable;
            this.targetMaxUses = targetMaxUses;
            this.updateColumns = updateColumns;
        }

        public boolean check(PlanNode root) {
            requireStepIsolation = false;
            root.accept(this);
            return requireStepIsolation;
        }

        private void indexScan(IndexScan scan) {
            if (scan instanceof SingleIndexScan) {
                SingleIndexScan single = (SingleIndexScan)scan;
                if (single.isCovering()) { // Non-covering loads via XxxLookup.
                    for (TableSource table : single.getTables()) {
                        if (table.getTable() == targetTable) {
                            targetMaxUses--;
                            if (targetMaxUses < 0) {
                                requireStepIsolation = true;
                            }
                            break;
                        }
                    }
                }
                if (updateColumns != null) {
                    for (IndexColumn indexColumn : single.getIndex().getAllColumns()) {
                        if (updateColumns.contains(indexColumn.getColumn())) {
                            requireStepIsolation = true;
                            break;
                        }
                    }
                }
            }
            else if (scan instanceof MultiIndexIntersectScan) {
                MultiIndexIntersectScan multi = (MultiIndexIntersectScan)scan;
                indexScan(multi.getOutputIndexScan());
                indexScan(multi.getSelectorIndexScan());
            }
        }

        @Override
        public boolean visitEnter(PlanNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(PlanNode n) {
            return !requireStepIsolation;
        }

        @Override
        public boolean visit(PlanNode n) {
            if (n instanceof IndexScan) {
                indexScan((IndexScan)n);
            }
            else if (n instanceof TableLoader) {
                for (TableSource table : ((TableLoader)n).getTables()) {
                    if (table.getTable() == targetTable) {
                        targetMaxUses--;
                        if (targetMaxUses < 0) {
                            requireStepIsolation = true;
                            break;
                        }
                    }
                }
            }
            return !requireStepIsolation;
        }

        @Override
        public boolean visitEnter(ExpressionNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(ExpressionNode n) {
            return !requireStepIsolation;
        }

        @Override
        public boolean visit(ExpressionNode n) {
            return !requireStepIsolation;
        }
    }
}

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

package com.akiban.qp.operator;

import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.explain.*;
import com.akiban.server.explain.std.NestedLoopsExplainer;
import com.akiban.util.ArgumentValidation;
import com.akiban.util.ShareHolder;
import com.akiban.util.tap.InOutTap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**

 <h1>Overview</h1>

 Map_NestedLoops implements a mapping using a nested-loop algorithm. The left input operator (outer loop)
 provides a stream of input rows. The right input operator (inner loop) binds this row, and 
 its output, combined across all input rows, forms the Map_NestedLoops output stream. 
 
 <h1>Arguments</h1>

 <ul>

 <li><b>Operator outerInputOperator:</b> Provides stream of input.

 <li><b>Operator innerInputOperator:</b> Provides Map_NestedLoops output.

 <li><b>int inputBindingPosition:</b> Position of inner loop row in query context.

 </ul>

 <h1>Behavior</h1>

 The outer input operator provides a stream of rows. Each is bound in turn to the query context, in the 
 position specified by inputBindingPosition. For each input row, the inner operator binds the row and executes,
 yielding a stream of output rows. The concatenation of these streams comprises the output from Map_NestedLoops.
 
 The inner operator is used multiple times, once for each input row. On each iteration, the input row is bound
 to the query context, the inner cursor is opened, and then the inner cursor is consumed.

 <h1>Output</h1>

 The concatenation of streams from the inner operator.

 <h1>Assumptions</h1>

 None.

 <h1>Performance</h1>

 Product_NestedLoops does no IO.

 <h1>Memory Requirements</h1>

 A single row from the outer loops is stored at all times.
 
 */


class Map_NestedLoops extends Operator
{
    // Operator interface

    @Override
    protected Cursor cursor(QueryContext context, QueryBindingsCursor bindingsCursor)
    {
        return new Execution(context, bindingsCursor);
    }

    @Override
    public void findDerivedTypes(Set<RowType> derivedTypes)
    {
        innerInputOperator.findDerivedTypes(derivedTypes);
        outerInputOperator.findDerivedTypes(derivedTypes);
    }

    @Override
    public List<Operator> getInputOperators()
    {
        List<Operator> result = new ArrayList<>(2);
        result.add(outerInputOperator);
        result.add(innerInputOperator);
        return result;
    }

    @Override
    public String describePlan()
    {
        return String.format("%s\n%s", describePlan(outerInputOperator), describePlan(innerInputOperator));
    }

    // Project_Default interface

    public Map_NestedLoops(Operator outerInputOperator,
                           Operator innerInputOperator,
                           int inputBindingPosition)
    {
        ArgumentValidation.notNull("outerInputOperator", outerInputOperator);
        ArgumentValidation.notNull("innerInputOperator", innerInputOperator);
        ArgumentValidation.isGTE("inputBindingPosition", inputBindingPosition, 0);
        this.outerInputOperator = outerInputOperator;
        this.innerInputOperator = innerInputOperator;
        this.inputBindingPosition = inputBindingPosition;
    }

    // Class state

    private static final InOutTap TAP_OPEN = OPERATOR_TAP.createSubsidiaryTap("operator: Map_NestedLoops open");
    private static final InOutTap TAP_NEXT = OPERATOR_TAP.createSubsidiaryTap("operator: Map_NestedLoops next");
    private static final Logger LOG = LoggerFactory.getLogger(Map_NestedLoops.class);

    // Object state

    private final Operator outerInputOperator;
    private final Operator innerInputOperator;
    private final int inputBindingPosition;

    @Override
    public CompoundExplainer getExplainer(ExplainContext context)
    {
        CompoundExplainer ex = new NestedLoopsExplainer(getName(), innerInputOperator, outerInputOperator, null, null, context);
        ex.addAttribute(Label.BINDING_POSITION, PrimitiveExplainer.getInstance(inputBindingPosition));
        if (context.hasExtraInfo(this))
            ex.get().putAll(context.getExtraInfo(this).get());
        return ex;
    }

    // Inner classes

    private class Execution extends OperatorExecutionBase implements Cursor
    {
        // Cursor interface

        @Override
        public void open()
        {
            TAP_OPEN.in();
            try {
                CursorLifecycle.checkIdle(this);
                this.outerInput.open();
                this.closed = false;
            } finally {
                TAP_OPEN.out();
            }
        }

        @Override
        public Row next()
        {
            if (TAP_NEXT_ENABLED) {
                TAP_NEXT.in();
            }
            try {
                if (CURSOR_LIFECYCLE_ENABLED) {
                    CursorLifecycle.checkIdleOrActive(this);
                }
                checkQueryCancelation();
                Row outputRow = null;
                while (!closed && outputRow == null) {
                    outputRow = nextOutputRow();
                    if (outputRow == null) {
                        Row row = outerInput.next();
                        if (row == null) {
                            close();
                        } else {
                            outerRow.hold(row);
                            if (LOG_EXECUTION) {
                                LOG.debug("Map_NestedLoops: restart inner loop using current branch row");
                            }
                            startNewInnerLoop(row);
                        }
                    }
                }
                if (LOG_EXECUTION) {
                    LOG.debug("Map_NestedLoops: yield {}", outputRow);
                }
                return outputRow;
            } finally {
                if (TAP_NEXT_ENABLED) {
                    TAP_NEXT.out();
                }
            }
        }

        @Override
        public void close()
        {
            CursorLifecycle.checkIdleOrActive(this);
            if (!closed) {
                innerInput.close();
                closeOuter();
                closed = true;
            }
        }

        @Override
        public void destroy()
        {
            close();
            innerInput.destroy();
            outerInput.destroy();
        }

        @Override
        public boolean isIdle()
        {
            return outerInput.isIdle();
        }

        @Override
        public boolean isActive()
        {
            return outerInput.isActive();
        }

        @Override
        public boolean isDestroyed()
        {
            return outerInput.isDestroyed();
        }

        // Execution interface

        Execution(QueryContext context, QueryBindingsCursor bindingsCursor)
        {
            super(context);
            this.outerInput = outerInputOperator.cursor(context, bindingsCursor);
            this.innerInput = innerInputOperator.cursor(context, bindingsCursor);
        }

        // For use by this class

        private Row nextOutputRow()
        {
            Row outputRow = null;
            if (outerRow.isHolding()) {
                Row innerRow = innerInput.next();
                if (innerRow == null) {
                    outerRow.release();
                } else {
                    outputRow = innerRow;
                }
            }
            return outputRow;
        }

        private void closeOuter()
        {
            outerRow.release();
            outerInput.close();
        }

        private void startNewInnerLoop(Row row)
        {
            innerInput.close();
            bindings.setRow(inputBindingPosition, row);
            innerInput.open();
        }

        // Object state

        private final Cursor outerInput;
        private final Cursor innerInput;
        private final ShareHolder<Row> outerRow = new ShareHolder<>();
        private boolean closed = true;
    }
}

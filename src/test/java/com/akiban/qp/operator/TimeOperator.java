
package com.akiban.qp.operator;

import com.akiban.qp.exec.Plannable;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.explain.CompoundExplainer;
import com.akiban.server.explain.ExplainContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TimeOperator extends Operator
{
    // Object interface

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }

    // Operator interface

    @Override
    protected Cursor cursor(QueryContext context)
    {
        return new Execution(context);
    }

    @Override
    public void findDerivedTypes(Set<RowType> derivedTypes)
    {
        inputOperator.findDerivedTypes(derivedTypes);
    }

    @Override
    public List<Operator> getInputOperators()
    {
        List<Operator> result = new ArrayList<>(1);
        result.add(inputOperator);
        return result;
    }

    @Override
    public String describePlan()
    {
        return describePlan(inputOperator);
    }

    // TimeOperator interface

    public long elapsedNsec()
    {
        return elapsedNsec;
    }

    public TimeOperator(Operator inputOperator)
    {
        this.inputOperator = inputOperator;
    }

    // Object state

    private final Operator inputOperator;
    private long elapsedNsec = 0;

    @Override
    public CompoundExplainer getExplainer(ExplainContext context) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    // Inner classes

    private class Execution extends OperatorExecutionBase implements Cursor
    {
        // Cursor interface

        @Override
        public void open()
        {
            long start = System.nanoTime();
            input.open();
            long stop = System.nanoTime();
            elapsedNsec += stop - start;
        }

        @Override
        public Row next()
        {
            long start = System.nanoTime();
            Row next = input.next();
            long stop = System.nanoTime();
            elapsedNsec += stop - start;
            return next;
        }

        @Override
        public void close()
        {
            long start = System.nanoTime();
            input.close();
            long stop = System.nanoTime();
            elapsedNsec += stop - start;
        }

        @Override
        public void destroy()
        {
            input.destroy();
        }

        @Override
        public boolean isIdle()
        {
            return input.isIdle();
        }

        @Override
        public boolean isActive()
        {
            return input.isActive();
        }

        @Override
        public boolean isDestroyed()
        {
            return input.isDestroyed();
        }

        // Execution interface

        Execution(QueryContext context)
        {
            super(context);
            this.input = inputOperator.cursor(context);
        }

        // Object state

        private final Cursor input;
    }
}

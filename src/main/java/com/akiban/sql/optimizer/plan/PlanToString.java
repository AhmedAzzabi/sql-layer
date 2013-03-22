
package com.akiban.sql.optimizer.plan;

import java.util.Deque;
import java.util.ArrayDeque;

public class PlanToString implements PlanVisitor, ExpressionVisitor
{
    public static String of(PlanNode plan) {
        PlanToString str = new PlanToString();
        str.add(plan);
        return str.toString();
    }
    
    private StringBuilder string = new StringBuilder();
    private Deque<PlanNode> pending = new ArrayDeque<>();
    private int planDepth = 0, expressionDepth = 0;

    protected void add(PlanNode n) {
        pending.addLast(n);
    }

    @Override
    public String toString() {
        while (!pending.isEmpty()) {
            PlanNode p = pending.removeFirst();
            if (string.length() > 0)
                string.append("\n");
            p.accept(this);
        }
        return string.toString();
    }

    @Override
    public boolean visitEnter(PlanNode n) {
        boolean result = visit(n);
        planDepth++;
        return result;
    }

    @Override
    public boolean visitLeave(PlanNode n) {
        planDepth--;
        return true;
    }

    @Override
    public boolean visit(PlanNode n) {
        if (expressionDepth > 0) {
            // Don't print subquery in expression until get back out to top-level.
            add(n);
            return false;
        }
        if (string.length() > 0) string.append("\n");
        for (int i = 0; i < planDepth; i++)
            string.append("  ");
        string.append(n.summaryString());
        return true;
    }
    
    @Override
    public boolean visitEnter(ExpressionNode n) {
        boolean result = visit(n);
        expressionDepth++;
        return result;
    }

    @Override
    public boolean visitLeave(ExpressionNode n) {
        expressionDepth--;
        return true;
    }

    @Override
    public boolean visit(ExpressionNode n) {
        return true;
    }
}


package com.akiban.sql.optimizer.rule;

import com.akiban.sql.optimizer.plan.PlanNode;
import com.akiban.sql.optimizer.plan.PlanVisitor;

import com.akiban.qp.exec.Plannable;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.operator.SimpleQueryContext;
import com.akiban.server.explain.ExplainContext;
import com.akiban.server.expression.Expression;

import java.util.Map;
import java.util.HashMap;

/** A plan and its common context while running rules. */
// TODO: Consider extending this to a inheritance tree of Scenarios
// to allow exploring alternatives efficiently.
public class PlanContext
{
    private RulesContext rulesContext;
    private PlanNode plan;

    public PlanContext(RulesContext rulesContext) {
        this.rulesContext = rulesContext;
    }
                       
    public PlanContext(RulesContext rulesContext, PlanNode plan) {
        this.rulesContext = rulesContext;
        this.plan = plan;
    }
                       
    public RulesContext getRulesContext () {
        return rulesContext;
    }

    public PlanNode getPlan() {
        return plan;
    }
    public void setPlan(PlanNode plan) {
        this.plan = plan;
    }
    
    public void accept(PlanVisitor visitor) {
        plan.accept(visitor);
    }
    
    /** Type safe tag for storing objects on the context whiteboard. */
    public interface WhiteboardMarker<T> {
    }

    /** A marker class if no other conveniently unique object exists. */
    public static final class DefaultWhiteboardMarker<T> implements WhiteboardMarker<T> {
        // poor man's substitute for diamond operator
        public static <T> WhiteboardMarker<T> create() {
            return new DefaultWhiteboardMarker<>();
        }
    }

    private Map<WhiteboardMarker<?>,Object> whiteboard = 
        new HashMap<>();

    /** Store information associated with the plan for use by more
     * than one rule, but not associated directly with any part of the
     * plan tree.
     */
    public <T> T getWhiteboard(WhiteboardMarker<T> marker) {
        return (T)whiteboard.get(marker);
    }
    public <T> void putWhiteboard(WhiteboardMarker<T> marker, T value) {
        whiteboard.put(marker, value);
    }

    /** Get a {@link QueryContext} for evaluations performed during
     * compilation, issuing warnings, etc.
     */
    public QueryContext getQueryContext() {
        return new SimpleQueryContext(null);
    }
}

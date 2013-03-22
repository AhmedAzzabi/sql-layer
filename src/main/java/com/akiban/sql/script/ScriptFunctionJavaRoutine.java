
package com.akiban.sql.script;

import com.akiban.ais.model.Parameter;
import com.akiban.ais.model.Routine;
import com.akiban.server.error.ExternalRoutineInvocationException;
import com.akiban.sql.server.ServerCallExplainer;
import com.akiban.sql.server.ServerJavaRoutine;
import com.akiban.sql.server.ServerJavaValues;
import com.akiban.sql.server.ServerQueryContext;
import com.akiban.sql.server.ServerRoutineInvocation;
import com.akiban.server.explain.Attributes;
import com.akiban.server.explain.CompoundExplainer;
import com.akiban.server.explain.ExplainContext;
import com.akiban.server.explain.Explainable;
import com.akiban.server.explain.Label;
import com.akiban.server.explain.PrimitiveExplainer;
import com.akiban.server.service.routines.ScriptInvoker;
import com.akiban.server.service.routines.ScriptPool;

import java.sql.ResultSet;
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/** Implementation of the <code>SCRIPT_FUNCTION_JAVA</code> calling convention. 
 * Like standard <code>PARAMETER STYLE JAVA</code>, outputs are passed
 * as 1-long arrays that the called function stores into.
 */
public class ScriptFunctionJavaRoutine extends ServerJavaRoutine
{
    private ScriptPool<ScriptInvoker> pool;
    private Object[] functionArgs;
    private Object functionResult;
    
    public ScriptFunctionJavaRoutine(ServerQueryContext context,
                                     ServerRoutineInvocation invocation,
                                     ScriptPool<ScriptInvoker> pool) {
        super(context, invocation);
        this.pool = pool;
    }

    @Override
    public void push() {
        super.push();
        functionArgs = functionArgs(getInvocation().getRoutine());
    }

    protected static Object[] functionArgs(Routine routine) {
        List<Parameter> parameters = routine.getParameters();
        int dynamicResultSets = routine.getDynamicResultSets();
        Object[] result = new Object[parameters.size() + dynamicResultSets];
        int index = 0;
        for (Parameter parameter : parameters) {
            if (parameter.getDirection() != Parameter.Direction.IN) {
                result[index] = new Object[1];
            }
            index++;
        }
        for (int i = 0; i < dynamicResultSets; i++) {
            result[index++] = new Object[1];
        }
        return result;
    }

    @Override
    public void setInParameter(Parameter parameter, ServerJavaValues values, int index) {
        if (parameter.getDirection() == Parameter.Direction.INOUT) {
            Array.set(functionArgs[index], 0, values.getObject(index));
        }
        else {
            functionArgs[index] = values.getObject(index);
        }
    }

    @Override
    public void invoke() {
        ScriptInvoker invoker = pool.get();
        boolean success = false;
        try {
            functionResult = invoker.invoke(functionArgs);
            success = true;
        }
        finally {
            pool.put(invoker, !success);
        }
    }

    @Override
    public Object getOutParameter(Parameter parameter, int index) {
        if (parameter.getDirection() == Parameter.Direction.RETURN) {
            return functionResult;
        }
        else {
            return Array.get(functionArgs[index], 0);
        }
    }
    
    @Override
    public Queue<ResultSet> getDynamicResultSets() {
        Queue<ResultSet> result = new ArrayDeque<>();
        for (int index = getInvocation().getRoutine().getParameters().size();
             index < functionArgs.length; index++) {
            ResultSet rs = (ResultSet)((Object[])functionArgs[index])[0];
            if (rs != null)
                result.add(rs);
        }
        return result;
    }

    @Override
    public CompoundExplainer getExplainer(ExplainContext context) {
        Attributes atts = new Attributes();
        ScriptInvoker invoker = pool.get();
        atts.put(Label.PROCEDURE_IMPLEMENTATION,
                 PrimitiveExplainer.getInstance(invoker.getEngineName()));
        atts.put(Label.PROCEDURE_IMPLEMENTATION, 
                 PrimitiveExplainer.getInstance(invoker.getFunctionName()));
        if (invoker.isCompiled())
            atts.put(Label.PROCEDURE_IMPLEMENTATION, 
                     PrimitiveExplainer.getInstance("compiled"));
        pool.put(invoker, true);        
        return new ServerCallExplainer(getInvocation(), atts, context);
    }

}


package com.akiban.server.types3.common.funcs;

import com.akiban.server.explain.CompoundExplainer;
import com.akiban.server.explain.ExplainContext;
import com.akiban.server.explain.Label;
import com.akiban.server.explain.PrimitiveExplainer;
import com.akiban.server.types3.LazyList;
import com.akiban.server.types3.TExecutionContext;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.TScalar;
import com.akiban.server.types3.TOverloadResult;
import com.akiban.server.types3.TPreptimeValue;
import com.akiban.server.types3.aksql.aktypes.AkBool;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.Constantness;
import com.akiban.server.types3.texpressions.TInputSetBuilder;
import com.akiban.server.types3.texpressions.TScalarBase;
import com.akiban.server.types3.texpressions.TPreparedExpression;
import com.google.common.base.Objects;

import java.util.List;

public class BoolLogic extends TScalarBase
{
    public static final TScalar BINARIES[] = new TScalar[Op.values().length];
    static
    {
        Op op[] = Op.values();
        for (int n = 0; n <  op.length; ++n)
            BINARIES[n] = new BoolLogic(op[n]);
    }

    public static final TScalar NOT = new TScalarBase() {
        @Override
        protected void buildInputSets(TInputSetBuilder builder) {
            builder.covers(AkBool.INSTANCE, 0);
        }

        @Override
        protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs,
                                  PValueTarget output) {
            output.putBool(!inputs.get(0).getBoolean());
        }

        @Override
        public String displayName() {
            return "NOT";
        }

        @Override
        public TOverloadResult resultType() {
            return TOverloadResult.fixed(AkBool.INSTANCE);
        }
    };

    private static enum Op
    {
        AND(Boolean.FALSE),
        OR(Boolean.TRUE),
        XOR(null)
        {
            @Override
            boolean evaluate(boolean first, boolean second) {
                return first ^ second;
            }
        };

        private Op(Boolean contaminant) {
            this.contaminant = contaminant;
        }

        private final Boolean contaminant;

        boolean evaluate(boolean first, boolean second) {
            // this implementation works for both AND and OR.
            // Since AND's contaminant is FALSE, if we get to this method we know first is true.
            // In that case, the result is true iff second is true.
            // Likewise, since OR's contaminant is TRUE, if we get to this method we know first is false, and
            // the result is true iff second is true.
            // This means we'll only ever need to override this method for XOR. Since that's a relatively rare
            // method, hopefully we'll never need it and the JIT can optimize assuming that this method is not
            // overridden.
            return second;
        }
    }
    
    private final Op op;
    
    BoolLogic (Op op)
    {
        this.op = op;
    }

    @Override
    public CompoundExplainer getExplainer(ExplainContext context, List<? extends TPreparedExpression> inputs, TInstance resultType)
    {
        CompoundExplainer ex = super.getExplainer(context, inputs, resultType);
        ex.addAttribute(Label.INFIX_REPRESENTATION, PrimitiveExplainer.getInstance(op.name()));
        return ex;
    }

    @Override
    protected Constantness constness(int inputIndex, LazyList<? extends TPreptimeValue> values) {
        // The expression is const iff either argument is a const whose value is equal to op.contaminant.
        // The first argument can never make the expression non-const (though it can make it const), and the second
        // argument can never leave the constness unknown.
        PValueSource preptimeValue = constSource(values, inputIndex);
        if ((preptimeValue != null) && Objects.equal(op.contaminant, getBoolean(preptimeValue)))
            return Constantness.CONST;
        return (inputIndex == 0) ? Constantness.UNKNOWN : Constantness.NOT_CONST;
    }

    @Override
    protected boolean nullContaminates(int inputIndex) {
        return false; // we'll deal with contamination ourselves
    }

    @Override
    protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output)
    {
        Boolean firstArg = getBoolean(inputs, 0);
        final Boolean result;
        if (Objects.equal(op.contaminant, firstArg)) {
            result = firstArg;
        }
        else {
            // need to look at the second arg
            Boolean secondArg = getBoolean(inputs, 1);
            if (Objects.equal(op.contaminant, secondArg)) {
                result = secondArg;
            }
            else if ( (firstArg == null) || (secondArg == null) ) {
                result = null;
            }
            else {
                result = op.evaluate(firstArg, secondArg);
            }
        }
        if (result == null)
            output.putNull();
        else
            output.putBool(result);
    }

    private Boolean getBoolean(LazyList<? extends PValueSource> inputs, int i) {
        return getBoolean(inputs.get(i));
    }

    private Boolean getBoolean(PValueSource firstInput) {
        return firstInput.isNull() ? null : firstInput.getBoolean();
    }


    @Override
    protected void buildInputSets(TInputSetBuilder builder)
    {
        builder.covers(AkBool.INSTANCE, 0, 1);
    }

    @Override
    public String displayName()
    {
        return op.name();
    }

    @Override
    public TOverloadResult resultType()
    {
        return TOverloadResult.fixed(AkBool.INSTANCE);
    }
    
}

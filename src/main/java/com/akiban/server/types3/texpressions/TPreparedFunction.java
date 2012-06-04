/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.server.types3.texpressions;

import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.row.Row;
import com.akiban.server.types3.LazyList;
import com.akiban.server.types3.TExecutionContext;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.TPreptimeContext;
import com.akiban.server.types3.TPreptimeValue;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.server.types3.pvalue.PValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TPreparedFunction implements TPreparedExpression {

    @Override
    public TInstance resultType() {
        return resultType;
    }

    @Override
    public TPreptimeValue evaluateConstant() {
        return overload.overload().evaluateConstant(preptimeContext, new LazyList<TPreptimeValue>() {
            @Override
            public TPreptimeValue get(int i) {
                return inputs.get(i).evaluateConstant();
            }

            @Override
            public int size() {
                return inputs.size();
            }
        });
    }

    @Override
    public TEvaluatableExpression build() {
        List<TEvaluatableExpression> children = new ArrayList<TEvaluatableExpression>(inputs.size());
        for (TPreparedExpression input : inputs)
            children.add(input.build());
        return new TEvaluatableFunction(
                overload,
                resultType,
                children,
                preptimeContext.createExecutionContext());
    }

    public TPreparedFunction(TValidatedOverload overload,
                             TInstance resultType,
                             List<? extends TPreparedExpression> inputs)
    {
        this.overload = overload;
        this.resultType = resultType;
        TInstance[] localInputTypes = new TInstance[inputs.size()];
        for (int i = 0, inputsSize = inputs.size(); i < inputsSize; i++) {
            TPreparedExpression input = inputs.get(i);
            localInputTypes[i] = input.resultType();
        }
        this.inputs = inputs;
        this.preptimeContext = new TPreptimeContext(Arrays.asList(localInputTypes), resultType);
    }

    private final TValidatedOverload overload;
    private final TInstance resultType;
    private final List<? extends TPreparedExpression> inputs;
    private final TPreptimeContext preptimeContext;

    private static final class TEvaluatableFunction implements TEvaluatableExpression {

        @Override
        public void with(Row row) {
            for (int i = 0, inputsSize = inputs.size(); i < inputsSize; i++) {
                TEvaluatableExpression input = inputs.get(i);
                input.with(row);
            }
        }

        @Override
        public void with(QueryContext context) {
            for (int i = 0, inputsSize = inputs.size(); i < inputsSize; i++) {
                TEvaluatableExpression input = inputs.get(i);
                input.with(context);
            }
        }

        @Override
        public PValueSource resultValue() {
            return resultValue;
        }

        @Override
        public void evaluate() {
            overload.overload().evaluate(context, evaluations, resultValue);
        }

        public TEvaluatableFunction(TValidatedOverload overload,
                                    TInstance resultType,
                                    List<? extends TEvaluatableExpression> inputs,
                                    TExecutionContext context)
        {
            this.overload = overload;
            this.inputs = inputs;
            this.inputValues = new PValueSource[inputs.size()];
            this.context = context;
            resultValue = new PValue(resultType.typeClass().underlyingType());
        }

        private final TValidatedOverload overload;
        private final PValueSource[] inputValues;
        private final List<? extends TEvaluatableExpression> inputs;
        private final PValue resultValue;
        private final TExecutionContext context;

        private final LazyList<PValueSource> evaluations = new LazyList<PValueSource>() {
            @Override
            public PValueSource get(int i) {
                PValueSource value = inputValues[i];
                // TODO clear cache at some point
                if (value == null) {
                    TEvaluatableExpression inputExpr = inputs.get(i);
                    inputExpr.evaluate();
                    value = inputExpr.resultValue();
                    inputValues[i] = value;
                }
                return value;
            }

            @Override
            public int size() {
                return inputValues.length;
            }
        };
    }
}

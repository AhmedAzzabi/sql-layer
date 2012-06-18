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
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A)
 * DO NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS
 * OF YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO
 * SIGN FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT.
 * THE LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON
 * ACCEPTANCE BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */
package com.akiban.server.types3.common.funcs;

import com.akiban.server.types3.*;
import com.akiban.server.types3.mcompat.mtypes.MDouble;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.TInputSetBuilder;
import com.akiban.server.types3.texpressions.TOverloadBase;
import java.util.Random;

public abstract class Rand extends TOverloadBase {

    public static TOverload[] create(TClass inputType, TClass resultType) {
        TOverload noArg = new Rand(inputType, resultType) {

            @Override
            protected void buildInputSets(TInputSetBuilder builder) {
            }

            @Override
            protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output) {
                if (!hasExectimeObject(context, output)) {
                    putRandom(context, new Random(System.currentTimeMillis()), output);
                }
            }
        };

        TOverload oneArg = new Rand(inputType, resultType) {

            @Override
            protected void buildInputSets(TInputSetBuilder builder) {
                builder.covers(inputType, 0);
            }

            @Override
            protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output) {
                if (!hasExectimeObject(context, output)) {
                    putRandom(context, new Random(inputs.get(0).getInt32()), output);
                }
            }
        };

        return new TOverload[]{noArg, oneArg};
    }
    protected final TClass inputType;
    protected final TClass resultType;

    protected Rand(TClass inputType, TClass resultType) {
        this.inputType = inputType;
        this.resultType = resultType;
    }

    protected boolean hasExectimeObject(TExecutionContext context, PValueTarget output) {
        boolean hasExectimeObject = context.hasExectimeObject(0).equals(true);

        if (hasExectimeObject) {
            output.putDouble(((Random) context.exectimeObjectAt(0)).nextDouble());
        }
        return hasExectimeObject;

    }

    protected void putRandom(TExecutionContext context, Random rand, PValueTarget output) {
        context.putExectimeObject(0, rand);
        output.putDouble(rand.nextDouble());
    }

    @Override
    public String overloadName() {
        return "RAND";
    }

    @Override
    public TOverloadResult resultType() {
        return TOverloadResult.fixed(resultType.instance());
    }
}

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
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.TInputSetBuilder;
import com.akiban.server.types3.texpressions.TOverloadBase;
import java.util.List;

public abstract class Trim extends TOverloadBase {

    // Described by TRIM(<trim_spec>, <char_to_trim>, <string_to_trim>
    public static TOverload[] create(TClass stringType, TClass intType) {
        TOverload rtrim = new Trim(stringType, intType) {

            @Override
            protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output) {
                String trim = (String) inputs.get(1).getObject();
                String st = (String) inputs.get(2).getObject();
                output.putObject(rtrim(st, trim));
            }

            @Override
            public String overloadName() {
                return "RTRIM";
            }
        };

        TOverload ltrim = new Trim(stringType, intType) {

            @Override
            protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output) {
                String trim = (String) inputs.get(1).getObject();
                String st = (String) inputs.get(2).getObject();
                output.putObject(ltrim(st, trim));
            }

            @Override
            public String overloadName() {
                return "LTRIM";
            }
        };

        TOverload trim = new Trim(stringType, intType) {

            @Override
            protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output) {
                int trimType = inputs.get(0).getInt32();
                String trim = (String) inputs.get(1).getObject();
                String st = (String) inputs.get(2).getObject();
                
                if (trimType != RTRIM)
                    st = ltrim(st, trim);
                if (trimType != LTRIM)
                    st = rtrim(st, trim);
                output.putObject(st);
            }

            @Override
            public String overloadName() {
                return "TRIM";
            }
        };
        
        return new TOverload[]{ltrim, rtrim, trim};
    }

    protected final int RTRIM = 0;
    protected final int LTRIM = 1;
    
    protected final TClass stringType;
    protected final TClass intType;

    private Trim(TClass stringType, TClass intType) {
        this.stringType = stringType;
        this.intType = intType;
    }

    @Override
    protected void buildInputSets(TInputSetBuilder builder) {
        builder.covers(intType, 0);
        builder.covers(stringType, 1, 2);
    }

    @Override
    public TOverloadResult resultType() {
        // actual return type is exactly the same as input type
        return TOverloadResult.fixed(stringType.instance());
    }

    // Helper methods
    protected static String ltrim(String st, String trim) {
        int n = 0;
        while (n < st.length()) {
            for (int i = 0; i < trim.length(); ++i, ++n) {
                if (st.charAt(n) != trim.charAt(i))
                    return st.substring(n- i);
            }
        }
        return st;
    }

    protected static String rtrim(String st, String trim) {
        int n = st.length() - 1;
        while (n >= 0) {
            for (int i = trim.length()-1; i >= 0; --i, --n) {
                if (st.charAt(n) != trim.charAt(i))
                    return st.substring(0, n + trim.length() - i);
            }
        }
        return st;
    }
}

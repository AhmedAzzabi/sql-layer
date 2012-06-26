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

package com.akiban.server.types3.mcompat.mfuncs;

import com.akiban.server.types3.*;
import com.akiban.server.types3.common.UnitValue;
import com.akiban.server.types3.mcompat.mtypes.MDatetimes;
import com.akiban.server.types3.mcompat.mtypes.MNumeric;
import com.akiban.server.types3.mcompat.mtypes.MString;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.TInputSetBuilder;
import com.akiban.server.types3.texpressions.TOverloadBase;
import java.util.List;
import org.joda.time.MutableDateTime;

public abstract class MDatetimeArith extends TOverloadBase {
    
    private final String name;
    private static final long DAY_FACTOR = 3600L * 1000 * 24;
    
    public static TOverload DATE_ADD = new MDatetimeArith("DATE_ADD") {

        @Override
        protected void buildInputSets(TInputSetBuilder builder) {
            buildTwoTypeInputSets(builder);
        }
                
        @Override
        protected void evaluate(long input, MutableDateTime datetime, PValueTarget output) {
            datetime.add(input);
            long date = MDatetimes.encodeDatetime(MDatetimes.fromJodaDatetime(datetime)); 
            output.putInt64(date);
        }
        
        @Override
        public TOverloadResult resultType() {
            return dateResultType();
        }
    };
    
    public static TOverload DATE_SUB = new MDatetimeArith("DATE_SUB") {

        @Override
        protected void buildInputSets(TInputSetBuilder builder) {
            buildTwoTypeInputSets(builder);
        }
                
        @Override
        protected void evaluate(long input, MutableDateTime datetime, PValueTarget output) {
            datetime.add(-input);
            long date = MDatetimes.encodeDatetime(MDatetimes.fromJodaDatetime(datetime)); 
            output.putInt64(date);
        }

         @Override
        public TOverloadResult resultType() {
            return dateResultType();
        }
    };
    
    public static TOverload SUBTIME = new MDatetimeArith("SUBTIME") {

        @Override
        protected void buildInputSets(TInputSetBuilder builder) {
            buildOneTypeInputSets(builder);
        }
                
        @Override
        protected void evaluate(long input, MutableDateTime datetime, PValueTarget output) {
            datetime.add(-input);
            output.putInt32((int) (datetime.getMillis() / DAY_FACTOR));
        }

        @Override
        public TOverloadResult resultType() {
            return intResultType();
        }
    };
    
    public static TOverload ADDTIME = new MDatetimeArith("ADDTIME") {
       
        @Override
        protected void buildInputSets(TInputSetBuilder builder) {
            buildOneTypeInputSets(builder);
        }
        
        @Override
        protected void evaluate(long input, MutableDateTime datetime, PValueTarget output) {
            datetime.add(input);
            int time = (int) MDatetimes.encodeTime(MDatetimes.fromJodaDatetime(datetime));
            output.putInt32(time);
        }
        
        @Override
        public TOverloadResult resultType() {
            return timeResultType();
        }
    };
    
    public static TOverload DATEDIFF = new MDatetimeArith("DATEDIFF") {

        @Override
        protected void buildInputSets(TInputSetBuilder builder) {
            buildOneTypeInputSets(builder);
        }
                
        @Override
        protected void evaluate(long input, MutableDateTime datetime, PValueTarget output) {
            datetime.add(-input);
            output.putInt32((int) (datetime.getMillis() / DAY_FACTOR));
        }

        @Override
        public TOverloadResult resultType() {
            return intResultType();
        }
    };
    
    public static TOverload TIMEDIFF = new MDatetimeArith("TIMEDIFF") {

        @Override
        protected void buildInputSets(TInputSetBuilder builder) {
            buildOneTypeInputSets(builder);
        }
        
        @Override
        protected void evaluate(long input, MutableDateTime datetime, PValueTarget output) {
            datetime.add(-input);
            int time = (int) MDatetimes.encodeTime(MDatetimes.fromJodaDatetime(datetime));
            output.putInt32(time);
        }
        
        @Override
        public TOverloadResult resultType() {
            return timeResultType();
        }
    };
    
    private MDatetimeArith(String name) {
        this.name = name;
    }
    
    protected abstract void evaluate(long input, MutableDateTime datetime, PValueTarget output);
    
    @Override
    protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output) {
        long[] arr1 = MDatetimes.decodeDatetime(inputs.get(1).getInt64());
        MutableDateTime datetime = MDatetimes.toJodaDatetime(arr1, context.getCurrentTimezone());
        long value = datetime.getMillis();

        // Reuse MutableDateTime object to save additional allocation
        long[] arr0 = MDatetimes.decodeDatetime(inputs.get(0).getInt64());
        datetime.setDateTime((int) arr0[MDatetimes.YEAR_INDEX], (int) arr0[MDatetimes.MONTH_INDEX], (int) arr0[MDatetimes.DAY_INDEX],
                (int) arr0[MDatetimes.HOUR_INDEX], (int) arr0[MDatetimes.MIN_INDEX], (int) arr0[MDatetimes.SEC_INDEX], 0);
        evaluate(value, datetime, output);
    }
    
    protected void buildTwoTypeInputSets(TInputSetBuilder builder) {
        builder.covers(MDatetimes.DATETIME, 0, 1);
        builder.covers(MNumeric.INT, 2);
    }
    
    protected void buildOneTypeInputSets(TInputSetBuilder builder) {
        builder.covers(MDatetimes.DATETIME, 0, 1);
    }
        
    @Override
    public String overloadName() {
        return name;
    }
    
    public TOverloadResult dateResultType() {
        return TOverloadResult.custom(MDatetimes.DATETIME.instance(), new TCustomOverloadResult() {

            @Override
            public TInstance resultInstance(List<TPreptimeValue> inputs, TPreptimeContext context) {
                TClass typeClass = inputs.get(0).instance().typeClass();
                int val =  inputs.get(2).value().getInt32();
                if (typeClass == MDatetimes.DATETIME || typeClass == MDatetimes.TIMESTAMP
                        || (typeClass == MDatetimes.DATE && val == UnitValue.HOUR 
                        || val == UnitValue.MINUTE || val == UnitValue.SECOND)) {
                    return MDatetimes.DATETIME.instance();
                }
                return MString.VARCHAR.instance();
            }
        });
    }
    
    public TOverloadResult intResultType() {
        return TOverloadResult.custom(MDatetimes.DATETIME.instance(), new TCustomOverloadResult() {

            @Override
            public TInstance resultInstance(List<TPreptimeValue> inputs, TPreptimeContext context) {
                return MNumeric.INT.instance();
            }
        });
    }

    public TOverloadResult timeResultType() {
        return TOverloadResult.custom(MDatetimes.DATETIME.instance(), new TCustomOverloadResult() {

            @Override
            public TInstance resultInstance(List<TPreptimeValue> inputs, TPreptimeContext context) {
                return MDatetimes.TIME.instance();
            }
        });
    }
}

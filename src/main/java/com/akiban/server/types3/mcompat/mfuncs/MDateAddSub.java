
package com.akiban.server.types3.mcompat.mfuncs;

import com.akiban.server.error.AkibanInternalException;
import com.akiban.server.error.InvalidDateFormatException;
import com.akiban.server.types3.LazyList;
import com.akiban.server.types3.TClass;
import com.akiban.server.types3.TCustomOverloadResult;
import com.akiban.server.types3.TExecutionContext;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.TInstanceGenerator;
import com.akiban.server.types3.TScalar;
import com.akiban.server.types3.TOverloadResult;
import com.akiban.server.types3.TPreptimeContext;
import com.akiban.server.types3.TPreptimeValue;
import com.akiban.server.types3.aksql.aktypes.AkInterval;
import com.akiban.server.types3.mcompat.mtypes.MApproximateNumber;
import com.akiban.server.types3.mcompat.mtypes.MDatetimes;
import com.akiban.server.types3.mcompat.mtypes.MDatetimes.StringType;
import com.akiban.server.types3.mcompat.mtypes.MString;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.TInputSetBuilder;
import com.akiban.server.types3.texpressions.TScalarBase;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.joda.time.MutableDateTime;

public class MDateAddSub extends TScalarBase
{
    public static final TScalar[] COMMUTATIVE = new TScalar[]
    {
        //ADDDATE
        new MDateAddSub(Helper.DO_ADD, FirstType.DATE, SecondType.DAY, "ADDDATE"),
        new MDateAddSub(Helper.DO_ADD, FirstType.DATETIME, SecondType.DAY, "ADDDATE"),
        new MDateAddSub(Helper.DO_ADD, FirstType.TIMESTAMP, SecondType.DAY, "ADDDATE"),
        new AddSubWithVarchar(Helper.DO_ADD, SecondType.DAY, "ADDDATE"),
        
        new MDateAddSub(Helper.DO_ADD, FirstType.DATE, SecondType.INTERVAL_MILLIS, "DATE_ADD", "ADDDATE", "plus"),
        new MDateAddSub(Helper.DO_ADD_MONTH, FirstType.DATE, SecondType.INTERVAL_MONTH, "DATE_ADD", "ADDDATE", "plus"),
        
        new MDateAddSub(Helper.DO_ADD, FirstType.DATETIME, SecondType.INTERVAL_MILLIS, "DATE_ADD", "ADDDATE", "plus"),
        new MDateAddSub(Helper.DO_ADD_MONTH, FirstType.DATETIME, SecondType.INTERVAL_MONTH, "DATE_ADD", "ADDDATE", "plus"),
        
        new MDateAddSub(Helper.DO_ADD, FirstType.TIMESTAMP, SecondType.INTERVAL_MILLIS, "DATE_ADD", "ADDDATE", "plus"),
        new MDateAddSub(Helper.DO_ADD_MONTH, FirstType.TIMESTAMP, SecondType.INTERVAL_MONTH, "DATE_ADD", "ADDDATE", "plus"),
        
        new AddSubWithVarchar(Helper.DO_ADD, SecondType.INTERVAL_MILLIS, "DATE_ADD", "ADDDATE", "plus"),
        new AddSubWithVarchar(Helper.DO_ADD_MONTH, SecondType.INTERVAL_MONTH, "DATE_ADD", "ADDDATE", "plus"),

        // SUBDATE
        new MDateAddSub(Helper.DO_SUB, FirstType.DATE, SecondType.DAY, "SUBDATE"),
        new MDateAddSub(Helper.DO_SUB, FirstType.DATETIME, SecondType.DAY, "SUBDATE"),
        new MDateAddSub(Helper.DO_SUB, FirstType.TIMESTAMP, SecondType.DAY, "SUBDATE"),
        new AddSubWithVarchar(Helper.DO_SUB, SecondType.DAY, "SUBDATE"),
        
        new MDateAddSub(Helper.DO_SUB, FirstType.DATE, SecondType.INTERVAL_MILLIS, "DATE_SUB", "SUBDATE", "minus"),
        new MDateAddSub(Helper.DO_SUB_MONTH, FirstType.DATE, SecondType.INTERVAL_MONTH, "DATE_SUB", "SUBDATE", "minus"),
        
        new MDateAddSub(Helper.DO_SUB, FirstType.DATETIME, SecondType.INTERVAL_MILLIS, "DATE_SUB", "SUBDATE", "minus"),
        new MDateAddSub(Helper.DO_SUB_MONTH, FirstType.DATETIME, SecondType.INTERVAL_MONTH, "DATE_SUB", "SUBDATE", "minus"),
        
        new MDateAddSub(Helper.DO_SUB, FirstType.TIMESTAMP, SecondType.INTERVAL_MILLIS, "DATE_SUB", "SUBDATE", "minus"),
        new MDateAddSub(Helper.DO_SUB_MONTH, FirstType.TIMESTAMP, SecondType.INTERVAL_MONTH, "DATE_SUB", "SUBDATE", "minus"),
        
        new AddSubWithVarchar(Helper.DO_SUB, SecondType.INTERVAL_MILLIS, "DATE_SUB", "SUBDATE", "minus"),
        new AddSubWithVarchar(Helper.DO_SUB_MONTH, SecondType.INTERVAL_MONTH, "DATE_SUB", "SUBDATE", "minus"),
        
        // ADDTIME
        new MDateAddSub(Helper.DO_ADD, FirstType.TIME, SecondType.SECOND, "TIME_ADD", "ADDTIME"),
        new AddSubWithVarchar(Helper.DO_ADD, SecondType.SECOND, "TIME_ADD", "ADDTIME"),
        
        new MDateAddSub(Helper.DO_ADD, FirstType.TIME, SecondType.INTERVAL_MILLIS, "addtime"),
        
        new MDateAddSub(Helper.DO_ADD, FirstType.TIME, SecondType.TIME, "TIME_ADD", "ADDTIME"),

        new AddSubWithVarchar(Helper.DO_ADD, SecondType.TIME, "TIME_ADD", "ADDTIME"),
        new AddSubWithVarchar(Helper.DO_ADD, SecondType.TIME_STRING, "ADDTIME"),
        
        //  SUBTIME
        new MDateAddSub(Helper.DO_SUB, FirstType.TIME, SecondType.SECOND, "SUBTIME"),
        new AddSubWithVarchar(Helper.DO_SUB, SecondType.SECOND, "SUBTIME"),
        new MDateAddSub(Helper.DO_SUB, FirstType.TIME, SecondType.TIME, "SUBTIME"),
        new AddSubWithVarchar(Helper.DO_SUB, SecondType.TIME, "SUBTIME"),
        
        new AddSubWithVarchar(Helper.DO_ADD, SecondType.TIME_STRING, "SUBTIME"),

        // additional date/time ariths
        new AddSubWithVarchar(Helper.DO_ADD, SecondType.INTERVAL_MILLIS, 1, 0, "plus"),
        new AddSubWithVarchar(Helper.DO_ADD_MONTH, SecondType.INTERVAL_MONTH, 1, 0, "plus"),
        
        new MDateAddSub(Helper.DO_ADD, 1, 0, FirstType.DATE, SecondType.INTERVAL_MILLIS, "plus"),
        new MDateAddSub(Helper.DO_ADD_MONTH, 1, 0, FirstType.DATE, SecondType.INTERVAL_MONTH, "plus"),
        
        new MDateAddSub(Helper.DO_ADD, 1, 0, FirstType.DATETIME, SecondType.INTERVAL_MILLIS, "plus"),
        new MDateAddSub(Helper.DO_ADD_MONTH, 1, 0, FirstType.DATETIME, SecondType.INTERVAL_MONTH, "plus"),
        
        new MDateAddSub(Helper.DO_ADD, 1, 0, FirstType.TIMESTAMP, SecondType.INTERVAL_MILLIS, "plus"),
        new MDateAddSub(Helper.DO_ADD_MONTH, 1, 0, FirstType.TIMESTAMP, SecondType.INTERVAL_MONTH, "plus"),
        
        // Special case, <TIME> plus/minus <INTERVAL> ===> <DATE/DATETIME> plus <INTERVAL>
        new MDateAddSub(Helper.DO_ADD, 1, 0, FirstType.TIME_TO_DATE, SecondType.INTERVAL_MILLIS, "plus"),
        new MDateAddSub(Helper.DO_ADD, FirstType.TIME_TO_DATE, SecondType.INTERVAL_MILLIS, "plus"),
        new MDateAddSub(Helper.DO_ADD, 1, 0, FirstType.TIME_TO_DATE, SecondType.INTERVAL_MONTH, "plus"),
        new MDateAddSub(Helper.DO_ADD, 0, 1, FirstType.TIME_TO_DATE, SecondType.INTERVAL_MONTH, "plus"),

        new MDateAddSub(Helper.DO_SUB, FirstType.TIME_TO_DATE, SecondType.INTERVAL_MILLIS, "minus"),
        new MDateAddSub(Helper.DO_SUB, FirstType.TIME_TO_DATE, SecondType.INTERVAL_MONTH, "minus"),
    };

    private static class AddSubWithVarchar extends MDateAddSub
    {
        AddSubWithVarchar (Helper h, SecondType sec, String...ns)
        {
            super(h, 0, 1, FirstType.VARCHAR, sec, ns);
        }

        AddSubWithVarchar (Helper h, SecondType sec, int pos0, int pos1, String...ns)
        {
            super(h, pos0, pos1, FirstType.VARCHAR, sec, ns);
        }
        
        @Override
        public TOverloadResult resultType()
        {
            return TOverloadResult.fixed(MString.VARCHAR, 29);
        }
        @Override
        protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output)
        {
            long ymd[] = new long[6];
            StringType stType;
            long millis;
            String arg0 = inputs.get(pos0).getString();
   
            try
            {
                stType = MDatetimes.parseDateOrTime(arg0, ymd);
                if (!MDatetimes.isValidType(stType))
                {
                    context.warnClient(new InvalidDateFormatException(stType.name(), arg0));
                    output.putNull();
                    return;
                }
                millis = secondArg.toMillis(inputs.get(pos1));
            }
            catch (InvalidDateFormatException e)
            {
                context.warnClient(e);
                output.putNull();
                return;
            }

            MutableDateTime dt;
            switch (stType)
            {
                case DATE_ST:
                    dt = MDatetimes.toJodaDatetime(ymd, "UTC");
                    helper.compute(dt, millis);
                    
                    if (FirstType.DATE.adjustFirstArg(context.inputTInstanceAt(1)) == FirstType.DATE)
                        output.putString(dt.toString("YYYY-MM-dd"), null);
                    else
                        output.putString(dt.toString("YYYY-MM-dd HH:mm:ss"), null);
                    
                    break;
                case DATETIME_ST:
                    dt = MDatetimes.toJodaDatetime(ymd, context.getCurrentTimezone());
                    helper.compute(dt, millis);
                    output.putString(dt.toString("YYYY-MM-dd HH:mm:ss"), null);
                    break;
                case TIME_ST:
                    long arg0Millis = timeToMillis(ymd);
                    
                    long ret = helper == Helper.DO_ADD ? arg0Millis + millis: arg0Millis - millis;
                    int sign = 1;
                    if (ret < 0)
                        ret *= (sign = -1);
                    
                    // turn millis back to hour-min-sec
                    long seconds = ret / 1000L;
                    long hours = seconds / 3600;
                    long minutes = (seconds - hours * 3600) / 60;
                    seconds -= (minutes * 60 + hours * 3600);
                    hours *= sign;
                    
                    output.putString(String.format("%02d:%02d:%02d",
                                                   hours, minutes, seconds),
                                     null);
                    break;
                default:
                    throw new AkibanInternalException("unexpected argument: " + stType);
            }
        }
    }
    
    private static enum Helper
    {
        DO_ADD_MONTH
        {
            @Override
            protected void compute(MutableDateTime date, long delta)
            {
                date.addMonths((int)delta);
            }
        },
        DO_SUB_MONTH
        {
            @Override
            protected void compute(MutableDateTime date, long delta)
            {
                date.addMonths(-(int)delta);
            }
        },
        DO_ADD
        {
            @Override
            protected void compute(MutableDateTime date, long delta)
            {
                date.add(delta);
            }
        },
        DO_SUB
        {
            @Override
            protected void compute(MutableDateTime date, long delta)
            {
                date.add(-delta);
            }
        };
        
        abstract protected void compute(MutableDateTime date, long delta);
    }

    private static enum FirstType
    {
        TIME_TO_DATE(MDatetimes.TIME)
        {
            @Override
            FirstType adjustFirstArg(TInstance ins)
            {
                if (ins != null
                    && ins.typeClass() instanceof AkInterval
                    && ((AkInterval)ins.typeClass()).isTime(ins))
                    return FirstType.DATETIME_STR;
                else
                    return FirstType.DATE_STR;
            }
            
            @Override
            long[] decode(PValueSource val, TExecutionContext context)
            {
                long ret[] = MDatetimes.decodeTime(val.getInt32());
                MDatetimes.timeToDatetime(ret);
                
                return  MDatetimes.isValidDayMonth(ret)        // zero dates are considered 'valid'
                            && !MDatetimes.isZeroDayMonth(ret) // but here we don't want them to be
                        ? ret 
                        : null;
            }
            
            @Override
            protected void putResult(PValueTarget out, MutableDateTime par3, TExecutionContext context)
            {
                out.putInt32(MDatetimes.encodeDate(MDatetimes.fromJodaDatetime(par3)));
            }
        },
        DATE_STR(MString.VARCHAR, 29)
        {
            @Override
            long[] decode(PValueSource val, TExecutionContext context)
            {
                return FirstType.TIME_TO_DATE.decode(val, context);
            }
            
            @Override
            protected void putResult(PValueTarget out, MutableDateTime par3, TExecutionContext context)
            {
                out.putString(par3.toString("YYYY-MM-dd"), null);
            }
        },
        DATETIME_STR(MString.VARCHAR, 29)
        {
            @Override
            long[] decode(PValueSource val, TExecutionContext context)
            {
                return FirstType.TIME_TO_DATE.decode(val, context);
            }
            
            @Override
            protected void putResult(PValueTarget out, MutableDateTime par3, TExecutionContext context)
            {
                out.putString(par3.toString("YYYY-MM-dd HH:mm:ss"), null);
            }
        },
        VARCHAR(MString.VARCHAR, 29)
        {
            @Override
            long[] decode(PValueSource val, TExecutionContext context)
            {
                throw new AkibanInternalException("shouldn't have been used");
            }
            
            @Override
            protected void putResult(PValueTarget out, MutableDateTime par3, TExecutionContext context)
            {
                throw new AkibanInternalException("shouldn't have been used");
            }
        },
        DATE(MDatetimes.DATE)
        {
            @Override
            FirstType adjustFirstArg(TInstance ins)
            {
                if (ins != null
                    && ins.typeClass() instanceof AkInterval
                    && ((AkInterval)ins.typeClass()).isTime(ins))
                    return FirstType.DATETIME;
                else
                    return this;
            }
            
            @Override
            long[] decode(PValueSource val, TExecutionContext context)
            {
                long ret[] = MDatetimes.decodeDate(val.getInt32());
         
                return  MDatetimes.isValidDayMonth(ret) // zero dates are considered 'valid'
                            && !MDatetimes.isZeroDayMonth(ret) // but here we don't want them to be
                        ? ret 
                        : null;
            }
            
            @Override
            protected void putResult(PValueTarget out, MutableDateTime par3, TExecutionContext context)
            {
                out.putInt32(MDatetimes.encodeDate(MDatetimes.fromJodaDatetime(par3)));
            }
            
        },
        TIME(MDatetimes.TIME)
        {
            @Override
            long[] decode(PValueSource val, TExecutionContext context)
            {
                long ret[] = MDatetimes.decodeTime(val.getInt32());
                return MDatetimes.isValidHrMinSec(ret, false) ? ret : null;
            }
            
            @Override
            protected void putResult(PValueTarget out, MutableDateTime par3, TExecutionContext context)
            {
                out.putInt32(MDatetimes.encodeTime(MDatetimes.fromJodaDatetime(par3)));
            }
        },
        DATETIME(MDatetimes.DATETIME)
        {
            @Override
            long[] decode(PValueSource val, TExecutionContext context)
            {
                long ret[] = MDatetimes.decodeDatetime(val.getInt64());
                return MDatetimes.isValidDatetime(ret) 
                            && !MDatetimes.isZeroDayMonth(ret)
                       ? ret 
                       : null;
            }
            
            @Override
            protected void putResult(PValueTarget out, MutableDateTime par3, TExecutionContext context)
            {
                out.putInt64(MDatetimes.encodeDatetime(MDatetimes.fromJodaDatetime(par3)));
            }
        },
        TIMESTAMP(MDatetimes.TIMESTAMP)
        {
            @Override
            long[] decode(PValueSource val, TExecutionContext context)
            {
                return MDatetimes.decodeTimestamp(val.getInt32(), context.getCurrentTimezone());
            }
            
            @Override
            protected void putResult(PValueTarget out, MutableDateTime par3, TExecutionContext context)
            {
                out.putInt32((int)MDatetimes.encodeTimetamp(par3.getMillis(), context));
            }
        };
        
        FirstType(TClass t, int... attrs)
        {
            this.type = t;
            this.attrs = attrs;
        }

        abstract long[] decode (PValueSource val, TExecutionContext context);
        protected abstract void putResult(PValueTarget out, MutableDateTime par3, TExecutionContext context);
        
        FirstType adjustFirstArg(TInstance ins) // to be overriden in DATE
        {
            // only needs adjusting if <first arg> is DATE
            return this;
        }
        private final TClass type;
        private final int[] attrs;
    }

    private static enum SecondType
    {
        INTERVAL_MILLIS(AkInterval.SECONDS)
        {
            @Override
            protected long toMillis(PValueSource arg)
            {
                return AkInterval.secondsIntervalAs(arg, TimeUnit.MILLISECONDS);
            }
        },
        INTERVAL_MONTH(AkInterval.MONTHS)
        {
            @Override
            protected long toMillis(PValueSource arg)
            {
                // this return the number of months, not millis
                 return arg.getInt64();
            }
        },
        TIME(MDatetimes.TIME)
        {
            @Override
            protected long toMillis(PValueSource arg)
            {
                int val = arg.getInt32();
                long hms[] = MDatetimes.decodeTime(val);
                
                return timeToMillis(hms);
                
            }
        },
        TIME_STRING(MString.VARCHAR)
        {
            @Override
            protected long toMillis(PValueSource arg)
            {
                String st = arg.getString();
                long hms[] = new long[6];
                StringType stType = MDatetimes.parseDateOrTime(st, hms);
                
                switch(stType)
                {
                    case TIME_ST:
                        return timeToMillis(hms);
                    default:
                        throw new InvalidDateFormatException("TIME", st);
                }
            }
        },
        SECOND(MApproximateNumber.DOUBLE)
        {
            @Override
            protected long toMillis(PValueSource arg)
            {
                return Math.round(arg.getDouble()) * 1000L;
            }
        },
        DAY(MApproximateNumber.DOUBLE)
        {
            @Override
            protected long toMillis(PValueSource arg)
            {
                return Math.round(arg.getDouble()) * MILLS_PER_DAY;
            }
        };

        private SecondType (TClass t)
        {
            type = t;
        }
        
        protected abstract long toMillis(PValueSource arg);
        
        TClass type;
        private static final long MILLS_PER_DAY = 24 * 3600 * 1000;
    }
    
    protected final Helper helper;
    protected final FirstType firstArg;
    protected final SecondType secondArg;
    protected final String names[];
    protected final int pos0;
    protected final int pos1;
    
    private MDateAddSub(Helper h, FirstType first, SecondType sec, String...ns)
    {
        this(h, 0, 1, first, sec, ns);
    }
    
    private MDateAddSub(Helper h, int pos0, int pos1, FirstType first, SecondType sec, String...ns)
    {
        helper = h;
        firstArg = first;
        secondArg = sec;
        names = ns;
        this.pos0 = pos0;
        this.pos1 = pos1;
    }

    @Override
    protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output)
    {
        PValueSource arg0 = inputs.get(pos0);
        long ymd[] = firstArg.decode(arg0, context);
        if (ymd == null)
        {
            output.putNull();
            context.warnClient(new InvalidDateFormatException("DATE", arg0.toString()));
        }
        else
        {
            MutableDateTime dt = MDatetimes.toJodaDatetime(ymd, "UTC");    // calculations should be done
            helper.compute(dt, secondArg.toMillis(inputs.get(pos1)));      // in UTC (to avoid daylight-saving side effects)
            firstArg.adjustFirstArg(context.inputTInstanceAt(pos1)).putResult(output, dt, context);
        }
    }

    @Override
    protected void buildInputSets(TInputSetBuilder builder)
    {
        builder.covers(firstArg.type, pos0).covers(secondArg.type, pos1);
    }

    @Override
    public String displayName()
    {
        return names[0];
    }

    @Override
    public String[] registeredNames()
    {
        return names;
    }
    
    @Override
    public TOverloadResult resultType()
    {
        return TOverloadResult.custom(new TCustomOverloadResult()
        {
            @Override
            public TInstance resultInstance(List<TPreptimeValue> inputs, TPreptimeContext context)
            {
                FirstType adjusted = firstArg.adjustFirstArg(inputs.get(pos1).instance());
                return new TInstanceGenerator(adjusted.type, adjusted.attrs).setNullable(anyContaminatingNulls(inputs));
            }
        });
    }
    
    
    static long timeToMillis(long ymd[])
    {
        int sign = 1;
        if (ymd[MDatetimes.HOUR_INDEX] < 0)
            ymd[MDatetimes.HOUR_INDEX] *= sign = -1;
        
        return sign * (ymd[MDatetimes.HOUR_INDEX] * 3600000
                        + ymd[MDatetimes.MIN_INDEX] * 60000
                        + ymd[MDatetimes.SEC_INDEX] * 1000);
    }
}

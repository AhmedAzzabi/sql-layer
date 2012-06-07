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

package com.akiban.server.types3.common.funcs;

import com.akiban.server.error.InvalidOperationException;
import com.akiban.server.error.InvalidParameterValueException;
import com.akiban.server.types3.LazyList;
import com.akiban.server.types3.TClass;
import com.akiban.server.types3.TExecutionContext;
import com.akiban.server.types3.TOverloadResult;
import com.akiban.server.types3.aksql.aktypes.AkBool;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.TInputSetBuilder;
import com.akiban.server.types3.texpressions.TOverloadBase;

public class TLike extends TOverloadBase
{
    /**
     * 
     * @param stringType
     * @return an arrays of all OverLoads available for the LIKE function 
     * with this specifict string type (type: akString vs Mstring, etc)
     */
    public static TLike[] create(TClass stringType)
    {
        TLike ret[] = new TLike[LikeType.values().length * 2];
        
        int n = 0;
        for (LikeType t : LikeType.values())
        {
            ret[n++] = new TLike(new int[] {0, 1}, stringType, t);
            // optional escape char
            ret[n++] = new TLike(new int[] {0, 1, 2}, stringType, t);
        }
        
        return ret;
    }
    
    static  enum LikeType
    {
        BLIKE, // case sensitive
        LIKE,  // ditto
        ILIKE // case insensitive
    }
    
    private final int coverage[];
    private final TClass stringType;
    private final LikeType likeType;
    
    // compiled pattern
    private Matcher matcher = null;
    private String oldPattern = null;
    private char oldEscape = '\0';
    TLike (int c[], TClass sType, LikeType lType)
    {
        coverage = c;
        stringType = sType;
        likeType = lType;
    }
    
    @Override
    protected void buildInputSets(TInputSetBuilder builder)
    {
        builder.covers(stringType, coverage);
    }

    @Override
    protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output)
    {
        String left = (String)inputs.get(0).getObject();
        String right = (String)inputs.get(1).getObject();
        char esca = '\\';        
        if (inputs.size() == 3)
        {
            String escapeString = (String)inputs.get(2).getObject();
            if (escapeString.length() != 1)
                throw new InvalidParameterValueException("Invalid escape character: " + escapeString); 
            esca = escapeString.charAt(0);
        }
     
        if (matcher == null
            //  || check whether right is a literal, if not, just compile a new pattern
                || (!right.equals(oldPattern) && esca != oldEscape)
           )
        {
            oldPattern = right;
            oldEscape = esca;
            matcher = Matchers.getMatcher(oldPattern, esca, likeType == LikeType.ILIKE);
        }
        
        try
        {
            output.putBool(matcher.match(left));
        }
        catch (InvalidOperationException e)
        {
            // TODO:
            // What's the new way of issuing a warning?
            output.putNull();
        }
    }

    @Override
    public String overloadName()
    {
        return likeType.name();
    }

    @Override
    public TOverloadResult resultType()
    {
        // TODO: for now, there is only one kind of boolean
        // this may change later
        return new TOverloadResult(AkBool.INSTANCE);
    }
    
}

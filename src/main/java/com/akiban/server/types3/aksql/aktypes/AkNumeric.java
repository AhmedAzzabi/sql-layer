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

package com.akiban.server.types3.aksql.aktypes;

import com.akiban.server.types3.TClass;
import com.akiban.server.types3.TFactory;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.aksql.ABundle;
import com.akiban.server.types3.common.IntAttribute;
import com.akiban.server.types3.pvalue.PUnderlying;

public class AkNumeric extends TClass {

    private AkNumeric(String name, int serializationSize, PUnderlying pUnderlying) {
        super(ABundle.INSTANCE.id(), name, 
                IntAttribute.values(),
                1, 1, serializationSize, 
                pUnderlying);
    }

    @Override
    public TFactory factory() {
        return new AkNumericFactory(this);
    }
     
    @Override
    protected TInstance doPickInstance(TInstance instance0, TInstance instance1) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    // numeric types
    public static final TClass SMALLINT = new AkNumeric("smallint", 2, PUnderlying.INT_16);
    public static final TClass INT = new AkNumeric("int", 4, PUnderlying.INT_32);
    public static final TClass BIGINT = new AkNumeric("bigint", 8, PUnderlying.INT_64);
}
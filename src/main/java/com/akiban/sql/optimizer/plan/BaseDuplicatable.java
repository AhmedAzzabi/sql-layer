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

package com.akiban.sql.optimizer.plan;

import java.util.*;

public abstract class BaseDuplicatable implements Duplicatable, Cloneable
{
    @Override
    public final Duplicatable duplicate() {
        return duplicate(new DuplicateMap());
    }

    protected boolean maintainInDuplicateMap() {
        return false;
    }

    @Override
    public Duplicatable duplicate(DuplicateMap map) {
        BaseDuplicatable copy;
        try {
            if (maintainInDuplicateMap()) {
                copy = map.get(this);
                if (copy != null)
                    return copy;
                copy = (BaseDuplicatable)clone();
                map.put(this, copy);
            }
            else
                copy = (BaseDuplicatable)clone();
        }
        catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
        copy.deepCopy(map);
        return copy;
    }

    /** Deep copy all the fields, using the given map. */
    protected void deepCopy(DuplicateMap map) {
    }

    protected static <T extends Duplicatable> List<T> duplicateList(List<T> list,
                                                                    DuplicateMap map) {
        List<T> copy = new ArrayList<>(list.size());
        for (T elem : list) {
            copy.add((T)elem.duplicate(map));
        }
        return copy;
    }

    protected static <T extends Duplicatable> Set<T> duplicateSet(Set<T> set,
                                                                  DuplicateMap map) {
        Set<T> copy = new HashSet<>(set.size());
        for (T elem : set) {
            copy.add((T)elem.duplicate(map));
        }
        return copy;
    }

}

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

package com.akiban.server.test.it.store;

import static org.junit.Assert.assertEquals;

import com.akiban.ais.model.Index;
import com.akiban.server.rowdata.RowDef;
import org.junit.Test;

import com.akiban.server.TableStatistics;

public class AnalyzeIndexIT extends AbstractScanBase {

    @Test
    public void testPopulateTableStatistics() throws Exception {
        final RowDef rowDef = rowDef("aa");
        final TableStatistics ts = serviceManager.getDXL().dmlFunctions().getTableStatistics(session, rowDef.getRowDefId(), true);
        {
            // Checks a secondary index
            //
            final int indexId = findIndexId(rowDef, "str");
            TableStatistics.Histogram histogram = null;
            for (TableStatistics.Histogram h : ts.getHistogramList()) {
                if (h.getIndexId() == indexId) {
                    histogram = h;
                    break;
                }
            }
            assertEquals(33, histogram.getHistogramSamples().size());
            assertEquals(100, histogram.getHistogramSamples().get(32)
                    .getRowCount());
        }
        {
            // Checks an hkeyEquivalent index
            //
            final int indexId = findIndexId(rowDef, Index.PRIMARY_KEY_CONSTRAINT);
            TableStatistics.Histogram histogram = null;
            for (TableStatistics.Histogram h : ts.getHistogramList()) {
                if (h.getIndexId() == indexId) {
                    histogram = h;
                    break;
                }
            }
            assertEquals(33, histogram.getHistogramSamples().size());
            assertEquals(100, histogram.getHistogramSamples().get(32)
                    .getRowCount());
        }
    }

}

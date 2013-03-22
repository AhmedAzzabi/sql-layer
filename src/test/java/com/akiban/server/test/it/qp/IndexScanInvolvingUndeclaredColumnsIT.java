
package com.akiban.server.test.it.qp;

import com.akiban.qp.expression.IndexBound;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.server.api.dml.SetColumnSelector;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.test.ExpressionGenerators;
import com.akiban.server.expression.std.FieldExpression;
import org.junit.Before;
import org.junit.Test;

import static com.akiban.qp.operator.API.cursor;
import static com.akiban.qp.operator.API.indexScan_Default;
import static com.akiban.server.test.ExpressionGenerators.field;

// Inspired by Bug 979162

public class IndexScanInvolvingUndeclaredColumnsIT extends OperatorITBase
{
    @Override
    protected void setupCreateSchema()
    {
        region = createTable(
            "schema", "region",
            "rid int not null",
            "primary key(rid)");
        regionChildren = createTable(
            "schema", "region_children",
            "rid int not null",
            "locid int not null",
            "grouping foreign key(rid) references region(rid)");
        createIndex("schema", "region_children", "idx_locid", "locid");
    }

    @Override
    protected void setupPostCreateSchema()
    {
        schema = new Schema(ais());
        regionChildrenRowType = schema.userTableRowType(userTable(regionChildren));
        idxRowType = indexType(regionChildren, "locid");
        db = new NewRow[]{
            // region
            createNewRow(region, 10L),
            createNewRow(region, 20L),
            // region_children (last column is akiban PK)
            createNewRow(regionChildren, 10L, 100L, 1L),
            createNewRow(regionChildren, 10L, 110L, 2L),
            createNewRow(regionChildren, 10L, 120L, 3L),
            createNewRow(regionChildren, 20L, 200L, 4L),
            createNewRow(regionChildren, 20L, 210L, 5L),
            createNewRow(regionChildren, 20L, 220L, 6L),
        };
        adapter = persistitAdapter(schema);
        queryContext = queryContext(adapter);
        use(db);
    }

    @Test
    public void test()
    {
        IndexBound bound = new IndexBound(row(idxRowType, 110L, 15L),
                                          new SetColumnSelector(0, 1));
        IndexKeyRange range = IndexKeyRange.bounded(idxRowType, bound, true, bound, true);
        API.Ordering ordering = new API.Ordering();
        ordering.append(ExpressionGenerators.field(idxRowType, 0), true);
        ordering.append(ExpressionGenerators.field(idxRowType, 1), true);
        Operator plan =
            indexScan_Default(
                idxRowType,
                range,
                ordering);
        compareRows(new RowBase[0], cursor(plan, queryContext));
    }

    // For use by this class

    private API.Ordering ordering(Object ... ord) // alternating column positions and asc/desc
    {
        API.Ordering ordering = API.ordering();
        int i = 0;
        while (i < ord.length) {
            int column = (Integer) ord[i++];
            boolean asc = (Boolean) ord[i++];
            ordering.append(field(idxRowType, column), asc);
        }
        return ordering;
    }

    private int region;
    private int regionChildren;
    private RowType regionChildrenRowType;
    private IndexRowType idxRowType;
}

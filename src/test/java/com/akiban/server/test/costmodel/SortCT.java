
package com.akiban.server.test.costmodel;

import com.akiban.ais.model.Group;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.operator.StoreAdapter;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.Schema;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.test.ExpressionGenerators;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static com.akiban.qp.operator.API.*;

public class SortCT extends CostModelBase
{
    @Test
    public void run() throws Exception
    {
        createSchema();
        populateDB(100000);
        // Unidirectional
        // run(1, 0x1);
        run(2, 0x3);
        run(3, 0x7);
        // One change of direction
        run(2, 0x2);
        run(3, 0x4);
        // Two changes of direction
        run(3, 0x5);
    }

    private void run(int sortFields, int ordering)
    {
        // Warmup
        sort(sortFields, ordering, FILLER_100_COLUMN, WARMUP_RUNS, 1, false);
        // Measurements
        sort(sortFields, ordering, FILLER_100_COLUMN, MEASUREMENT_RUNS, 100000, true);
        sort(sortFields, ordering, FILLER_200_COLUMN, MEASUREMENT_RUNS, 100000, true);
        sort(sortFields, ordering, FILLER_300_COLUMN, MEASUREMENT_RUNS, 100000, true);
        sort(sortFields, ordering, FILLER_400_COLUMN, MEASUREMENT_RUNS, 100000, true);
        sort(sortFields, ordering, FILLER_100_COLUMN, MEASUREMENT_RUNS, 10000, true);
        sort(sortFields, ordering, FILLER_200_COLUMN, MEASUREMENT_RUNS, 10000, true);
        sort(sortFields, ordering, FILLER_300_COLUMN, MEASUREMENT_RUNS, 10000, true);
        sort(sortFields, ordering, FILLER_400_COLUMN, MEASUREMENT_RUNS, 10000, true);
        sort(sortFields, ordering, FILLER_100_COLUMN, MEASUREMENT_RUNS, 1000, true);
        sort(sortFields, ordering, FILLER_200_COLUMN, MEASUREMENT_RUNS, 1000, true);
        sort(sortFields, ordering, FILLER_300_COLUMN, MEASUREMENT_RUNS, 1000, true);
        sort(sortFields, ordering, FILLER_400_COLUMN, MEASUREMENT_RUNS, 1000, true);
        sort(sortFields, ordering, FILLER_100_COLUMN, MEASUREMENT_RUNS, 100, true);
        sort(sortFields, ordering, FILLER_200_COLUMN, MEASUREMENT_RUNS, 100, true);
        sort(sortFields, ordering, FILLER_300_COLUMN, MEASUREMENT_RUNS, 100, true);
        sort(sortFields, ordering, FILLER_400_COLUMN, MEASUREMENT_RUNS, 100, true);
        sort(sortFields, ordering, FILLER_100_COLUMN, MEASUREMENT_RUNS, 10, true);
        sort(sortFields, ordering, FILLER_200_COLUMN, MEASUREMENT_RUNS, 10, true);
        sort(sortFields, ordering, FILLER_300_COLUMN, MEASUREMENT_RUNS, 10, true);
        sort(sortFields, ordering, FILLER_400_COLUMN, MEASUREMENT_RUNS, 10, true);
        sort(sortFields, ordering, FILLER_100_COLUMN, MEASUREMENT_RUNS, 1, true);
        sort(sortFields, ordering, FILLER_200_COLUMN, MEASUREMENT_RUNS, 1, true);
        sort(sortFields, ordering, FILLER_300_COLUMN, MEASUREMENT_RUNS, 1, true);
        sort(sortFields, ordering, FILLER_400_COLUMN, MEASUREMENT_RUNS, 1, true);
    }

    private void createSchema()
    {
        t = createTable(
            "schema", "t",
            /* 0 */ "id int not null",
            /* 1 */ "a int",
            /* 2 */ "b int",
            /* 3 */ "c int",
            /* 4 */ "filler100 varchar(100)",
            /* 5 */ "filler200 varchar(200)",
            /* 6 */ "filler300 varchar(300)",
            /* 7 */ "filler400 varchar(400)",
            "primary key(id)");
        group = group(t);
        schema = new Schema(ais());
        tRowType = schema.userTableRowType(userTable(t));
        adapter = persistitAdapter(schema);
        queryContext = queryContext((PersistitAdapter) adapter);
    }

    private void populateDB(int rows)
    {
        for (int id = 0; id < rows; id++) {
            NewRow row =
                createNewRow(t,
                             id,
                             random.nextInt(),
                             random.nextInt(),
                             random.nextInt(),
                             FILLER_100,
                             FILLER_200,
                             FILLER_300,
                             FILLER_400);
            dml().writeRow(session(), row);
        }
    }

    // ordering is a bitmask. bit[i] is true iff ith field is ascending.
    private void sort(int sortFields,
                      int orderingMask,
                      int fillerColumn,
                      int runs,
                      int rows,
                      boolean report)
    {
        Operator setup =
            project_Default(
                limit_Default(
                    groupScan_Default(group),
                    rows),
                tRowType,
                Arrays.asList(ExpressionGenerators.field(tRowType, 0),
                              ExpressionGenerators.field(tRowType, 1),
                              ExpressionGenerators.field(tRowType, 2),
                              ExpressionGenerators.field(tRowType, 3),
                              ExpressionGenerators.field(tRowType, fillerColumn)));
        RowType inputRowType = setup.rowType();
        int sortComplexity = 0;
        Ordering ordering = ordering();
        for (int f = 0; f < sortFields; f++) {
            boolean ascending = (orderingMask & (1 << f)) != 0;
            ordering.append(ExpressionGenerators.field(inputRowType, f), ascending);
            boolean previousAscending = (orderingMask & (1 << (f - 1))) != 0;
            if (f > 0 && ascending != previousAscending) {
                sortComplexity++;
            }
        }
        Operator sort =
            sort_Tree(
                setup,
                inputRowType,
                ordering,
                SortOption.PRESERVE_DUPLICATES);
        long start;
        long stop;
        // Measure time for setup
        start = System.nanoTime();
        for (int r = 0; r < runs; r++) {
            Cursor cursor = cursor(setup, queryContext);
            cursor.open();
            while (cursor.next() != null);
        }
        stop = System.nanoTime();
        long setupNsec = stop - start;
        // Measure time for complete plan
        start = System.nanoTime();
        for (int r = 0; r < runs; r++) {
            Cursor cursor = cursor(sort, queryContext);
            cursor.open();
            while (cursor.next() != null);
        }
        stop = System.nanoTime();
        long planNsec = stop - start;
        if (report) {
            // Report the difference
            long sortNsec = planNsec - setupNsec;
            double averageUsecPerRow = sortNsec / (1000.0 * runs * rows);
            int rowSize =
                32 /* 4 int columns */ +
                (fillerColumn == FILLER_100_COLUMN ? 100 :
                 fillerColumn == FILLER_200_COLUMN ? 200 :
                 fillerColumn == FILLER_300_COLUMN ? 300 : 400);
            System.out.println(String.format("rows: %s, row size: %s, sort fields: %s, sort complexity: %s, %s usec/row",
                                             rows,
                                             rowSize,
                                             sortFields,
                                             sortComplexity,
                                             averageUsecPerRow));
        }
    }

    private static final String FILLER_100;
    private static final String FILLER_200;
    private static final String FILLER_300;
    private static final String FILLER_400;

    static {
        StringBuilder buffer = new StringBuilder(100);
        for (int i = 0; i < 100; i++) {
            buffer.append('x');
        }
        FILLER_100 = buffer.toString();
        FILLER_200 = FILLER_100 + FILLER_100;
        FILLER_300 = FILLER_200 + FILLER_100;
        FILLER_400 = FILLER_300 + FILLER_100;
    }

    private static final int FILLER_100_COLUMN = 4;
    private static final int FILLER_200_COLUMN = FILLER_100_COLUMN + 1;
    private static final int FILLER_300_COLUMN = FILLER_200_COLUMN + 1;
    private static final int FILLER_400_COLUMN = FILLER_300_COLUMN + 1;
    private static final int WARMUP_RUNS = 10000;
    private static final int MEASUREMENT_RUNS = 10;

    private final Random random = new Random();
    private int t;
    private Group group;
    private Schema schema;
    private RowType tRowType;
    private StoreAdapter adapter;
}

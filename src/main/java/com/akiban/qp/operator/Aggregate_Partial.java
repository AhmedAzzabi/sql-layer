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

package com.akiban.qp.operator;

import com.akiban.qp.row.Row;
import com.akiban.qp.row.ValuesHolderRow;
import com.akiban.qp.rowtype.AggregatedRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.aggregation.Aggregator;
import com.akiban.server.aggregation.AggregatorFactory;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.util.ValueHolder;
import com.akiban.util.ArgumentValidation;
import com.akiban.util.ShareHolder;
import com.akiban.util.tap.InOutTap;
import com.akiban.util.tap.PointTap;
import com.akiban.util.tap.Tap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**

 <h1>Overview</h1>

 Aggregation_Partial applies a partial aggregation to rows. By partial
 we mean that if the aggregation contains a GROUP BY, output rows will
 be streamed out as soon as a change is detected in the GROUP BY
 columns; no attempt is made to sort or hash values. This means that if
 the incoming rows are not sorted by their GROUP BY columns, this
 operator may output more than one aggregation per GROUP BY value. If
 this happens, each aggregation happens independent of the others.

 <h1>Arguments</h1>

 <ul>

 <li><b>input:</b> the input operator

 <li><b>inputsIndex:</b> the first index of the input rows that
 represents an input; indexes before this are GROUP BY
 fields. Required: <i>0 <= inputsIndex < input.rowType().nFields()</i>

 <li><b>aggregatorFactory:</b> a mapping of <i>String -> Aggregator</i>

 <li><b>aggregatorNames:</b> a list of aggregator function to be given
 to the factory. Required: <i>inputsIndex + aggregatorNames.size() ==
 input.rowType().nFields()</i>

 </ul>

 <h1>Behavior</h1>

 This operator takes as input a row which is interpreted as having two
 sections: a GROUP BY section and an inputs section. These are
 delimited by the <i>inputsIndex</i> argument, which specifies the
 index of the first input. The operator also has a list of aggregator
 functions (specified by the <i>aggregatorNames</i> list), one per
 input.

 For each input row of type <i>input.rowType()</i>, the
 Aggregation_Partial's cursor applies each input to its appropriate
 aggregator. When the cursor notices a change in any of the GROUP BY
 columns, or when the input cursor is finished, the aggregation cursor
 outputs a row with the GROUP BY columns and the result of each
 aggregation.

 <h2>Example</h2>

 Let's say we have a table describing various SKUs in warehouses. Each
 warehouse has a category for SKUs, but these categories are not unique
 among all warehouses. We want to get the sum, min and max price of
 each category in each warehouse.

 <i>SELECT warehouse, product_category, SUM(price), MIN(price), MAX(price) FROM products GROUP BY warehouse, product_category</i>

 The input rows to the Aggregation_Partial cursor would be something like:

 <table>
 <tr><td> warehouse </td><td> product_category </td><td> price </td><td> price </td><td> price </td><td> notes </td></tr>
 <tr><td> 0001 </td><td> AAA </td><td>  5.00 </td><td>  5.00 </td><td>  5.00 </td><td> sku 1 </td></tr>
 <tr><td> 0001 </td><td> AAA </td><td> 10.00 </td><td> 10.00 </td><td> 10.00 </td><td> sku 2 </td></tr>
 <tr><td> 0002 </td><td> AAA </td><td>  7.00 </td><td>  7.00 </td><td>  7.00 </td><td> sku 3 </td></tr>
 <tr><td> 0002 </td><td> AAA </td><td>  3.00 </td><td>  3.00 </td><td>  3.00 </td><td> sku 4 </td></tr>
 <tr><td> 0002 </td><td> BBB </td><td> 11.00 </td><td> 11.00 </td><td> 11.00 </td><td> sku 5 </td></tr>
 </table>

 The <i>inputsIndex</i> here is <i>2</i>. Note that the "price" column has been repeated three times,
 once for each aggregate function. In this case, the input rows are already ordered by their GROUP BY columns;
 if we knew this were the case (due to another operator's ordering), this partial aggregation would also be a
 full aggregation.

 The output rows would look like:

 <table>
 <tr><td> warehouse </td><td> product_category </td><td> SUM(price) </td><td> MIN(price) </td><td> MAX(price) </td></tr>
 <tr><td> 0001 </td><td> AAA </td><td> 15.00 </td><td>  5.00 </td><td> 10.00 </td></tr>
 <tr><td> 0002 </td><td> AAA </td><td> 10.00 </td><td>  3.00 </td><td>  7.00 </td></tr>
 <tr><td> 0002 </td><td> BBB </td><td> 11.00 </td><td> 11.00 </td><td> 11.00 </td></tr>
 </table>

 <h2>Notes</h2>

 If there are no input rows, behavior depends on
 the <i>inputIndex</i>. If it is 0 (no GROUP BY) columns, this
 Aggregation_Partial will not output any rows. If <i>inputIndex > 0</i>
 (there is a GROUP BY), a single row will be outputted with all NULL
 values. This is due to the SQL spec.

 This operator cannot do something like an average directly. Instead,
 the operator tree would use this operator to get a SUM and COUNT of
 rows, and another operator would be responsible for dividing them to
 get the average.

 <h1>Output</h1>

 All input rows are swallowed. Output rows are as described above. All
 rows from the incoming operator with a type other
 than <i>input.rowType()</i> are passed through unchanged.

 <h1>Assumptions</h1>

 None; but if you want a full aggregation, it is up to you to pre-order
 the rows by their GROUP BY columns. If an aggregator is not amenable
 to this piecemeal aggregation, you should not use it with a partial
 aggregation (none of the aggregators we plan on writing have this
 problem).

 <h1>Performance</h1>

 Partially dictated by aggregators, though expected to be
 minimal. Comparison of GROUP BY columns is O(N).

 <h1>Memory requirements</h1>

 One row and one set of grouping column values.

 */

final class Aggregate_Partial extends Operator
{

    // Operator interface

    @Override
    protected Cursor cursor(QueryContext context) {
        List<Aggregator> aggregators = new ArrayList<Aggregator>();
        for (AggregatorFactory factory : aggregatorFactories) {
            aggregators.add(factory.get());
        }
        return new AggregateCursor(
                context,
                inputOperator.cursor(context),
                inputRowType,
                aggregatorFactories,
                aggregators,
                inputsIndex,
                outputType
        );
    }

    @Override
    public void findDerivedTypes(Set<RowType> derivedTypes) {
        inputOperator.findDerivedTypes(derivedTypes);
        derivedTypes.add(outputType);
    }

    @Override
    public List<Operator> getInputOperators() {
        return Collections.singletonList(inputOperator);
    }

    @Override
    public RowType rowType() {
        return outputType;
    }

    // AggregationOperator interface

    public Aggregate_Partial(Operator inputOperator,
                             RowType inputRowType,
                             int inputsIndex,
                             List<AggregatorFactory> aggregatorFactories) {
        this(
                inputOperator,
                inputRowType,
                inputsIndex,
                aggregatorFactories,
                inputRowType.schema().newAggregateType(inputRowType, inputsIndex, aggregatorFactories)
        );
    }

    // Object interface

    @Override
    public String toString() {
        if (inputsIndex == 0) {
            return String.format("Aggregation(without GROUP BY: %s)", aggregatorFactories);
        }
        if (inputsIndex == 1) {
            return String.format("Aggregation(GROUP BY 1 field, then: %s)", aggregatorFactories);
        }
        return String.format("Aggregation(GROUP BY %d fields, then: %s)", inputsIndex, aggregatorFactories);
    }


    // package-private (for testing)

    Aggregate_Partial(Operator inputOperator,
                      RowType inputRowType,
                      int inputsIndex,
                      List<AggregatorFactory> aggregatorFactories,
                      AggregatedRowType outputType) {
        this.inputOperator = inputOperator;
        this.inputRowType = inputRowType;
        this.inputsIndex = inputsIndex;
        this.aggregatorFactories = new ArrayList<AggregatorFactory>(aggregatorFactories);
        this.outputType = outputType;
        validate();
    }

    // private methods

    private void validate() {
        if (inputOperator == null || inputRowType == null || outputType == null)
            throw new NullPointerException();
        ArgumentValidation.isBetween("inputsIndex", 0, inputsIndex, inputRowType.nFields()+1);
        if (inputsIndex + aggregatorFactories.size() != inputRowType.nFields()) {
            throw new IllegalArgumentException(
                    String.format("inputsIndex(=%d) + aggregatorNames.size(=%d) != inputRowType.nFields(=%d)",
                            inputsIndex, aggregatorFactories.size(), inputRowType.nFields()
            ));
        }
    }
    
    // class state
    
    private static final InOutTap TAP_OPEN = OPERATOR_TAP.createSubsidiaryTap("operator: Aggregate_Partial open");
    private static final InOutTap TAP_NEXT = OPERATOR_TAP.createSubsidiaryTap("operator: Aggregate_Partial next");

    // object state

    private final Operator inputOperator;
    private final RowType inputRowType;
    private final AggregatedRowType outputType;
    private final int inputsIndex;
    private final List<AggregatorFactory> aggregatorFactories;

    // nested classes

    private static class AggregateCursor extends OperatorExecutionBase implements Cursor
    {

        // Cursor interface

        @Override
        public void open() {
            if (cursorState != CursorState.CLOSED)
                throw new IllegalStateException("can't open cursor: already open");
            TAP_OPEN.in();
            try {
                inputCursor.open();
                cursorState = CursorState.OPENING;
            } finally {
                TAP_OPEN.out();
            }
        }

        @Override
        public Row next() {
            TAP_NEXT.in();
            try {
                checkQueryCancelation();
                if (cursorState == CursorState.CLOSED)
                    throw new IllegalStateException("cursor not open");
                if (cursorState == CursorState.CLOSING) {
                    close();
                    return null;
                }

                assert cursorState == CursorState.OPENING || cursorState == CursorState.RUNNING : cursorState;
                while (true) {
                    Row input = nextInput();
                    if (input == null) {
                        if (everSawInput) {
                            cursorState = CursorState.CLOSING;
                            return createOutput();
                        }
                        else if (noGroupBy()) {
                            cursorState = CursorState.CLOSING;
                            return createEmptyOutput();
                        }
                        else {
                            close();
                            return null;
                        }
                    }
                    if (!input.rowType().equals(inputRowType)) {
                        return input; // pass through
                    }
                    everSawInput = true;
                    if (outputNeeded(input)) {
                        saveInput(input); // save this input for the next time this method is invoked
                        return createOutput();
                    }
                    aggregate(input);
                }
            } finally {
                TAP_NEXT.out();
            }
        }

        @Override
        public void close() {
            if (cursorState != CursorState.CLOSED) {
                holder.release();
                inputCursor.close();
                cursorState = CursorState.CLOSED;
            }
        }

        // for use in this class

        private void aggregate(Row input) {
            for (int i=0; i < aggregators.size(); ++i) {
                Aggregator aggregator = aggregators.get(i);
                int inputIndex = i + inputsIndex;
                aggregator.input(input.eval(inputIndex));
            }
        }

        private Row createOutput() {
            ValuesHolderRow outputRow = unsharedOutputRow();
            for(int i = 0; i < inputsIndex; ++i) {
                ValueHolder holder = outputRow.holderAt(i);
                ValueSource key = keyValues.get(i);
                holder.copyFrom(key);
            }
            for (int i = inputsIndex; i < inputRowType.nFields(); ++i) {
                ValueHolder holder = outputRow.holderAt(i);
                int aggregatorIndex = i - inputsIndex;
                AggregatorFactory factory = aggregatorFactories.get(aggregatorIndex);
                Aggregator aggregator = aggregators.get(aggregatorIndex);
                holder.expectType(factory.outputType());
                aggregator.output(holder);
            }
            return outputRow;
        }

        private Row createEmptyOutput() {
            assert noGroupBy() : "shouldn't be creating null output row when I have a grouping";
            ValuesHolderRow outputRow = unsharedOutputRow();
            for (int i = 0; i < outputRow.rowType().nFields(); ++i) {
                outputRow.holderAt(i).copyFrom(aggregators.get(i).emptyValue());
            }
            return outputRow;
        }

        private boolean noGroupBy() {
            return inputsIndex == 0;
        }

        private boolean outputNeeded(Row givenInput) {
            if (noGroupBy())
                return false;   // no GROUP BYs, so aggregate until givenInput is null

            // check for any changes to keys
            // Coming into this code, we're either RUNNING (within a GROUP BY run) or OPENING (about to start
            // a new run).
            if (cursorState == CursorState.OPENING) {
                // Copy over this row's values; switch mode to RUNNING; return false
                for (int i = 0; i < keyValues.size(); ++i) {
                    keyValues.get(i).copyFrom(givenInput.eval(i));
                }
                cursorState = CursorState.RUNNING;
                return false;
            }
            else {
                assert cursorState == CursorState.RUNNING : cursorState;
                // If any keys are different, switch mode to OPENING and return true; else return false.
                for (int i = 0; i < keyValues.size(); ++i) {
                    ValueHolder key = keyValues.get(i);
                    scratchValueHolder.copyFrom(givenInput.eval(i));
                    if (!scratchValueHolder.equals(key)) {
                        cursorState = CursorState.OPENING;
                        return true;
                    }
                }
                return false;
            }
        }

        private Row nextInput() {
            final Row result;
            if (holder.isHolding()) {
                result = holder.get();
                holder.release();
            }
            else {
                result = inputCursor.next();
            }
            return result;
        }

        private void saveInput(Row input) {
            assert holder.isEmpty() : holder;
            assert cursorState == CursorState.OPENING : cursorState;
            holder.hold(input);
        }

        private ValuesHolderRow unsharedOutputRow() {
            return new ValuesHolderRow(outputRowType); // TODO row sharing, etc
        }

        // AggregateCursor interface

        private AggregateCursor(QueryContext context,
                                Cursor inputCursor,
                                RowType inputRowType,
                                List<AggregatorFactory> aggregatorFactories,
                                List<Aggregator> aggregators,
                                int inputsIndex,
                                AggregatedRowType outputRowType) {
            super(context);
            this.inputCursor = inputCursor;
            this.inputRowType = inputRowType;
            this.aggregatorFactories = aggregatorFactories;
            this.aggregators = aggregators;
            this.inputsIndex = inputsIndex;
            this.outputRowType = outputRowType;
            keyValues = new ArrayList<ValueHolder>();
            for (int i = 0; i < inputsIndex; ++i) {
                keyValues.add(new ValueHolder());
            }
        }


        // object state

        private final Cursor inputCursor;
        private final RowType inputRowType;
        private final List<AggregatorFactory> aggregatorFactories;
        private final List<Aggregator> aggregators;
        private final int inputsIndex;
        private final AggregatedRowType outputRowType;
        private final List<ValueHolder> keyValues;
        private final ValueHolder scratchValueHolder = new ValueHolder();
        private final ShareHolder<Row> holder = new ShareHolder<Row>();
        private CursorState cursorState = CursorState.CLOSED;
        private boolean everSawInput = false;
    }

    private enum CursorState {
        /**
         * Freshly opened, or about to start a new run of group-bys
         */
        OPENING,
        /**
         * Within a run of group-bys
         */
        RUNNING,
        /**
         * The last row we returned (or the row we're about to return) is the last row; the next row will be
         * null, and will set the state to closing.
         */
        CLOSING,
        /**
         * The cursor is closed.
         */
        CLOSED
    }

}

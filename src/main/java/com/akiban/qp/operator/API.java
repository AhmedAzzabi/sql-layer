/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.akiban.qp.operator;

import com.akiban.ais.model.Group;
import com.akiban.ais.model.UserTable;
import com.akiban.qp.exec.UpdatePlannable;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.row.BindableRow;
import com.akiban.qp.row.RowBase;
import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.UserTableRowType;
import com.akiban.server.aggregation.AggregatorRegistry;
import com.akiban.server.aggregation.Aggregators;
import com.akiban.server.collation.AkCollator;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.std.FieldExpression;
import com.akiban.server.t3expressions.T3RegistryService;
import com.akiban.server.types.AkType;
import com.akiban.server.types3.TAggregator;
import com.akiban.server.types3.TComparison;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.Types3Switch;
import com.akiban.server.types3.texpressions.TPreparedExpression;
import com.akiban.server.types3.texpressions.TPreparedField;

import java.util.*;

public class API
{
    // Aggregate

    public static Operator aggregate_Partial(Operator inputOperator,
                                                     RowType rowType,
                                                     int inputsIndex,
                                                     AggregatorRegistry registry,
                                                     List<String> aggregatorNames,
                                                     List<Object> options)
    {
        return new Aggregate_Partial(
                inputOperator, rowType,
                inputsIndex,
                Aggregators.factories(
                        registry,
                        Aggregators.aggregatorIds(aggregatorNames, rowType, inputsIndex)
                ),
                options
        );
    }

    public static Operator aggregate_Partial(Operator inputOperator,
                                             RowType rowType,
                                             int inputsIndex,
                                             List<? extends TAggregator> aggregatorFactories,
                                             List<? extends TInstance> aggregatorTypes,
                                             List<Object> options
                                             )
    {
        return new Aggregate_Partial(inputOperator, rowType, inputsIndex, aggregatorFactories, aggregatorTypes, options);
    }

    // Project

    public static Operator project_Default(Operator inputOperator,
                                           RowType rowType,
                                           List<ExpressionGenerator> projections)
    {
        return new Project_Default(inputOperator, rowType, generateOld(projections), generateNew(projections));
    }

    public static List<Expression> generateOld(List<? extends ExpressionGenerator> expressionGenerators) {
        if ((expressionGenerators == null) || Types3Switch.ON)
            return null;
        List<Expression> results = new ArrayList<>(expressionGenerators.size());
        for (ExpressionGenerator generator : expressionGenerators) {
            results.add(generator.getExpression());
        }
        return results;
    }

    public static List<TPreparedExpression> generateNew(List<? extends ExpressionGenerator> expressionGenerators) {
        if ((expressionGenerators == null) || (!Types3Switch.ON) )
            return null;
        List<TPreparedExpression> results = new ArrayList<>(expressionGenerators.size());
        for (ExpressionGenerator generator : expressionGenerators) {
            results.add(generator.getTPreparedExpression());
        }
        return results;
    }

    public static Operator project_Default(Operator inputOperator,
                                                   RowType rowType,
                                                   List<Expression> projections,
                                                   List<? extends TPreparedExpression> pExpressions)
    {
        return new Project_Default(inputOperator, rowType, projections, pExpressions);
    }
    
    public static Operator project_Table(Operator inputOperator,
                                                 RowType inputRowType,
                                                 RowType outputRowType,
                                                 List<Expression> projections,
                                                 List<? extends TPreparedExpression> pExpressions)
    {
        return new Project_Default(inputOperator, inputRowType, outputRowType, projections, pExpressions);
    }
    // Flatten

    public static Operator flatten_HKeyOrdered(Operator inputOperator,
                                                       RowType parentType,
                                                       RowType childType,
                                                       JoinType joinType)
    {
        return flatten_HKeyOrdered(inputOperator, parentType, childType, joinType, EnumSet.noneOf(FlattenOption.class));
    }

    public static Operator flatten_HKeyOrdered(Operator inputOperator,
                                                       RowType parentType,
                                                       RowType childType,
                                                       JoinType joinType,
                                                       FlattenOption flag0)
    {
        return new Flatten_HKeyOrdered(inputOperator, parentType, childType, joinType, EnumSet.of(flag0));
    }

    public static Operator flatten_HKeyOrdered(Operator inputOperator,
                                                       RowType parentType,
                                                       RowType childType,
                                                       JoinType joinType,
                                                       FlattenOption flag0,
                                                       FlattenOption flag1)
    {
        return new Flatten_HKeyOrdered(inputOperator, parentType, childType, joinType, EnumSet.of(flag0, flag1));
    }

    public static Operator flatten_HKeyOrdered(Operator inputOperator,
                                                       RowType parentType,
                                                       RowType childType,
                                                       JoinType joinType,
                                                       EnumSet<FlattenOption> flags)
    {
        return new Flatten_HKeyOrdered(inputOperator, parentType, childType, joinType, flags);
    }

    // GroupScan

    public static Operator groupScan_Default(Group group)
    {
        return new GroupScan_Default(new GroupScan_Default.FullGroupCursorCreator(group));
    }

    public static Operator groupScan_Default(Group group,
                                             int hKeyBindingPosition,
                                             boolean deep,
                                             UserTable hKeyType,
                                             UserTable shortenUntil)
    {
        return new GroupScan_Default(
                new GroupScan_Default.PositionalGroupCursorCreator(group, hKeyBindingPosition, deep, hKeyType, shortenUntil));
    }

    // ValuesScan

    public static Operator valuesScan_Default (Collection<? extends BindableRow> rows, RowType rowType)
    {
        return new ValuesScan_Default (rows, rowType);
    }
    
    // BranchLookup

    public static Operator branchLookup_Default(Operator inputOperator,
                                                Group group,
                                                RowType inputRowType,
                                                UserTableRowType outputRowType,
                                                InputPreservationOption flag)
    {
        return branchLookup_Default(inputOperator, group, inputRowType, outputRowType, flag, NO_LIMIT);
    }

    public static Operator branchLookup_Default(Operator inputOperator,
                                                Group group,
                                                RowType inputRowType,
                                                UserTableRowType outputRowType,
                                                InputPreservationOption flag,
                                                Limit limit)
    {
        return new BranchLookup_Default(inputOperator, group, inputRowType, outputRowType, flag, limit);
    }

    /** deprecated */
    public static Operator branchLookup_Nested(Group group,
                                               RowType inputRowType,
                                               UserTableRowType outputRowType,
                                               InputPreservationOption flag,
                                               int inputBindingPosition)
    {
        return new BranchLookup_Nested(group,
                                       inputRowType,
                                       null,
                                       outputRowType,
                                       flag,
                                       inputBindingPosition);
    }

    public static Operator branchLookup_Nested(Group group,
                                               RowType inputRowType,
                                               UserTableRowType ancestorRowType,
                                               UserTableRowType outputRowType,
                                               InputPreservationOption flag,
                                               int inputBindingPosition)
    {
        return new BranchLookup_Nested(group,
                                       inputRowType,
                                       ancestorRowType,
                                       outputRowType,
                                       flag,
                                       inputBindingPosition);
    }

    // Limit

    public static Operator limit_Default(Operator inputOperator, int limitRows)
    {
        return limit_Default(inputOperator, limitRows, Types3Switch.ON);
    }

    public static Operator limit_Default(Operator inputOperator, int limitRows, boolean usePVals)
    {
        return new Limit_Default(inputOperator, limitRows, usePVals);
    }

    public static Operator limit_Default(Operator inputOperator,
                                         int skipRows,
                                         boolean skipIsBinding,
                                         int limitRows,
                                         boolean limitIsBinding)
    {
        return limit_Default(inputOperator, skipRows, skipIsBinding, limitRows, limitIsBinding, Types3Switch.ON);
    }
    public static Operator limit_Default(Operator inputOperator,
                                                 int skipRows,
                                                 boolean skipIsBinding,
                                                 int limitRows,
                                                 boolean limitIsBinding,
                                                 boolean usePVals)
    {
        return new Limit_Default(inputOperator, skipRows, skipIsBinding, limitRows, limitIsBinding, usePVals);
    }

    // AncestorLookup

    public static Operator ancestorLookup_Default(Operator inputOperator,
                                                  Group group,
                                                  RowType rowType,
                                                  Collection<UserTableRowType> ancestorTypes,
                                                  InputPreservationOption flag)
    {
        return new AncestorLookup_Default(inputOperator, group, rowType, ancestorTypes, flag);
    }

    public static Operator ancestorLookup_Nested(Group group,
                                                 RowType rowType,
                                                 Collection<UserTableRowType> ancestorTypes,
                                                 int hKeyBindingPosition)
    {
        return new AncestorLookup_Nested(group, rowType, ancestorTypes, hKeyBindingPosition);
    }

    // IndexScan

    /**
     * Creates a full ascending scan operator for the given index using LEFT JOIN semantics after the indexType's
     * tableType
     * @param indexType the index to scan
     * @return the scan operator
     * @deprecated use {@link #indexScan_Default(IndexRowType, IndexKeyRange, Ordering, IndexScanSelector, boolean)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static Operator indexScan_Default(IndexRowType indexType)
    {
        boolean usePValues = Types3Switch.ON;
        return indexScan_Default(indexType, false, IndexKeyRange.unbounded(indexType, usePValues), usePValues);
    }

    /**
     * Creates a full ascending scan operator for the given index using LEFT JOIN semantics after the indexType's
     * tableType
     * @param indexType the index to scan
     * @param reverse whether to scan in reverse order
     * @return the scan operator
     * @deprecated use {@link #indexScan_Default(IndexRowType, IndexKeyRange, Ordering, IndexScanSelector, boolean)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static Operator indexScan_Default(IndexRowType indexType, boolean reverse)
    {
        boolean usePValues = Types3Switch.ON;
        return indexScan_Default(indexType, reverse, IndexKeyRange.unbounded(indexType, usePValues), usePValues);
    }

    public static Operator indexScan_Default(IndexRowType indexType, boolean reverse, IndexKeyRange indexKeyRange)
    {
        return indexScan_Default(indexType, reverse, indexKeyRange, Types3Switch.ON);
    }
    /**
     * Creates a scan operator for the given index, using LEFT JOIN semantics after the indexType's tableType.
     * @param indexType the index to scan
     * @param reverse whether to scan in reverse order
     * @param indexKeyRange the scan range
     * @return the scan operator
     * @deprecated use {@link #indexScan_Default(IndexRowType, IndexKeyRange, Ordering, IndexScanSelector, boolean)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static Operator indexScan_Default(IndexRowType indexType, boolean reverse, IndexKeyRange indexKeyRange, boolean usePValues)
    {
        if (indexKeyRange == null) {
            indexKeyRange = IndexKeyRange.unbounded(indexType, usePValues);
        }
        return indexScan_Default(indexType, reverse, indexKeyRange, indexType.tableType(), usePValues);
    }
    public static Operator indexScan_Default(IndexRowType indexType,
                                             boolean reverse,
                                             IndexKeyRange indexKeyRange,
                                             UserTableRowType innerJoinUntilRowType)
    {
        return indexScan_Default(indexType, reverse, indexKeyRange, innerJoinUntilRowType, Types3Switch.ON);
    }

    /**
     * Creates a scan operator for the given index, using LEFT JOIN semantics after the given table type.
     * @param indexType the index to scan
     * @param reverse whether to scan in reverse order
     * @param indexKeyRange the scan range
     * @param innerJoinUntilRowType the table after which the scan should start using LEFT JOIN GI semantics.
     * @return the scan operator
     * @deprecated use {@link #indexScan_Default(IndexRowType, IndexKeyRange, Ordering, IndexScanSelector, boolean)}
     */
    @Deprecated
    public static Operator indexScan_Default(IndexRowType indexType,
                                             boolean reverse,
                                             IndexKeyRange indexKeyRange,
                                             UserTableRowType innerJoinUntilRowType,
                                             boolean usePVals)
    {
        Ordering ordering = new Ordering(usePVals);
        int fields = indexType.nFields();
        if (usePVals) {
            for (int f = 0; f < fields; f++) {
                ordering.append(null, new TPreparedField(indexType.typeInstanceAt(f), f), !reverse);
            }
        }
        else {
            for (int f = 0; f < fields; f++) {
                ordering.append(new FieldExpression(indexType, f), null, !reverse);
            }
        }
        return indexScan_Default(indexType, indexKeyRange, ordering, innerJoinUntilRowType, usePVals);
    }

    /**
     * Creates a scan operator for the given index, using LEFT JOIN semantics after the given table type.
     * @param indexType the index to scan
     * @param reverse whether to scan in reverse order
     * @param indexKeyRange the scan range
     * @param indexScanSelector
     * @return the scan operator
     * @deprecated use {@link #indexScan_Default(IndexRowType, IndexKeyRange, Ordering, IndexScanSelector, boolean)}
     */
    @Deprecated
    public static Operator indexScan_Default(IndexRowType indexType,
                                             boolean reverse,
                                             IndexKeyRange indexKeyRange,
                                             IndexScanSelector indexScanSelector,
                                             boolean usePVals)
    {
        Ordering ordering = new Ordering(usePVals);
        int fields = indexType.nFields();
        if (usePVals) {
            for (int f = 0; f < fields; f++) {
                ordering.append(null, new TPreparedField(indexType.typeInstanceAt(f), f), !reverse);
            }
        }
        else {
            for (int f = 0; f < fields; f++) {
                ordering.append(new FieldExpression(indexType, f), null, !reverse);
            }
        }
        return indexScan_Default(indexType, indexKeyRange, ordering, indexScanSelector, usePVals);
    }

    public static Operator indexScan_Default(IndexRowType indexType,
                                             IndexKeyRange indexKeyRange,
                                             Ordering ordering)
    {
        return indexScan_Default(indexType, indexKeyRange, ordering, Types3Switch.ON);
    }

    public static Operator indexScan_Default(IndexRowType indexType,
                                             IndexKeyRange indexKeyRange,
                                             Ordering ordering,
                                             boolean usePVals)
    {
        return indexScan_Default(indexType, indexKeyRange, ordering, indexType.tableType(), usePVals);
    }

    public static Operator indexScan_Default(IndexRowType indexType,
                                             IndexKeyRange indexKeyRange,
                                             Ordering ordering,
                                             UserTableRowType innerJoinUntilRowType)
    {
        return indexScan_Default(indexType, indexKeyRange, ordering, innerJoinUntilRowType, Types3Switch.ON);
    }

    public static Operator indexScan_Default(IndexRowType indexType,
                                             IndexKeyRange indexKeyRange,
                                             Ordering ordering,
                                             UserTableRowType innerJoinUntilRowType,
                                             boolean usePVals)
    {
        return indexScan_Default(indexType,
                                 indexKeyRange,
                                 ordering,
                                 IndexScanSelector.leftJoinAfter(indexType.index(),
                                                                 innerJoinUntilRowType.userTable()), usePVals);
    }

    public static Operator indexScan_Default(IndexRowType indexType,
                                             IndexKeyRange indexKeyRange,
                                             Ordering ordering,
                                             IndexScanSelector indexScanSelector)
    {
        return new IndexScan_Default(indexType, indexKeyRange, ordering, indexScanSelector, Types3Switch.ON);
    }


    public static Operator indexScan_Default(IndexRowType indexType,
                                             IndexKeyRange indexKeyRange,
                                             Ordering ordering,
                                             IndexScanSelector indexScanSelector,
                                             boolean usePVals)
    {
        return new IndexScan_Default(indexType, indexKeyRange, ordering, indexScanSelector, usePVals);
    }

    // Select

    public static Operator select_HKeyOrdered(Operator inputOperator,
                                              RowType predicateRowType,
                                              TPreparedExpression predicate)
    {
        return new Select_HKeyOrdered(inputOperator, predicateRowType, predicate);
    }

    public static Operator select_HKeyOrdered(Operator inputOperator,
                                                      RowType predicateRowType,
                                                      Expression predicate)
    {
        return new Select_HKeyOrdered(inputOperator, predicateRowType, predicate);
    }

    public static Operator select_HKeyOrdered(Operator inputOperator,
                                              RowType predicateRowType,
                                              ExpressionGenerator predicate)
    {
        if (Types3Switch.ON)
            return new Select_HKeyOrdered(inputOperator, predicateRowType, predicate.getTPreparedExpression());
        return new Select_HKeyOrdered(inputOperator, predicateRowType, predicate.getExpression());
    }

    // Filter

    public static Operator filter_Default(Operator inputOperator, Collection<? extends RowType> keepTypes)
    {
        return new Filter_Default(inputOperator, keepTypes);
    }

    // Product

    /** deprecated */
    public static Operator product_NestedLoops(Operator outerInput,
                                                       Operator innerInput,
                                                       RowType outerType,
                                                       RowType innerType,
                                                       int inputBindingPosition)
    {
        return new Product_NestedLoops(outerInput, innerInput, outerType, null, innerType, inputBindingPosition);
    }

    public static Operator product_NestedLoops(Operator outerInput,
                                                       Operator innerInput,
                                                       RowType outerType,
                                                       UserTableRowType branchType,
                                                       RowType innerType,
                                                       int inputBindingPosition)
    {
        return new Product_NestedLoops(outerInput, innerInput, outerType, branchType, innerType, inputBindingPosition);
    }

    // Count

    public static Operator count_Default(Operator input,
                                         RowType countType)
    {
        return new Count_Default(input, countType, Types3Switch.ON);
    }

    public static Operator count_Default(Operator input,
                                         RowType countType,
                                         boolean usePValues)
    {
        return new Count_Default(input, countType, usePValues);
    }

    public static Operator count_TableStatus(RowType tableType)
    {
        return count_TableStatus(tableType, Types3Switch.ON);
    }


    public static Operator count_TableStatus(RowType tableType, boolean usePValues)
    {
        return new Count_TableStatus(tableType, usePValues);
    }

    // Sort

    public static Operator sort_InsertionLimited(Operator inputOperator,
                                                 RowType sortType,
                                                 Ordering ordering,
                                                 SortOption sortOption,
                                                 int limit)
    {
        return new Sort_InsertionLimited(inputOperator, sortType, ordering, sortOption, limit);
    }

    public static Operator sort_Tree(Operator inputOperator,
                                     RowType sortType,
                                     Ordering ordering,
                                     SortOption sortOption)
    {
        return new Sort_Tree(inputOperator, sortType, ordering, sortOption, Types3Switch.ON);
    }

    public static Operator sort_Tree(Operator inputOperator, 
                                     RowType sortType, 
                                     Ordering ordering, 
                                     SortOption sortOption,
                                     boolean usePVals)
    {
        return new Sort_Tree(inputOperator, sortType, ordering, sortOption, usePVals);
    }

    public static Ordering ordering()
    {
        return ordering(Types3Switch.ON);
    }
    
    public static Ordering ordering(boolean usePVals)
    {
        return new Ordering(usePVals);
    }

    // Distinct
    public static Operator distinct_Partial(Operator input, RowType distinctType)
    {
        return new Distinct_Partial(input, distinctType, null, Types3Switch.ON);
    }

    public static Operator distinct_Partial(Operator input,
                                            RowType distinctType,
                                            List<AkCollator> collators,
                                            boolean usePValues)
    {
        return new Distinct_Partial(input, distinctType, collators, usePValues);
    }

    // Map

    public static Operator map_NestedLoops(Operator outerInput,
                                           Operator innerInput,
                                           int inputBindingPosition)
    {
        return new Map_NestedLoops(outerInput, innerInput, inputBindingPosition);
    }

    // IfEmpty

    public static Operator ifEmpty_Default(Operator input, RowType rowType,
                                           List<? extends Expression> expressions,
                                           List<? extends TPreparedExpression> pExpressions,
                                           InputPreservationOption inputPreservation)
    {
        return new IfEmpty_Default(input, rowType, expressions, pExpressions, inputPreservation);
    }

    public static Operator ifEmpty_Default(Operator input, RowType rowType,
                                           List<? extends ExpressionGenerator> expressions,
                                           InputPreservationOption inputPreservation)
    {
        return new IfEmpty_Default(input, rowType, generateOld(expressions), generateNew(expressions), inputPreservation);
    }

    // Union

    public static Operator unionAll(Operator input1, RowType input1RowType, Operator input2, RowType input2RowType)
    {
        return unionAll(input1, input1RowType, input2, input2RowType, Types3Switch.ON);
    }

    public static Operator unionAll(Operator input1, RowType input1RowType, Operator input2, RowType input2RowType,
                                    boolean usePvalues)
    {
        return new UnionAll_Default(input1, input1RowType, input2, input2RowType, usePvalues);
    }
    
    // Intersect
    
    /** deprecated */

    public static Operator intersect_Ordered(Operator leftInput, Operator rightInput,
                                             IndexRowType leftRowType, IndexRowType rightRowType,
                                             int leftOrderingFields,
                                             int rightOrderingFields,
                                             int comparisonFields,
                                             JoinType joinType,
                                             IntersectOption intersectOutput,
                                             List<TComparison> comparisons)
    {
        return intersect_Ordered(leftInput, rightInput,  leftRowType, rightRowType,
                leftOrderingFields, rightOrderingFields, comparisonFields, joinType, intersectOutput,
                Types3Switch.ON, comparisons);
    }

    public static Operator intersect_Ordered(Operator leftInput, Operator rightInput,
                                            IndexRowType leftRowType, IndexRowType rightRowType,
                                            int leftOrderingFields,
                                            int rightOrderingFields,
                                            int comparisonFields,
                                            JoinType joinType,
                                            IntersectOption intersectOutput,
                                            boolean usePValues,
                                            List<TComparison> comparisons)
    {
        if (comparisonFields < 0) {
            throw new IllegalArgumentException();
        }
        boolean[] ascending = new boolean[comparisonFields];
        Arrays.fill(ascending, true);
        return new Intersect_Ordered(leftInput, rightInput,
                                     leftRowType, rightRowType,
                                     leftOrderingFields,
                                     rightOrderingFields,
                                     ascending,
                                     joinType,
                                     EnumSet.of(intersectOutput),
                                     usePValues,
                                     comparisons);
    }

    public static Operator intersect_Ordered(Operator leftInput, Operator rightInput,
                                             IndexRowType leftRowType, IndexRowType rightRowType,
                                             int leftOrderingFields,
                                             int rightOrderingFields,
                                             boolean[] ascending,
                                             JoinType joinType,
                                             EnumSet<IntersectOption> intersectOptions,
                                             List<TComparison> comparisons)
    {
        return new Intersect_Ordered(leftInput, rightInput,
                leftRowType, rightRowType,
                leftOrderingFields,
                rightOrderingFields,
                ascending,
                joinType,
                intersectOptions,
                Types3Switch.ON,
                comparisons);
    }

    public static Operator intersect_Ordered(Operator leftInput, Operator rightInput,
                                            IndexRowType leftRowType, IndexRowType rightRowType,
                                            int leftOrderingFields,
                                            int rightOrderingFields,
                                            boolean[] ascending,
                                            JoinType joinType,
                                            EnumSet<IntersectOption> intersectOptions,
                                            boolean usePValues,
                                            List<TComparison> comparisons)
    {
        return new Intersect_Ordered(leftInput, rightInput,
                                     leftRowType, rightRowType,
                                     leftOrderingFields,
                                     rightOrderingFields,
                                     ascending,
                                     joinType,
                                     intersectOptions,
                                     usePValues,
                                     comparisons);
    }
    
    // Union

    public static Operator union_Ordered(Operator leftInput, Operator rightInput,
                                         IndexRowType leftRowType, IndexRowType rightRowType,
                                         int leftOrderingFields,
                                         int rightOrderingFields,
                                         boolean[] ascending,
                                         boolean outputEqual)
    {
        return new Union_Ordered(leftInput, rightInput,
                leftRowType, rightRowType,
                leftOrderingFields,
                rightOrderingFields,
                ascending, outputEqual,
                Types3Switch.ON);
    }

    public static Operator union_Ordered(Operator leftInput, Operator rightInput,
                                         IndexRowType leftRowType, IndexRowType rightRowType,
                                         int leftOrderingFields,
                                         int rightOrderingFields,
                                         boolean[] ascending,
                                         boolean outputEqual,
                                         boolean usePValues)
    {
        return new Union_Ordered(leftInput, rightInput,
                                 leftRowType, rightRowType,
                                 leftOrderingFields,
                                 rightOrderingFields,
                                 ascending, outputEqual,
                                 usePValues);
    }

    // HKeyUnion

    public static Operator hKeyUnion_Ordered(Operator leftInput, Operator rightInput,
                                             RowType leftRowType, RowType rightRowType,
                                             int leftOrderingFields, int rightOrderingFields,
                                             int comparisonFields,
                                             UserTableRowType outputHKeyTableRowType)
    {
        return new HKeyUnion_Ordered(leftInput, rightInput,
                                     leftRowType, rightRowType,
                                     leftOrderingFields, rightOrderingFields,
                                     comparisonFields,
                                     outputHKeyTableRowType);
    }

    // Using_BloomFilter

    public static Operator using_BloomFilter(Operator filterInput,
                                             RowType filterRowType,
                                             long estimatedRowCount,
                                             int filterBindingPosition,
                                             Operator streamInput)
    {
        return new Using_BloomFilter(filterInput,
                                     filterRowType,
                                     estimatedRowCount,
                                     filterBindingPosition,
                                     streamInput,
                                     null,
                                     Types3Switch.ON);
    }

    public static Operator using_BloomFilter(Operator filterInput,
                                             RowType filterRowType,
                                             long estimatedRowCount,
                                             int filterBindingPosition,
                                             Operator streamInput,
                                             List<AkCollator> collators,
                                             boolean usePValues)
    {
        return new Using_BloomFilter(filterInput,
                                     filterRowType,
                                     estimatedRowCount,
                                     filterBindingPosition,
                                     streamInput,
                                     collators,
                                     usePValues);
    }

    // Select_BloomFilter

    public static Operator select_BloomFilter(Operator input,
                                              Operator onPositive,
                                              List<? extends ExpressionGenerator> filterFields,
                                              int bindingPosition)
    {
        return select_BloomFilter(input, onPositive, generateOld(filterFields), generateNew(filterFields), null, bindingPosition);
    }

    public static Operator select_BloomFilter(Operator input,
                                              Operator onPositive,
                                              List<? extends Expression> filterFields,
                                              List<? extends TPreparedExpression> tFilterFields,
                                              int bindingPosition)
    {
        return select_BloomFilter(input, onPositive, filterFields, tFilterFields, null, bindingPosition);
    }

    public static Operator select_BloomFilter(Operator input,
                                              Operator onPositive,
                                              List<? extends Expression> filterFields,
                                              List<? extends TPreparedExpression> tFilterFields,
                                              List<AkCollator> collators,
                                              int bindingPosition)
    {
        return new Select_BloomFilter(input,
                                      onPositive,
                                      filterFields,
                                      tFilterFields,
                                      collators,
                                      bindingPosition);
    }

    public static Operator select_BloomFilter(Operator input,
                                              Operator onPositive,
                                              List<? extends ExpressionGenerator> filterFields,
                                              List<AkCollator> collators,
                                              int bindingPosition,
                                              ExpressionGenerator.ErasureMaker marker)
    {
        return new Select_BloomFilter(input,
                onPositive,
                generateOld(filterFields),
                generateNew(filterFields),
                collators,
                bindingPosition);
    }

    // EmitBoundRow_Nested

    public static Operator emitBoundRow_Nested(Operator input,
                                               RowType inputRowType,
                                               RowType outputRowType,
                                               RowType boundRowType,
                                               int bindingPosition)
    {
        return new EmitBoundRow_Nested(input,
                                       inputRowType, outputRowType, boundRowType,
                                       bindingPosition);
    }

    // Insert
    public static UpdatePlannable insert_Default(Operator inputOperator)
    {
        return insert_Default(inputOperator, Types3Switch.ON);
    }

    public static UpdatePlannable insert_Default(Operator inputOperator, boolean usePVals)
    {
        return new Insert_Default(inputOperator, usePVals);
    }

    public static Operator insert_Returning (Operator inputOperator, boolean usePVals) 
    {
        return new Insert_Returning (inputOperator, usePVals);
    }

    // Update

    public static UpdatePlannable update_Default(Operator inputOperator,
                                                 UpdateFunction updateFunction)
    {
        return new Update_Default(inputOperator, updateFunction);
    }
    
    public static Operator update_Returning (Operator inputOperator,
                                            UpdateFunction updateFunction, 
                                            boolean usePValues)
    {
        return new Update_Returning (inputOperator, updateFunction, usePValues);
    }
    
    // Delete

    public static UpdatePlannable delete_Default(Operator inputOperator, boolean usePValues)
    {
        return new Delete_Default(inputOperator, usePValues);
    }

    public static Operator delete_Returning (Operator inputOperator, boolean usePValues, boolean cascadeDelete)
    {
        return new Delete_Returning(inputOperator, usePValues, cascadeDelete);
    }

    // Execution interface

    public static Cursor cursor(Operator root, QueryContext context)
    {
        // if all they need is the wrapped cursor, create it directly
        return new ChainedCursor(context, root.cursor(context));
    }

    // Options

    // Flattening flags

    public static enum JoinType {
        INNER_JOIN,
        LEFT_JOIN,
        RIGHT_JOIN,
        FULL_JOIN
    }

    public static enum FlattenOption {
        KEEP_PARENT,
        KEEP_CHILD,
        LEFT_JOIN_SHORTENS_HKEY
    }

    // Lookup flags

    public static enum InputPreservationOption
    {
        KEEP_INPUT,
        DISCARD_INPUT
    }

    // Sort flags

    public static enum SortOption {
        PRESERVE_DUPLICATES,
        SUPPRESS_DUPLICATES
    }

    // Intersect output flags

    public static enum IntersectOption
    {
        OUTPUT_LEFT,
        OUTPUT_RIGHT,
        SEQUENTIAL_SCAN,
        SKIP_SCAN
    }

    // Ordering specification

    public static class Ordering
    {
        public String toString()
        {
            StringBuilder buffer = new StringBuilder();
            buffer.append('(');
            List<?> exprs = usingPVals() ? tExpressions : oExpressions;
            for (int i = 0, size = sortColumns(); i < size; i++) {
                if (i > 0) {
                    buffer.append(", ");
                }
                buffer.append(exprs.get(i));
                buffer.append(' ');
                buffer.append(directions.get(i) ? "ASC" : "DESC");
            }
            buffer.append(')');
            return buffer.toString();
        }

        public int sortColumns()
        {
            return usingPVals() ? tExpressions.size() : oExpressions.size();
        }

        public Expression expression(int i) {
            return oExpressions.get(i);
        }
        
        public TPreparedExpression tExpression(int i) {
            return tExpressions.get(i);
        }

        public AkType type(int i)
        {
            return oExpressions.get(i).valueType();
        }
        
        public TInstance tInstance(int i) {
            return tExpressions.get(i).resultType();
        }

        public boolean ascending(int i)
        {
            return directions.get(i);
        }

        public boolean allAscending()
        {
            boolean allAscending = true;
            for (Boolean direction : directions) {
                if (!direction) {
                    allAscending = false;
                }
            }
            return allAscending;
        }

        public boolean allDescending()
        {
            boolean allDescending = true;
            for (Boolean direction : directions) {
                if (direction) {
                    allDescending = false;
                }
            }
            return allDescending;
        }

        public AkCollator collator(int i)
        {
            return collators.get(i);
        }

        public void append(ExpressionGenerator expressionGenerator, boolean ascending)
        {
            TPreparedExpression newExpr;
            Expression oldExpr;
            if (Types3Switch.ON) {
                newExpr = expressionGenerator.getTPreparedExpression();
                oldExpr = null;
            }
            else {
                newExpr = null;
                oldExpr = expressionGenerator.getExpression();
            }
            append(oldExpr, newExpr, ascending);
        }

        public void append(Expression expression, boolean ascending)
        {
            append(expression, null, ascending);
        }

        public void append(Expression expression, TPreparedExpression tExpression, boolean ascending)
        {
            append(expression, tExpression, ascending, null);
        }
        
        public void append(ExpressionGenerator expression, boolean ascending, AkCollator collator)
        {
            Expression oldStyle;
            TPreparedExpression newStyle;
            if (Types3Switch.ON) {
                newStyle = expression.getTPreparedExpression();
                oldStyle = null;
            }
            else {
                newStyle = null;
                oldStyle = expression.getExpression();
            }
            append(oldStyle, newStyle, ascending, collator);
        }

        public void append(Expression expression, TPreparedExpression tExpression,  boolean ascending,
                           AkCollator collator)
        {
            if (oExpressions != null)
                oExpressions.add(expression);
            else
                tExpressions.add(tExpression);
            directions.add(ascending);
            collators.add(collator);
        }

        public Ordering copy()
        {
            boolean pvals = usingPVals();
            Ordering copy = new Ordering(pvals);
            if (pvals)
                copy.tExpressions.addAll(tExpressions);
            else
                copy.oExpressions.addAll(oExpressions);
            copy.directions.addAll(directions);
            copy.collators.addAll(collators);
            return copy;
        }
        
        public boolean usingPVals() {
            return tExpressions != null;
        }
        
        public Ordering() {
            this(Types3Switch.ON);
        }
        
        private Ordering(boolean usePVals) {
            if (usePVals) {
                tExpressions = new ArrayList<>();
                oExpressions = null; 
            }
            else {
                tExpressions = null;
                oExpressions = new ArrayList<>();
            }
        }

        private final List<com.akiban.server.expression.Expression> oExpressions;
        private final List<TPreparedExpression> tExpressions;
        private final List<Boolean> directions = new ArrayList<>(); // true: ascending, false: descending
        private final List<AkCollator> collators = new ArrayList<>();
    }

    // Class state

    private static final Limit NO_LIMIT = new Limit()
    {

        @Override
        public boolean limitReached(RowBase row)
        {
            return false;
        }

        @Override
        public String toString()
        {
            return "NO LIMIT";
        }

    };
}

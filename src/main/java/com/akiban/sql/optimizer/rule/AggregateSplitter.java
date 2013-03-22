
package com.akiban.sql.optimizer.rule;

import com.akiban.sql.optimizer.rule.AggregateMapper.AggregateSourceFinder;

import com.akiban.server.error.UnsupportedSQLException;

import com.akiban.sql.optimizer.plan.*;
import com.akiban.sql.optimizer.plan.AggregateSource.Implementation;

import com.akiban.sql.optimizer.plan.Sort.OrderByExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Aggregates need to be split into a Project and the aggregation
 * proper, so that the project can go into a nested loop Map. */
public class AggregateSplitter extends BaseRule
{
    private static final Logger logger = LoggerFactory.getLogger(AggregateSplitter.class);

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void apply(PlanContext plan) {
        List<AggregateSource> sources = new AggregateSourceFinder().find(plan.getPlan());
        for (AggregateSource source : sources) {
            split(source);
        }
    }

    protected void split(AggregateSource source) {
        assert !source.isProjectSplitOff();
        if (!source.hasGroupBy() && source.getAggregates().size() == 1) {
            AggregateFunctionExpression aggr1 = source.getAggregates().get(0);
            String fun = aggr1.getFunction();
            if ((fun.equals("COUNT")) && (aggr1.getOperand() == null)) {
                TableSource singleTable = trivialCountStar(source);
                if (singleTable != null) {
                    source.setImplementation(Implementation.COUNT_TABLE_STATUS);
                    source.setTable(singleTable);
                }
                else
                    // TODO: This is only to support the old
                    // Count_Default operator while we have it.
                    source.setImplementation(Implementation.COUNT_STAR);
                return;
            }
            else if (fun.equals("MIN") || fun.equals("MAX")) {
                if (directIndexMinMax(source)) {
                    source.setImplementation(Implementation.FIRST_FROM_INDEX);
                    // Still need project to get correct field.
                }
            }
        }
        
        Object[] distinctOrderby = checkDistinctDistinctsAndOrderby(source);
        List<ExpressionNode> fields = source.splitOffProject();
        PlanNode input = source.getInput();
        
        // order-by and distinct cannot exist together for now
        // so it's safe to do this:
        //
        // order by
        List<OrderByExpression> orderBy = (List<OrderByExpression>)distinctOrderby[1];
        PlanNode ninput;
        
        if (orderBy != null)
        {
            ninput = new Sort(input, orderBy);
            ninput = new Project(ninput, fields);
        }
        // distinct
        else if ((Boolean)distinctOrderby[0])
        {
            ninput = new Project(input, fields);
            ninput = new Distinct(ninput);
        }
        else
            ninput = new Project(input, fields);
        
        source.replaceInput(input, ninput);
    }

    /** Check that all the <code>DISTINCT</code> qualifications are
     * for the same expression and that there are no
     * non-<code>DISTINCT</code> unless they are for the same
     * expression and unaffected by it.
     */
    protected Object[] checkDistinctDistinctsAndOrderby(AggregateSource source) {
      //  boolean orderBy = false;
        boolean distinct = true;
        
        List<OrderByExpression> ret = null;
        
        ExpressionNode operand = null;
        for (AggregateFunctionExpression aggregate : source.getAggregates()) {
            if (aggregate.isDistinct()) {
                ExpressionNode other = aggregate.getOperand();
                if (operand == null)
                    operand = other;
                else if (!matchExpressionNode(operand, other))
                    throw new UnsupportedSQLException("More than one DISTINCT",
                                                      other.getSQLsource());
            }
            List<OrderByExpression> cur = aggregate.getOrderBy();
            if (ret == null)
                ret = cur;
            else if (cur != null && !cur.equals(ret))
                 throw new UnsupportedSQLException("Mix of ORDERY-BY ",
                                                   aggregate.getSQLsource());
        }
        if (operand == null)
            distinct = false;
        else
            for (AggregateFunctionExpression aggregate : source.getAggregates()) {
                if (!aggregate.isDistinct()) {
                    ExpressionNode other = aggregate.getOperand();
                    if (!matchExpressionNode(operand,other))
                        throw new UnsupportedSQLException("Mix of DISTINCT and non-DISTINCT",
                                                          operand.getSQLsource());
                    else if (!distinctDoesNotMatter(aggregate.getFunction()))
                        throw new UnsupportedSQLException("Mix of DISTINCT and non-DISTINCT",
                                                          other.getSQLsource());
                }
            }
       
        // both distinct and order-by are used, throw an error for now
        if (ret != null && distinct)
            throw new UnsupportedSQLException("Use of BOTH DISTINCT and ORDER-BY is not supported yet in" + source.getName());
        return new Object[]{distinct, ret};
    }

    protected boolean matchExpressionNode (ExpressionNode operand, ExpressionNode other) {
        if (operand instanceof CastExpression) {
            if (other instanceof CastExpression) {
                return ((CastExpression)operand).getOperand().equals(((CastExpression)operand).getOperand());
            }
            return ((CastExpression)operand).getOperand().equals(other);
        }
        else if (other instanceof CastExpression) {
            return ((CastExpression)other).getOperand().equals(operand);
        }
        else
            return operand.equals(other);
    }
    
    protected boolean distinctDoesNotMatter(String aggregateFunction) {
        return ("MAX".equals(aggregateFunction) ||
                "MIN".equals(aggregateFunction));
    }

    /** COUNT(*) from single table? */
    protected TableSource trivialCountStar(AggregateSource source) {
        PlanNode input = source.getInput();
        if (!(input instanceof Select))
            return null;
        Select select = (Select)input;
        if (!select.getConditions().isEmpty())
            return null;
        input = select.getInput();
        if (input instanceof SingleIndexScan) {
            SingleIndexScan index = (SingleIndexScan)input;
            if (index.isCovering() && !index.hasConditions() &&
                index.getIndex().isTableIndex())
                return index.getLeafMostTable();
        }
        else if (input instanceof Flatten) {
            Flatten flatten = (Flatten)input;
            if (flatten.getTableNodes().size() != 1)
                return null;
            input = flatten.getInput();
            if (input instanceof GroupScan)
                return flatten.getTableSources().get(0);
        }
        return null;
    }

    /** MIN(val) from index ordering by val? */
    protected boolean directIndexMinMax(AggregateSource source) {
        PlanNode input = source.getInput();
        if (!(input instanceof Select))
            return false;
        Select select = (Select)input;
        if (!select.getConditions().isEmpty())
            return false;
        input = select.getInput();
        if (!(input instanceof IndexScan))
            return false;
        IndexScan index = (IndexScan)input;
        int nequals = index.getNEquality();
        List<Sort.OrderByExpression> ordering = index.getOrdering();
        // Get number of leading columns available for ordering. This
        // includes those tested for equality, which only have that
        // one value.
        int ncols = (nequals < ordering.size()) ? (nequals + 1) : nequals;
        AggregateFunctionExpression aggr1 = source.getAggregates().get(0);
        for (int i = 0; i < ncols; i++) {
            Sort.OrderByExpression orderBy = ordering.get(i);
            if (orderBy.getExpression() == null) continue;
            if (orderBy.getExpression().equals(aggr1.getOperand())) {
                if ((i == nequals) &&
                    (orderBy.isAscending() != aggr1.getFunction().equals("MIN"))) {
                    // Fetching the MAX of an ascending index (or MIN
                    // of descending): reverse the scan to get it
                    // first.  (Order doesn't matter on the
                    // equalities, MIN and MAX are the same.)
                    for (Sort.OrderByExpression otherOtherBy : ordering) {
                        otherOtherBy.setAscending(!otherOtherBy.isAscending());
                    }
                }
                if ((index instanceof SingleIndexScan) &&
                    (index.getOrderEffectiveness() == IndexScan.OrderEffectiveness.NONE)) {
                    SingleIndexScan sindex = (SingleIndexScan)index;
                    if (sindex.getConditionRange() != null) {
                        // Need to make sure index gets the right kind of merge.
                        sindex.setOrderEffectiveness(IndexScan.OrderEffectiveness.FOR_MIN_MAX);
                    }
                }
                return true;
            }
        }
        return false;
    }

}

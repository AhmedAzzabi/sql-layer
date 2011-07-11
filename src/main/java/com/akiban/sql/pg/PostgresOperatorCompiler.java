/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.sql.pg;

import com.akiban.qp.exec.UpdatePlannable;
import com.akiban.qp.physicaloperator.PhysicalOperator;
import com.akiban.qp.rowtype.Schema;
import com.akiban.sql.StandardException;

import com.akiban.sql.optimizer.OperatorCompiler;
import static com.akiban.sql.optimizer.SimplifiedQuery.*;

import com.akiban.sql.parser.DMLStatementNode;
import com.akiban.sql.parser.SQLParser;
import com.akiban.sql.parser.StatementNode;
import com.akiban.sql.parser.ParameterNode;
import com.akiban.sql.types.DataTypeDescriptor;

import com.akiban.ais.model.Column;

import com.akiban.server.service.EventTypes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Compile SQL SELECT statements into operator trees if possible.
 */
public class PostgresOperatorCompiler extends OperatorCompiler
                                      implements PostgresStatementGenerator
{
    private static final Logger logger = LoggerFactory.getLogger(PostgresOperatorCompiler.class);

    public PostgresOperatorCompiler(PostgresServerSession server) {
        super(server.getParser(), server.getAIS(), server.getDefaultSchemaName());

        server.setAttribute("aisBinder", binder);
        server.setAttribute("compiler", this);
    }

    @Override
    public PostgresStatement parse(PostgresServerSession server,
                                   String sql, int[] paramTypes) 
            throws StandardException {
        // This very inefficient reparsing by every generator is actually avoided.
        SQLParser parser = server.getParser();
        return generate(server, parser.parseStatement(sql), 
                        parser.getParameterList(), paramTypes);
    }

    @Override
    public void sessionChanged(PostgresServerSession server) {
        binder.setDefaultSchemaName(server.getDefaultSchemaName());
    }

    static class PostgresResultColumn extends ResultColumnBase {
        private PostgresType type;
        
        public PostgresResultColumn(String name, PostgresType type) {
            super(name);
            this.type = type;
        }

        public PostgresType getType() {
            return type;
        }
    }

    @Override
    public ResultColumnBase getResultColumn(SimpleSelectColumn selectColumn) 
            throws StandardException {
        String name = selectColumn.getName();
        PostgresType type = null;
        SimpleExpression selectExpr = selectColumn.getExpression();
        if (selectExpr.isColumn()) {
            ColumnExpression columnExpression = (ColumnExpression)selectExpr;
            Column column = columnExpression.getColumn();
            if (selectColumn.isNameDefaulted())
                name = column.getName(); // User-preferred case.
            type = PostgresType.fromAIS(column);
        }
        else {
            type = PostgresType.fromDerby(selectColumn.getType());
        }
        return new PostgresResultColumn(name, type);
    }

    @Override
    public PostgresStatement generate(PostgresServerSession session,
                                      StatementNode stmt, 
                                      List<ParameterNode> params, int[] paramTypes)
            throws StandardException {
        if (!(stmt instanceof DMLStatementNode))
            return null;
        DMLStatementNode dmlStmt = (DMLStatementNode)stmt;
        Result result = null;
        try {
            session.getSessionTracer().beginEvent(EventTypes.COMPILE);
            result = compile(session.getSessionTracer(), dmlStmt, params);
        } finally {
            session.getSessionTracer().endEvent();
        }

        logger.debug("Operator:\n{}", result);

        PostgresType[] parameterTypes = null;
        if (result.getParameterTypes() != null) {
            DataTypeDescriptor[] sqlTypes = result.getParameterTypes();
            int nparams = sqlTypes.length;
            parameterTypes = new PostgresType[nparams];
            for (int i = 0; i < nparams; i++) {
                DataTypeDescriptor sqlType = sqlTypes[i];
                if (sqlType != null)
                    parameterTypes[i] = PostgresType.fromDerby(sqlType);
            }
        }

        if (result.isModify())
            return new PostgresModifyOperatorStatement(stmt.statementToString(),
                                                       (UpdatePlannable) result.getResultOperator(),
                                                       parameterTypes);
        else {
            int ncols = result.getResultColumns().size();
            List<String> columnNames = new ArrayList<String>(ncols);
            List<PostgresType> columnTypes = new ArrayList<PostgresType>(ncols);
            for (ResultColumnBase rcBase : result.getResultColumns()) {
                PostgresResultColumn resultColumn = (PostgresResultColumn)rcBase;
                columnNames.add(resultColumn.getName());
                columnTypes.add(resultColumn.getType());
            }
            return new PostgresOperatorStatement((PhysicalOperator)result.getResultOperator(),
                                                 columnNames, columnTypes,
                                                 parameterTypes,
                                                 result.getOffset(),
                                                 result.getLimit());
        }
    }

    protected Schema getSchema() {
        return schema;
    }

}

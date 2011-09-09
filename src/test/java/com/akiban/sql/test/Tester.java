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

package com.akiban.sql.test;

import com.akiban.sql.StandardException;
import com.akiban.sql.compiler.BooleanNormalizer;
import com.akiban.sql.optimizer.AISBinder;
import com.akiban.sql.optimizer.AISTypeComputer;
import com.akiban.sql.optimizer.BindingNodeFactory;
import com.akiban.sql.optimizer.BoundNodeToString;
import com.akiban.sql.optimizer.Grouper;
import com.akiban.sql.optimizer.OperatorCompiler;
import com.akiban.sql.optimizer.OperatorCompilerTest;
import com.akiban.sql.optimizer.SubqueryFlattener;
import com.akiban.sql.optimizer.simplified.SimplifiedQuery;
import com.akiban.sql.optimizer.simplified.SimplifiedDeleteStatement;
import com.akiban.sql.optimizer.simplified.SimplifiedInsertStatement;
import com.akiban.sql.optimizer.simplified.SimplifiedSelectQuery;
import com.akiban.sql.optimizer.simplified.SimplifiedUpdateStatement;
import com.akiban.sql.optimizer.plan.AST;
import com.akiban.sql.optimizer.plan.PlanNode;
import com.akiban.sql.optimizer.plan.PlanToString;
import com.akiban.sql.optimizer.rule.ASTToStatement;
import com.akiban.sql.optimizer.rule.AggregateMapper;
import com.akiban.sql.parser.CursorNode;
import com.akiban.sql.parser.DMLStatementNode;
import com.akiban.sql.parser.DeleteNode;
import com.akiban.sql.parser.InsertNode;
import com.akiban.sql.parser.NodeTypes;
import com.akiban.sql.parser.SQLParser;
import com.akiban.sql.parser.StatementNode;
import com.akiban.sql.parser.UpdateNode;
import com.akiban.sql.parser.ValueNode;
import com.akiban.sql.pg.PostgresSessionTracer;
import com.akiban.sql.views.ViewDefinition;

import com.akiban.ais.ddl.SchemaDef;
import com.akiban.ais.ddl.SchemaDefToAis;
import com.akiban.ais.model.AkibanInformationSchema;

import java.util.*;
import java.io.FileReader;

/** Standalone testing. */
public class Tester
{
    enum Action { 
        ECHO, PARSE, CLONE,
        PRINT_TREE, PRINT_SQL, PRINT_BOUND_SQL,
        BIND, COMPUTE_TYPES,
        BOOLEAN_NORMALIZE, FLATTEN_SUBQUERIES,
        GROUP, GROUP_REWRITE, 
        SIMPLIFY, SIMPLIFY_REORDER, PLAN_0, PLAN_1, PLAN_2, OPERATORS
    }

    List<Action> actions;
    SQLParser parser;
    BoundNodeToString unparser;
    AISBinder binder;
    AISTypeComputer typeComputer;
    BooleanNormalizer booleanNormalizer;
    SubqueryFlattener subqueryFlattener;
    Grouper grouper;
    OperatorCompiler operatorCompiler;
    int repeat;

    public Tester() {
        actions = new ArrayList<Action>();
        parser = new SQLParser();
        parser.setNodeFactory(new BindingNodeFactory(parser.getNodeFactory()));
        unparser = new BoundNodeToString();
        typeComputer = new AISTypeComputer();
        booleanNormalizer = new BooleanNormalizer(parser);
        subqueryFlattener = new SubqueryFlattener(parser);
        grouper = new Grouper(parser);
    }

    public void addAction(Action action) {
        actions.add(action);
    }

    public int getRepeat() {
        return repeat;
    }
    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }

    public void process(String sql) throws Exception {
        process(sql, false);
        if (repeat > 0) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < repeat; i++) {
                process(sql, true);
            }
            long end =  System.currentTimeMillis();
            System.out.println((end - start) + " ms.");
        }
    }

    public void process(String sql, boolean silent) throws Exception {
        StatementNode stmt = null;
        for (Action action : actions) {
            switch (action) {
            case ECHO:
                if (!silent) {
                    System.out.println("=====");
                    System.out.println(sql);
                }
                break;
            case PARSE:
                stmt = parser.parseStatement(sql);
                break;
            case CLONE:
                stmt = (StatementNode)parser.getNodeFactory().copyNode(stmt, parser);
                break;
            case PRINT_TREE:
                stmt.treePrint();
                break;
            case PRINT_SQL:
            case PRINT_BOUND_SQL:
                {
                    unparser.setUseBindings(action == Action.PRINT_BOUND_SQL);
                    String usql = unparser.toString(stmt);
                    if (!silent)
                        System.out.println(usql);
                }
                break;
            case BIND:
                binder.bind(stmt);
                break;
            case COMPUTE_TYPES:
                typeComputer.compute(stmt);
                break;
            case BOOLEAN_NORMALIZE:
                stmt = booleanNormalizer.normalize(stmt);
                break;
            case FLATTEN_SUBQUERIES:
                stmt = subqueryFlattener.flatten((DMLStatementNode)stmt);
                break;
            case GROUP:
                grouper.group(stmt);
                break;
            case GROUP_REWRITE:
                grouper.group(stmt);
                grouper.rewrite(stmt);
                break;
            case SIMPLIFY:
            case SIMPLIFY_REORDER:
                {
                    SimplifiedQuery query = simplify((DMLStatementNode)stmt,
                                                     grouper.getJoinConditions());
                    if (action == Action.SIMPLIFY_REORDER)
                        query.reorderJoins();
                    if (!silent)
                        System.out.println(query);
                }
                break;
            case PLAN_0:
            case PLAN_1:
            case PLAN_2:
                {
                    PlanNode plan = new AST((DMLStatementNode)stmt);
                    if (action != Action.PLAN_0) {
                        plan = new ASTToStatement().apply(plan);
                    }
                    if ((action != Action.PLAN_0) &&
                        (action != Action.PLAN_1)) {
                        plan = new AggregateMapper().apply(plan);
                    }
                    System.out.println(PlanToString.of(plan));
                }
                break;
            case OPERATORS:
                {
                    Object compiled = operatorCompiler.compile(new PostgresSessionTracer(1, false),
                                                               (DMLStatementNode)stmt,
                                                               parser.getParameterList());
                    if (!silent)
                        System.out.println(compiled);
                }
                break;
            }
        }
    }

    private static SimplifiedQuery simplify(DMLStatementNode stmt,
                                            Set<ValueNode> joinConditions) 
            throws Exception {
        switch (stmt.getNodeType()) {
        case NodeTypes.CURSOR_NODE:
            return new SimplifiedSelectQuery((CursorNode)stmt, joinConditions);
        case NodeTypes.DELETE_NODE:
            return new SimplifiedDeleteStatement((DeleteNode)stmt, joinConditions);
        case NodeTypes.UPDATE_NODE:
            return new SimplifiedUpdateStatement((UpdateNode)stmt, joinConditions);
        case NodeTypes.INSERT_NODE:
            return new SimplifiedInsertStatement((InsertNode)stmt, joinConditions);
        default:
            throw new StandardException("Unsupported statement type: " +
                                        stmt.statementToString());
        }
    }

    public void setSchema(String sql) throws Exception {
        SchemaDef schemaDef = SchemaDef.parseSchema("use user; " + sql);
        SchemaDefToAis toAis = new SchemaDefToAis(schemaDef, false);
        AkibanInformationSchema ais = toAis.getAis();
        if (actions.indexOf(Action.BIND) >= 0)
            binder = new AISBinder(ais, "user");
        if (actions.indexOf(Action.OPERATORS) >= 0)
            operatorCompiler = OperatorCompilerTest.TestOperatorCompiler.create(parser, ais, "user");
    }

    public void addView(String sql) throws Exception {
        ViewDefinition view = new ViewDefinition(sql, parser);
        if (binder != null)
            binder.addView(view);
        if (operatorCompiler != null)
            operatorCompiler.addView(view);
    }

    public static String maybeFile(String sql) throws Exception {
        if (!sql.startsWith("@"))
            return sql;
        FileReader reader = null;
        try {
            reader = new FileReader(sql.substring(1));
            StringBuilder str = new StringBuilder();
            char[] buf = new char[128];
            while (true) {
                int nc = reader.read(buf);
                if (nc < 0) break;
                str.append(buf, 0, nc);
            }
            return str.toString();
        }
        finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: Tester " +
                               "[-clone] [-bind] [-types] [-boolean] [-flatten] [-group] [-group-rewrite] [-simplify] [-simplify-reorder] [-plan-{0,1,2}] [-operators]" +
                               "[-tree] [-print] [-print-bound]" +
                               "[-schema ddl] [-view ddl]..." +
                               "sql...");
            System.out.println("Examples:");
            System.out.println("-tree 'SELECT t1.x+2 FROM t1'");
            System.out.println("-bind -print -tree -schema 'CREATE TABLE t1(x INT NOT NULL, y VARCHAR(7), z DECIMAL); CREATE table t2(w CHAR(1) NOT NULL);' -view 'CREATE VIEW v1(x,y) AS SELECT y,z FROM t1 WHERE y IS NOT NULL' \"SELECT x FROM v1 WHERE y > 'foo'\"");
            System.out.println("-operators -schema 'CREATE TABLE parent(id INT, PRIMARY KEY(id), name VARCHAR(256) NOT NULL, UNIQUE(name), state CHAR(2)); CREATE TABLE child(id INT, PRIMARY KEY(id), pid INT, CONSTRAINT `__akiban_fk0` FOREIGN KEY akibanfk(pid) REFERENCES parent(id), name VARCHAR(256) NOT NULL);' \"SELECT parent.name,child.name FROM parent,child WHERE child.pid = parent.id AND parent.state = 'MA'\"");

        }
        Tester tester = new Tester();
        tester.addAction(Action.ECHO);
        tester.addAction(Action.PARSE);
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.startsWith("-")) {
                if ("-tree".equals(arg))
                    tester.addAction(Action.PRINT_TREE);
                else if ("-print".equals(arg))
                    tester.addAction(Action.PRINT_SQL);
                else if ("-print-bound".equals(arg))
                    tester.addAction(Action.PRINT_BOUND_SQL);
                else if ("-clone".equals(arg))
                    tester.addAction(Action.CLONE);
                else if ("-bind".equals(arg))
                    tester.addAction(Action.BIND);
                else if ("-schema".equals(arg))
                    tester.setSchema(maybeFile(args[i++]));
                else if ("-view".equals(arg))
                    tester.addView(maybeFile(args[i++]));
                else if ("-types".equals(arg))
                    tester.addAction(Action.COMPUTE_TYPES);
                else if ("-boolean".equals(arg))
                    tester.addAction(Action.BOOLEAN_NORMALIZE);
                else if ("-flatten".equals(arg))
                    tester.addAction(Action.FLATTEN_SUBQUERIES);
                else if ("-group".equals(arg))
                    tester.addAction(Action.GROUP);
                else if ("-group-rewrite".equals(arg))
                    tester.addAction(Action.GROUP_REWRITE);
                else if ("-simplify".equals(arg))
                    tester.addAction(Action.SIMPLIFY);
                else if ("-simplify-reorder".equals(arg))
                    tester.addAction(Action.SIMPLIFY_REORDER);
                else if ("-plan-0".equals(arg))
                    tester.addAction(Action.PLAN_0);
                else if ("-plan-1".equals(arg))
                    tester.addAction(Action.PLAN_1);
                else if ("-plan-2".equals(arg))
                    tester.addAction(Action.PLAN_2);
                else if ("-operators".equals(arg))
                    tester.addAction(Action.OPERATORS);
                else if ("-repeat".equals(arg))
                    tester.setRepeat(Integer.parseInt(args[i++]));
                else
                    throw new Exception("Unknown switch: " + arg);
            }
            else {
                try {
                    tester.process(maybeFile(arg));
                }
                catch (StandardException ex) {
                    System.out.flush();
                    ex.printStackTrace();
                }
            }
        }
    }
}

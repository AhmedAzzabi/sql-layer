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

import com.akiban.server.aggregation.AggregatorRegistry;
import com.akiban.server.expression.ExpressionFactory;
import com.akiban.server.service.dxl.DXLService;
import com.akiban.server.service.instrumentation.InstrumentationService;
import com.akiban.server.service.session.SessionService;
import com.akiban.server.service.tree.TreeService;
import com.akiban.server.store.Store;

public final class PostgresServiceRequirements {

    public PostgresServiceRequirements(DXLService dxlService,
                                       InstrumentationService instrumentation,
                                       SessionService sessionService,
                                       Store store,
                                       TreeService treeService,
                                       ExpressionFactory expressionFactory,
                                       AggregatorRegistry aggregatorRegistry
                                       ) {
        this.instrumentation = instrumentation;
        this.dxlService = dxlService;
        this.sessionService = sessionService;
        this.store = store;
        this.treeService = treeService;
        this.expressionFactory = expressionFactory;
        this.aggregatorRegistry = aggregatorRegistry;
    }

    public InstrumentationService instrumentation() {
        return instrumentation;
    }

    public DXLService dxl() {
        return dxlService;
    }

    public SessionService sessionService() {
        return sessionService;
    }

    public Store store() {
        return store;
    }

    public TreeService treeService() {
        return treeService;
    }

    public ExpressionFactory expressionFactory() {
        return expressionFactory;
    }

    public AggregatorRegistry aggregatorRegistry() {
        return aggregatorRegistry;
    }

    private final InstrumentationService instrumentation;
    private final DXLService dxlService;
    private final SessionService sessionService;
    private final Store store;
    private final TreeService treeService;
    private final ExpressionFactory expressionFactory;
    private final AggregatorRegistry aggregatorRegistry;
}

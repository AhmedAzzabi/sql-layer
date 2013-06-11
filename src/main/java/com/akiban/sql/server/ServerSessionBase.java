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

package com.akiban.sql.server;

import com.akiban.ais.model.UserTable;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.operator.StoreAdapter;
import com.akiban.qp.memoryadapter.MemoryAdapter;
import com.akiban.server.error.AkibanInternalException;
import com.akiban.server.error.InvalidOperationException;
import com.akiban.server.error.InvalidParameterValueException;
import com.akiban.server.error.NoTransactionInProgressException;
import com.akiban.server.error.TransactionAbortedException;
import com.akiban.server.error.TransactionInProgressException;
import com.akiban.server.error.TransactionReadOnlyException;
import com.akiban.server.service.ServiceManager;
import com.akiban.server.service.dxl.DXLService;
import com.akiban.server.service.externaldata.ExternalDataService;
import com.akiban.server.service.functions.FunctionsRegistry;
import com.akiban.server.service.monitor.SessionMonitor;
import com.akiban.server.service.routines.RoutineLoader;
import com.akiban.server.service.security.SecurityService;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.transaction.TransactionService;
import com.akiban.server.service.tree.KeyCreator;
import com.akiban.server.service.tree.TreeService;
import com.akiban.server.t3expressions.T3RegistryService;
import com.akiban.sql.optimizer.AISBinderContext;
import com.akiban.sql.optimizer.rule.cost.CostEstimator;

import java.util.*;

public abstract class ServerSessionBase extends AISBinderContext implements ServerSession
{
    public static final String COMPILER_PROPERTIES_PREFIX = "optimizer.";

    protected final ServerServiceRequirements reqs;
    protected Properties compilerProperties;
    protected Map<String,Object> attributes = new HashMap<>();
    
    protected Session session;
    protected Map<StoreAdapter.AdapterType, StoreAdapter> adapters = 
        new HashMap<>();
    protected ServerTransaction transaction;
    protected boolean transactionDefaultReadOnly = false;
    protected ServerSessionMonitor sessionMonitor;

    protected Long queryTimeoutMilli = null;
    protected ServerValueEncoder.ZeroDateTimeBehavior zeroDateTimeBehavior = ServerValueEncoder.ZeroDateTimeBehavior.NONE;
    protected QueryContext.NotificationLevel maxNotificationLevel = QueryContext.NotificationLevel.INFO;

    public ServerSessionBase(ServerServiceRequirements reqs) {
        this.reqs = reqs;
    }

    @Override
    public void setProperty(String key, String value) {
        String ovalue = (String)properties.get(key); // Not inheriting.
        super.setProperty(key, value);
        try {
            if (!propertySet(key, properties.getProperty(key)))
                sessionChanged();   // Give individual handlers a chance.
        }
        catch (InvalidOperationException ex) {
            super.setProperty(key, ovalue);
            try {
                if (!propertySet(key, properties.getProperty(key)))
                    sessionChanged();
            }
            catch (InvalidOperationException ex2) {
                throw new AkibanInternalException("Error recovering " + key + " setting",
                                                  ex2);
            }
            throw ex;
        }
    }

    protected void setProperties(Properties properties) {
        super.setProperties(properties);
        for (String key : properties.stringPropertyNames()) {
            propertySet(key, properties.getProperty(key));
        }
        sessionChanged();
    }

    /** React to a property change.
     * Implementers are not required to remember the old state on
     * error, but must not leave things in such a mess that reverting
     * to the old value will not work.
     * @see InvalidParameterValueException
     **/
    protected boolean propertySet(String key, String value) {
        if ("zeroDateTimeBehavior".equals(key)) {
            zeroDateTimeBehavior = ServerValueEncoder.ZeroDateTimeBehavior.fromProperty(value);
            return true;
        }
        if ("maxNotificationLevel".equals(key)) {
            maxNotificationLevel = (value == null) ? 
                QueryContext.NotificationLevel.INFO :
                QueryContext.NotificationLevel.valueOf(value);
            return true;
        }
        if ("queryTimeoutSec".equals(key)) {
            if (value == null)
                queryTimeoutMilli = null;
            else
                queryTimeoutMilli = (long)(Double.parseDouble(value) * 1000);
            return true;
        }
        return false;
    }

    @Override
    public void setDefaultSchemaName(String defaultSchemaName) {
        super.setDefaultSchemaName(defaultSchemaName);
        sessionChanged();
    }

    protected abstract void sessionChanged();

    @Override
    public Map<String,Object> getAttributes() {
        return attributes;
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object attr) {
        attributes.put(key, attr);
        sessionChanged();
    }

    @Override
    public DXLService getDXL() {
        return reqs.dxl();
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public AISBinderContext getBinderContext() {
        return this;
    }

    @Override
    public Properties getCompilerProperties() {
        if (compilerProperties == null)
            compilerProperties = reqs.config().deriveProperties(COMPILER_PROPERTIES_PREFIX);
        return compilerProperties;
    }

    @Override
    public SessionMonitor getSessionMonitor() {
        return sessionMonitor;
     }

    @Override
    public StoreAdapter getStore() {
        return adapters.get(StoreAdapter.AdapterType.STORE_ADAPTER);
    }
    
    @Override
    public StoreAdapter getStore(UserTable table) {
        if (table.hasMemoryTableFactory()) {
            return adapters.get(StoreAdapter.AdapterType.MEMORY_ADAPTER);
        }
        return adapters.get(StoreAdapter.AdapterType.STORE_ADAPTER);
    }

    @Override
    public TreeService getTreeService() {
        return reqs.treeService();
    }

    @Override
    public TransactionService getTransactionService() {
        return reqs.txnService();
    }

    @Override
    public boolean isTransactionActive() {
        return (transaction != null);
    }

    @Override
    public boolean isTransactionRollbackPending() {
        return ((transaction != null) && transaction.isRollbackPending());
    }

    @Override
    public void beginTransaction() {
        if (transaction != null)
            throw new TransactionInProgressException();
        transaction = new ServerTransaction(this, transactionDefaultReadOnly);
    }

    @Override
    public void commitTransaction() {
        if (transaction == null)
            throw new NoTransactionInProgressException();
        try {
            transaction.commit();            
        }
        finally {
            transaction = null;
        }
    }

    @Override
    public void rollbackTransaction() {
        if (transaction == null)
            throw new NoTransactionInProgressException();
        try {
            transaction.rollback();
        }
        finally {
            transaction = null;
        }
    }

    @Override
    public void setTransactionReadOnly(boolean readOnly) {
        if (transaction == null)
            throw new NoTransactionInProgressException();
        transaction.setReadOnly(readOnly);
    }

    @Override
    public void setTransactionDefaultReadOnly(boolean readOnly) {
        this.transactionDefaultReadOnly = readOnly;
    }

    @Override
    public FunctionsRegistry functionsRegistry() {
        return reqs.functionsRegistry();
    }

    @Override
    public T3RegistryService t3RegistryService() {
        return reqs.t3RegistryService();
    }

    @Override
    public RoutineLoader getRoutineLoader() {
        return reqs.routineLoader();
    }

    @Override
    public ExternalDataService getExternalDataService() {
        return reqs.externalData();
    }

    @Override
    public SecurityService getSecurityService() {
        return reqs.securityService();
    }

    @Override
    public ServiceManager getServiceManager() {
        return reqs.serviceManager();
    }

    @Override
    public Date currentTime() {
        return new Date();
    }

    @Override
    public long getQueryTimeoutMilli() {
        if (queryTimeoutMilli != null)
            return queryTimeoutMilli;
        else
            return reqs.config().queryTimeoutMilli();
    }

    @Override
    public ServerValueEncoder.ZeroDateTimeBehavior getZeroDateTimeBehavior() {
        return zeroDateTimeBehavior;
    }

    @Override
    public CostEstimator costEstimator(ServerOperatorCompiler compiler, KeyCreator keyCreator) {
        return new ServerCostEstimator(this, reqs, compiler, keyCreator);
    }

    protected void initAdapters(ServerOperatorCompiler compiler) {
        // Add the Store Adapter - default for most tables
        adapters.put(StoreAdapter.AdapterType.STORE_ADAPTER,
                     reqs.store().createAdapter(session, compiler.getSchema()));
        // Add the Memory Adapter - for the in memory tables
        adapters.put(StoreAdapter.AdapterType.MEMORY_ADAPTER, 
                     new MemoryAdapter(compiler.getSchema(),
                                       session,
                                       reqs.config()));
    }

    /** Prepare to execute given statement.
     * Uses current global transaction or makes a new local one.
     * Returns any local transaction that should be committed / rolled back immediately.
     */
    protected ServerTransaction beforeExecute(ServerStatement stmt) {
        ServerStatement.TransactionMode transactionMode = stmt.getTransactionMode();
        ServerTransaction localTransaction = null;
        if (transaction != null) {
            // Use global transaction.
            transaction.checkTransactionMode(transactionMode);
        }
        else {
            switch (transactionMode) {
            case REQUIRED:
            case REQUIRED_WRITE:
                throw new NoTransactionInProgressException();
            case READ:
            case NEW:
                localTransaction = new ServerTransaction(this, true);
                break;
            case WRITE:
            case NEW_WRITE:
            case WRITE_STEP_ISOLATED:
                if (transactionDefaultReadOnly)
                    throw new TransactionReadOnlyException();
                localTransaction = new ServerTransaction(this, false);
                localTransaction.beforeUpdate(true);
                break;
            }
        }
        if (isTransactionRollbackPending()) {
            ServerStatement.TransactionAbortedMode abortedMode = stmt.getTransactionAbortedMode();
            switch (abortedMode) {
                case ALLOWED:
                    break;
                case NOT_ALLOWED:
                    throw new TransactionAbortedException();
                default:
                    throw new IllegalStateException("Unknown mode: " + abortedMode);
            }
        }
        return localTransaction;
    }

    /** Complete execute given statement.
     * @see #beforeExecute
     */
    protected void afterExecute(ServerStatement stmt, 
                                ServerTransaction localTransaction,
                                boolean success) {
        if (localTransaction != null) {
            if (success)
                localTransaction.commit();
            else
                localTransaction.abort();
        }
        else {
            // Make changes visible in open global transaction.
            ServerStatement.TransactionMode transactionMode = stmt.getTransactionMode();
            switch (transactionMode) {
            case REQUIRED_WRITE:
            case WRITE:
            case WRITE_STEP_ISOLATED:
                if (transaction != null)
                    transaction.afterUpdate(transactionMode == ServerStatement.TransactionMode.WRITE_STEP_ISOLATED);
                break;
            }
        }
    }

    protected void inheritFromCall() {
        ServerCallContextStack.Entry call = ServerCallContextStack.current();
        if (call != null) {
            ServerSessionBase server = (ServerSessionBase)call.getContext().getServer();
            defaultSchemaName = server.defaultSchemaName;
            session = server.session;
            transaction = server.transaction;
            transactionDefaultReadOnly = server.transactionDefaultReadOnly;
            sessionMonitor.setCallerSessionId(server.getSessionMonitor().getSessionId());
        }
    }

    public boolean shouldNotify(QueryContext.NotificationLevel level) {
        return (level.ordinal() <= maxNotificationLevel.ordinal());
    }

    @Override
    public boolean isSchemaAccessible(String schemaName) {
        return reqs.securityService().isAccessible(session, schemaName);
    }

}

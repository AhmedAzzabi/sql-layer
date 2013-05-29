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

package com.akiban.server.store;

import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.transaction.TransactionService;
import com.akiban.server.service.tree.TreeService;
import com.akiban.util.MultipleCauseException;
import com.google.inject.Inject;
import com.persistit.Transaction;
import com.persistit.exception.PersistitException;

import java.util.Deque;

import static com.akiban.server.service.session.Session.Key;
import static com.akiban.server.service.session.Session.StackKey;

public class PersistitTransactionService implements TransactionService {
    private static final Key<Transaction> TXN_KEY = Key.named("TXN_KEY");
    private static final StackKey<Callback> PRE_COMMIT_KEY = StackKey.stackNamed("TXN_PRE_COMMIT");
    private static final StackKey<Callback> AFTER_END_KEY = StackKey.stackNamed("TXN_AFTER_END");
    private static final StackKey<Callback> AFTER_COMMIT_KEY = StackKey.stackNamed("TXN_AFTER_COMMIT");
    private static final StackKey<Callback> AFTER_ROLLBACK_KEY = StackKey.stackNamed("TXN_AFTER_ROLLBACK");

    private final TreeService treeService;

    @Inject
    public PersistitTransactionService(TreeService treeService) {
        this.treeService = treeService;
    }

    @Override
    public boolean isTransactionActive(Session session) {
        Transaction txn = getTransaction(session);
        return (txn != null) && txn.isActive();
    }

    @Override
    public boolean isRollbackPending(Session session) {
        Transaction txn = getTransaction(session);
        return (txn != null) && txn.isRollbackPending();
    }

    @Override
    public long getTransactionStartTimestamp(Session session) {
        Transaction txn = getTransaction(session);
        requireActive(txn);
        return txn.getStartTimestamp();
    }

    @Override
    public void beginTransaction(Session session) {
        Transaction txn = getTransaction(session);
        requireInactive(txn); // Do not want to use Persistit nesting
        try {
            txn.begin();
        } catch(PersistitException e) {
            PersistitAdapter.handlePersistitException(session, e);
        }
    }

    @Override
    public CloseableTransaction beginCloseableTransaction(final Session session) {
        beginTransaction(session);
        return new CloseableTransaction() {
            @Override
            public void commit() {
                commitTransaction(session);
            }

            @Override
            public boolean commitOrRetry() {
                commit();
                return false;
            }

            @Override
            public void rollback() {
                rollbackTransaction(session);
            }

            @Override
            public void close() {
                rollbackTransactionIfOpen(session);
            }
        };
    }

    @Override
    public void commitTransaction(Session session) {
        Transaction txn = getTransaction(session);
        requireActive(txn);
        RuntimeException re = null;
        try {
            runCallbacks(session, PRE_COMMIT_KEY, txn.getStartTimestamp(), null);
            txn.commit();
            runCallbacks(session, AFTER_COMMIT_KEY, txn.getCommitTimestamp(), null);
        } catch(RuntimeException e) {
            re = e;
        } catch(PersistitException e) {
            re = PersistitAdapter.wrapPersistitException(session, e);
        } finally {
            end(session, txn, re);
        }
    }

    @Override
    public void rollbackTransaction(Session session) {
        Transaction txn = getTransaction(session);
        requireActive(txn);
        RuntimeException re = null;
        try {
            txn.rollback();
            runCallbacks(session, AFTER_ROLLBACK_KEY, -1, null);
        } catch(RuntimeException e) {
            re = e;
        } finally {
            end(session, txn, re);
        }
    }

    @Override
    public void rollbackTransactionIfOpen(Session session) {
        Transaction txn = getTransaction(session);
        if((txn != null) && txn.isActive()) {
            rollbackTransaction(session);
        }
    }

    @Override
    public int getTransactionStep(Session session) {
        Transaction txn = getTransaction(session);
        requireActive(txn);
        return txn.getStep();
    }

    @Override
    public int setTransactionStep(Session session, int newStep) {
        Transaction txn = getTransaction(session);
        requireActive(txn);
        return txn.setStep(newStep);
    }

    @Override
    public int incrementTransactionStep(Session session) {
        Transaction txn = getTransaction(session);
        requireActive(txn);
        return txn.incrementStep();
    }

    @Override
    public void addCallback(Session session, CallbackType type, Callback callback) {
        session.push(getCallbackKey(type), callback);
    }

    @Override
    public void addCallbackOnActive(Session session, CallbackType type, Callback callback) {
        requireActive(getTransaction(session));
        session.push(getCallbackKey(type), callback);
    }

    @Override
    public void addCallbackOnInactive(Session session, CallbackType type, Callback callback) {
        requireInactive(getTransaction(session));
        session.push(getCallbackKey(type), callback);
    }

    @Override
    public void start() {
        // None
    }

    @Override
    public void stop() {
        // None
    }

    @Override
    public void crash() {
        // None
    }

    private Transaction getTransaction(Session session) {
        Transaction txn = session.get(TXN_KEY); // Note: Assumes 1 session per thread
        if(txn == null) {
            txn = treeService.getDb().getTransaction();
            session.put(TXN_KEY, txn);
        }
        return txn;
    }

    private void requireInactive(Transaction txn) {
        if((txn != null) && txn.isActive()) {
            throw new IllegalStateException("Transaction already began");
        }
    }

    private void requireActive(Transaction txn) {
        if((txn == null) || !txn.isActive()) {
            throw new IllegalStateException("No transaction open");
        }
    }

    private void end(Session session, Transaction txn, RuntimeException cause) {
        RuntimeException re = cause;
        try {
            if(txn.isActive() && !txn.isCommitted() && !txn.isRollbackPending()) {
                txn.rollback(); // Abnormally ended, do not call rollback hooks
            }
        } catch(RuntimeException e) {
            re = MultipleCauseException.combine(re, e);
        }
        try {
            txn.end();
            //session.remove(TXN_KEY); // Needed if Sessions ever move between threads
        } catch(RuntimeException e) {
            re = MultipleCauseException.combine(re, e);
        } finally {
            clearStack(session, PRE_COMMIT_KEY);
            clearStack(session, AFTER_COMMIT_KEY);
            clearStack(session, AFTER_ROLLBACK_KEY);
            runCallbacks(session, AFTER_END_KEY, -1, re);
        }
    }

    private void clearStack(Session session, StackKey<Callback> key) {
        Deque<Callback> stack = session.get(key);
        if(stack != null) {
            stack.clear();
        }
    }

    private void runCallbacks(Session session, StackKey<Callback> key, long timestamp, RuntimeException cause) {
        RuntimeException exceptions = cause;
        Callback cb;
        while((cb = session.pop(key)) != null) {
            try {
                cb.run(session, timestamp);
            } catch(RuntimeException e) {
                exceptions = MultipleCauseException.combine(exceptions, e);
            }
        }
        if(exceptions != null) {
            throw exceptions;
        }
    }

    private static StackKey<Callback> getCallbackKey(CallbackType type) {
        switch(type) {
            case PRE_COMMIT:    return PRE_COMMIT_KEY;
            case COMMIT:        return AFTER_COMMIT_KEY;
            case ROLLBACK:      return AFTER_ROLLBACK_KEY;
            case END:           return AFTER_END_KEY;
        }
        throw new IllegalArgumentException("Unknown CallbackType: " + type);
    }
}

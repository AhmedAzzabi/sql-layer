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

import com.akiban.sql.server.ServerServiceRequirements;
import com.akiban.sql.server.ServerSessionBase;
import com.akiban.sql.server.ServerSessionTracer;
import com.akiban.sql.server.ServerStatementCache;
import com.akiban.sql.server.ServerTransaction;

import com.akiban.sql.StandardException;
import com.akiban.sql.parser.ParameterNode;
import com.akiban.sql.parser.SQLParser;
import com.akiban.sql.parser.SQLParserException;
import com.akiban.sql.parser.StatementNode;

import com.akiban.qp.loadableplan.LoadablePlan;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.server.api.DDLFunctions;
import com.akiban.server.error.*;
import com.akiban.server.service.EventTypes;

import com.akiban.util.tap.InOutTap;
import com.akiban.util.tap.Tap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.io.*;
import java.util.*;

/**
 * Connection to a Postgres server client.
 * Runs in its own thread; has its own AkServer Session.
 *
 */
public class PostgresServerConnection extends ServerSessionBase
                                      implements PostgresServerSession, Runnable
{
    private static final Logger logger = LoggerFactory.getLogger(PostgresServerConnection.class);
    private static final InOutTap READ_MESSAGE = Tap.createTimer("PostgresServerConnection: read message");
    private static final InOutTap PROCESS_MESSAGE = Tap.createTimer("PostgresServerConnection: process message");

    private final PostgresServer server;
    private boolean running = false, ignoreUntilSync = false;
    private Socket socket;
    private PostgresMessenger messenger;
    private int pid, secret;
    private int version;
    private Map<String,PostgresStatement> preparedStatements =
        new HashMap<String,PostgresStatement>();
    private Map<String,PostgresBoundQueryContext> boundPortals =
        new HashMap<String,PostgresBoundQueryContext>();

    private ServerStatementCache<PostgresStatement> statementCache;
    private PostgresStatementParser[] unparsedGenerators;
    private PostgresStatementGenerator[] parsedGenerators;
    private Thread thread;
    
    private boolean instrumentationEnabled = false;
    private String sql;
    
    public PostgresServerConnection(PostgresServer server, Socket socket, 
                                    int pid, int secret,
                                    ServerServiceRequirements reqs) {
        super(reqs);
        this.server = server;

        this.socket = socket;
        this.pid = pid;
        this.secret = secret;
        this.sessionTracer = new ServerSessionTracer(pid, server.isInstrumentationEnabled());
        sessionTracer.setRemoteAddress(socket.getInetAddress().getHostAddress());
    }

    public void start() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void stop() {
        running = false;
        // Can only wake up stream read by closing down socket.
        try {
            socket.close();
        }
        catch (IOException ex) {
        }
        if ((thread != null) && (thread != Thread.currentThread())) {
            try {
                // Wait a bit, but don't hang up shutdown if thread is wedged.
                thread.join(500);
                if (thread.isAlive())
                    logger.warn("Connection " + pid + " still running.");
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    public void run() {
        try {
            // We flush() when we mean it. 
            // So, turn off kernel delay, but wrap a buffer so every
            // message isn't its own packet.
            socket.setTcpNoDelay(true);
            messenger = new PostgresMessenger(socket.getInputStream(),
                                              new BufferedOutputStream(socket.getOutputStream()));
            topLevel();
        }
        catch (Exception ex) {
            if (running)
                logger.warn("Error in server", ex);
        }
        finally {
            try {
                socket.close();
            }
            catch (IOException ex) {
            }
        }
    }

    protected void topLevel() throws IOException, Exception {
        logger.info("Connect from {}" + socket.getRemoteSocketAddress());
        boolean startupComplete = false;
        try {
            while (running) {
                READ_MESSAGE.in();
                PostgresMessages type = messenger.readMessage(startupComplete);
                READ_MESSAGE.out();
                PROCESS_MESSAGE.in();
                if (ignoreUntilSync) {
                    if ((type != PostgresMessages.EOF_TYPE) && (type != PostgresMessages.SYNC_TYPE))
                        continue;
                    ignoreUntilSync = false;
                }
                try {
                    sessionTracer.beginEvent(EventTypes.PROCESS);
                    switch (type) {
                    case EOF_TYPE: // EOF
                        stop();
                        break;
                    case SYNC_TYPE:
                        readyForQuery();
                        break;
                    case STARTUP_MESSAGE_TYPE:
                        startupComplete = processStartupMessage();
                        break;
                    case PASSWORD_MESSAGE_TYPE:
                        processPasswordMessage();
                        break;
                    case QUERY_TYPE:
                        processQuery();
                        break;
                    case PARSE_TYPE:
                        processParse();
                        break;
                    case BIND_TYPE:
                        processBind();
                        break;
                    case DESCRIBE_TYPE:
                        processDescribe();
                        break;
                    case EXECUTE_TYPE:
                        processExecute();
                        break;
                    case CLOSE_TYPE:
                        processClose();
                        break;
                    case TERMINATE_TYPE:
                        processTerminate();
                        break;
                    }
                } catch (QueryCanceledException ex) {
                    if (!reqs.config().testing()) {
                        logger.warn(ex.getMessage());
                        logger.warn("StackTrace: {}", ex);
                    }
                    String message = (ex.getMessage() == null ? ex.getClass().toString() : ex.getMessage());
                    sendErrorResponse(type, ex, ErrorCode.QUERY_CANCELED, message);
                } catch (InvalidOperationException ex) {
                    logger.warn("Error in query: {}",ex.getMessage());
                    logger.warn("StackTrace: {}", ex);
                    sendErrorResponse(type, ex, ex.getCode(), ex.getShortMessage());
                } catch (Exception ex) {
                    logger.warn("Unexpected error in query", ex);
                    logger.warn("Stack Trace: {}", ex);
                    String message = (ex.getMessage() == null ? ex.getClass().toString() : ex.getMessage());
                    sendErrorResponse(type, ex, ErrorCode.UNEXPECTED_EXCEPTION, message);
                }
                finally {
                    sessionTracer.endEvent();
                }
                PROCESS_MESSAGE.out();
            }
        }
        finally {
            if (transaction != null) {
                transaction.abort();
                transaction = null;
            }
            server.removeConnection(pid);
        }
    }

    private void sendErrorResponse(PostgresMessages type, Exception exception, ErrorCode errorCode, String message)
        throws Exception
    {
        if (type.errorMode() == PostgresMessages.ErrorMode.NONE) throw exception;
        else {
            messenger.beginMessage(PostgresMessages.ERROR_RESPONSE_TYPE.code());
            messenger.write('S');
            messenger.writeString("ERROR");
            messenger.write('C');
            messenger.writeString(errorCode.getFormattedValue());
            messenger.write('M');
            messenger.writeString(message);
            if (exception instanceof BaseSQLException) {
                int pos = ((BaseSQLException)exception).getErrorPosition();
                if (pos > 0) {
                    messenger.write('P');
                    messenger.writeString(Integer.toString(pos));
                }
            }
            messenger.write(0);
            messenger.sendMessage(true);
        }
        if (type.errorMode() == PostgresMessages.ErrorMode.EXTENDED)
            ignoreUntilSync = true;
        else
            readyForQuery();
    }

    protected void readyForQuery() throws IOException {
        messenger.beginMessage(PostgresMessages.READY_FOR_QUERY_TYPE.code());
        messenger.writeByte('I'); // Idle ('T' -> xact open; 'E' -> xact abort)
        messenger.sendMessage(true);
    }

    protected boolean processStartupMessage() throws IOException {
        int version = messenger.readInt();
        switch (version) {
        case PostgresMessenger.VERSION_CANCEL:
            processCancelRequest();
            return false;
        case PostgresMessenger.VERSION_SSL:
            processSSLMessage();
            return false;
        default:
            this.version = version;
            logger.debug("Version {}.{}", (version >> 16), (version & 0xFFFF));
        }

        Properties clientProperties = new Properties(server.getProperties());
        while (true) {
            String param = messenger.readString();
            if (param.length() == 0) break;
            String value = messenger.readString();
            clientProperties.put(param, value);
        }
        logger.debug("Properties: {}", clientProperties);
        setProperties(clientProperties);

        // Get initial version of AIS.
        session = reqs.sessionService().createSession();
        updateAIS();

        {
            messenger.beginMessage(PostgresMessages.AUTHENTICATION_TYPE.code());
            messenger.writeInt(PostgresMessenger.AUTHENTICATION_CLEAR_TEXT);
            messenger.sendMessage(true);
        }
        return true;
    }

    protected void processCancelRequest() throws IOException {
        int pid = messenger.readInt();
        int secret = messenger.readInt();
        PostgresServerConnection connection = server.getConnection(pid);
        if ((connection != null) && (secret == connection.secret)) {
            // A running query checks session state for query cancelation during Cursor.next() calls. If the
            // query is stuck in a blocking operation, then thread interruption should unstick it. Either way,
            // the query should eventually throw QueryCanceledException which will be caught by topLevel().
            connection.session.cancelCurrentQuery(true);
            if (connection.thread != null) {
                connection.thread.interrupt();
            }
            connection.messenger.setCancel(true);
        }
        stop();                                         // That's all for this connection.
    }

    protected void processSSLMessage() throws IOException {
        OutputStream raw = messenger.getOutputStream();
        raw.write('N');         // No SSL support.
        raw.flush();
    }

    protected void processPasswordMessage() throws IOException {
        String user = properties.getProperty("user");
        String pass = messenger.readString();
        logger.info("Login {}/{}", user, pass);
        Properties status = new Properties();
        // This is enough to make the JDBC driver happy.
        status.put("client_encoding", properties.getProperty("client_encoding", "UNICODE"));
        status.put("server_encoding", messenger.getEncoding());
        status.put("server_version", "8.4.7"); // Not sure what the min it'll accept is.
        status.put("session_authorization", user);
        status.put("DateStyle", "ISO, MDY");
        
        {
            messenger.beginMessage(PostgresMessages.AUTHENTICATION_TYPE.code());
            messenger.writeInt(PostgresMessenger.AUTHENTICATION_OK);
            messenger.sendMessage();
        }
        for (String prop : status.stringPropertyNames()) {
            messenger.beginMessage(PostgresMessages.PARAMETER_STATUS_TYPE.code());
            messenger.writeString(prop);
            messenger.writeString(status.getProperty(prop));
            messenger.sendMessage();
        }
        {
            messenger.beginMessage(PostgresMessages.BACKEND_KEY_DATA_TYPE.code());
            messenger.writeInt(pid);
            messenger.writeInt(secret);
            messenger.sendMessage();
        }
        readyForQuery();
    }

    protected void processQuery() throws IOException {
        long startTime = System.nanoTime();
        int rowsProcessed = 0;
        sql = messenger.readString();
        sessionTracer.setCurrentStatement(sql);
        logger.info("Query: {}", sql);

        updateAIS();

        PostgresQueryContext context = new PostgresQueryContext(this);
        PostgresStatement pstmt = null;
        if (statementCache != null)
            pstmt = statementCache.get(sql);
        if (pstmt == null) {
            for (PostgresStatementParser parser : unparsedGenerators) {
                // Try special recognition first; only allowed to turn
                // into one statement.
                pstmt = parser.parse(this, sql, null);
                if (pstmt != null)
                    break;
            }
        }
        if (pstmt != null) {
            pstmt.sendDescription(context, false);
            rowsProcessed = executeStatement(pstmt, context, -1);
        }
        else {
            // Parse as a _list_ of statements and process each in turn.
            List<StatementNode> stmts;
            try {
                sessionTracer.beginEvent(EventTypes.PARSE);
                stmts = parser.parseStatements(sql);
            } 
            catch (SQLParserException ex) {
                throw new SQLParseException(ex);
            }
            catch (StandardException ex) {
                throw new SQLParserInternalException(ex);
            }
            finally {
                sessionTracer.endEvent();
            }
            for (StatementNode stmt : stmts) {
                pstmt = generateStatement(stmt, null, null);
                if ((statementCache != null) && (stmts.size() == 1))
                    statementCache.put(sql, pstmt);
                pstmt.sendDescription(context, false);
                rowsProcessed = executeStatement(pstmt, context, -1);
            }
        }
        readyForQuery();
        logger.debug("Query complete");
        if (reqs.instrumentation().isQueryLogEnabled()) {
            reqs.instrumentation().logQuery(pid, sql, (System.nanoTime() - startTime), rowsProcessed);
        }
    }

    protected void processParse() throws IOException {
        String stmtName = messenger.readString();
        sql = messenger.readString();
        short nparams = messenger.readShort();
        int[] paramTypes = new int[nparams];
        for (int i = 0; i < nparams; i++)
            paramTypes[i] = messenger.readInt();
        logger.info("Parse: {}", sql);

        updateAIS();

        PostgresStatement pstmt = null;
        if (statementCache != null)
            pstmt = statementCache.get(sql);
        if (pstmt == null) {
            StatementNode stmt;
            List<ParameterNode> params;
            try {
                sessionTracer.beginEvent(EventTypes.PARSE);
                stmt = parser.parseStatement(sql);
                params = parser.getParameterList();
            } 
            catch (SQLParserException ex) {
                throw new SQLParseException(ex);
            }
            catch (StandardException ex) {
                throw new SQLParserInternalException(ex);
            }
            finally {
                sessionTracer.endEvent();
            }
            pstmt = generateStatement(stmt, params, paramTypes);
            if (statementCache != null)
                statementCache.put(sql, pstmt);
        }
        preparedStatements.put(stmtName, pstmt);
        messenger.beginMessage(PostgresMessages.PARSE_COMPLETE_TYPE.code());
        messenger.sendMessage();
    }

    protected void processBind() throws IOException {
        String portalName = messenger.readString();
        String stmtName = messenger.readString();
        Object[] params = null;
        {
            boolean[] paramsBinary = null;
            short nformats = messenger.readShort();
            if (nformats > 0) {
                paramsBinary = new boolean[nformats];
                for (int i = 0; i < nformats; i++)
                    paramsBinary[i] = (messenger.readShort() == 1);
            }
            short nparams = messenger.readShort();
            if (nparams > 0) {
                params = new Object[nparams];
                boolean binary = false;
                for (int i = 0; i < nparams; i++) {
                    if (i < nformats)
                        binary = paramsBinary[i];
                    int len = messenger.readInt();
                    if (len < 0) continue;      // Null
                    byte[] param = new byte[len];
                    messenger.readFully(param, 0, len);
                    if (binary) {
                        params[i] = param;
                    }
                    else {
                        params[i] = new String(param, messenger.getEncoding());
                    }
                }
            }
        }
        boolean[] resultsBinary = null; 
        boolean defaultResultsBinary = false;
        {        
            short nresults = messenger.readShort();
            if (nresults == 1)
                defaultResultsBinary = (messenger.readShort() == 1);
            else if (nresults > 0) {
                resultsBinary = new boolean[nresults];
                for (int i = 0; i < nresults; i++) {
                    resultsBinary[i] = (messenger.readShort() == 1);
                }
                defaultResultsBinary = resultsBinary[nresults-1];
            }
        }
        PostgresStatement pstmt = preparedStatements.get(stmtName);
        boundPortals.put(portalName, new PostgresBoundQueryContext(this, pstmt, 
                                                                   params, resultsBinary, defaultResultsBinary));
        messenger.beginMessage(PostgresMessages.BIND_COMPLETE_TYPE.code());
        messenger.sendMessage();
    }

    protected void processDescribe() throws IOException{
        byte source = messenger.readByte();
        String name = messenger.readString();
        PostgresStatement pstmt;
        PostgresQueryContext context;
        switch (source) {
        case (byte)'S':
            pstmt = preparedStatements.get(name);
            context = new PostgresQueryContext(this);
            break;
        case (byte)'P':
            {
                PostgresBoundQueryContext bound = boundPortals.get(name);
                pstmt = bound.getStatement();
                context = bound;
            }
            break;
        default:
            throw new IOException("Unknown describe source: " + (char)source);
        }
        pstmt.sendDescription(context, true);
    }

    protected void processExecute() throws IOException {
        long startTime = System.nanoTime();
        int rowsProcessed = 0;
        String portalName = messenger.readString();
        int maxrows = messenger.readInt();
        PostgresBoundQueryContext context = boundPortals.get(portalName);
        PostgresStatement pstmt = context.getStatement();
        logger.info("Execute: {}", pstmt);
        rowsProcessed = executeStatement(pstmt, context, maxrows);
        logger.debug("Execute complete");
        if (reqs.instrumentation().isQueryLogEnabled()) {
            reqs.instrumentation().logQuery(pid, sql, (System.nanoTime() - startTime), rowsProcessed);
        }
    }

    protected void processClose() throws IOException {
        byte source = messenger.readByte();
        String name = messenger.readString();
        PostgresStatement pstmt;        
        switch (source) {
        case (byte)'S':
            pstmt = preparedStatements.remove(name);
            break;
        case (byte)'P':
            pstmt = boundPortals.remove(name).getStatement();
            break;
        default:
            throw new IOException("Unknown describe source: " + (char)source);
        }
        messenger.beginMessage(PostgresMessages.CLOSE_COMPLETE_TYPE.code());
        messenger.sendMessage();
    }
    
    protected void processTerminate() throws IOException {
        stop();
    }

    // When the AIS changes, throw everything away, since it might
    // point to obsolete objects.
    protected void updateAIS() {
        DDLFunctions ddl = reqs.dxl().ddlFunctions();
        // TODO: This could be more reliable if the AIS object itself
        // also knew its generation. Right now, can get new generation
        // # and old AIS and not notice until next change.
        long currentTimestamp = ddl.getTimestamp();
        if (aisTimestamp == currentTimestamp) 
            return;             // Unchanged.
        aisTimestamp = currentTimestamp;
        ais = ddl.getAIS(session);

        parser = new SQLParser();

        defaultSchemaName = getProperty("database");
        // Temporary until completely removed.
        // TODO: Any way / need to ask AIS if schema exists and report error?

        PostgresStatementGenerator compiler;
        {
            PostgresOperatorCompiler c = new PostgresOperatorCompiler(this);
            compiler = c;
            adapter = new PersistitAdapter(c.getSchema(),
                                           reqs.store().getPersistitStore(),
                                           reqs.treeService(),
                                           session,
                                           reqs.config());
        }

        statementCache = server.getStatementCache(aisTimestamp);
        unparsedGenerators = new PostgresStatementParser[] {
            new PostgresEmulatedMetaDataStatementParser(this)
        };
        parsedGenerators = new PostgresStatementGenerator[] {
            // Can be ordered by frequency so long as there is no overlap.
            compiler,
            new PostgresDDLStatementGenerator(this),
            new PostgresSessionStatementGenerator(this),
            new PostgresCallStatementGenerator(this),
            new PostgresExplainStatementGenerator(this)
        };
    }

    protected void sessionChanged() {
        if (parsedGenerators == null) return; // setAttribute() from generator's ctor.
        for (PostgresStatementParser parser : unparsedGenerators) {
            parser.sessionChanged(this);
        }
        for (PostgresStatementGenerator generator : parsedGenerators) {
            generator.sessionChanged(this);
        }
    }

    protected PostgresStatement generateStatement(StatementNode stmt, 
                                                  List<ParameterNode> params,
                                                  int[] paramTypes) {
        try {
            sessionTracer.beginEvent(EventTypes.OPTIMIZE);
            for (PostgresStatementGenerator generator : parsedGenerators) {
                PostgresStatement pstmt = generator.generate(this, stmt, 
                                                             params, paramTypes);
                if (pstmt != null) return pstmt;
            }
        }
        finally {
            sessionTracer.endEvent();
        }
        throw new UnsupportedSQLException ("", stmt);
    }

    protected int executeStatement(PostgresStatement pstmt, PostgresQueryContext context, int maxrows) 
            throws IOException {
        PostgresStatement.TransactionMode transactionMode = pstmt.getTransactionMode();
        ServerTransaction localTransaction = null;
        if (transaction != null) {
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
                if (transactionDefaultReadOnly)
                    throw new TransactionReadOnlyException();
                localTransaction = new ServerTransaction(this, false);
                break;
            }
        }
        int rowsProcessed = 0;
        boolean success = false;
        try {
            sessionTracer.beginEvent(EventTypes.EXECUTE);
            rowsProcessed = pstmt.execute(context, maxrows);
            success = true;
        }
        finally {
            if (localTransaction != null) {
                if (success)
                    localTransaction.commit();
                else
                    localTransaction.abort();
            }
            sessionTracer.endEvent();
        }
        return rowsProcessed;
    }

    @Override
    public LoadablePlan<?> loadablePlan(String planName)
    {
        return server.loadablePlan(planName);
    }

    @Override
    public Date currentTime() {
        Date override = server.getOverrideCurrentTime();
        if (override != null)
            return override;
        else
            return super.currentTime();
    }

    /* PostgresServerSession */

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public PostgresMessenger getMessenger() {
        return messenger;
    }

    @Override
    protected boolean propertySet(String key, String value) {
        if ("client_encoding".equals(key)) {
            if ("UNICODE".equals(value))
                messenger.setEncoding("UTF-8");
            else
                messenger.setEncoding(value);
            return true;
        }
        return super.propertySet(key, value);
    }

    /* MBean-related access */

    public boolean isInstrumentationEnabled() {
        return instrumentationEnabled;
    }
    
    public void enableInstrumentation() {
        sessionTracer.enable();
        instrumentationEnabled = true;
    }
    
    public void disableInstrumentation() {
        sessionTracer.disable();
        instrumentationEnabled = false;
    }
    
    public String getSqlString() {
        return sql;
    }
    
    public String getRemoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

}

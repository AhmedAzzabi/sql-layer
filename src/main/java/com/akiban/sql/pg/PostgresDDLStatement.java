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

import com.akiban.server.api.DDLFunctions;
import com.akiban.server.error.ParseException;
import com.akiban.server.error.UnsupportedParametersException;
import com.akiban.server.error.UnsupportedSQLException;
import com.akiban.server.service.session.Session;
import com.akiban.sql.aisddl.*;

import com.akiban.sql.StandardException;

import com.akiban.sql.parser.AlterTableNode;
import com.akiban.sql.parser.CreateIndexNode;
import com.akiban.sql.parser.CreateTableNode;
import com.akiban.sql.parser.CreateSchemaNode;
import com.akiban.sql.parser.DropIndexNode;
import com.akiban.sql.parser.DropTableNode;
import com.akiban.sql.parser.DropSchemaNode;
import com.akiban.sql.parser.DDLStatementNode;
import com.akiban.sql.parser.DropViewNode;
import com.akiban.sql.parser.NodeTypes;
import com.akiban.sql.parser.RenameNode;

import com.akiban.sql.optimizer.AISBinder;
import com.akiban.sql.views.ViewDefinition;

import com.akiban.ais.model.AkibanInformationSchema;

import java.io.IOException;

/** SQL DDL statements. */
public class PostgresDDLStatement implements PostgresStatement
{
    private DDLStatementNode ddl;
    
    public PostgresDDLStatement(DDLStatementNode ddl) {
        this.ddl = ddl;
    }

    @Override
    public PostgresStatement getBoundStatement(String[] parameters,
                                               boolean[] columnBinary, 
                                               boolean defaultColumnBinary){
        if (parameters != null)
            throw new UnsupportedParametersException ();
        return this;
    }

    @Override
    public void sendDescription(PostgresServerSession server, boolean always) 
            throws IOException {
        if (always) {
            PostgresMessenger messenger = server.getMessenger();
            messenger.beginMessage(PostgresMessages.NO_DATA_TYPE.code());
            messenger.sendMessage();
        }
    }

    public int execute(PostgresServerSession server, int maxrows)
            throws IOException {
        AkibanInformationSchema ais = server.getAIS();
        String schema = server.getDefaultSchemaName();
        DDLFunctions ddlFunctions = server.getDXL().ddlFunctions();
        Session session = server.getSession();

        switch (ddl.getNodeType()) {
        case NodeTypes.CREATE_SCHEMA_NODE:
            SchemaDDL.createSchema(ais, schema, (CreateSchemaNode)ddl);
            break;
        case NodeTypes.DROP_SCHEMA_NODE:
            SchemaDDL.dropSchema(ddlFunctions, session, (DropSchemaNode)ddl);
            break;
        case NodeTypes.CREATE_TABLE_NODE:
            TableDDL.createTable(ddlFunctions, session, schema, (CreateTableNode)ddl);
            break;
        case NodeTypes.DROP_TABLE_NODE:
            TableDDL.dropTable(ddlFunctions, session, schema, (DropTableNode)ddl);
            break;
        case NodeTypes.CREATE_VIEW_NODE:
            // TODO: Need to store persistently in AIS (or its extension).
            try {
                ((AISBinder)server.getAttribute("aisBinder")).addView(new ViewDefinition(ddl, server.getParser()));
            } catch (StandardException ex) {
                throw new ParseException ("", ex.getMessage(), ddl.toString());
            }
            break;
        case NodeTypes.DROP_VIEW_NODE:
            ((AISBinder)server.getAttribute("aisBinder")).removeView(((DropViewNode)ddl).getObjectName());
            break;
        case NodeTypes.CREATE_INDEX_NODE:
            IndexDDL.createIndex(ddlFunctions, session, schema, (CreateIndexNode)ddl);
            break;
        case NodeTypes.DROP_INDEX_NODE:
            IndexDDL.dropIndex(ddlFunctions, session, schema, (DropIndexNode)ddl);
            break;
        case NodeTypes.ALTER_TABLE_NODE:
            AlterTableDDL.alterTable(ddlFunctions, session, schema, (AlterTableNode)ddl);
            break;
        case NodeTypes.RENAME_NODE:
            if (((RenameNode)ddl).getRenameType() == RenameNode.RenameType.INDEX) {
                IndexDDL.renameIndex(ddlFunctions, session, schema, (RenameNode)ddl);
            } else if (((RenameNode)ddl).getRenameType() == RenameNode.RenameType.TABLE) {
                TableDDL.renameTable(ddlFunctions, session, schema, (RenameNode)ddl);
            }
        case NodeTypes.REVOKE_NODE:
        default:
            throw new UnsupportedSQLException (ddl.statementToString(), ddl);
        }

        {        
            PostgresMessenger messenger = server.getMessenger();
            messenger.beginMessage(PostgresMessages.COMMAND_COMPLETE_TYPE.code());
            messenger.writeString(ddl.statementToString());
            messenger.sendMessage();
        }
        return 0;
    }
}

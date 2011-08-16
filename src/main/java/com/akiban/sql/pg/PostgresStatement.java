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

import java.io.IOException;

/**
 * An SQL statement compiled for use with Postgres server.
 * @see PostgresStatementGenerator
 */
public interface PostgresStatement
{
    /** Get the bound version of this statement with the given parameters. */
    public PostgresStatement getBoundStatement(String[] parameters,
                                               boolean[] columnBinary, 
                                               boolean defaultColumnBinary);

    /** Send a description message. If <code>always</code>, do so even
     * if no result set. */
    public void sendDescription(PostgresServerSession server, boolean always) 
            throws IOException;

    /** Execute statement and output results. Return number of rows processed. */
    public int execute(PostgresServerSession server, int maxrows)
            throws IOException;

}

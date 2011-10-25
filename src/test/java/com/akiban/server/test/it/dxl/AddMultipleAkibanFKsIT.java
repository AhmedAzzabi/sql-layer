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

package com.akiban.server.test.it.dxl;

import com.akiban.ais.model.*;
import com.akiban.ais.model.validation.AISValidation;
import com.akiban.ais.model.validation.AISValidationFailure;
import com.akiban.ais.model.validation.AISValidationOutput;
import com.akiban.ais.model.validation.AISValidationResults;
import com.akiban.server.error.DuplicateIndexTreeNamesException;
import com.akiban.server.test.it.ITBase;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

// Inspired by bug 874459. These DDL steps simulate MySQL running ALTER TABLE statements which add Akiban FKs.

public class AddMultipleAkibanFKsIT extends ITBase
{
    @Test
    public void createRenameCreate()
    {
        createTable("schema", "root", "id int, primary key(id)");
        // Create children
        createTable("schema", "child1", "id int, rid int, primary key(id)");
        createTable("schema", "child2", "id int, rid int, primary key(id)");
        createTable("schema", "child3", "id int, rid int, primary key(id)");
        // Add Akiban FK to child1
        createTable("schema", "TEMP", "id int, rid int, primary key(id)", akibanFK("rid", "root", "id"));
        ddl().renameTable(session(), tableName("schema", "child1"), tableName("schema", "TEMP2"));
        ddl().renameTable(session(), tableName("schema", "TEMP"), tableName("schema", "child1"));
        ddl().dropTable(session(), tableName("schema", "TEMP2"));
        // Add Akiban FK to child2
        createTable("schema", "TEMP", "id int, rid int, primary key(id)", akibanFK("rid", "root", "id"));
        ddl().renameTable(session(), tableName("schema", "child2"), tableName("schema", "TEMP2"));
        ddl().renameTable(session(), tableName("schema", "TEMP"), tableName("schema", "child2"));
        ddl().dropTable(session(), tableName("schema", "TEMP2"));
        // Add Akiban FK to child3
        createTable("schema", "TEMP", "id int, rid int, primary key(id)", akibanFK("rid", "root", "id"));
        ddl().renameTable(session(), tableName("schema", "child3"), tableName("schema", "TEMP2"));
        ddl().renameTable(session(), tableName("schema", "TEMP"), tableName("schema", "child3"));
        ddl().dropTable(session(), tableName("schema", "TEMP2"));
        AkibanInformationSchema ais = ddl().getAIS(session());
        UserTable root = ais.getUserTable("schema", "root");
        int check = 0;
        for (Join join : root.getChildJoins()) {
            UserTable child = join.getChild();
            assertEquals(root, join.getParent());
            assertEquals(join, child.getParentJoin());
            String childName = child.getName().getTableName();
            if (childName.equals("child1")) {
                check |= 0x1;
            } else if (childName.equals("child2")) {
                check |= 0x2;
            } else if (childName.equals("child3")) {
                check |= 0x4;
            } else {
                fail();
            }
        }
        assertEquals(0x7, check);
        for (UserTable userTable : ais.getUserTables().values()) {
            assertTrue(!userTable.getName().getTableName().startsWith("TEMP"));
        }
    }
}

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

package com.akiban.sql.aisddl;

import com.akiban.ais.model.AISBuilder;
import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.JoinColumn;
import com.akiban.ais.model.Sequence;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.UserTable;
import com.akiban.ais.model.aisb2.AISBBasedBuilder;
import com.akiban.ais.model.aisb2.NewAISBuilder;
import com.akiban.ais.model.validation.AISValidations;
import com.akiban.ais.util.TableChange;
import com.akiban.qp.operator.QueryContext;
import com.akiban.server.api.ddl.DDLFunctionsMockBase;
import com.akiban.server.error.ColumnAlreadyGeneratedException;
import com.akiban.server.error.ColumnNotGeneratedException;
import com.akiban.server.error.DuplicateColumnNameException;
import com.akiban.server.error.DuplicateIndexException;
import com.akiban.server.error.JoinColumnMismatchException;
import com.akiban.server.error.JoinToMultipleParentsException;
import com.akiban.server.error.JoinToUnknownTableException;
import com.akiban.server.error.NoSuchColumnException;
import com.akiban.server.error.NoSuchGroupingFKException;
import com.akiban.server.error.NoSuchIndexException;
import com.akiban.server.error.NoSuchTableException;
import com.akiban.server.error.NoSuchUniqueException;
import com.akiban.server.error.SequenceIntervalZeroException;
import com.akiban.server.error.UnsupportedCheckConstraintException;
import com.akiban.server.error.UnsupportedSQLException;
import com.akiban.server.service.session.Session;
import com.akiban.server.types3.Types3Switch;
import com.akiban.sql.StandardException;
import com.akiban.sql.parser.AlterTableNode;
import com.akiban.sql.parser.SQLParser;
import com.akiban.sql.parser.SQLParserException;
import com.akiban.sql.parser.StatementNode;
import com.akiban.util.Strings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.akiban.ais.util.TableChangeValidator.ChangeLevel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AlterTableDDLTest {
    private static final String SCHEMA = "test";
    private static final TableName C_NAME = tn(SCHEMA, "c");
    private static final TableName O_NAME = tn(SCHEMA, "o");
    private static final TableName I_NAME = tn(SCHEMA, "i");
    private static final TableName A_NAME = tn(SCHEMA, "a");

    private SQLParser parser;
    private DDLFunctionsMock ddlFunctions;
    private NewAISBuilder builder;

    @Before
    public void before() {
        parser = new SQLParser();
        builder = AISBBasedBuilder.create();
        ddlFunctions = null;
    }

    @After
    public void after() {
        parser = null;
        builder = null;
        ddlFunctions = null;
    }

    //
    // Assume check is done early, don't confirm for every action
    //

    @Test(expected=NoSuchTableException.class)
    public void cannotAlterUnknownTable() throws StandardException {
        parseAndRun("ALTER TABLE foo ADD COLUMN bar INT");
    }

    //
    // ADD COLUMN
    //

    @Test(expected=DuplicateColumnNameException.class)
    public void cannotAddDuplicateColumnName() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", true);
        parseAndRun("ALTER TABLE a ADD COLUMN aid INT");
    }

    @Test
    public void addMultipleColumns() throws StandardException
    {
        builder.userTable(A_NAME).colBigInt("b", false);
        parseAndRun("ALTER TABLE a ADD COLUMN d INT, e INT");
        expectColumnChanges("ADD:d", "ADD:e");
        if (Types3Switch.ON)
            expectFinalTable(A_NAME, "b MCOMPAT_ BIGINT(21) NOT NULL",
                                     "d MCOMPAT_ INTEGER(11) NULL",
                                     "e MCOMPAT_ INTEGER(11) NULL");
        else
            expectFinalTable(A_NAME, "b bigint NOT NULL",
                                     "d int NULL",
                                     "e int NULL");
        
    }
    
    @Test
    public void addColumnSingleTableGroupNoPK() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false);
        parseAndRun("ALTER TABLE a ADD COLUMN x INT");
        expectColumnChanges("ADD:x");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(A_NAME, "aid MCOMPAT_ BIGINT(21) NOT NULL", "x MCOMPAT_ INTEGER(11) NULL");
        else
            expectFinalTable(A_NAME, "aid bigint NOT NULL", "x int NULL");
    }

    @Test
    public void addColumnSingleTableGroup() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false).pk("aid");
        parseAndRun("ALTER TABLE a ADD COLUMN v1 VARCHAR(32)");
        expectColumnChanges("ADD:v1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(A_NAME, "aid MCOMPAT_ BIGINT(21) NOT NULL", "v1 MCOMPAT_ VARCHAR(32", "UTF8", "UCS_BINARY) NULL", "PRIMARY(aid)");
        else
            expectFinalTable(A_NAME, "aid bigint NOT NULL", "v1 varchar(32) NULL", "PRIMARY(aid)");
    }

    @Test
    public void addNotNullColumnSingleTableGroup() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false).pk("aid");
        parseAndRun("ALTER TABLE a ADD COLUMN x INT NOT NULL DEFAULT 0");
        expectColumnChanges("ADD:x");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(A_NAME, "aid MCOMPAT_ BIGINT(21) NOT NULL", "x MCOMPAT_ INTEGER(11) NOT NULL DEFAULT 0", "PRIMARY(aid)");
        else
            expectFinalTable(A_NAME, "aid bigint NOT NULL", "x int NOT NULL DEFAULT 0", "PRIMARY(aid)");
    }

    @Test
    public void addColumnRootOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE c ADD COLUMN d1 DECIMAL(10,3)");
        expectColumnChanges("ADD:d1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "c_c MCOMPAT_ BIGINT(21) NULL", "d1 MCOMPAT_ DECIMAL(10, 3) NULL", "PRIMARY(id)");
        else
            expectFinalTable(C_NAME, "id bigint NOT NULL", "c_c bigint NULL", "d1 decimal(10, 3) NULL", "PRIMARY(id)");
        expectUnchangedTables(O_NAME, I_NAME, A_NAME);
    }

    @Test
    public void addColumnMiddleOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE o ADD COLUMN f1 real");
        expectColumnChanges("ADD:f1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(O_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "cid MCOMPAT_ BIGINT(21) NULL", "o_o MCOMPAT_ BIGINT(21) NULL",
                                     "f1 MCOMPAT_ FLOAT(-1, -1) NULL", "__akiban_fk1(cid)", "PRIMARY(id)", "join(cid->id)");
        else
            expectFinalTable(O_NAME, "id bigint NOT NULL", "cid bigint NULL", "o_o bigint NULL", "f1 float NULL", "__akiban_fk1(cid)", "PRIMARY(id)", "join(cid->id)");
        expectUnchangedTables(C_NAME, I_NAME, A_NAME);
    }

    @Test
    public void addColumnLeafOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE i ADD COLUMN d1 double");
        expectColumnChanges("ADD:d1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(I_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "oid MCOMPAT_ BIGINT(21) NULL",
                                     "i_i MCOMPAT_ BIGINT(21) NULL", "d1 MCOMPAT_ DOUBLE(-1, -1) NULL", "__akiban_fk2(oid)",
                                     "PRIMARY(id)", "join(oid->id)");
        else
            expectFinalTable(I_NAME, "id bigint NOT NULL", "oid bigint NULL", "i_i bigint NULL", "d1 double NULL", "__akiban_fk2(oid)", "PRIMARY(id)", "join(oid->id)");
        expectUnchangedTables(C_NAME, O_NAME, A_NAME);
    }
    
    @Test
    public void addColumnSerialNoPk() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false);
        parseAndRun("ALTER TABLE a ADD COLUMN new SERIAL");
        expectColumnChanges("ADD:new");
        expectIndexChanges();
        if (Types3Switch.ON) {
            expectFinalTable(A_NAME, "aid MCOMPAT_ BIGINT(21) NOT NULL", "new MCOMPAT_ BIGINT(21) NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)", "UNIQUE new(new)");
        } else {
            expectFinalTable(A_NAME, "aid bigint NOT NULL", "new bigint NOT NULL", "UNIQUE new(new)");
        }
    }
    
    @Test
    public void addColumnSerialPk() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false);
        parseAndRun("ALTER TABLE a ADD COLUMN new SERIAL PRIMARY KEY");
        expectColumnChanges("ADD:new");
        expectIndexChanges("ADD:PRIMARY");
        if (Types3Switch.ON) {
            expectFinalTable(A_NAME, "aid MCOMPAT_ BIGINT(21) NOT NULL", "new MCOMPAT_ BIGINT(21) NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)", "UNIQUE new(new)", "PRIMARY(new)");
        } else {
            expectFinalTable(A_NAME, "aid bigint NOT NULL", "new bigint NOT NULL", "UNIQUE new(new)", "PRIMARY(new)");
        }
    }

    //
    // DROP COLUMN
    //

    @Test(expected=NoSuchColumnException.class)
    public void cannotDropColumnUnknownColumn() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", true);
        parseAndRun("ALTER TABLE a DROP COLUMN bar");
    }

    @Test
    public void dropColumnPKColumn() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false).colBigInt("x", true).pk("aid");
        parseAndRun("ALTER TABLE a DROP COLUMN aid");
        expectColumnChanges("DROP:aid");
        expectIndexChanges("DROP:PRIMARY");
        if(Types3Switch.ON)
            expectFinalTable(A_NAME, "x MCOMPAT_ BIGINT(21) NULL");
        else
            expectFinalTable(A_NAME, "x bigint NULL");
    }

    @Test
    public void dropColumnSingleTableGroupNoPK() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false).colBigInt("x");
        parseAndRun("ALTER TABLE a DROP COLUMN x");
        expectColumnChanges("DROP:x");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(A_NAME, "aid MCOMPAT_ BIGINT(21) NOT NULL");
        else
            expectFinalTable(A_NAME, "aid bigint NOT NULL");
    }

    @Test
    public void dropColumnSingleTableGroup() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false).colString("v1", 32).pk("aid");
        parseAndRun("ALTER TABLE a DROP COLUMN v1");
        expectColumnChanges("DROP:v1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(A_NAME, "aid MCOMPAT_ BIGINT(21) NOT NULL", "PRIMARY(aid)");
        else
            expectFinalTable(A_NAME, "aid bigint NOT NULL", "PRIMARY(aid)");
    }

    @Test
    public void dropColumnRootOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE c DROP COLUMN c_c");
        expectColumnChanges("DROP:c_c");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "PRIMARY(id)");
        else
            expectFinalTable(C_NAME, "id bigint NOT NULL", "PRIMARY(id)");
        expectUnchangedTables(O_NAME, I_NAME, A_NAME);
    }

    @Test
    public void dropColumnMiddleOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE o DROP COLUMN o_o");
        expectColumnChanges("DROP:o_o");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(O_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "cid MCOMPAT_ BIGINT(21) NULL",
                                     "__akiban_fk1(cid)", "PRIMARY(id)", "join(cid->id)");
        else
            expectFinalTable(O_NAME, "id bigint NOT NULL", "cid bigint NULL", "__akiban_fk1(cid)", "PRIMARY(id)", "join(cid->id)");
        expectUnchangedTables(C_NAME, I_NAME, A_NAME);
    }

    @Test
    public void dropColumnLeafOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE i DROP COLUMN i_i");
        expectColumnChanges("DROP:i_i");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(I_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "oid MCOMPAT_ BIGINT(21) NULL",
                                     "__akiban_fk2(oid)", "PRIMARY(id)", "join(oid->id)");
        else
            expectFinalTable(I_NAME, "id bigint NOT NULL", "oid bigint NULL", "__akiban_fk2(oid)", "PRIMARY(id)", "join(oid->id)");
        expectUnchangedTables(C_NAME, O_NAME, A_NAME);
    }

    @Test
    public void dropColumnWasIndexed() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colString("c1", 10).pk("id").key("c1", "c1");
        parseAndRun("ALTER TABLE c DROP COLUMN c1");
        expectColumnChanges("DROP:c1");
        expectIndexChanges("DROP:c1");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "PRIMARY(id)");
        else
            expectFinalTable(C_NAME, "id bigint NOT NULL", "PRIMARY(id)");
    }

    @Test
    public void dropColumnWasInMultiIndexed() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("c1", true).colBigInt("c2", true).pk("id").key("c1_c2", "c1", "c2");
        parseAndRun("ALTER TABLE c DROP COLUMN c1");
        expectColumnChanges("DROP:c1");
        expectIndexChanges("MODIFY:c1_c2->c1_c2");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "c2 MCOMPAT_ BIGINT(21) NULL", "c1_c2(c2)", "PRIMARY(id)");
        else
            expectFinalTable(C_NAME, "id bigint NOT NULL", "c2 bigint NULL", "c1_c2(c2)", "PRIMARY(id)");
    }

    @Test
    public void dropColumnFromChildIsGroupedToParent() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE i DROP COLUMN oid");
        expectColumnChanges("DROP:oid");
        expectIndexChanges("DROP:__akiban_fk2");
        // Do not check group and assume join removal handled at lower level (TableChangeValidator)
        if(Types3Switch.ON)
            expectFinalTable(I_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "i_i MCOMPAT_ BIGINT(21) NULL", "PRIMARY(id)", "join(oid->id)");
        else
            expectFinalTable(I_NAME, "id bigint NOT NULL", "i_i bigint NULL", "PRIMARY(id)", "join(oid->id)");
    }

    //
    // ALTER COLUMN <metadata>
    //

    @Test
    public void alterColumnSetDefault() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", true);
        builder.unvalidatedAIS().getUserTable(C_NAME).getColumn("c1").setDefaultValue(null);
        parseAndRun("ALTER TABLE c ALTER COLUMN c1 SET DEFAULT 42");
        expectColumnChanges("MODIFY:c1->c1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NULL DEFAULT 42");
        else
            expectFinalTable(C_NAME, "c1 bigint NULL DEFAULT 42");
    }

    @Test
    public void alterColumnDropDefault() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", true);
        builder.unvalidatedAIS().getUserTable(C_NAME).getColumn("c1").setDefaultValue("42");
        parseAndRun("ALTER TABLE c ALTER COLUMN c1 DROP DEFAULT");
        expectColumnChanges("MODIFY:c1->c1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NULL");
        else
            expectFinalTable(C_NAME, "c1 bigint NULL");
    }

    @Test
    public void alterColumnNull() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false);
        parseAndRun("ALTER TABLE c ALTER COLUMN c1 NULL");
        expectColumnChanges("MODIFY:c1->c1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NULL");
        else
            expectFinalTable(C_NAME, "c1 bigint NULL");
    }

    @Test
    public void alterColumnNotNull() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false);
        parseAndRun("ALTER TABLE c ALTER COLUMN c1 NOT NULL");
        expectColumnChanges("MODIFY:c1->c1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NOT NULL");
        else
            expectFinalTable(C_NAME, "c1 bigint NOT NULL");
    }

    @Test
    public void renameColumn() throws StandardException
    {   
        builder.userTable(C_NAME).colBigInt("a", true)
                                 .colBigInt("b", true)
                                 .colBigInt("x", false)
                                 .colBigInt("d", true)
                                 .pk("x")
                                 .key("idx1", "b", "x");
        
        parseAndRun("RENAME COLUMN c.x TO y");
        expectColumnChanges("MODIFY:x->y");
        expectIndexChanges();
        if (Types3Switch.ON)
            expectFinalTable(C_NAME,
                             "a MCOMPAT_ BIGINT(21) NULL, " +
                                "b MCOMPAT_ BIGINT(21) NULL, " +
                                "y MCOMPAT_ BIGINT(21) NOT NULL, " +
                                "d MCOMPAT_ BIGINT(21) NULL",
                             "idx1(b,y)",
                             "PRIMARY(y)");
        else
            expectFinalTable(C_NAME,
                             "a bigint NULL, " +
                                "b bigint NULL, " +
                                "y bigint NOT NULL, " +
                                "d bigint NULL",
                             "idx1(b,y)",
                             "PRIMARY(y)");
    }

    //
    // ALTER COLUMN SET DATA TYPE
    //

    @Test(expected=NoSuchColumnException.class)
    public void cannotAlterColumnUnknownColumn() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", true);
        parseAndRun("ALTER TABLE a ALTER COLUMN bar SET DATA TYPE INT");
    }

    @Test
    public void alterColumnFromChildIsGroupedToParent() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE i ALTER COLUMN oid SET DATA TYPE varchar(32)");
        expectColumnChanges("MODIFY:oid->oid");
        expectIndexChanges();
        // Do not check group and assume join removal handled at lower level (TableChangeValidator)
        if(Types3Switch.ON)
            expectFinalTable(I_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "oid MCOMPAT_ VARCHAR(32, UTF8, UCS_BINARY) NULL",
                                     "i_i MCOMPAT_ BIGINT(21) NULL", "__akiban_fk2(oid)", "PRIMARY(id)", "join(oid->id)");
        else
            expectFinalTable(I_NAME, "id bigint NOT NULL", "oid varchar(32) NULL", "i_i bigint NULL", "__akiban_fk2(oid)",
                             "PRIMARY(id)", "join(oid->id)");
    }

    @Test
    public void alterColumnPKColumnSingleTableGroup() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false).pk("aid");
        parseAndRun("ALTER TABLE a ALTER COLUMN aid SET DATA TYPE INT");
        expectColumnChanges("MODIFY:aid->aid");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(A_NAME, "aid MCOMPAT_ INTEGER(11) NOT NULL", "PRIMARY(aid)");
        else
            expectFinalTable(A_NAME, "aid int NOT NULL", "PRIMARY(aid)");
    }

    @Test
    public void alterColumnSetDataTypeSingleTableGroupNoPK() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false).colBigInt("x");
        parseAndRun("ALTER TABLE a ALTER COLUMN x SET DATA TYPE varchar(32)");
        expectColumnChanges("MODIFY:x->x");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(A_NAME, "aid MCOMPAT_ BIGINT(21) NOT NULL", "x MCOMPAT_ VARCHAR(32, UTF8, UCS_BINARY) NOT NULL");
        else
            expectFinalTable(A_NAME, "aid bigint NOT NULL", "x varchar(32) NOT NULL"); // keeps NULL-ability
    }

    @Test
    public void alterColumnSetDataTypeSingleTableGroup() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false).colString("v1", 32, true).pk("aid");
        parseAndRun("ALTER TABLE a ALTER COLUMN v1 SET DATA TYPE INT");
        expectColumnChanges("MODIFY:v1->v1");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(A_NAME, "aid MCOMPAT_ BIGINT(21) NOT NULL", "v1 MCOMPAT_ INTEGER(11) NULL", "PRIMARY(aid)");
        else
            expectFinalTable(A_NAME, "aid bigint NOT NULL", "v1 int NULL", "PRIMARY(aid)");
    }

    @Test
    public void alterColumnSetDataTypeRootOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE c ALTER COLUMN c_c SET DATA TYPE DECIMAL(5,2)");
        expectColumnChanges("MODIFY:c_c->c_c");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "c_c MCOMPAT_ DECIMAL(5, 2) NULL", "PRIMARY(id)");
        else
            expectFinalTable(C_NAME, "id bigint NOT NULL", "c_c decimal(5, 2) NULL", "PRIMARY(id)");
        expectUnchangedTables(O_NAME, I_NAME, A_NAME);
    }

    @Test
    public void alterColumnSetDataTypeMiddleOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE o ALTER COLUMN o_o SET DATA TYPE varchar(10)");
        expectColumnChanges("MODIFY:o_o->o_o");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(O_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "cid MCOMPAT_ BIGINT(21) NULL",
                                     "o_o MCOMPAT_ VARCHAR(10, UTF8, UCS_BINARY) NULL", "__akiban_fk1(cid)",
                                     "PRIMARY(id)", "join(cid->id)");
        else
            expectFinalTable(O_NAME, "id bigint NOT NULL", "cid bigint NULL", "o_o varchar(10) NULL", "__akiban_fk1(cid)", "PRIMARY(id)", "join(cid->id)");
        expectUnchangedTables(C_NAME, I_NAME, A_NAME);
    }

    @Test
    public void alterColumnSetDataTypeLeafOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE i ALTER COLUMN i_i SET DATA TYPE double");
        expectColumnChanges("MODIFY:i_i->i_i");
        expectIndexChanges();
        if(Types3Switch.ON)
            expectFinalTable(I_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "oid MCOMPAT_ BIGINT(21) NULL",
                                     "i_i MCOMPAT_ DOUBLE(-1, -1) NULL", "__akiban_fk2(oid)", "PRIMARY(id)", "join(oid->id)");
        else
            expectFinalTable(I_NAME, "id bigint NOT NULL", "oid bigint NULL", "i_i double NULL", "__akiban_fk2(oid)", "PRIMARY(id)", "join(oid->id)");
        expectUnchangedTables(C_NAME, O_NAME, A_NAME);
    }

    //
    // ALTER COLUMN SET INCREMENT BY <number>
    //

    @Test
    public void alterColumnSetIncrementByLess() throws StandardException {
        buildCWithGeneratedID(1, true);
        parseAndRun("ALTER TABLE c ALTER COLUMN id SET INCREMENT BY -1");
        expectColumnChanges("MODIFY:id->id");
        expectIndexChanges();
        expectFinalTable(C_NAME, "id MCOMPAT_ INTEGER(11) NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY -1)", "PRIMARY(id)");
    }

    @Test
    public void alterColumnSetIncrementByMore() throws StandardException {
        buildCWithGeneratedID(1, true);
        parseAndRun("ALTER TABLE c ALTER COLUMN id SET INCREMENT BY 5");
        expectColumnChanges("MODIFY:id->id");
        expectIndexChanges();
        expectFinalTable(C_NAME, "id MCOMPAT_ INTEGER(11) NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 5)", "PRIMARY(id)");
    }

    @Test(expected=ColumnNotGeneratedException.class)
    public void alterColumnSetIncrementInvalid() throws StandardException {
        buildCWithID();
        parseAndRun("ALTER TABLE c ALTER COLUMN id SET INCREMENT BY 5");
    }

    //
    // ALTER COLUMN RESTART WITH <number>
    //

    @Test(expected=UnsupportedSQLException.class)
    public void alterColumnRestartWith() throws StandardException {
        buildCWithGeneratedID(1, true);
        parseAndRun("ALTER TABLE c ALTER COLUMN id RESTART WITH 10");
    }

    //
    // ALTER COLUMN [SET] GENERATED <BY DEFAULT | ALWAYS>
    //

    @Test
    public void alterColumnSetGeneratedByDefault() throws StandardException {
        buildCWithID();
        parseAndRun("ALTER TABLE c ALTER COLUMN id SET GENERATED BY DEFAULT AS IDENTITY (START WITH 10, INCREMENT BY 50)");
        expectColumnChanges("MODIFY:id->id");
        expectIndexChanges();
        expectFinalTable(C_NAME, "id MCOMPAT_ INTEGER(11) NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 10, INCREMENT BY 50)", "PRIMARY(id)");
    }

    @Test
    public void alterColumnSetGeneratedAlways() throws StandardException {
        buildCWithID();
        parseAndRun("ALTER TABLE c ALTER COLUMN id SET GENERATED ALWAYS AS IDENTITY (START WITH 42, INCREMENT BY 100)");
        expectColumnChanges("MODIFY:id->id");
        expectIndexChanges();
        expectFinalTable(C_NAME, "id MCOMPAT_ INTEGER(11) NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 42, INCREMENT BY 100)", "PRIMARY(id)");
    }

    @Test(expected=ColumnAlreadyGeneratedException.class)
    public void alterColumnSetGeneratedAlreadyGenerated() throws StandardException {
        buildCWithGeneratedID(1, true);
        parseAndRun("ALTER TABLE c ALTER COLUMN id SET GENERATED ALWAYS AS IDENTITY (START WITH 42, INCREMENT BY 100)");
    }

    //
    // ADD [CONSTRAINT] UNIQUE
    //

    @Test(expected=NoSuchColumnException.class)
    public void cannotAddUniqueUnknownColumn() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false);
        parseAndRun("ALTER TABLE c ADD UNIQUE(c2)");
    }

    @Test
    public void addUniqueUnnamedSingleTableGroupNoPK() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false);
        parseAndRun("ALTER TABLE c ADD UNIQUE(c1)");
        expectColumnChanges();
        expectIndexChanges("ADD:c1");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NOT NULL", "UNIQUE c1(c1)");
        else
            expectFinalTable(C_NAME, "c1 bigint NOT NULL", "UNIQUE c1(c1)");
    }

    @Test
     public void addUniqueNamedSingleTableGroupNoPK() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false);
        parseAndRun("ALTER TABLE c ADD CONSTRAINT x UNIQUE(c1)");
        expectColumnChanges();
        expectIndexChanges("ADD:x");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NOT NULL", "UNIQUE x(c1)");
        else
            expectFinalTable(C_NAME, "c1 bigint NOT NULL", "UNIQUE x(c1)");
    }

    @Test
    public void addUniqueMiddleOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE o ADD UNIQUE(o_o)");
        expectColumnChanges();
        expectIndexChanges("ADD:o_o");
        if(Types3Switch.ON)
            expectFinalTable(O_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "cid MCOMPAT_ BIGINT(21) NULL", "o_o MCOMPAT_ BIGINT(21) NULL",
                            "__akiban_fk1(cid)", "UNIQUE o_o(o_o)", "PRIMARY(id)", "join(cid->id)");
        else
            expectFinalTable(O_NAME, "id bigint NOT NULL", "cid bigint NULL", "o_o bigint NULL", "__akiban_fk1(cid)",
                             "UNIQUE o_o(o_o)", "PRIMARY(id)", "join(cid->id)");
        expectUnchangedTables(C_NAME, I_NAME, A_NAME);
    }

    //
    // DROP UNIQUE
    //

    @Test(expected=NoSuchUniqueException.class)
    public void cannotDropUniqueUnknown() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false);
        parseAndRun("ALTER TABLE c DROP UNIQUE c1");
    }

    @Test
      public void dropUniqueSingleColumnSingleTableGroup() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false).uniqueKey("c1", "c1");
        parseAndRun("ALTER TABLE c DROP UNIQUE c1");
        expectColumnChanges();
        expectIndexChanges("DROP:c1");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NOT NULL");
        else
            expectFinalTable(C_NAME, "c1 bigint NOT NULL");
    }

    @Test
    public void dropUniqueMultiColumnSingleTableGroup() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false).colBigInt("c2", false).uniqueKey("x", "c2", "c1");
        parseAndRun("ALTER TABLE c DROP UNIQUE x");
        expectColumnChanges();
        expectIndexChanges("DROP:x");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NOT NULL", "c2 MCOMPAT_ BIGINT(21) NOT NULL");
        else
            expectFinalTable(C_NAME, "c1 bigint NOT NULL", "c2 bigint NOT NULL");
    }

    @Test
    public void dropUniqueMiddleOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        AISBuilder builder2 = new AISBuilder(builder.unvalidatedAIS());
        builder2.index(SCHEMA, "o", "x", true, Index.UNIQUE_KEY_CONSTRAINT);
        builder2.indexColumn(SCHEMA, "o", "x", "o_o", 0, true, null);
        parseAndRun("ALTER TABLE o DROP UNIQUE x");
        expectColumnChanges();
        expectIndexChanges("DROP:x");
        if(Types3Switch.ON)
            expectFinalTable(O_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "cid MCOMPAT_ BIGINT(21) NULL", "o_o MCOMPAT_ BIGINT(21) NULL",
                             "__akiban_fk1(cid)", "PRIMARY(id)", "join(cid->id)");
        else
            expectFinalTable(O_NAME, "id bigint NOT NULL", "cid bigint NULL", "o_o bigint NULL", "__akiban_fk1(cid)",
                             "PRIMARY(id)", "join(cid->id)");
        expectUnchangedTables(C_NAME, I_NAME, A_NAME);
    }

    //
    // ADD [CONSTRAINT] PRIMARY KEY
    //

    @Test(expected=DuplicateIndexException.class)
    public void cannotAddPrimaryKeyAnotherPK() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false).pk("c1");
        parseAndRun("ALTER TABLE c ADD PRIMARY KEY(c1)");
    }

    //bug1047037
    @Test(expected=DuplicateIndexException.class)
    public void cannotAddNamedConstraintPrimaryKeyAnotherPK() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false).pk("c1");
        parseAndRun("ALTER TABLE c ADD CONSTRAINT break PRIMARY KEY(c1)");
    }

    @Test
    public void addPrimaryKeySingleTableGroupNoPK() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false);
        parseAndRun("ALTER TABLE c ADD PRIMARY KEY(c1)");
        expectColumnChanges();
        expectIndexChanges("ADD:PRIMARY");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NOT NULL", "PRIMARY(c1)");
        else
            expectFinalTable(C_NAME, "c1 bigint NOT NULL", "PRIMARY(c1)");
    }

    @Test
    public void addPrimaryKeyLeafTableTwoTableGroup() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("c_c", true).pk("id");
        builder.userTable(O_NAME).colBigInt("id", false).colBigInt("cid", true).joinTo(SCHEMA, "c", "fk").on("cid", "id");
        parseAndRun("ALTER TABLE o ADD PRIMARY KEY(id)");
        expectColumnChanges();
        // Cascading changes due to PK (e.g. additional indexes) handled by lower layer
        expectIndexChanges("ADD:PRIMARY");
        if(Types3Switch.ON)
            expectFinalTable(O_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "cid MCOMPAT_ BIGINT(21) NULL", "__akiban_fk(cid)", "PRIMARY(id)", "join(cid->id)");
        else
            expectFinalTable(O_NAME, "id bigint NOT NULL", "cid bigint NULL", "__akiban_fk(cid)", "PRIMARY(id)", "join(cid->id)");
        expectUnchangedTables(C_NAME);
    }

    //
    // DROP PRIMARY KEY
    //

    @Test(expected=NoSuchIndexException.class)
    public void cannotDropPrimaryKeySingleTableGroupNoPK() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false);
        parseAndRun("ALTER TABLE c DROP PRIMARY KEY");
    }

    @Test
    public void dropPrimaryKeySingleTableGroup() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false).pk("c1");
        parseAndRun("ALTER TABLE c DROP PRIMARY KEY");
        expectColumnChanges();
        expectIndexChanges("DROP:PRIMARY");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NOT NULL");
        else
            expectFinalTable(C_NAME, "c1 bigint NOT NULL");
    }

    @Test
    public void dropPrimaryKeyLeafTableTwoTableGroup() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("c_c", true).pk("id");
        builder.userTable(O_NAME).colBigInt("id", false).colBigInt("cid", true).pk("id").joinTo(SCHEMA, "c", "fk").on(
                "cid", "id");
        parseAndRun("ALTER TABLE o DROP PRIMARY KEY");
        expectColumnChanges();
        // Cascading changes due to PK (e.g. additional indexes) handled by lower layer
        expectIndexChanges("DROP:PRIMARY");
        if(Types3Switch.ON)
            expectFinalTable(O_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "cid MCOMPAT_ BIGINT(21) NULL", "__akiban_fk(cid)", "join(cid->id)");
        else
            expectFinalTable(O_NAME, "id bigint NOT NULL", "cid bigint NULL", "__akiban_fk(cid)", "join(cid->id)");
        expectUnchangedTables(C_NAME);
    }

    @Test
    public void dropPrimaryKeyMiddleOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE o DROP PRIMARY KEY");
        expectColumnChanges();
        // Cascading changes due to PK (e.g. additional indexes) handled by lower layer
        expectIndexChanges("DROP:PRIMARY");
        if(Types3Switch.ON)
             expectFinalTable(O_NAME, "id MCOMPAT_ BIGINT(21) NOT NULL", "cid MCOMPAT_ BIGINT(21) NULL", "o_o MCOMPAT_ BIGINT(21) NULL",
                              "__akiban_fk1(cid)", "join(cid->id)");
        else
            expectFinalTable(O_NAME, "id bigint NOT NULL", "cid bigint NULL", "o_o bigint NULL", "__akiban_fk1(cid)", "join(cid->id)");
        // Note: Cannot check I_NAME, grouping change propagated below AlterTableDDL layer
        expectUnchangedTables(C_NAME);
    }

    //
    // ADD [CONSTRAINT] CHECK
    //

    @Test(expected=UnsupportedCheckConstraintException.class)
    public void cannotAddCheckConstraint() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false);
        parseAndRun("ALTER TABLE c ADD CHECK (c1 % 5 = 0)");
    }

    //
    // DROP CHECK
    //

    @Test(expected=UnsupportedCheckConstraintException.class)
    public void cannotDropCheckConstraint() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false).uniqueKey("c1", "c1");
        parseAndRun("ALTER TABLE c DROP CHECK c1");
    }

    //
    // DROP CONSTRAINT
    //

    @Test(expected=NoSuchUniqueException.class)
    public void cannotDropConstraintRegularIndex() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false).key("c1", "c1");
        parseAndRun("ALTER TABLE c DROP CONSTRAINT c1");
    }

    @Test
    public void dropConstraintIsPrimaryKey() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false).pk("c1");
        parseAndRun("ALTER TABLE c DROP CONSTRAINT \"PRIMARY\"");
        expectColumnChanges();
        expectIndexChanges("DROP:PRIMARY");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NOT NULL");
        else
            expectFinalTable(C_NAME, "c1 bigint NOT NULL");
    }

    @Test
    public void dropConstraintIsUnique() throws StandardException {
        builder.userTable(C_NAME).colBigInt("c1", false).uniqueKey("c1", "c1");
        parseAndRun("ALTER TABLE c DROP CONSTRAINT c1");
        if(Types3Switch.ON)
            expectFinalTable(C_NAME, "c1 MCOMPAT_ BIGINT(21) NOT NULL");
        else
            expectFinalTable(C_NAME, "c1 bigint NOT NULL");
    }

    //
    // ADD [CONSTRAINT] GROUPING FOREIGN KEY
    //

    @Test(expected=JoinToUnknownTableException.class)
    public void cannotAddGFKToUnknownParent() throws StandardException {
        builder.userTable(C_NAME).colBigInt("cid", false).colBigInt("other").pk("cid");
        parseAndRun("ALTER TABLE c ADD GROUPING FOREIGN KEY(other) REFERENCES zap(id)");
    }

    @Test(expected=JoinToMultipleParentsException.class)
    public void cannotAddGFKToTableWithParent() throws StandardException {
        builder.userTable(C_NAME).colBigInt("cid", false).pk("cid");
        builder.userTable(O_NAME).colBigInt("oid", false).colBigInt("cid").pk("oid").joinTo(C_NAME).on("cid", "cid");
        parseAndRun("ALTER TABLE o ADD GROUPING FOREIGN KEY(cid) REFERENCES c(cid)");
    }

    @Test(expected=NoSuchColumnException.class)
    public void cannotAddGFKToUnknownParentColumns() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(aid) REFERENCES c(banana)");
    }

    @Test(expected=NoSuchColumnException.class)
    public void cannotAddGFKToUnknownChildColumns() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(banana) REFERENCES c(id)");
    }

    @Test(expected= JoinColumnMismatchException.class)
    public void cannotAddGFKToTooManyChildColumns() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).pk("id");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("y").pk("id");
        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(id,y) REFERENCES c(id)");
    }

    @Test(expected=JoinColumnMismatchException.class)
    public void cannotAddGFKToTooManyParentColumns() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("x").pk("id");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("y").pk("id");
        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(y) REFERENCES c(id,x)");
    }

    @Test
    public void dropGFKToTableWithChild() throws StandardException {
        builder.userTable(A_NAME).colBigInt("aid", false).pk("aid");
        builder.userTable(C_NAME).colBigInt("cid", false).colBigInt("aid").pk("cid");
        builder.userTable(O_NAME).colBigInt("oid", false).colBigInt("cid").pk("oid").joinTo(C_NAME).on("cid", "cid");
        parseAndRun("ALTER TABLE c ADD GROUPING FOREIGN KEY(aid) REFERENCES a(aid)");
        expectGroupIsSame(A_NAME, C_NAME, true);
        expectChildOf(A_NAME, C_NAME);
        expectChildOf(C_NAME, O_NAME);
    }

    @Test
    public void addGFKToSingleTableOnSingleTable() throws StandardException {
        builder.userTable(C_NAME).colBigInt("cid", false).pk("cid");
        builder.userTable(O_NAME).colBigInt("oid", false).colBigInt("cid").pk("oid");

        parseAndRun("ALTER TABLE o ADD GROUPING FOREIGN KEY(cid) REFERENCES c(cid)");

        expectGroupIsSame(C_NAME, O_NAME, true);
        expectChildOf(C_NAME, O_NAME);
    }

    @Test
    public void addGFKToPkLessTable() throws StandardException {
        builder.userTable(C_NAME).colBigInt("cid", false).pk("cid");
        builder.userTable(O_NAME).colBigInt("oid", false).colBigInt("cid");

        parseAndRun("ALTER TABLE o ADD GROUPING FOREIGN KEY(cid) REFERENCES c(cid)");

        expectGroupIsSame(C_NAME, O_NAME, true);
        expectChildOf(C_NAME, O_NAME);
    }

    @Test
    public void addGFKToSingleTableOnRootOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();

        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(other_id) REFERENCES c(id)");

        expectGroupIsSame(C_NAME, A_NAME, true);
        expectGroupIsSame(C_NAME, O_NAME, true);
        expectGroupIsSame(C_NAME, I_NAME, true);
        expectChildOf(C_NAME, A_NAME);
    }

    @Test
    public void addGFKToSingleTableOnMiddleOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();

        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(other_id) REFERENCES o(id)");

        expectGroupIsSame(C_NAME, A_NAME, true);
        expectGroupIsSame(C_NAME, O_NAME, true);
        expectGroupIsSame(C_NAME, I_NAME, true);
        expectChildOf(O_NAME, A_NAME);
    }

    @Test
    public void addGFKToSingleTableOnLeafOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();

        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(other_id) REFERENCES i(id)");

        expectGroupIsSame(C_NAME, A_NAME, true);
        expectGroupIsSame(C_NAME, O_NAME, true);
        expectGroupIsSame(C_NAME, I_NAME, true);
        expectChildOf(I_NAME, A_NAME);
    }

    @Test
    public void addGFKToTableDifferentSchema() throws StandardException {
        String schema2 = "foo";
        TableName xName = tn(schema2, "x");

        builder.userTable(C_NAME).colBigInt("id", false).pk("id");
        builder.userTable(xName).colBigInt("id", false).colBigInt("cid").pk("id");

        parseAndRun("ALTER TABLE foo.x ADD GROUPING FOREIGN KEY(cid) REFERENCES c(id)");

        expectGroupIsSame(C_NAME, xName, true);
        expectChildOf(C_NAME, xName);
    }

    // Should map automatically to the PK
    @Test
    public void addGFKWithNoReferencedSingleColumn() throws StandardException {
        buildCOIJoinedAUnJoined();

        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(other_id) REFERENCES c");

        expectGroupIsSame(C_NAME, A_NAME, true);
        expectChildOf(C_NAME, A_NAME);
    }

    @Test
    public void addGFKWithNoReferencedMultiColumn() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("id2", false).pk("id", "id2");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("other_id").colBigInt("other_id2").pk("id");

        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(other_id,other_id2) REFERENCES c");

        expectGroupIsSame(C_NAME, A_NAME, true);
        expectChildOf(C_NAME, A_NAME);
    }

    @Test(expected=JoinColumnMismatchException.class)
    public void addGFKWithNoReferenceSingleColumnToMultiColumn() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("id2", false).pk("id","id2");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("other_id").colBigInt("other_id2").pk("id");
        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(other_id) REFERENCES c");
    }

    @Test(expected=SQLParserException.class)
    public void addGFKReferencedColumnListCannotBeEmpty() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("id2", false).pk("id","id2");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("other_id").colBigInt("other_id2").pk("id");
        parseAndRun("ALTER TABLE a ADD GROUPING FOREIGN KEY(other_id,other_id2) REFERENCES c()");
    }


    //
    // DROP [CONSTRAINT] GROUPING FOREIGN KEY
    //

    @Test(expected=NoSuchGroupingFKException.class)
    public void cannotDropGFKFromSingleTableGroup() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).pk("id");
        parseAndRun("ALTER TABLE c DROP GROUPING FOREIGN KEY");
    }

    @Test(expected=NoSuchGroupingFKException.class)
    public void cannotDropGFKFromRootOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE c DROP GROUPING FOREIGN KEY");
    }

    @Test
    public void dropGFKFromMiddleOfGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE o DROP GROUPING FOREIGN KEY");
        expectGroupIsSame(C_NAME, O_NAME, false);
        expectChildOf(O_NAME, I_NAME);
    }

    @Test
     public void dropGFKLeafFromTwoTableGroup() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).pk("id");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("cid").pk("id").joinTo(C_NAME).on("cid", "id");
        parseAndRun("ALTER TABLE a DROP GROUPING FOREIGN KEY");
        expectGroupIsSame(C_NAME, A_NAME, false);
    }

    @Test
    public void dropGFKLeafFromGroup() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER TABLE i DROP GROUPING FOREIGN KEY");
        expectGroupIsSame(C_NAME, I_NAME, false);
    }

    @Test
    public void dropGFKLeafWithNoPKFromGroup() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).pk("id");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("cid").joinTo(C_NAME).on("cid", "id");
        parseAndRun("ALTER TABLE a DROP GROUPING FOREIGN KEY");
        expectGroupIsSame(C_NAME, A_NAME, false);
    }

    @Test
    public void dropGFKFromCrossSchemaGroup() throws StandardException {
        String schema2 = "foo";
        TableName xName = tn(schema2, "x");
        builder.userTable(C_NAME).colBigInt("id", false).pk("id");
        builder.userTable(xName).colBigInt("id", false).colBigInt("cid").pk("id").joinTo(C_NAME).on("cid", "id");
        parseAndRun("ALTER TABLE foo.x DROP GROUPING FOREIGN KEY");
        expectGroupIsSame(C_NAME, xName, false);
    }


    //
    // ALTER GROUP ADD
    //

    @Test
    public void groupAddSimple() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER GROUP ADD TABLE a(other_id) TO c(id)");
        expectGroupIsSame(C_NAME, A_NAME, true);
        expectChildOf(C_NAME, A_NAME);
    }

    @Test
    public void groupAddNoReferencedSingleColumn() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER GROUP ADD TABLE a(other_id) TO c");
        expectGroupIsSame(C_NAME, A_NAME, true);
        expectChildOf(C_NAME, A_NAME);
    }

    @Test
    public void groupAddNoReferencedMultiColumn() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("id2", false).pk("id","id2");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("other_id").colBigInt("other_id2").pk("id");
        parseAndRun("ALTER GROUP ADD TABLE a(other_id,other_id2) TO c");
        expectGroupIsSame(C_NAME, A_NAME, true);
        expectChildOf(C_NAME, A_NAME);
    }

    @Test(expected=JoinColumnMismatchException.class)
    public void groupAddNoReferencedSingleColumnToMultiColumn() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("id2", false).pk("id","id2");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("other_id").colBigInt("other_id2").pk("id");
        parseAndRun("ALTER GROUP ADD TABLE a(other_id) TO c");
    }

    @Test(expected=SQLParserException.class)
    public void groupAddReferencedListCannotBeEmpty() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER GROUP ADD TABLE a(other_id) TO c()");
    }


    //
    // ALTER GROUP DROP
    //

    @Test
    public void groupDropTableTwoTableGroup() throws StandardException {
        builder.userTable(C_NAME).colBigInt("id", false).pk("id");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("cid").pk("id").joinTo(C_NAME).on("cid", "id");
        parseAndRun("ALTER GROUP DROP TABLE a");
        expectGroupIsSame(C_NAME, A_NAME, false);
    }

    @Test
    public void groupDropTableLeafOfMultiple() throws StandardException {
        buildCOIJoinedAUnJoined();
        parseAndRun("ALTER GROUP DROP TABLE i");
        expectGroupIsSame(C_NAME, I_NAME, false);
    }

    private void parseAndRun(String sqlText) throws StandardException {
        StatementNode node = parser.parseStatement(sqlText);
        assertEquals("Was alter", AlterTableNode.class, node.getClass());
        ddlFunctions = new DDLFunctionsMock(builder.ais());
        AlterTableDDL.alterTable(ddlFunctions, null, null, SCHEMA, (AlterTableNode)node, null);
    }

    private void expectGroupIsSame(TableName t1, TableName t2, boolean equal) {
        // Only check the name of the group, DDLFunctionsMock doesn't re-serialize
        UserTable table1 = ddlFunctions.ais.getUserTable(t1);
        UserTable table2 = ddlFunctions.ais.getUserTable(t2);
        String groupName1 = ((table1 != null) && (table1.getGroup() != null)) ? table1.getGroup().getName().toString() : "<NO_GROUP>1";
        String groupName2 = ((table2 != null) && (table2.getGroup() != null)) ? table2.getGroup().getName().toString() : "<NO_GROUP>2";
        if(equal) {
            assertEquals("Same group for tables " + t1 + "," + t2, groupName1, groupName2);
        } else if(groupName1.equals(groupName2)) {
            fail("Expected different group for tables " + t1 + "," + t2);
        }
    }

    private void expectChildOf(TableName pTableName, TableName cTableName) {
        // Only check the names of tables, DDLFunctionsMock doesn't re-serialize
        UserTable table1 = ddlFunctions.ais.getUserTable(cTableName);
        UserTable parent = (table1.getParentJoin() != null) ? table1.getParentJoin().getParent() : null;
        TableName parentName = (parent != null) ? parent.getName() : null;
        assertEquals(cTableName + " parent name", pTableName, parentName);
    }

    private void expectColumnChanges(String... changes) {
        assertEquals("Column changes", Arrays.asList(changes).toString(), ddlFunctions.columnChangeDesc.toString());
    }

    private void expectIndexChanges(String... changes) {
        assertEquals("Index changes", Arrays.asList(changes).toString(), ddlFunctions.indexChangeDesc.toString());
    }

    private void expectFinalTable(TableName table, String... parts) {
        String expected = table.toString() + "(" + Strings.join(Arrays.asList(parts), ", ") + ")";
        assertEquals("Final structure for " + table, expected, ddlFunctions.newTableDesc);
    }

    private void expectUnchangedTables(TableName... names) {
        for(TableName name : names) {
            String expected = name.toString();
            if(Types3Switch.ON) {
                if(name == C_NAME) {
                    expected += "(id MCOMPAT_ BIGINT(21) NOT NULL, c_c MCOMPAT_ BIGINT(21) NULL, PRIMARY(id))";
                } else if(name == O_NAME) {
                    expected += "(id MCOMPAT_ BIGINT(21) NOT NULL, cid MCOMPAT_ BIGINT(21) NULL, o_o MCOMPAT_ BIGINT(21) NULL, __akiban_fk1(cid), PRIMARY(id), join(cid->id))";
                } else if(name == I_NAME) {
                    expected += "(id MCOMPAT_ BIGINT(21) NOT NULL, oid MCOMPAT_ BIGINT(21) NULL, i_i MCOMPAT_ BIGINT(21) NULL, __akiban_fk2(oid), PRIMARY(id), join(oid->id))";
                } else if(name == A_NAME) {
                    expected += "(id MCOMPAT_ BIGINT(21) NOT NULL, other_id MCOMPAT_ BIGINT(21) NULL, PRIMARY(id))";
                } else {
                    fail("Unknown table: " + name);
                }
            } else {
                if(name == C_NAME) {
                    expected += "(id bigint NOT NULL, c_c bigint NULL, PRIMARY(id))";
                } else if(name == O_NAME) {
                    expected += "(id bigint NOT NULL, cid bigint NULL, o_o bigint NULL, __akiban_fk1(cid), PRIMARY(id), join(cid->id))";
                } else if(name == I_NAME) {
                    expected += "(id bigint NOT NULL, oid bigint NULL, i_i bigint NULL, __akiban_fk2(oid), PRIMARY(id), join(oid->id))";
                } else if(name == A_NAME) {
                    expected += "(id bigint NOT NULL, other_id bigint NULL, PRIMARY(id))";
                } else {
                    fail("Unknown table: " + name);
                }
            }
            UserTable table = ddlFunctions.ais.getUserTable(name);
            String actual = simpleDescribeTable(table);
            assertEquals(name + " was unchanged", expected, actual);
        }
    }

    private void buildCOIJoinedAUnJoined() {
        builder.userTable(C_NAME).colBigInt("id", false).colBigInt("c_c", true).pk("id");
        builder.userTable(O_NAME).colBigInt("id", false).colBigInt("cid", true).colBigInt("o_o", true).pk("id").joinTo(SCHEMA, "c", "fk1").on("cid", "id");
        builder.userTable(I_NAME).colBigInt("id", false).colBigInt("oid", true).colBigInt("i_i", true).pk("id").joinTo(SCHEMA, "o", "fk2").on("oid", "id");
        builder.userTable(A_NAME).colBigInt("id", false).colBigInt("other_id", true).pk("id");
    }

    private void buildCWithGeneratedID(int startWith, boolean always) {
        builder.userTable(C_NAME).autoIncLong("id", startWith, always).pk("id");
    }

    private void buildCWithID() {
        builder.userTable(C_NAME).colLong("id", false).pk("id");
    }

    private static class DDLFunctionsMock extends DDLFunctionsMockBase {
        final AkibanInformationSchema ais;
        final List<String> columnChangeDesc = new ArrayList<>();
        final List<String> indexChangeDesc = new ArrayList<>();
        String newTableDesc = "";

        public DDLFunctionsMock(AkibanInformationSchema ais) {
            this.ais = ais;
        }

        @Override
        public ChangeLevel alterTable(Session session, TableName tableName, UserTable newDefinition,
                                      List<TableChange> columnChanges, List<TableChange> indexChanges, QueryContext context) {
            if(ais.getUserTable(tableName) == null) {
                throw new NoSuchTableException(tableName);
            }
            ais.getUserTables().remove(tableName);
            ais.getUserTables().put(newDefinition.getName(), newDefinition);
            for(TableChange change : columnChanges) {
                columnChangeDesc.add(change.toString());
            }
            for(TableChange change : indexChanges) {
                indexChangeDesc.add(change.toString());
            }
            newTableDesc = simpleDescribeTable(newDefinition);
            return ChangeLevel.NONE; // Doesn't matter, just can't be null
        }

        @Override
        public AkibanInformationSchema getAIS(Session session) {
            return ais;
        }
    }

    private static TableName tn(String schema, String table) {
        return new TableName(schema, table);
    }

    private static String simpleDescribeTable(UserTable table) {
        // Trivial description: ordered columns and indexes
        StringBuilder sb = new StringBuilder();
        sb.append(table.getName()).append('(');
        boolean first = true;
        for(Column col : table.getColumns()) {
            sb.append(first ? "" : ", ").append(col.getName()).append(' ');
            first = false;
            if(Types3Switch.ON) {
                sb.append(col.tInstance().toString());
            } else {
                sb.append(col.getTypeDescription());
                sb.append(col.getNullable() ? " NULL" : " NOT NULL");
            }
            String defaultVal = col.getDefaultValue();
            if(defaultVal != null) {
                sb.append(" DEFAULT ");
                sb.append(defaultVal);
            }
            Boolean identity = col.getDefaultIdentity();
            if(identity != null) {
                Sequence seq = col.getIdentityGenerator();
                sb.append(" GENERATED ");
                sb.append(identity ? "BY DEFAULT" : "ALWAYS");
                sb.append(" AS IDENTITY (START WITH ");
                sb.append(seq.getStartsWith());
                sb.append(", INCREMENT BY ");
                sb.append(seq.getIncrement());
                sb.append(')');
            }
        }
        for(Index index : table.getIndexes()) {
            sb.append(", ");
            if(!index.isPrimaryKey() && index.isUnique()) {
                sb.append("UNIQUE ");
            }
            sb.append(index.getIndexName().getName()).append('(');
            first = true;
            for(IndexColumn indexColumn : index.getKeyColumns()) {
                sb.append(first ? "" : ',').append(indexColumn.getColumn().getName());
                first = false;
            }
            sb.append(')');
        }
        Join join = table.getParentJoin();
        if(join != null) {
            sb.append(", join(");
            first = true;
            for(JoinColumn joinColumn : join.getJoinColumns()) {
                sb.append(first ? "" : ", ").append(joinColumn.getChild().getName()).append("->").append(joinColumn.getParent().getName());
                first = false;
            }
            sb.append(")");
        }
        sb.append(')');
        return sb.toString();
    }
}

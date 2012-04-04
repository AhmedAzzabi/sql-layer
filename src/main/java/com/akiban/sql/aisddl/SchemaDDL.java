/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.sql.aisddl;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.TableName;

import com.akiban.server.api.DDLFunctions;
import com.akiban.server.error.DropSchemaNotAllowedException;
import com.akiban.server.error.DuplicateSchemaException;
import com.akiban.server.service.session.Session;

import com.akiban.sql.parser.CreateSchemaNode;
import com.akiban.sql.parser.DropSchemaNode;
import com.akiban.sql.parser.StatementType;


public class SchemaDDL {
    private SchemaDDL () {
    }
    
    public static void createSchema (AkibanInformationSchema ais,
                                   String defaultSchemaName,
                                   CreateSchemaNode createSchema)
    {
        final String schemaName = createSchema.getSchemaName();
        
        if (checkSchema (ais, schemaName)) {
            throw new DuplicateSchemaException (schemaName);
        }
        
        // If you get to this point, the schema name isn't being used by any user or group table
        // therefore is a valid "new" schema. 
        // TODO: update the AIS to store the new schema. 
    }
    
    public static void dropSchema (DDLFunctions ddlFunctions,
            Session session,
            DropSchemaNode dropSchema)
    {
        AkibanInformationSchema ais = ddlFunctions.getAIS(session);
        final String schemaName = dropSchema.getSchemaName();

        // 1 == RESTRICT, meaning no drop if the schema isn't empty 
        if (dropSchema.getDropBehavior() == StatementType.DROP_RESTRICT ||
            dropSchema.getDropBehavior() == StatementType.DROP_DEFAULT) {
            if (checkSchema (ais, schemaName)) {
                throw new DropSchemaNotAllowedException (schemaName);
            }
            // If the schema isn't used by any existing tables, it has effectively 
            // been dropped, so the drop "succeeds".
        } else if (dropSchema.getDropBehavior() == StatementType.DROP_CASCADE) {
            ddlFunctions.dropSchema(session, schemaName);
        }
    }
    
    /**
     * Check if a schema exists, by checking all the user/group tables names if the schema
     * is used for any of them..
     */
    public static boolean checkSchema (AkibanInformationSchema ais, String schemaName) {
        for (TableName t : ais.getUserTables().keySet()) {
            if (t.getSchemaName().compareToIgnoreCase(schemaName) == 0) {
                return true;
            }
        }
        for (TableName t : ais.getGroupTables().keySet()) {
            if (t.getSchemaName().compareToIgnoreCase(schemaName) == 0) {
                return true;
            }
        }
        return false;
    }
}

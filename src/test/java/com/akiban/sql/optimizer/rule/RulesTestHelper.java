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

package com.akiban.sql.optimizer.rule;

// TODO: Think about all this.
import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.UserTable;
import com.akiban.server.TableStatus;
import com.akiban.server.rowdata.RowDef;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;

public class RulesTestHelper
{
    private RulesTestHelper() {
    }

    public static List<BaseRule> loadRules(File file) throws Exception {
        Yaml yaml = new Yaml();
        FileInputStream istr = new FileInputStream(file);
        List<Object> list = (List<Object>)yaml.load(istr);
        istr.close();
        return parseRules(list);
    }

    public static List<BaseRule> parseRules(String str) throws Exception {
        Yaml yaml = new Yaml();
        List<Object> list = (List<Object>)yaml.load(str);
        return parseRules(list);
    }

    public static List<BaseRule> parseRules(List<Object> list) throws Exception {
        List<BaseRule> result = new ArrayList<BaseRule>();
        for (Object obj : list) {
            if (obj instanceof String) {
                String cname = (String)obj;
                if (cname.indexOf('.') < 0)
                    cname = RulesTestHelper.class.getPackage().getName() + '.' + cname;
                result.add((BaseRule)Class.forName(cname).newInstance());
            }
            else {
                // TODO: Someday parse options from hash, etc.
                throw new Exception("Don't know what to do with " + obj);
            }
        }
        return result;
    }

    // This just needs to be enough to keep from UserTableRowType
    // constructor from getting NPE.
    // TODO: Think about where this really goes.
    public static void ensureRowDefs(AkibanInformationSchema ais) {
        for (UserTable userTable : ais.getUserTables().values()) {
            int tableId = userTable.getTableId();
            TableStatus ts = new TableStatus(tableId);
            ts.setOrdinal(tableId);
            new RowDef(userTable, ts);
        }
    }

}

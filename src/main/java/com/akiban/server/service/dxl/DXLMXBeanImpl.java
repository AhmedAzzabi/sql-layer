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

package com.akiban.server.service.dxl;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.GroupIndex;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.staticgrouping.Group;
import com.akiban.ais.model.staticgrouping.Grouping;
import com.akiban.ais.model.staticgrouping.GroupingVisitorStub;
import com.akiban.ais.model.staticgrouping.GroupsBuilder;
import com.akiban.ais.util.AISPrinter;
import com.akiban.server.api.DDLFunctions;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.dml.scan.NiceRow;
import com.akiban.server.error.InvalidOperationException;
import com.akiban.server.service.ServiceManagerImpl;
import com.akiban.server.service.session.Session;
import com.akiban.server.util.GroupIndexCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class DXLMXBeanImpl implements DXLMXBean {
    private DXLServiceImpl dxlService;
    private final AtomicReference<String> usingSchema = new AtomicReference<String>("test");
    private static final Logger LOG = LoggerFactory.getLogger(DXLMXBeanImpl.class);
    private static final String CREATE_GROUP_INDEX_LOG_FORMAT = "createGroupIndex failed: %s %s %s";

    public DXLMXBeanImpl(DXLServiceImpl dxlService) {
        this.dxlService = dxlService;
    }

    @Override
    public String getUsingSchema() {
        return usingSchema.get();
    }

    @Override
    public void setUsingSchema(String schema) {
        usingSchema.set(schema);
    }

    public void createTable(String schema, String ddl) {
        Session session = ServiceManagerImpl.newSession();
        try {
            dxlService.ddlFunctions().createTable(session, schema, ddl);
        } finally {
            session.close();
        }
    }

    @Override
    public void createTable(String ddl) {
        createTable(usingSchema.get(), ddl);
    }

    @Override
    public void recreateGroupIndexes() {
        Session session = ServiceManagerImpl.newSession();
        try {
            Map<String,List<GroupIndex>> gisByGroup = new HashMap<String, List<GroupIndex>>();
            DDLFunctions ddl = dxlService.ddlFunctions();
            AkibanInformationSchema ais = ddl.getAIS(session);

            for (com.akiban.ais.model.Group group : ais.getGroups().values()) {
                gisByGroup.put(group.getName(), new ArrayList<GroupIndex>(group.getIndexes()));
            }

            for (Map.Entry<String,List<GroupIndex>> entry : gisByGroup.entrySet()) {
                List<GroupIndex> gis = entry.getValue();
                List<String> giNames = new ArrayList<String>(gis.size());
                for (Index gi : gis) {
                    giNames.add(gi.getIndexName().getName());
                }
                ddl.dropGroupIndexes(session, entry.getKey(), giNames);
                ddl.createIndexes(session, gis);
            }
        }
        finally {
            session.close();
        }
    }

    @Override
    public void createGroupIndex(String groupName, String indexName, String tableColumnList, Index.JoinType joinType) {
        Session session = ServiceManagerImpl.newSession();
        try {
            DDLFunctions ddlFunctions = dxlService.ddlFunctions();
            AkibanInformationSchema ais = ddlFunctions.getAIS(session);
            Index index = GroupIndexCreator.createIndex(ais, groupName, indexName, tableColumnList, joinType);
            ddlFunctions.createIndexes(session, Collections.singleton(index));
        }
        catch (InvalidOperationException e) {
            LOG.debug(e.getMessage());
            LOG.debug(String.format(CREATE_GROUP_INDEX_LOG_FORMAT, groupName, indexName, tableColumnList));
            throw e;
        }
        finally {
            session.close();
        }
    }

    public void dropTable(String schema, String tableName) {
        Session session = ServiceManagerImpl.newSession();
        try {
            dxlService.ddlFunctions().dropTable(session, new TableName(schema, tableName));
        } finally {
            session.close();
        }
    }

    @Override
    public void dropTable(String tableName) {
        dropTable(usingSchema.get(), tableName);
    }

    @Override
    public void dropGroupIndex(String groupName, String indexName) {
        Session session = ServiceManagerImpl.newSession();
        try {
            dxlService.ddlFunctions().dropGroupIndexes(session, groupName, Collections.singleton(indexName));
        } finally {
            session.close();
        }
    }

    @Override
    public void dropGroupBySchema(String schemaName)
    {
        final Session session = ServiceManagerImpl.newSession();
        try {
            AkibanInformationSchema ais = dxlService.ddlFunctions().getAIS(session);
            for(com.akiban.ais.model.Group group: ais.getGroups().values()) {
                final String groupTableSchema = group.getGroupTable().getName().getSchemaName();
                if(groupTableSchema.equals(schemaName)) {
                    dxlService.ddlFunctions().dropGroup(session, group.getName());
                }
            }
        } finally {
            session.close();
        }
    }

    @Override
    public void dropGroup(String groupName) {
        Session session = ServiceManagerImpl.newSession();
        try {
            dxlService.ddlFunctions().dropGroup(session, groupName);
        } finally {
            session.close();
        }
    }

    @Override
    public void dropAllGroups() {
        Session session = ServiceManagerImpl.newSession();
        try {
            for(String groupName : dxlService.ddlFunctions().getAIS(session).getGroups().keySet()) {
                dropGroup(groupName);
            }
        } finally {
            session.close();
        }
    }

    @Override
    public List<String> getGrouping() {
        return getGrouping(usingSchema.get());
    }

    @Override
    public String getGroupNameFromTableName(String schemaName, String tableName) {
        Session session = ServiceManagerImpl.newSession();
        final AkibanInformationSchema ais;
        try {
            ais = dxlService.ddlFunctions().getAIS(session);
        } finally {
            session.close();
        }
        Table table = ais.getTable(schemaName, tableName);
        if(table != null) {
            final com.akiban.ais.model.Group group = table.getGroup();
            if(group != null) {
                return group.getName();
            }
        }
        return null;
    }

    @Override
    public String printAIS() {
        Session session = ServiceManagerImpl.newSession();
        try {
            AkibanInformationSchema ais = dxlService.ddlFunctions().getAIS(session);
            return AISPrinter.toString(ais);
        }
        finally {
            session.close();
        }
    }

    public List<String> getGrouping(String schema) {
        Session session = ServiceManagerImpl.newSession();
        try {
            AkibanInformationSchema ais = dxlService.ddlFunctions().getAIS(session);
            Grouping grouping = GroupsBuilder.fromAis(ais, schema);

            stripAISFromGrouping(grouping);

            String groupingString = grouping.toString();
            return Arrays.asList(groupingString.split("\\n"));
        } finally {
            session.close();
        }
    }

    public void writeRow(String schema, String table, String fields) {
        final Session session = ServiceManagerImpl.newSession();
        try {
            int tableId = dxlService.ddlFunctions().getTableId(session, new TableName(schema, table));
            NewRow row = new NiceRow(tableId);
            String[] fieldsArray = fields.split(",\\s*");
            for (int i=0; i < fieldsArray.length; ++i) {
                String field = java.net.URLDecoder.decode(fieldsArray[i], "UTF-8");
                row.put(i, field);
            }
            dxlService.dmlFunctions().writeRow(session, row);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            session.close();
        }
    }

    @Override
    public void writeRow(String table, String fields) {
        writeRow(usingSchema.get(), table, fields);
    }

    private static void stripAISFromGrouping(Grouping grouping) {
        List<Group> groupsToRemove = grouping.traverse(new GroupingVisitorStub<List<Group>>() {
            private final List<Group> ret = new ArrayList<Group>();

            @Override
            public void visitGroup(Group group, TableName rootTable) {
                if (rootTable.getSchemaName().equals("akiban_information_schema")) {
                    ret.add(group);
                }
            }

            @Override
            public boolean startVisitingChildren() {
                return false;
            }

            @Override
            public List<Group> end() {
                return ret;
            }
        });

        GroupsBuilder manipulator = new GroupsBuilder(grouping);
        for (Group group : groupsToRemove) {
            manipulator.dropGroup(group.getGroupName());
        }
    }
}

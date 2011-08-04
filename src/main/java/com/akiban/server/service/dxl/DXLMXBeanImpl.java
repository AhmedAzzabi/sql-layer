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
import java.util.List;
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

    public void createTable(String schema, String ddl) throws Exception {
        Session session = ServiceManagerImpl.newSession();
        try {
            dxlService.ddlFunctions().createTable(session, schema, ddl);
        } catch (InvalidOperationException e) {
            throw new RuntimeException(e);
        } finally {
            session.close();
        }
    }

    @Override
    public void createTable(String ddl) throws Exception {
        createTable(usingSchema.get(), ddl);
    }

    @Override
    public void createGroupIndex(String groupName, String indexName, String tableColumnList) {
        Session session = ServiceManagerImpl.newSession();
        try {
            DDLFunctions ddlFunctions = dxlService.ddlFunctions();
            AkibanInformationSchema ais = ddlFunctions.getAIS(session);
            Index index = GroupIndexCreator.createIndex(ais, groupName, indexName, tableColumnList);
            ddlFunctions.createIndexes(session, Collections.singleton(index));
        }
        catch(GroupIndex.GroupIndexCreationException e) {
            LOG.debug(String.format(CREATE_GROUP_INDEX_LOG_FORMAT, groupName, indexName, tableColumnList), e);
            throw new RuntimeException(e.getMessage());
        }
        catch(GroupIndexCreator.GroupIndexCreatorException e) {
            LOG.debug(String.format(CREATE_GROUP_INDEX_LOG_FORMAT, groupName, indexName, tableColumnList), e);
            throw new RuntimeException(e.getMessage());
        }
        catch(Exception e) {
            LOG.error(String.format(CREATE_GROUP_INDEX_LOG_FORMAT, groupName, indexName, tableColumnList), e);
            throw new RuntimeException(e.getMessage());
        }
        finally {
            session.close();
        }
    }

    public void dropTable(String schema, String tableName) throws Exception {
        Session session = ServiceManagerImpl.newSession();
        try {
            dxlService.ddlFunctions().dropTable(session, new TableName(schema, tableName));
        } catch (InvalidOperationException e) {
            throw new RuntimeException(e);
        } finally {
            session.close();
        }
    }

    @Override
    public void dropTable(String tableName) throws Exception {
        dropTable(usingSchema.get(), tableName);
    }

    @Override
    public void dropGroupIndex(String groupName, String indexName) throws Exception {
        Session session = ServiceManagerImpl.newSession();
        try {
            dxlService.ddlFunctions().dropGroupIndexes(session, groupName, Collections.singleton(indexName));
        } catch (InvalidOperationException e) {
            throw new RuntimeException(e);
        } finally {
            session.close();
        }
    }

    @Override
    public void dropGroupBySchema(String schemaName) throws Exception
    {
        final Session session = ServiceManagerImpl.newSession();
        try {
            AkibanInformationSchema ais = dxlService.ddlFunctions().getAIS(session);
            for(com.akiban.ais.model.Group group: ais.getGroups().values()) {
                final String groupTableSchema = group.getGroupTable().getName().getSchemaName();
                if(groupTableSchema.equals(schemaName)) {
                    try {
                        dxlService.ddlFunctions().dropGroup(session, group.getName());
                    } catch(InvalidOperationException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } finally {
            session.close();
        }
    }

    @Override
    public void dropGroup(String groupName) throws Exception {
        Session session = ServiceManagerImpl.newSession();
        try {
            dxlService.ddlFunctions().dropGroup(session, groupName);
        } catch (InvalidOperationException e) {
            throw new RuntimeException(e);
        } finally {
            session.close();
        }
    }

    @Override
    public void dropAllGroups() throws Exception {
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
        catch(Exception e) {
            throw new RuntimeException(e);
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

    public void writeRow(String schema, String table, String fields) throws Exception {
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
        } catch (InvalidOperationException e) {
            throw new RuntimeException(e.getMessage());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            session.close();
        }
    }

    @Override
    public void writeRow(String table, String fields) throws Exception {
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

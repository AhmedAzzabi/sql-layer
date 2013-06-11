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

package com.akiban.server.service.dxl;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.GroupIndex;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.staticgrouping.Group;
import com.akiban.ais.model.staticgrouping.Grouping;
import com.akiban.ais.model.staticgrouping.GroupingVisitorStub;
import com.akiban.ais.model.staticgrouping.GroupsBuilder;
import com.akiban.ais.protobuf.ProtobufWriter;
import com.akiban.server.api.DDLFunctions;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.dml.scan.NiceRow;
import com.akiban.server.error.InvalidOperationException;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.session.SessionService;
import com.akiban.server.util.GroupIndexCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

class DXLMXBeanImpl implements DXLMXBean {
    private final DXLServiceImpl dxlService;
    private final AtomicReference<String> usingSchema = new AtomicReference<>("test");
    private final SessionService sessionService;
    private static final Logger LOG = LoggerFactory.getLogger(DXLMXBeanImpl.class);
    private static final String CREATE_GROUP_INDEX_LOG_FORMAT = "createGroupIndex failed: %s %s %s";

    public DXLMXBeanImpl(DXLServiceImpl dxlService, SessionService sessionService) {
        this.dxlService = dxlService;
        this.sessionService = sessionService;
    }

    @Override
    public String getUsingSchema() {
        return usingSchema.get();
    }

    @Override
    public void setUsingSchema(String schema) {
        usingSchema.set(schema);
    }

    @Override
    public void createGroupIndex(String schemaName, String groupName, String indexName, String tableColumnList, Index.JoinType joinType) {
        Session session = sessionService.createSession();
        try {
            DDLFunctions ddlFunctions = dxlService.ddlFunctions();
            AkibanInformationSchema ais = ddlFunctions.getAIS(session);
            Index index = GroupIndexCreator.createIndex(ais, new TableName(schemaName, groupName), indexName, tableColumnList, joinType);
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
        Session session = sessionService.createSession();
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
    public void dropGroupIndex(String schemaName, String groupName, String indexName) {
        Session session = sessionService.createSession();
        try {
            dxlService.ddlFunctions().dropGroupIndexes(session, new TableName(schemaName, groupName), Collections.singleton(indexName));
        } finally {
            session.close();
        }
    }

    @Override
    public void dropGroupBySchema(String schemaName)
    {
        final Session session = sessionService.createSession();
        try {
            AkibanInformationSchema ais = dxlService.ddlFunctions().getAIS(session);
            for(com.akiban.ais.model.Group group: ais.getGroups().values()) {
                final String groupTableSchema = group.getRoot().getName().getSchemaName();
                if(groupTableSchema.equals(schemaName)) {
                    dxlService.ddlFunctions().dropGroup(session, group.getName());
                }
            }
        } finally {
            session.close();
        }
    }

    @Override
    public void dropGroup(String schemaName, String groupName) {
        Session session = sessionService.createSession();
        try {
            dxlService.ddlFunctions().dropGroup(session, new TableName(schemaName, groupName));
        } finally {
            session.close();
        }
    }

    @Override
    public void dropAllGroups() {
        Session session = sessionService.createSession();
        try {
            for(TableName groupName : dxlService.ddlFunctions().getAIS(session).getGroups().keySet()) {
                dropGroup(groupName.getSchemaName(), groupName.getTableName());
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
    public List<String> getGroupIndexDDLs() {
        AkibanInformationSchema ais = ais();
        return listGiDDLs(ais, getUsingSchema());
    }

    @Override
    public TableName getGroupNameFromTableName(String schemaName, String tableName) {
        AkibanInformationSchema ais = ais();
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
        return new ProtobufWriter().save(ais()).toString();
    }

    public List<String> getGrouping(String schema) {
        AkibanInformationSchema ais = ais();
        Grouping grouping = GroupsBuilder.fromAis(ais, schema);

        stripAISFromGrouping(grouping);

        String groupingString = grouping.toString();
        return Arrays.asList(groupingString.split("\\n"));
    }

    public void writeRow(String schema, String table, String fields) {
        final Session session = sessionService.createSession();
        try {
            int tableId = dxlService.ddlFunctions().getTableId(session, new TableName(schema, table));
            NewRow row = new NiceRow(tableId, dxlService.ddlFunctions().getRowDef(session, tableId));
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

    static List<String> listGiDDLs(AkibanInformationSchema ais, String usingSchema) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (com.akiban.ais.model.Group group : ais.getGroups().values()) {
            for (GroupIndex gi : group.getIndexes()) {
                // CREATE INDEX name_date ON "order"(customer.name, "order".order_date) USING LEFT JOIN
                sb.setLength(0);
                sb.append("CREATE INDEX ");
                escapeName(sb, gi.getIndexName().getName());
                sb.append(" ON ");
                String schemaName = gi.leafMostTable().getName().getSchemaName();
                if (!schemaName.equals(usingSchema))
                    escapeName(sb, schemaName).append('.');
                escapeName(sb, gi.leafMostTable().getName().getTableName()).append(" ( ");
                for (Iterator<IndexColumn> colsIter = gi.getKeyColumns().iterator(); colsIter.hasNext(); ) {
                    Column column = colsIter.next().getColumn();
                    if (!column.getTable().getName().getSchemaName().equals(schemaName))
                        throw new UnsupportedOperationException("mixed-schema GIs are not supported");
                    escapeName(sb, column.getTable().getName().getTableName()).append('.');
                    escapeName(sb, column.getName());
                    if (colsIter.hasNext())
                        sb.append(" , ");
                }
                sb.append(" ) USING ");
                switch (gi.getJoinType()) {
                case LEFT:  sb.append("LEFT JOIN"); break;
                case RIGHT: sb.append("RIGHT JOIN"); break;
                default: throw new AssertionError(gi.getJoinType());
                }
                result.add(sb.toString());
            }
        }
        return result;
    }

    private static void stripAISFromGrouping(Grouping grouping) {
        List<Group> groupsToRemove = grouping.traverse(new GroupingVisitorStub<List<Group>>() {
            private final List<Group> ret = new ArrayList<>();

            @Override
            public void visitGroup(Group group, TableName rootTable) {
                if (rootTable.getSchemaName().equals(TableName.INFORMATION_SCHEMA)) {
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

    private AkibanInformationSchema ais() {
        AkibanInformationSchema ais;
        Session session = sessionService.createSession();
        try {
            ais = dxlService.ddlFunctions().getAIS(session);
        } finally {
            session.close();
        }
        return ais;
    }

    static StringBuilder escapeName(StringBuilder sb, String name) {
        // Eventually we'll want to quote this. For now, just check that it's safe.
        boolean needsEscaping = !SAFE_NAMES.matcher(name).matches();
        if (needsEscaping && (name.indexOf('"') >= 0))
            throw new UnsupportedOperationException("illegal identifier: " + name);
        if (needsEscaping)
            sb.append('"');
        sb.append(name);
        if (needsEscaping)
            sb.append('"');
        return sb;
    }

    private static final Pattern SAFE_NAMES = Pattern.compile("[a-zA-Z]\\w*");
}

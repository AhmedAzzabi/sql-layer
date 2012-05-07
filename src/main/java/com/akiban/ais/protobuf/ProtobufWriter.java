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

package com.akiban.ais.protobuf;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.CharsetAndCollation;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.Group;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.IndexColumn;
import com.akiban.ais.model.IndexName;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.JoinColumn;
import com.akiban.ais.model.Schema;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.Type;

import com.akiban.ais.model.UserTable;
import com.akiban.server.error.ProtobufWriteException;
import com.akiban.util.GrowableByteBuffer;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ProtobufWriter {
    private static final GrowableByteBuffer NO_BUFFER = new GrowableByteBuffer(0);
    private final GrowableByteBuffer buffer;
    private AISProtobuf.AkibanInformationSchema pbAIS;
    private final String restrictSchema;

    public ProtobufWriter() {
        this(NO_BUFFER);
    }

    public ProtobufWriter(GrowableByteBuffer buffer) {
        this(buffer, null);
    }

    public ProtobufWriter(GrowableByteBuffer buffer, String restrictToSchema) {
        assert buffer.hasArray() : buffer;
        this.buffer = buffer;
        this.restrictSchema = restrictToSchema;
    }

    public AISProtobuf.AkibanInformationSchema save(AkibanInformationSchema ais) {
        AISProtobuf.AkibanInformationSchema.Builder aisBuilder = AISProtobuf.AkibanInformationSchema.newBuilder();

        // Write top level proto messages and recurse down as needed
        if(restrictSchema == null) {
            for(Type type : ais.getTypes()) {
                writeType(aisBuilder, type);
            }

            for(Schema schema : ais.getScheams().values()) {
                writeSchema(aisBuilder, schema);
            }
        } else {
            Schema schema = ais.getSchema(restrictSchema);
            if(schema != null) {
                writeSchema(aisBuilder, schema);
            }
        }

        pbAIS = aisBuilder.build();
        if (buffer != NO_BUFFER)
            writeMessageLite(pbAIS);
        return pbAIS;
    }

    private void writeMessageLite(MessageLite msg) {
        final int serializedSize = msg.getSerializedSize();
        buffer.prepareForSize(serializedSize + 4);
        buffer.limit(buffer.capacity());
        buffer.putInt(serializedSize);
        final int initialPos = buffer.position();
        final int bufferSize = buffer.limit() - initialPos;
        CodedOutputStream codedOutput = CodedOutputStream.newInstance(buffer.array(), initialPos, bufferSize);
        try {
            msg.writeTo(codedOutput);
            // Successfully written, update backing buffer info
            buffer.position(initialPos + serializedSize);
        } catch(IOException e) {
            // CodedOutputStream really only throws OutOfSpace exception, but declares IOE
            throw new ProtobufWriteException(
                    AISProtobuf.AkibanInformationSchema.getDescriptor().getFullName(),
                    String.format("Required size exceeded available size: %d vs %d", serializedSize, bufferSize)
            );
        }
    }

    private static void writeType(AISProtobuf.AkibanInformationSchema.Builder aisBuilder, Type type) {
        AISProtobuf.Type pbType = AISProtobuf.Type.newBuilder().
                setTypeName(type.name()).
                setParameters(type.nTypeParameters()).
                setFixedSize(type.fixedSize()).
                setMaxSizeBytes(type.maxSizeBytes()).
                build();
        aisBuilder.addTypes(pbType);
    }

    private static void writeSchema(AISProtobuf.AkibanInformationSchema.Builder aisBuilder, Schema schema) {
        AISProtobuf.Schema.Builder schemaBuilder = AISProtobuf.Schema.newBuilder();
        schemaBuilder.setSchemaName(schema.getName());

        // Write groups into same schema as root table
        for(UserTable table : schema.getUserTables().values()) {
            if (table.getParentJoin() == null && table.getGroup() != null) {
                writeGroup(schemaBuilder, table.getGroup());
            }
        }

        for(UserTable table : schema.getUserTables().values()) {
            writeTable(schemaBuilder, table);
        }

        aisBuilder.addSchemas(schemaBuilder.build());
    }

    private static void writeGroup(AISProtobuf.Schema.Builder schemaBuilder, Group group) {
        final UserTable rootTable = group.getGroupTable().getRoot();
        AISProtobuf.Group.Builder groupBuilder = AISProtobuf.Group.newBuilder().
                setRootTableName(rootTable.getName().getTableName()).
                setTreeName(rootTable.getTreeName());

        for(Index index : group.getIndexes()) {
            writeGroupIndex(groupBuilder, index);
        }

        schemaBuilder.addGroups(groupBuilder.build());
    }

    private static void writeTable(AISProtobuf.Schema.Builder schemaBuilder, UserTable table) {
        AISProtobuf.Table.Builder tableBuilder = AISProtobuf.Table.newBuilder();
        tableBuilder.
                setTableName(table.getName().getTableName()).
                setTableId(table.getTableId()).
                setCharColl(convertCharAndCol(table.getCharsetAndCollation()));
                // Not yet in AIS: ordinal, description, protected

        for(Column column : table.getColumnsIncludingInternal()) {
            writeColumn(tableBuilder, column);
        }

        for(Index index : table.getIndexesIncludingInternal()) {
            writeTableIndex(tableBuilder, index);
        }

        Join join = table.getParentJoin();
        if(join != null) {
            final UserTable parent = join.getParent();
            AISProtobuf.Join.Builder joinBuilder = AISProtobuf.Join.newBuilder();
            joinBuilder.setParentTable(AISProtobuf.TableName.newBuilder().
                    setSchemaName(parent.getName().getSchemaName()).
                    setTableName(parent.getName().getTableName()).
                    build());

            int position = 0;
            for(JoinColumn joinColumn : join.getJoinColumns()) {
                joinBuilder.addColumns(AISProtobuf.JoinColumn.newBuilder().
                        setParentColumn(joinColumn.getParent().getName()).
                        setChildColumn(joinColumn.getChild().getName()).
                        setPosition(position++).
                        build());
            }

            tableBuilder.setParentTable(joinBuilder.build());
        }

        schemaBuilder.addTables(tableBuilder.build());
    }

    private static void writeColumn(AISProtobuf.Table.Builder tableBuilder, Column column) {
        AISProtobuf.Column.Builder columnBuilder = AISProtobuf.Column.newBuilder().
                setColumnName(column.getName()).
                setTypeName(column.getType().name()).
                setIsNullable(column.getNullable()).
                setPosition(column.getPosition()).
                setCharColl(convertCharAndCol(column.getCharsetAndCollation()));

        if(column.getTypeParameter1() != null) {
            columnBuilder.setTypeParam1(column.getTypeParameter1());
        }
        if(column.getTypeParameter2() != null) {
            columnBuilder.setTypeParam2(column.getTypeParameter2());
        }
        if(column.getInitialAutoIncrementValue() != null) {
            columnBuilder.setInitAutoInc(column.getInitialAutoIncrementValue());
        }
        
        tableBuilder.addColumns(columnBuilder.build());
    }

    private static AISProtobuf.Index writeIndexCommon(Index index, boolean withTableName) {
        final IndexName indexName = index.getIndexName();
        AISProtobuf.Index.Builder indexBuilder = AISProtobuf.Index.newBuilder();
        indexBuilder.
                setIndexName(indexName.getName()).
                setTreeName(index.getTreeName()).
                setIndexId(index.getIndexId()).
                setIsPK(index.isPrimaryKey()).
                setIsUnique(index.isUnique()).
                setIsAkFK(index.isAkibanForeignKey()).
                setJoinType(convertJoinType(index.getJoinType()));
                // Not yet in AIS: description

        for(IndexColumn indexColumn : index.getKeyColumns()) {
            writeIndexColumn(indexBuilder, indexColumn, withTableName);
        }

        return indexBuilder.build();
    }

    private static void writeTableIndex(AISProtobuf.Table.Builder tableBuilder, Index index) {
        tableBuilder.addIndexes(writeIndexCommon(index, false));
    }

    private static void writeGroupIndex(AISProtobuf.Group.Builder groupBuilder, Index index) {
        groupBuilder.addIndexes(writeIndexCommon(index, true));
    }

    private static void writeIndexColumn(AISProtobuf.Index.Builder indexBuilder, IndexColumn indexColumn, boolean withTableName) {
        AISProtobuf.IndexColumn.Builder indexColumnBuilder = AISProtobuf.IndexColumn.newBuilder().
                setColumnName(indexColumn.getColumn().getName()).
                setIsAscending(indexColumn.isAscending()).
                setPosition(indexColumn.getPosition());
        
        if(withTableName) {
            TableName tableName = indexColumn.getColumn().getTable().getName();
            indexColumnBuilder.setTableName(
                    AISProtobuf.TableName.newBuilder().
                            setSchemaName(tableName.getSchemaName()).
                            setTableName(tableName.getTableName()).
                            build()
            );
        }

        indexBuilder.addColumns(indexColumnBuilder.build());
    }

    private static AISProtobuf.JoinType convertJoinType(Index.JoinType joinType) {
        switch(joinType) {
            case LEFT: return AISProtobuf.JoinType.LEFT_OUTER_JOIN;
            case RIGHT: return AISProtobuf.JoinType.RIGHT_OUTER_JOIN;
        }
        throw new ProtobufWriteException(AISProtobuf.Join.getDescriptor().getFullName(),
                                         "No match for Index.JoinType "+joinType.name());
    }

    private static AISProtobuf.CharCollation convertCharAndCol(CharsetAndCollation charAndColl) {
        if(charAndColl == null) {
            return null;
        }
        return AISProtobuf.CharCollation.newBuilder().
                setCharacterSetName(charAndColl.charset()).
                setCollationOrderName(charAndColl.collation()).
                build();
    }
}

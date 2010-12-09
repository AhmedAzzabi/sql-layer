/* <GENERIC-HEADER - BEGIN>
 *
 * $(COMPANY) $(COPYRIGHT)
 *
 * Created on: Nov, 20, 2009
 * Created by: Thomas Hazel
 *
 * </GENERIC-HEADER - END> */

package com.akiban.ais.model;

import com.akiban.ais.gwtutils.SerializableEnumSet;

import java.io.Serializable;
import java.util.*;

public class Join implements Serializable, ModelNames, Traversable, HasGroup
{
    public static Join create(AkibaInformationSchema ais, Map<String, Object> map)
    {
        String parentSchemaName = (String) map.get(join_parentSchemaName);
        String parentTableName = (String) map.get(join_parentTableName);
        String childSchemaName = (String) map.get(join_childSchemaName);
        String childTableName = (String) map.get(join_childTableName);
        String joinName = (String) map.get(join_joinName);
        Integer joinWeight = (Integer) map.get(join_joinWeight);
        String groupName = (String) map.get(join_groupName);
        UserTable parent = ais.getUserTable(parentSchemaName, parentTableName);
        UserTable child = ais.getUserTable(childSchemaName, childTableName);
        Join join = create(ais, joinName, parent, child);
        join.setWeight(joinWeight);
        if (groupName != null) {
            Group group = ais.getGroup(groupName);
            parent.setGroup(group);
            child.setGroup(group);
            join.setGroup(group);
        }
        int groupingUsageInt = (Integer) map.get(join_groupingUsage);
        join.setGroupingUsage(GroupingUsage.values()[groupingUsageInt]);
        int sourceTypesInt = (Integer) map.get(join_sourceTypes);
        SerializableEnumSet<SourceType> sourceTypes = new SerializableEnumSet<SourceType>(SourceType.class);
        sourceTypes.loadInt(sourceTypesInt);
        join.setSourceTypes(sourceTypes);
        return join;
    }

    public static Join create(AkibaInformationSchema ais,
                              String joinName,
                              UserTable parent,
                              UserTable child)
    {
        Join join = new Join(ais, joinName, parent, child);
        ais.addJoin(join);
        return join;
    }

    public Map<String, Object> map()
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(join_joinName, joinName);
        map.put(join_parentSchemaName, parent.getName().getSchemaName());
        map.put(join_parentTableName, parent.getName().getTableName());
        map.put(join_childSchemaName, child.getName().getSchemaName());
        map.put(join_childTableName, child.getName().getTableName());
        map.put(join_groupName, group == null ? null : group.getName());
        map.put(join_joinWeight, weight);
        map.put(join_groupingUsage, groupingUsage.ordinal());
        map.put(join_sourceTypes, sourceTypes.toInt());
        return map;
    }

    @SuppressWarnings("unused")
    private Join()
    {
        // GWT requires empty constructor
    }

    @Override
    public String toString()
    {
        return
                getGroup() == null
                ? "Join(" + joinName + ": " + child + " -> " + parent + ")"
                : "Join(" + joinName + ": " + child + " -> " + parent + ", group(" + getGroup().getName() + "))";
    }

    public void addJoinColumn(Column parent, Column child)
    {
        JoinColumn joinColumn = new JoinColumn(this, parent, child);
        joinColumns.add(joinColumn);
        joinColumnsStale = true;
    }

    public String getDescription()
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append(parent);
        buffer.append(" <- ");
        buffer.append(child);
        return buffer.toString();
    }

    public AkibaInformationSchema getAIS()
    {
        return ais;
    }

    public String getName()
    {
        return joinName;
    }

    public UserTable getParent()
    {
        return parent;
    }

    public UserTable getChild()
    {
        return child;
    }

    public Group getGroup()
    {
        return group;
    }

    public void setGroup(Group group)
    {
        // Join is permitted in group only if FK points to parent's PK.
        /*PrimaryKey parentPK =*/
        parent.getPrimaryKey();
/*
        if (parentPK == null) {
            throw new AISBuilder.UngroupableJoinException(this);
        }
        // FK and PK should match in size. hard to see how we get here if this isn't true, but check anyway.
        if (parentPK.getColumns().size() != joinColumns.size()) {
            throw new AISBuilder.UngroupableJoinException(this);
        }
        // Check that the join parent is actually the PK.
        Iterator<Column> parentPKColumnScan = parentPK.getColumns().iterator();
        Iterator<JoinColumn> joinColumnScan = joinColumns.iterator();
        while (parentPKColumnScan.hasNext()) {
            Column parentPKColumn = parentPKColumnScan.next();
            Column parentJoinColumn = joinColumnScan.next().getParent();
            if (parentPKColumn != parentJoinColumn) {
                throw new AISBuilder.UngroupableJoinException(this);
            }
        }
*/
        this.group = group;
    }

    public Integer getWeight()
    {
        return weight;
    }

    public void setWeight(Integer weight)
    {
        this.weight = weight;
    }

    public List<JoinColumn> getJoinColumns()
    {
        if (joinColumnsStale) {
            // Sort into same order as parent PK columns
            final List<Column> pkColumns = parent.getPrimaryKey().getColumns();
            Collections.sort(joinColumns,
                             new Comparator<JoinColumn>()
                             {
                                 @Override
                                 public int compare(JoinColumn x, JoinColumn y)
                                 {
                                     int xPosition = pkColumns.indexOf(x.getParent());
                                     assert xPosition >= 0;
                                     int yPosition = pkColumns.indexOf(y.getParent());
                                     assert yPosition >= 0;
                                     return xPosition - yPosition;
                                 }
                             });
            joinColumnsStale = false;
        }
        return joinColumns;
    }

    public GroupingUsage getGroupingUsage()
    {
        return groupingUsage;
    }

    public void setGroupingUsage(GroupingUsage usage)
    {
        this.groupingUsage = usage;
    }


    public Set<SourceType> getSourceTypes()
    {
        return sourceTypes;
    }

    public void setSourceTypes(SerializableEnumSet<SourceType> sourceTypes)
    {
        assert sourceTypes != null;
        this.sourceTypes = sourceTypes;
    }

    public Column getMatchingChild(Column parentColumn)
    {
        for (JoinColumn joinColumn : joinColumns) {
            if (joinColumn.getParent() == parentColumn) {
                return joinColumn.getChild();
            }
        }
        return null;
    }

    @Override
    public void traversePreOrder(Visitor visitor) throws Exception
    {
        for (JoinColumn joinColumn : joinColumns) {
            visitor.visitJoinColumn(joinColumn);
        }
    }

    @Override
    public void traversePostOrder(Visitor visitor) throws Exception
    {
        traversePreOrder(visitor);
    }

    private Join(AkibaInformationSchema ais, String joinName, UserTable parent, UserTable child)
    {
        this.ais = ais;
        this.joinName = joinName;
        this.parent = parent;
        this.child = child;
        joinColumns = new LinkedList<JoinColumn>();
        this.parent.addCandidateChildJoin(this);
        this.child.addCandidateParentJoin(this);
    }

    public enum GroupingUsage
    {
        ALWAYS, NEVER, WHEN_OPTIMAL, IGNORE
    }

    public enum SourceType
    {
        FK, COLUMN_NAME, QUERY, USER
    }

    public boolean checkIntegrity(List<String> out)
    {
        int initialSize = out.size();
        if (joinName == null) {
            out.add("null join name for join: " + this);
        } else if (parent == null) {
            out.add("null parent for join: " + this);
        } else if (child == null) {
            out.add("null child for join: " + this);
        } else if (joinColumns == null) {
            out.add("null join columns for join: " + this);
        } else {
            for (JoinColumn column : joinColumns) {
                if (column == null) {
                    out.add("join contained null column: " + this);
                } else {
                    Column child = column.getChild();
                    Column parent = column.getParent();
                    if (child == null) {
                        out.add("join contained null child column: " + this);
                    } else if (parent == null) {
                        out.add("join contained null parent column: " + this);
                    } else if (!child.getUserTable().equals(this.child)) {
                        out.add("child column's table wasn't child table: " + child + " <--> " + this.child);
                    } else if (!parent.getUserTable().equals(this.parent)) {
                        out.add("parent column's table wasn't parent table: " + child + " <--> " + this.parent);
                    }
                }
            }
        }
        return initialSize == out.size();
    }

    // State

    private AkibaInformationSchema ais;
    private String joinName;
    private Integer weight;
    private UserTable parent;
    private UserTable child;
    private Group group;
    private boolean joinColumnsStale = true;
    private List<JoinColumn> joinColumns;

    private GroupingUsage groupingUsage = GroupingUsage.WHEN_OPTIMAL;
    private SerializableEnumSet<SourceType> sourceTypes = new SerializableEnumSet<SourceType>(SourceType.class);
}

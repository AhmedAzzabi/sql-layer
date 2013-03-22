
package com.akiban.sql.optimizer.plan;

import com.akiban.ais.model.UserTable;

/** A subtree from the AIS.
 * In other words, a group.
 */
public class TableTree extends TableTreeBase<TableNode> 
{
    private int nbranches;

    protected TableNode createNode(UserTable table) {
        return new TableNode(table, this);
    }

    /** Determine branch sharing.
     * @return the number of branches. */
    public int colorBranches() {
        if (nbranches == 0)
            nbranches = colorBranches(root, 0);
        return nbranches;
    }

    private int colorBranches(TableNode node, int nbranches) {
        long branches = 0;
        for (TableNode child = node.getFirstChild(); 
             child != null; 
             child = child.getNextSibling()) {
            nbranches = colorBranches(child, nbranches);
            // A parent is on the same branch as any child.
            branches |= child.getBranches();
        }
        if (branches == 0) {
            // The leaf of a new branch.
            branches = (1L << nbranches++);
        }
        node.setBranches(branches);
        return nbranches;
    }

}

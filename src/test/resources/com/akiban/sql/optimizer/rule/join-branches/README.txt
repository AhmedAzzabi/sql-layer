group-covering: A covering group index. No lookups.

group-equals: Group index and ancestor lookup from there.

group-sort: Group index for sorting and ancestor lookup from there.

no-index: Group scan and flatten.

no-index-branch: Group scan and flatten along one branch and then
                 branch lookup the other branch a second time.

no-index-everything: A whole bunch of branches from a single group scan.

no-index-outer: Group scan and flatten with LEFT join.

order-covering: Index for ordering also covering. No lookups.

order-only: Index for ordering only.

side-branch: Index on one branch and all results from another branch,
             including an ancestor of the index.

single-table: Single ancestor of index.

two-branches: Some fields on index ancestors and some from another branch.

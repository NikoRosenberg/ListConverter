package org.example;

interface SchemaNode {}

final class LeafNode implements SchemaNode {
    static final LeafNode INSTANCE = new LeafNode();
    private LeafNode() {}
}

final class TableNode implements SchemaNode {
    final SchemaNode[] children;
    final int totalLeafCount;

    TableNode(SchemaNode[] children) {
        this.children = children;
        int count = 0;
        for (SchemaNode c : children) {
            count += (c instanceof LeafNode) ? 1 : ((TableNode) c).totalLeafCount;
        }
        this.totalLeafCount = count;
    }
}

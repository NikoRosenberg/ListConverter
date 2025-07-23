package org.example;

import java.util.*;

public class FlattenerJava17 {

    // === SCHEMA-NODES ===

    interface SchemaNode {}

    static final class LeafNode implements SchemaNode {
        static final LeafNode INSTANCE = new LeafNode();
        private LeafNode() {}
    }

    static final class TableNode implements SchemaNode {
        final SchemaNode[] children;
        final int totalLeafCount;

        TableNode(List<Object> rawSchema) {
            this.children = new SchemaNode[rawSchema.size()];
            int count = 0;
            for (int i = 0; i < rawSchema.size(); i++) {
                SchemaNode child = FlattenerJava17.compileSchema(rawSchema.get(i));
                this.children[i] = child;
                count += FlattenerJava17.countLeaves(child);
            }
            this.totalLeafCount = count;
        }
    }

    public interface CellConsumer {
        void accept(int row, int col, String value);
    }

    // === SCHEMA-HILFSMETHODEN ===

    public static SchemaNode compileSchema(Object raw) {
        if ("leaf".equals(raw)) return LeafNode.INSTANCE;
        if (raw instanceof List<?>) return new TableNode((List<Object>) raw);
        throw new IllegalArgumentException("Invalid schema element: " + raw);
    }

    public static int countLeaves(SchemaNode node) {
        if (node instanceof LeafNode) return 1;
        if (node instanceof TableNode) return ((TableNode) node).totalLeafCount;
        throw new IllegalStateException("Unknown schema node");
    }

    // === FLATTEN ===

    public static void flattenToConsumer(List<List<Object>> data, SchemaNode schema, CellConsumer consumer) {
        int totalCols = countLeaves(schema);
        String[] buffer = new String[totalCols];
        int[] rowCounter = new int[]{0};

        for (List<Object> row : data) {
            flattenRecursive(row, schema, buffer, 0, consumer, rowCounter);
        }
    }

    @SuppressWarnings("unchecked")
    private static void flattenRecursive(
        List<Object> row,
        SchemaNode schema,
        String[] buffer,
        int offset,
        CellConsumer consumer,
        int[] rowCounter
    ) {
        if (schema instanceof LeafNode) {
            buffer[offset] = (String) row.get(0);
            for (int i = 0; i < buffer.length; i++) {
                consumer.accept(rowCounter[0], i, buffer[i]);
            }
            rowCounter[0]++;
            return;
        }

        if (schema instanceof TableNode) {
            TableNode table = (TableNode) schema;
            int colOffset = offset;
            int inputIndex = 0;

            for (int i = 0; i < table.children.length; i++) {
                SchemaNode childSchema = table.children[i];
                Object value = row.get(inputIndex++);

                if (childSchema instanceof LeafNode) {
                    buffer[colOffset++] = (String) value;
                } else if (childSchema instanceof TableNode) {
                    List<List<Object>> nested = (List<List<Object>>) value;
                    for (List<Object> nestedRow : nested) {
                        flattenRecursive(nestedRow, childSchema, buffer, colOffset, consumer, rowCounter);
                    }
                    return; // verschachtelte Verarbeitung übernimmt rest
                } else {
                    throw new IllegalStateException("Unknown child schema type");
                }
            }

            // Falls keine nested Tabellen vorhanden waren:
            for (int i = 0; i < buffer.length; i++) {
                consumer.accept(rowCounter[0], i, buffer[i]);
            }
            rowCounter[0]++;
        }
    }

    // === TEST ===

    public static void main(String[] args) {
        List<Object> schemaRaw = Arrays.asList(
            "leaf",
            Arrays.asList("leaf"),
            "leaf",
            Arrays.asList("leaf", "leaf")
        );

        SchemaNode schema = compileSchema(schemaRaw);

        List<List<Object>> data = Arrays.asList(
            Arrays.asList(
                "Max",
                Arrays.asList(Arrays.asList("Mathe"), Arrays.asList("Physik")),
                "Berlin",
                Arrays.asList(
                    Arrays.asList("2021", "1.0"),
                    Arrays.asList("2022", "1.3")
                )
            )
        );

        flattenToConsumer(data, schema, new CellConsumer() {
            public void accept(int row, int col, String value) {
                System.out.printf("row=%d col=%d -> %s%n", row, col, value);
            }
        });
    }
}

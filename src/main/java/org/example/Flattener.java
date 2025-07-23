package org.example;

import java.util.*;

public class Flattener {

    public interface CellConsumer {
        void accept(int row, int col, String value);
    }

    public static void flattenToConsumer(List<List<Object>> data, SchemaNode schema, CellConsumer consumer) {
        int totalCols = countLeaves(schema);
        String[] buffer = new String[totalCols];
        int[] rowCounter = new int[]{0};

        for (List<Object> row : data) {
            flattenRecursive(row, schema, buffer, 0, consumer, rowCounter);
        }
    }

    private static int countLeaves(SchemaNode node) {
        if (node instanceof LeafNode) return 1;
        if (node instanceof TableNode) return ((TableNode) node).totalLeafCount;
        throw new IllegalStateException("Unknown schema node");
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
            buffer[offset] = row.get(0).toString();
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
                    buffer[colOffset++] = value.toString();
                } else if (childSchema instanceof TableNode) {
                    List<List<Object>> nested = (List<List<Object>>) value;
                    for (List<Object> nestedRow : nested) {
                        flattenRecursive(nestedRow, childSchema, buffer, colOffset, consumer, rowCounter);
                    }
                    return; // nested hat alles erledigt
                }
            }

            // Wenn keine nested Tabellen vorhanden
            for (int i = 0; i < buffer.length; i++) {
                consumer.accept(rowCounter[0], i, buffer[i]);
            }
            rowCounter[0]++;
        }
    }
}

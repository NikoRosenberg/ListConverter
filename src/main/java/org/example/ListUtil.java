package org.example;

import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

public class ListUtil {
    public static void walkThroughFlattenedList(Stack<Integer> structure, List<List<Object>> list, Consumer<String> visitor) {
        var end = structure.pop();

        for(int i=0;i<list.size();i++) {
            var row = list.get(i);
            for(int y=0; y<row.size();y++) {

            }

            for(int j=row.size(); j<end; j++) {

            }
        }
    }

    private static void handleColumn(int currentRow, int currentColumn, List<Object> list, Consumer<String> visitor) {
        for(int column = 0; column < list.size() -1; column++){

        }


    }
}

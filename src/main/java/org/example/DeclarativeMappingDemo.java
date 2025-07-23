package org.example;

import java.util.*;
import java.util.function.Function;

// === ENTITÄTEN ===
class Kunde {
    String name;
    List<Rechnung> rechnungen;
    List<Rechnung> rechnungenAltsystem;

    Kunde(String name, List<Rechnung> r1, List<Rechnung> r2) {
        this.name = name;
        this.rechnungen = r1;
        this.rechnungenAltsystem = r2;
    }

    String getName() { return name; }
    List<Rechnung> getRechnungen() { return rechnungen; }
    List<Rechnung> getRechnungenAltsystem() { return rechnungenAltsystem; }
}

class Rechnung {
    String nummer;
    List<Position> positionen;

    Rechnung(String nummer, List<Position> pos) {
        this.nummer = nummer;
        this.positionen = pos;
    }

    String getNummer() { return nummer; }
    List<Position> getPositionen() { return positionen; }
}

class Position {
    String artikel;
    int menge;

    Position(String artikel, int menge) {
        this.artikel = artikel;
        this.menge = menge;
    }

    String getArtikel() { return artikel; }
    int getMenge() { return menge; }
}

// === MAPPING ===
class Mapping<T> {
    private final List<FieldMapping<T>> fields = new ArrayList<>();
    private final List<ChildMapping<T, ?>> children = new ArrayList<>();

    public static <T> Mapping<T> of(Class<T> clazz) {
        return new Mapping<>();
    }

    public List<String> getHeader() {
        List<String> headers = new ArrayList<>();
        collectHeaders("", headers);
        return headers;
    }

    public void streamFlattened(T obj, Flattener.CellConsumer consumer) {
        SchemaNode schema = this.toCompiledSchema();
        int totalCols = countLeaves(schema);
        String[] buffer = new String[totalCols];
        int[] rowIndex = new int[] {0};

        streamRecursive(obj, this, buffer, 0, consumer, rowIndex);
    }

    private int countLeaves(SchemaNode node) {
        if (node instanceof LeafNode) return 1;
        if (node instanceof TableNode t) return t.totalLeafCount;
        throw new IllegalStateException("Unknown node");
    }

    @SuppressWarnings("unchecked")
    private static <T> void streamRecursive(
            T obj,
            Mapping<T> mapping,
            String[] buffer,
            int offset,
            Flattener.CellConsumer consumer,
            int[] rowIndex
    ) {
        int col = offset;

        // Felder direkt füllen
        for (FieldMapping<T> f : mapping.fields) {
            Object val = f.getter.apply(obj);
            buffer[col++] = val != null ? val.toString() : "";
        }

        // Kinder
        if (mapping.children.isEmpty()) {
            for (int i = 0; i < buffer.length; i++) {
                consumer.accept(rowIndex[0], i, buffer[i]);
            }
            rowIndex[0]++;
            return;
        }

        // Für jedes Child → rekursiv
        for (ChildMapping<T, ?> child : mapping.children) {
            Collection<?> children = child.getter.apply(obj);
            if (children == null || children.isEmpty()) continue;

            SchemaNode subSchema = child.mapping.toCompiledSchema();
            int childCols = child.mapping.countLeaves(subSchema);

            for (Object c : children) {
                streamRecursive(c, (Mapping<Object>) child.mapping, buffer, col, consumer, rowIndex);
            }
            return; // Achtung: downstream hat alles erledigt!
        }

        // Wenn keine Kinder aufgerufen wurden (nur primitive + keine Children)
        for (int i = 0; i < buffer.length; i++) {
            consumer.accept(rowIndex[0], i, buffer[i]);
        }
        rowIndex[0]++;
    }


    private void collectHeaders(String prefix, List<String> headers) {
        for (FieldMapping<T> f : fields) {
            headers.add(prefix + f.name);
        }
        for (ChildMapping<T, ?> c : children) {
            // wir nehmen hier 1 „Platzhalter“-Index für jede Kind-Liste
            String childPrefix = prefix + c.name + ".";
            c.mapping.collectHeaders(childPrefix, headers);
        }
    }
    public SchemaNode toCompiledSchema() {
        List<SchemaNode> schemaNodes = new ArrayList<>();
        for (FieldMapping<T> f : fields) {
            schemaNodes.add(LeafNode.INSTANCE);
        }
        for (ChildMapping<T, ?> c : children) {
            schemaNodes.add(c.mapping.toCompiledSchema());
        }
        return new TableNode(schemaNodes.toArray(new SchemaNode[0]));
    }


    public Mapping<T> field(String name, Function<T, ?> getter) {
        fields.add(new FieldMapping<>(name, getter));
        return this;
    }

    public <C> Mapping<T> child(String name, Function<T, Collection<C>> getter, Mapping<C> childMapping) {
        children.add(new ChildMapping<>(name, getter, childMapping));
        return this;
    }

    public List<Object> transform(T obj) {
        List<Object> result = new ArrayList<>();
        for (FieldMapping<T> f : fields) {
            Object value = f.getter.apply(obj);
            result.add(value);
        }
        for (ChildMapping<T, ?> cRaw : children) {
            result.add(transformChild(cRaw, obj));
        }
        return result;
    }

    private <C> List<Object> transformChild(ChildMapping<T, C> c, T obj) {
        List<Object> list = new ArrayList<>();
        Collection<C> coll = c.getter.apply(obj);
        for (C child : coll) {
            list.add(c.mapping.transform(child));
        }
        return list;
    }

    private static class FieldMapping<T> {
        String name;
        Function<T, ?> getter;

        FieldMapping(String name, Function<T, ?> getter) {
            this.name = name;
            this.getter = getter;
        }
    }

    private static class ChildMapping<T, C> {
        String name;
        Function<T, Collection<C>> getter;
        Mapping<C> mapping;

        ChildMapping(String name, Function<T, Collection<C>> getter, Mapping<C> mapping) {
            this.name = name;
            this.getter = getter;
            this.mapping = mapping;
        }
    }
}



// === MAIN ===
public class DeclarativeMappingDemo {
    public static void main(String[] args) {
        // Mappings
       var positionMapping = Mapping.of(Position.class)
                .field("artikel", Position::getArtikel)
                .field("menge", Position::getMenge);

        var rechnungMapping = Mapping.of(Rechnung.class)
                .field("nummer", Rechnung::getNummer)
                .child("positionen", Rechnung::getPositionen, positionMapping);

        var kundeMapping = Mapping.of(Kunde.class)
                .field("name", Kunde::getName)
                .child("rechnungen", Kunde::getRechnungen, rechnungMapping)
                .child("rechnungenAltsystem", Kunde::getRechnungenAltsystem, rechnungMapping);

        // Beispiel-Daten
        Position p1 = new Position("Apfel", 3);
        Position p2 = new Position("Banane", 5);
        Rechnung r1 = new Rechnung("R001", List.of(p1, p2));
        Rechnung r2 = new Rechnung("R002", List.of(new Position("Birne", 2)));

        Kunde k = new Kunde("Max Mustermann", List.of(r1), List.of(r2));

        // Transformation
        List<Object> result = kundeMapping.transform(k);

        System.out.println(kundeMapping.getHeader());


    }


}

package fr.gaulupeau.apps.Poche.tts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class TtsConverter {

    private static class Offset implements Comparable<Offset> {
        int index;
        int amount;

        Offset(int index, int amount) {
            this.index = index;
            this.amount = amount;
        }

        @Override
        public int compareTo(Offset o) {
            return Integer.compare(index, o.index);
        }
    }

    CharSequence convert(GenericItem genericItem) {
        if (genericItem == null) return null;

        if (genericItem instanceof TextItem) {
            return convert((TextItem) genericItem);
        }

        throw new RuntimeException("Conversion is not implemented for item type: " + genericItem);
    }

    private CharSequence convert(TextItem textItem) {
        String text = textItem.text;
        List<TextItem.Extra> extras = textItem.extras;
        if (extras == null || extras.isEmpty()) return text;

        int originalLength = text.length();

        List<Offset> offsets = new ArrayList<>(extras.size() * 2);

        for (TextItem.Extra extra : extras) {
            if (extra.type == TextItem.Extra.Type.EMPHASIS) {
                // I didn't find any proper way to emphasize in TTS, so I just add extra commas

                int start = extra.start;
                int end = extra.end;

                if (start >= end || start >= originalLength || end <= 0) continue;

                if (start < 0) start = 0;
                if (end > originalLength) end = originalLength;

                text = insert(offsets, text, " ,", start);
                text = insert(offsets, text, ", ", end);
            }
        }

        return text;
    }

    private String insert(List<Offset> offsets, String s, String insert, int originalIndex) {
        int index = adjustIndex(offsets, originalIndex);
        s = insert(s, insert, index);
        addOffset(offsets, index, insert.length());
        return s;
    }

    private String insert(String s, String insert, int index) {
        return s.substring(0, index) + insert + s.substring(index);
    }

    private int adjustIndex(List<Offset> offsets, int index) {
        for (Offset offset : offsets) {
            if (offset.index <= index) index += offset.amount;
        }
        return index;
    }

    private void addOffset(List<Offset> offsets, int index, int amount) {
        boolean addNew = true;

        for (Offset offset : offsets) {
            if (offset.index == index) {
                offset.amount += amount;
                addNew = false;
            } else if (offset.index > index) {
                offset.index += amount;
            }
        }

        if (addNew) {
            offsets.add(new Offset(index, amount));
            Collections.sort(offsets);
        }
    }

}

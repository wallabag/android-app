package fr.gaulupeau.apps.Poche.tts;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;

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

    private Context context;

    TtsConverter(Context context) {
        this.context = context;
    }

    CharSequence convert(GenericItem genericItem) {
        if (genericItem == null) return null;

        if (genericItem instanceof TextItem) {
            return convert((TextItem) genericItem);
        } else if (genericItem instanceof ImageItem) {
            return convert((ImageItem) genericItem);
        }

        throw new RuntimeException("Conversion is not implemented for item type: " + genericItem);
    }

    private CharSequence convert(TextItem textItem) {
        String text = textItem.text;
        List<TextItem.Extra> extras = textItem.extras;
        if (extras == null || extras.isEmpty()) return text;

        extras = simplifyTextItemExtras(extras);
        if (extras.isEmpty()) return text;

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

    /*
     * Removes unsupported extra types (only emphasis is supported),
     * removes invalid ranges (start >= end), collapses nested and adjacent extras.
     */
    private List<TextItem.Extra> simplifyTextItemExtras(List<TextItem.Extra> extras) {
        class Point {
            private int index;
            private boolean start;

            private Point(int index, boolean start) {
                this.index = index;
                this.start = start;
            }
        }

        List<Point> points = new ArrayList<>(extras.size() * 2);

        for (TextItem.Extra extra : extras) {
            if (extra.type != TextItem.Extra.Type.EMPHASIS) continue;
            if (extra.start >= extra.end) continue;

            points.add(new Point(extra.start, true));
            points.add(new Point(extra.end, false));
        }

        Collections.sort(points, (o1, o2) -> {
            int result = Integer.compare(o1.index, o2.index);
            if (result == 0) result = Boolean.compare(o2.start, o1.start); // reverse: `true` is earlier
            return result;
        });

        extras = new ArrayList<>();

        int level = 0;
        int startIndex = 0;
        for (Point point : points) {
            if (point.start) {
                if (level == 0) { // nesting is ignored
                    startIndex = point.index;
                }
                level++;
            } else {
                level--;
                if (level == 0) {
                    extras.add(new TextItem.Extra(
                            TextItem.Extra.Type.EMPHASIS, startIndex, point.index));
                }
            }
        }

        return extras;
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

    private CharSequence convert(ImageItem item) {
        if (!TextUtils.isEmpty(item.altText)) {
            if (item.altText.equals(context.getString(R.string.articleContent_globeIconAltText))
                    || item.altText.equals(context.getString(R.string.articleContent_previewImageAltText))) {
                return null;
            }
        }

        String title = item.altText;
        if (TextUtils.isEmpty(title)) title = item.title;
        // can also fall back to image name from src, but I'm not sure how appropriate it is

        if (!TextUtils.isEmpty(title)) {
            return context.getString(R.string.tts_image, title);
        }

        return null; // ignore the image
    }

}

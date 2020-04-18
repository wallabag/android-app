package fr.gaulupeau.apps.Poche.tts;

abstract class GenericItem {

    static class Range {
        String start;
        long startOffset;
        String end;
        long endOffset;

        Range(String start, long startOffset, String end, long endOffset) {
            this.start = start;
            this.startOffset = startOffset;
            this.end = end;
            this.endOffset = endOffset;
        }

        @Override
        public String toString() {
            return "Range{" +
                    "start='" + start + '\'' +
                    ", startOffset=" + startOffset +
                    ", end='" + end + '\'' +
                    ", endOffset=" + endOffset +
                    '}';
        }
    }

    Range range;
    float top;    // top location in the web view
    float bottom; // bottom location in the web view
    long timePosition; // in milliseconds from the beginning of the document

    GenericItem() {}

    GenericItem(Range range, float top, float bottom) {
        this.range = range;
        this.top = top;
        this.bottom = bottom;
    }

}

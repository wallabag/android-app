package fr.gaulupeau.apps.Poche.tts;

import java.util.List;
import java.util.Objects;

class TextItem extends GenericItem {

    static class Extra {
        enum Type {
            EMPHASIS("emphasis");

            final String type;

            Type(String type) {
                this.type = type;
            }

            static Type getType(String type) {
                for (Type value : values()) {
                    if (value.type.equals(type)) return value;
                }
                return null;
            }
        }

        Extra.Type type;
        int start, end;

        Extra() {}

        Extra(Extra.Type type, int start, int end) {
            this.type = Objects.requireNonNull(type);
            this.start = start;
            this.end = end;
        }
    }

    String text;
    List<Extra> extras;

    TextItem(String text, float top, float bottom, List<Extra> extras) {
        super(top, bottom);
        this.text = text;
        this.extras = extras;
    }

}

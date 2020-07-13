package fr.gaulupeau.apps.Poche.service;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;

public class ParcelableUtils {

    public static void writeLong(Long value, Parcel out) {
        out.writeByte((byte) (value == null ? 0 : 1));

        if (value != null) out.writeLong(value);
    }

    public static Long readLong(Parcel in) {
        if (in.readByte() == 0) return null;

        return in.readLong();
    }

    public static void writeInteger(Integer value, Parcel out) {
        out.writeByte((byte) (value == null ? 0 : 1));

        if (value != null) out.writeInt(value);
    }

    public static Integer readInteger(Parcel in) {
        if (in.readByte() == 0) return null;

        return in.readInt();
    }

    public static void writeDouble(Double value, Parcel out) {
        out.writeByte((byte) (value == null ? 0 : 1));

        if (value != null) out.writeDouble(value);
    }

    public static Double readDouble(Parcel in) {
        if (in.readByte() == 0) return null;

        return in.readDouble();
    }

    public static void writeString(String value, Parcel out) {
        out.writeByte((byte) (value == null ? 0 : 1));

        if (value != null) out.writeString(value);
    }

    public static String readString(Parcel in) {
        if (in.readByte() == 0) return null;

        return in.readString();
    }

    public static void writeBool(boolean value, Parcel out) {
        out.writeByte((byte) (value ? 1 : 0));
    }

    public static boolean readBool(Parcel in) {
        return in.readByte() != 0;
    }

    public static void writeBoolean(Boolean value, Parcel out) {
        out.writeByte((byte) (value == null ? 0 : 1));

        if (value != null) writeBool(value, out);
    }

    public static Boolean readBoolean(Parcel in) {
        if (in.readByte() == 0) return null;

        return in.readByte() == 0 ? Boolean.FALSE : Boolean.TRUE;
    }

    public static void writeEnum(Enum<?> value, Parcel out) {
        out.writeByte((byte) (value == null ? 0 : 1));

        if (value != null) out.writeInt(value.ordinal());
    }

    public static <T extends Enum<?>> T readEnum(Class<T> enumClass, Parcel in) {
        if (in.readByte() == 0) return null;

        return enumClass.getEnumConstants()[in.readInt()];
    }

    public interface Writer<T> {
        void write(Parcel parcel, T value);
    }

    public interface Reader<T> {
        T read(Parcel parcel);
    }

    public static <T> void writeList(Parcel out, List<T> list, Writer<? super T> writer) {
        if (list == null) {
            writeInteger(null, out);
            return;
        }

        writeInteger(list.size(), out);
        for (T item : list) {
            writer.write(out, item);
        }
    }

    public static <T> List<T> readList(Parcel in, Reader<? extends T> reader) {
        Integer size = readInteger(in);
        if (size == null) return null;

        List<T> list = new ArrayList<>(size);
        for (int i = size; i > 0; i--) {
            list.add(reader.read(in));
        }
        return list;
    }

}

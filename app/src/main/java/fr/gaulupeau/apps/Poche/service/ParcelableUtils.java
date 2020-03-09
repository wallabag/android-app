package fr.gaulupeau.apps.Poche.service;

import android.os.Parcel;

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

    public static void writeBoolean(Boolean value, Parcel out) {
        out.writeByte((byte) (value == null ? 0 : 1));

        if (value != null) out.writeByte((byte) (value ? 1 : 0));
    }

    public static Boolean readBoolean(Parcel in) {
        if (in.readByte() == 0) return null;

        return in.readByte() == 0 ? Boolean.FALSE : Boolean.TRUE;
    }

    public static void writeEnum(Enum value, Parcel out) {
        out.writeByte((byte) (value == null ? 0 : 1));

        if (value != null) out.writeInt(value.ordinal());
    }

    public static <T extends Enum> T readEnum(Class<T> enumClass, Parcel in) {
        if (in.readByte() == 0) return null;

        return enumClass.getEnumConstants()[in.readInt()];
    }

}

package fr.gaulupeau.apps.Poche.service.tasks;

import android.os.Parcel;

import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readBoolean;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readDouble;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readInteger;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readLong;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readString;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeBoolean;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeDouble;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeInteger;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeLong;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeString;

public class GenericFieldsTask extends SimpleTask {

    protected Integer genericIntField1;
    protected Long genericLongField1;

    protected String genericStringField1;
    protected String genericStringField2;

    protected Boolean genericBooleanField1;

    protected Double genericDoubleField1;

    // Parcelable implementation

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);

        writeInteger(genericIntField1, dest);
        writeLong(genericLongField1, dest);

        writeString(genericStringField1, dest);
        writeString(genericStringField2, dest);

        writeBoolean(genericBooleanField1, dest);

        writeDouble(genericDoubleField1, dest);
    }

    @Override
    protected void readFromParcel(Parcel in) {
        super.readFromParcel(in);

        genericIntField1 = readInteger(in);
        genericLongField1 = readLong(in);

        genericStringField1 = readString(in);
        genericStringField2 = readString(in);

        genericBooleanField1 = readBoolean(in);

        genericDoubleField1 = readDouble(in);
    }

    @SuppressWarnings("unused") // needed for CREATOR
    protected GenericFieldsTask() {}

    public static final TaskCreator<GenericFieldsTask> CREATOR
            = new TaskCreator<>(GenericFieldsTask.class);

}

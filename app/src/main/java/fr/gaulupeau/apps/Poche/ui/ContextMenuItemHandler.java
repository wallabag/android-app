package fr.gaulupeau.apps.Poche.ui;

import android.app.Activity;
import android.view.MenuItem;

interface ContextMenuItemHandler {

    boolean handleContextItemSelected(Activity activity, MenuItem item);

}

package fr.gaulupeau.apps.Poche.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.view.MenuItem;

interface ContextMenuItemHandler {

    boolean handleContextItemSelected(AppCompatActivity activity, MenuItem item);

}

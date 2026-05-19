package fr.gaulupeau.apps.Poche.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import android.os.Bundle;

import fr.gaulupeau.apps.InThePoche.R;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        ComposeView composeView = findViewById(R.id.compose_view);
        AboutLibsHelperKt.showAboutLibraries(composeView);  // Kotlin Extension = statische Methode in Java
    }
}

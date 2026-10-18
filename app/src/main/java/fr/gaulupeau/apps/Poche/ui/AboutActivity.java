package fr.gaulupeau.apps.Poche.ui;

import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mikepenz.aboutlibraries.LibsBuilder;

import fr.gaulupeau.apps.InThePoche.R;

public class AboutActivity extends BaseActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getTheme().applyStyle(R.style.AboutLibrariesThemeOverlay, true);
        setContentView(R.layout.activity_about);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setSupportActionBar(findViewById(R.id.toolbar));
        addBackButtonToActionBar();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.about_root), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });

        if(savedInstanceState == null) {
            CharSequence aboutCharSequence = getText(R.string.aboutText);
            String aboutString = aboutCharSequence instanceof Spanned
                ? Html.toHtml((Spanned) aboutCharSequence)
                : aboutCharSequence.toString()
            ;

            getSupportFragmentManager().beginTransaction()
                .replace(R.id.about_container, new LibsBuilder()
                    .withAboutIconShown(true)
                    .withAboutVersionShown(true)
                    .withAboutDescription(aboutString)
                    .supportFragment())
                .commit()
            ;
        }
    }

    @Override
    protected boolean hasOwnToolbar() {
        return true;
    }

}

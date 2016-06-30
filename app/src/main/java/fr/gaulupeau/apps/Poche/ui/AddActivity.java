package fr.gaulupeau.apps.Poche.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Random;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.events.AddLinkFinishedEvent;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;

public class AddActivity extends BaseActionBarActivity {

    private ProgressBar progressBar;
    private EditText pageUrlEditText;

    private Random random;

    private Long operationID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        pageUrlEditText = (EditText) findViewById(R.id.page_url);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        if(progressBar != null) progressBar.setIndeterminate(true);

        random = new Random();
    }

    @Override
    protected void onStart() {
        super.onStart();

        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);

        super.onStop();
    }

    public void addButtonClicked(View view) {
        // TODO: check url

        operationID = random.nextLong();
        ServiceHelper.addLink(this, pageUrlEditText.getText().toString(), operationID);

        progressBar.setVisibility(View.VISIBLE);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAddLinkFinishedEvent(AddLinkFinishedEvent event) {
        if(operationID != null && operationID == event.getOperationID()) {
            progressBar.setVisibility(View.GONE);

            // TODO: add visual feedback

            operationID = null;
        }
    }

}

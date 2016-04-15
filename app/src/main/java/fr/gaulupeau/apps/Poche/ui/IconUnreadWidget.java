package fr.gaulupeau.apps.Poche.ui;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;

/**
 * Implementation of App Widget functionality.
 */
public class IconUnreadWidget extends AppWidgetProvider {
    private static final String TAG = IconUnreadWidget.class.getSimpleName();

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {
        Log.d(TAG, "updateAppWidget() appWidgetId=" + appWidgetId);


        ArticleDao articleDao = DbConnection.getSession().getArticleDao();
        long unreadCount = articleDao.getUnreadCount(articleDao.getDatabase());
        Log.d(TAG, "updateAppWidget() read from database unreadCount=" + unreadCount);

        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.icon_unread);
        Log.d(TAG, "updateAppWidget() setting unread_count_text to " + unreadCount);

        if (unreadCount <= 0) {
            // Hide TextView for unread count if there are no unread messages.
            views.setViewVisibility(R.id.unread_count_text, View.GONE);
        } else {
            views.setViewVisibility(R.id.unread_count_text, View.VISIBLE);
            if (unreadCount > Settings.WALLABAG_WIDGET_MAX_UNREAD_COUNT) {
                views.setTextViewText(R.id.unread_count_text, Settings.WALLABAG_WIDGET_MAX_UNREAD_COUNT + "+");
            } else {
                views.setTextViewText(R.id.unread_count_text, "" + unreadCount);
            }
        }

        //views.setOnClickPendingIntent(R.id., getRefreshPendingIntent(context, appWidgetId));

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate() appWidgetIds.length=" + appWidgetIds.length);
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "onEnabled()");
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "onDisabled()");
        // Enter relevant functionality for when the last widget is disabled
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.d(TAG, "onReceive()");
        final String action = intent.getAction();
        Log.d(TAG, "onReceive() action=" + action);

        if (intent.getAction().equals("android.appwidget.action.APPWIDGET_UPDATE")) {
            Log.d(TAG, "onReceive() some code here that will update your widget");
            //some code here that will update your widget
        }

        super.onReceive(context, intent);
    }

    public static void triggerWidgetUpdate(Context context) {
        Log.d(TAG, "static triggerWidgetUpdate()");
        Context appContext = context.getApplicationContext();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(appContext);
        ComponentName thisWidget = new ComponentName(appContext, IconUnreadWidget.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        Intent intent = new Intent(context, IconUnreadWidget.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
        context.sendBroadcast(intent);
    }
}

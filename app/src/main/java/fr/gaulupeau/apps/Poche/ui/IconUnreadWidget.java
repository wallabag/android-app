package fr.gaulupeau.apps.Poche.ui;

import android.app.PendingIntent;
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

        // start article list activity on click on the widget
        Intent intent = new Intent(context, ArticlesListActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, 0);
        views.setOnClickPendingIntent(R.id.icon_unread_layout, pendingIntent);

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

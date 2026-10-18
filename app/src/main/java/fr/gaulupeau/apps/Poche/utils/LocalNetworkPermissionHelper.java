package fr.gaulupeau.apps.Poche.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import fr.gaulupeau.apps.InThePoche.R;
import okhttp3.HttpUrl;

/**
 * Handles the ACCESS_LOCAL_NETWORK permission introduced in Android 17 (API 37),
 * where access to the local network is blocked by default for apps targeting API 37+.
 * The permission is requested only if the server URL turns out to point
 * to the local network; it is not used on older Android versions.
 */
public class LocalNetworkPermissionHelper {

    private static final String TAG = LocalNetworkPermissionHelper.class.getSimpleName();

    private static final int REQUEST_CODE = 4203;

    public interface UrlLocalnessHandler {
        /**
         * @param isLocal true if the URL points to the local network, false if it
         *                definitely doesn't, null if it couldn't be determined
         */
        void onUrlLocalnessResult(Boolean isLocal);
    }

    public interface PermissionRequestHandler {
        void onLocalNetworkPermissionResolved(boolean granted);
    }

    private static PermissionRequestHandler pendingRequestHandler;
    private static AlertDialog pendingDialog;

    public static boolean isPermissionRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN;
    }

    public static boolean isPermissionGranted(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Calls the handler (on the UI thread) once the local network access is available,
     * requesting the permission first if needed. The handler is also called with
     * {@code granted == true} if the permission is not required or the server URL
     * doesn't appear to point to the local network.
     */
    public static void ensureLocalNetworkPermission(Activity activity, String url,
                                                    PermissionRequestHandler handler) {
        if (!isPermissionRequired() || activity == null || isPermissionGranted(activity)) {
            handler.onLocalNetworkPermissionResolved(true);
            return;
        }

        checkUrlLocalness(url, new UrlLocalnessHandler() {
            @Override
            public void onUrlLocalnessResult(Boolean isLocal) {
                if (isLocal == null || !isLocal) {
                    handler.onLocalNetworkPermissionResolved(true);
                    return;
                }

                requestPermission(activity, handler);
            }
        });
    }

    /**
     * Should be called from the activity's onRequestPermissionsResult().
     *
     * @return true if the result was consumed
     */
    public static boolean onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        if (requestCode != REQUEST_CODE) return false;

        dismissPendingDialog();

        if (pendingRequestHandler == null) return false;

        boolean granted = false;
        if (permissions != null && grantResults != null) {
            for(int i = 0; i < permissions.length; i++) {
                if(Manifest.permission.ACCESS_LOCAL_NETWORK.equals(permissions[i])) {
                    granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    break;
                }
            }
        }

        resolvePendingRequest(granted);

        return true;
    }

    public static void cancelPendingRequest() {
        dismissPendingDialog();
        resolvePendingRequest(false);
    }

    private static void requestPermission(Activity activity, PermissionRequestHandler handler) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            Log.i(TAG, "requestPermission() activity is not usable");
            handler.onLocalNetworkPermissionResolved(true);
            return;
        }

        if (pendingRequestHandler != null) {
            Log.i(TAG, "requestPermission() replacing pending request");
            dismissPendingDialog();
        }
        pendingRequestHandler = handler;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.d_localNetworkPermission_title)
                .setMessage(R.string.d_localNetworkPermission_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismissPendingDialog();

                        ActivityCompat.requestPermissions(activity,
                                new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK},
                                REQUEST_CODE);
                    }
                })
                .setNegativeButton(R.string.negative_answer, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resolvePendingRequest(false);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        resolvePendingRequest(false);
                    }
                });
        pendingDialog = builder.show();
    }

    private static void resolvePendingRequest(boolean granted) {
        PermissionRequestHandler handler = pendingRequestHandler;
        pendingRequestHandler = null;

        if (handler != null) handler.onLocalNetworkPermissionResolved(granted);
    }

    private static void dismissPendingDialog() {
        if (pendingDialog != null) {
            if (pendingDialog.isShowing()) pendingDialog.dismiss();
            pendingDialog = null;
        }
    }

    public static void checkUrlLocalness(String url, UrlLocalnessHandler handler) {
        new CheckUrlLocalnessTask(url, handler).execute();
    }

    private static class CheckUrlLocalnessTask extends AsyncTask<Void, Void, Boolean> {

        private final String url;
        private final UrlLocalnessHandler handler;

        CheckUrlLocalnessTask(String url, UrlLocalnessHandler handler) {
            this.url = url;
            this.handler = handler;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return checkUrlLocalness(url);
        }

        @Override
        protected void onPostExecute(Boolean isLocal) {
            handler.onUrlLocalnessResult(isLocal);
        }

    }

    private static Boolean checkUrlLocalness(String url) {
        String host = getUrlHost(url);
        if (TextUtils.isEmpty(host)) {
            Log.i(TAG, "checkUrlLocalness() no host in URL: " + url);
            return false;
        }

        // mDNS names are local by definition and may be unresolvable via DNS
        if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".local.")) {
            return true;
        }

        try {
            // IP literals are parsed without a DNS query
            InetAddress[] addresses = InetAddress.getAllByName(host);

            for (InetAddress address : addresses) {
                if (isLocalAddress(address)) return true;
            }

            return false;
        } catch (UnknownHostException e) {
            Log.i(TAG, "checkUrlLocalness() couldn't resolve host: " + host);
            return null;
        }
    }

    private static String getUrlHost(String url) {
        if (TextUtils.isEmpty(url)) return null;

        if (!url.contains("://")) url = "http://" + url;

        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) return null;

        return httpUrl.host();
    }

    // there is no single InetAddress method covering all local ranges
    public static boolean isLocalAddress(InetAddress address) {
        return address.isLoopbackAddress() // 127/8, ::1
                || address.isLinkLocalAddress() // 169.254/16, fe80::/10
                || address.isSiteLocalAddress() // 10/8, 172.16/12, 192.168/16
                || address.isAnyLocalAddress() // 0.0.0.0, ::
                || isUniqueLocalIpv6Address(address) // fc00::/7
                || isCarrierGradeNatIpv4Address(address); // 100.64/10
    }

    private static boolean isUniqueLocalIpv6Address(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private static boolean isCarrierGradeNatIpv4Address(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && bytes[0] == 100 && (bytes[1] & 0xc0) == 0x40;
    }

}

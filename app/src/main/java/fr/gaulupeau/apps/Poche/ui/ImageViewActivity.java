package fr.gaulupeau.apps.Poche.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.github.panpf.zoomimage.ZoomImageView;

import java.io.File;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.network.ImageCacheUtils;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ImageViewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "ImageViewActivity.imageUrl";
    public static final String EXTRA_ARTICLE_ID = "ImageViewActivity.articleId";

    private static final String TAG = ImageViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_view);

        ZoomImageView photoView = findViewById(R.id.photoView);
        ProgressBar progressBar = findViewById(R.id.progressBar);

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        long articleId = getIntent().getLongExtra(EXTRA_ARTICLE_ID, -1);

        if (imageUrl == null || imageUrl.isEmpty()) {
            Log.w(TAG, "onCreate() no image URL");
            finish();
            return;
        }

        photoView.setOnClickListener(v -> finish());

        new Thread(() -> {
            Bitmap bitmap = loadBitmap(imageUrl, articleId);
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (bitmap != null) {
                    photoView.setImageBitmap(bitmap);
                } else {
                    Log.w(TAG, "onCreate() failed to load image");
                    finish();
                }
            });
        }).start();
    }

    private Bitmap loadBitmap(String imageUrl, long articleId) {
        // Try loading from local cache first
        if (articleId >= 0) {
            try {
                File file = ImageCacheUtils.getCachedImageFile(imageUrl, articleId);
                if (file != null) {
                    Bitmap bitmap = decodeFileScaled(file.getAbsolutePath());
                    if (bitmap != null) return bitmap;
                }
            } catch (Exception e) {
                Log.w(TAG, "loadBitmap() cache load failed", e);
            }
        }

        // Try loading from file:// URL (cached images served to WebView)
        if (imageUrl.startsWith("file://")) {
            try {
                String path = imageUrl.substring("file://".length());
                Bitmap bitmap = decodeFileScaled(path);
                if (bitmap != null) return bitmap;
            } catch (Exception e) {
                Log.w(TAG, "loadBitmap() file URL load failed", e);
            }
        }

        // Fall back to loading from network
        try {
            OkHttpClient client = WallabagConnection.createClient();
            Request request = new Request.Builder().url(imageUrl).build();
            try (Response response = client.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (response.isSuccessful() && body != null) {
                    return decodeBytesScaled(body.bytes());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "loadBitmap() remote load failed", e);
        }

        return null;
    }

    // 2048 keeps the bitmap (~16MB @ ARGB_8888) under the Canvas draw limit and
    // within GL_MAX_TEXTURE_SIZE on older GPUs. Article images are typically <2048px wide anyway.
    private static final int MAX_BITMAP_DIMENSION = 2048;

    private static Bitmap decodeFileScaled(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = computeSampleSize(options.outWidth, options.outHeight);
        options.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "decodeFileScaled() out of memory");
            return null;
        }
    }

    private static Bitmap decodeBytesScaled(byte[] data) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        options.inSampleSize = computeSampleSize(options.outWidth, options.outHeight);
        options.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "decodeBytesScaled() out of memory");
            return null;
        }
    }

    private static int computeSampleSize(int width, int height) {
        int sampleSize = 1;
        while (width > 0 && height > 0
                && (width / sampleSize > MAX_BITMAP_DIMENSION
                || height / sampleSize > MAX_BITMAP_DIMENSION)) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}

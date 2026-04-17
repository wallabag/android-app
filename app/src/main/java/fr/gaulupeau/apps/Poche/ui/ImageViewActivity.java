package fr.gaulupeau.apps.Poche.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.github.chrisbanes.photoview.PhotoView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.network.ImageCacheUtils;

public class ImageViewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "ImageViewActivity.imageUrl";
    public static final String EXTRA_ARTICLE_ID = "ImageViewActivity.articleId";

    private static final String TAG = ImageViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_view);

        PhotoView photoView = findViewById(R.id.photoView);
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
            URL url = new URL(imageUrl);
            try (InputStream is = url.openStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = is.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                }
                return decodeBytesScaled(buffer.toByteArray());
            }
        } catch (Exception e) {
            Log.w(TAG, "loadBitmap() remote load failed", e);
        }

        return null;
    }

    // Canvas hardware-accelerated draw limit is ~100MB; cap at 4096px (64MB @ ARGB_8888).
    private static final int MAX_BITMAP_DIMENSION = 4096;

    private static Bitmap decodeFileScaled(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = computeSampleSize(options.outWidth, options.outHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private static Bitmap decodeBytesScaled(byte[] data) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        options.inSampleSize = computeSampleSize(options.outWidth, options.outHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
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

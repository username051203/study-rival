package com.studyrival.omega;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.*;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.app.AlertDialog;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.*;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private String pendingPhotoTargetIds = null;
    private boolean pendingFileImport = false;
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private PermissionRequest pendingWebKitPermission = null;

    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
        registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                final String data = result.getContents()
                    .replace("\\", "\\\\").replace("'", "\\'");
                runOnUiThread(() -> webView.evaluateJavascript(
                    "onQRScanned('" + data + "');", null));
            }
        });

    private final ActivityResultLauncher<String> imagePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null || pendingPhotoTargetIds == null) return;
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                byte[] bytes = readBytes(is);
                String b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                String mime = getContentResolver().getType(uri);
                if (mime == null) mime = "image/jpeg";
                String dataUrl = "data:" + mime + ";base64," + b64;
                final String ids = pendingPhotoTargetIds;
                pendingPhotoTargetIds = null;
                runOnUiThread(() -> webView.evaluateJavascript(
                    "onPhotoPicked('" + dataUrl.replace("'", "\\'") + "','" + ids.replace("'", "\\'") + "');", null));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show());
            }
        });

    private final ActivityResultLauncher<String> filePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null || !pendingFileImport) return;
            pendingFileImport = false;
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                final String content = sb.toString().replace("\\", "\\\\").replace("'", "\\'");
                runOnUiThread(() -> webView.evaluateJavascript(
                    "onFileImported('" + content + "');", null));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show());
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        setupWebView();
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean needsCamera = false;
                for (String r : request.getResources())
                    if (r.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) { needsCamera = true; break; }
                if (needsCamera) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread(() -> request.grant(request.getResources()));
                    } else {
                        pendingWebKitPermission = request;
                        ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
                    }
                } else { request.deny(); }
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && pendingWebKitPermission != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                runOnUiThread(() -> pendingWebKitPermission.grant(pendingWebKitPermission.getResources()));
            else {
                pendingWebKitPermission.deny();
                runOnUiThread(() -> Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show());
            }
            pendingWebKitPermission = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("onAndroidBack();", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void showQR(String data) {
            runOnUiThread(() -> {
                try {
                    MultiFormatWriter writer = new MultiFormatWriter();
                    BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, 600, 600);
                    int w = matrix.getWidth(), h = matrix.getHeight();
                    int[] pixels = new int[w * h];
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++)
                            pixels[y * w + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                    Bitmap bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
                    ImageView iv = new ImageView(MainActivity.this);
                    iv.setImageBitmap(bmp);
                    iv.setBackgroundColor(0xFF0d0f1a);
                    iv.setPadding(40, 40, 40, 40);
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Scan with other device")
                        .setView(iv)
                        .setPositiveButton("Done", null)
                        .show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "QR failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void startQRScan() {
            runOnUiThread(() -> {
                ScanOptions opts = new ScanOptions();
                opts.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
                opts.setPrompt("Point at the QR code on the other device");
                opts.setBeepEnabled(false);
                opts.setBarcodeImageEnabled(false);
                opts.setOrientationLocked(false);
                qrScanLauncher.launch(opts);
            });
        }

        @JavascriptInterface
        public void openImagePicker(String targetIdsJson) {
            pendingPhotoTargetIds = targetIdsJson;
            runOnUiThread(() -> imagePickerLauncher.launch("image/*"));
        }

        @JavascriptInterface
        public void openFilePicker(String mimeType) {
            pendingFileImport = true;
            runOnUiThread(() -> filePickerLauncher.launch(mimeType));
        }

        @JavascriptInterface
        public void writeFile(String filename, String content) {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(dir, filename);
                FileWriter fw = new FileWriter(file, false);
                fw.write(content); fw.close();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Saved to Downloads/" + filename, Toast.LENGTH_LONG).show();
                    webView.evaluateJavascript("onFileWritten('" + filename.replace("'", "\\'") + "');", null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    webView.evaluateJavascript("onFileWriteFailed('" + e.getMessage().replace("'", "\\'") + "');", null);
                });
            }
        }

        @JavascriptInterface
        public void shareText(String text, String filename) {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                intent.putExtra(Intent.EXTRA_SUBJECT, "StudyRival Backup");
                startActivity(Intent.createChooser(intent, "Share Backup"));
            });
        }

        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    private static byte[] readBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096]; int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }
}

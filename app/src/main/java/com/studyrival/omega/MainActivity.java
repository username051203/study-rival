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
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.*;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private String pendingPhotoTargetIds = null;
    private boolean pendingFileImport = false;

    // Camera permission request code
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    // Holds the pending WebKit permission request until native dialog resolves
    private PermissionRequest pendingWebKitPermission = null;

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
        s.setMediaPlaybackRequiresUserGesture(false); // allow camera autoplay in <video>

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new WebViewClient());

        // WebChromeClient with camera permission handling
        webView.setWebChromeClient(new WebChromeClient() {

            // Handle getUserMedia / camera permission requests from JS
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                String[] requestedResources = request.getResources();
                boolean needsCamera = false;
                for (String r : requestedResources) {
                    if (r.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        needsCamera = true;
                        break;
                    }
                }

                if (needsCamera) {
                    // Check if we already have the Android camera permission
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        // Already granted — grant the WebKit request immediately
                        runOnUiThread(() -> request.grant(request.getResources()));
                    } else {
                        // Need to ask Android for camera permission first
                        pendingWebKitPermission = request;
                        ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            CAMERA_PERMISSION_REQUEST);
                    }
                } else {
                    request.deny();
                }
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    // Called by Android after the user responds to the camera permission dialog
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && pendingWebKitPermission != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                final PermissionRequest req = pendingWebKitPermission;
                runOnUiThread(() -> req.grant(req.getResources()));
            } else {
                pendingWebKitPermission.deny();
                runOnUiThread(() ->
                    Toast.makeText(this, "Camera permission denied — QR scan unavailable", Toast.LENGTH_LONG).show());
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
                fw.write(content);
                fw.close();
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
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }
}

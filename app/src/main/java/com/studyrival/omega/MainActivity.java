package com.studyrival.omega;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.*;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;


import android.os.Build;
import android.net.Uri;
import java.io.*;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private String pendingPhotoTargetIds = null;
    private boolean pendingFileImport = false;
    private volatile String lastCompressedState = null;

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
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_main);
        // Request storage permission for Downloads access (needed for persistent save)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }, 1);
        }
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
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
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

        // ── ROOM DB BRIDGE ────────────────────────────────────────────────
        // Both save and load are fully synchronous.
        // JavascriptInterface methods run on a background thread (never main thread),
        // so synchronous Room access is safe and guaranteed to complete before
        // the JS call returns — no race conditions on force-stop.

        @JavascriptInterface
        public void dbSave(String key, String value) {
            // Synchronous write — completes before JS continues
            try {
                db.stateDao().upsert(new StateEntry(key, value));
            } catch (Exception e) {
                // silently ignore — IDB/localStorage are fallbacks
            }
        }

        @JavascriptInterface
        public String dbLoadSync(String key) {
            try {
                return db.stateDao().get(key);
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public void dbDelete(String key) {
            try { db.stateDao().delete(key); } catch (Exception e) {}
        }

        @JavascriptInterface
        public void dbClear() {
            try { db.stateDao().clear(); } catch (Exception e) {}
        }

        // ── EXISTING BRIDGE METHODS (unchanged) ──────────────────────────

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

        // Write to app-specific external dir — consistent path, no permission needed,
        // survives force stop and cache clear, only wiped by Clear Data or uninstall
        @JavascriptInterface
        public void writeFileSilent(String filename, String content) {
            if ("omega_state.txt".equals(filename)) lastCompressedState = content;
            try {
                File dir = getExternalFilesDir(null);
                if (dir == null) dir = getFilesDir();
                File file = new File(dir, filename);
                FileWriter fw = new FileWriter(file, false);
                fw.write(content);
                fw.close();
                final String path = file.getAbsolutePath();
                final int len = content.length();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "SAVED " + len + "b -> " + path, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "SAVE FAILED: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        // Read from same app-specific external dir
        @JavascriptInterface
        public String readFile(String filename) {
            try {
                File dir = getExternalFilesDir(null);
                if (dir == null) dir = getFilesDir();
                File file = new File(dir, filename);
                final String path = file.getAbsolutePath();
                final boolean exists = file.exists();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "READ " + path + " exists=" + exists, Toast.LENGTH_LONG).show());
                if (!exists) return null;
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String result = sb.toString();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "LOADED " + result.length() + "b", Toast.LENGTH_SHORT).show());
                return result;
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "READ FAILED: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return null;
            }
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

    @Override
    protected void onStop() {
        super.onStop();
        // Synchronously flush last known state to Downloads on every stop/force-stop
        String state = lastCompressedState;
        if (state != null) {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(dir, "omega_state.txt");
                FileWriter fw = new FileWriter(file, false);
                fw.write(state);
                fw.close();
            } catch (Exception e) {
                // ignore
            }
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

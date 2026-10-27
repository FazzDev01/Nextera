package com.nexus.mcbubble;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String MC = "com.mojang.minecraftpe";
    private boolean started = false;
    private boolean askedNotif = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (started) return;

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Aktifkan izin tampil di atas aplikasi lain, lalu buka lagi", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }

        if (Build.VERSION.SDK_INT >= 33 && !askedNotif
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            askedNotif = true;
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            return;
        }

        go();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        go();
    }

    private void go() {
        if (started) return;
        started = true;
        startForegroundService(new Intent(this, OverlayService.class));

        Intent mc = getPackageManager().getLaunchIntentForPackage(MC);
        if (mc == null) {
            Toast.makeText(this, "Minecraft belum terpasang", Toast.LENGTH_LONG).show();
        } else {
            startActivity(mc);
        }
        finish();
    }
}

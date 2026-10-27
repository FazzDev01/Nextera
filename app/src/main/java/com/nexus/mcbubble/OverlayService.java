package com.nexus.mcbubble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayDeque;

public class OverlayService extends Service {
    private static final String CHANNEL = "mcbubble";

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private Root root;
    private LinearLayout panel;
    private TextView hud;

    private boolean cpsOn = true, fpsOn = true;
    private final ArrayDeque<Long> clicks = new ArrayDeque<>();
    private int frames = 0, fps = 0;
    private long fpsStartMs = 0;
    private String lastHud = "";

    private float downX, downY;
    private int startX, startY;
    private boolean moved;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Choreographer.FrameCallback frameCb = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long nanos) {
            long ms = nanos / 1000000L;
            if (fpsStartMs == 0) fpsStartMs = ms;
            frames++;
            if (ms - fpsStartMs >= 1000) {
                fps = Math.round(frames * 1000f / (ms - fpsStartMs));
                frames = 0;
                fpsStartMs = ms;
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateHud();
            handler.postDelayed(this, 100);
        }
    };

    // Menghitung ketukan di luar jendela bubble (yaitu ketukan ke Minecraft)
    private class Root extends LinearLayout {
        Root(Context c) { super(c); }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) {
                clicks.add(SystemClock.uptimeMillis());
            }
            return super.onTouchEvent(e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            buildUi();
        } catch (Exception ex) {
            stopSelf();
            return;
        }
        startFps();
        handler.post(tick);
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "MC Bubble", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("MC Bubble aktif")
                .setContentText("Bubble CPS dan FPS sedang berjalan")
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        root = new Root(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Bubble
        TextView bubble = new TextView(this);
        bubble.setText("MC");
        bubble.setTextColor(Color.parseColor("#2A1D00"));
        bubble.setTextSize(16);
        bubble.setTypeface(null, android.graphics.Typeface.BOLD);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#FFB703"));
        bg.setStroke(dp(2), Color.WHITE);
        bubble.setBackground(bg);
        root.addView(bubble, new LinearLayout.LayoutParams(dp(52), dp(52)));

        bubble.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = lp.x; startY = lp.y;
                    downX = e.getRawX(); downY = e.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float mx = e.getRawX() - downX, my = e.getRawY() - downY;
                    if (Math.abs(mx) > dp(6) || Math.abs(my) > dp(6)) moved = true;
                    if (moved) {
                        lp.x = (int) (startX + mx);
                        lp.y = (int) (startY + my);
                        wm.updateViewLayout(root, lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    }
                    return true;
            }
            return false;
        });

        // HUD teks
        hud = new TextView(this);
        hud.setTextColor(Color.WHITE);
        hud.setTextSize(13);
        hud.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        hud.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0xCC0A0E1E);
        hb.setCornerRadius(dp(4));
        hud.setBackground(hb);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(4);
        root.addView(hud, hp);

        // Panel
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(0xF01B2750);
        pb.setCornerRadius(dp(8));
        pb.setStroke(dp(2), 0xFF2F3F75);
        panel.setBackground(pb);
        panel.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("Modul");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title);

        Switch sCps = new Switch(this);
        sCps.setText("CPS  ");
        sCps.setTextColor(Color.WHITE);
        sCps.setChecked(true);
        sCps.setOnCheckedChangeListener((b, c) -> cpsOn = c);
        panel.addView(sCps);

        Switch sFps = new Switch(this);
        sFps.setText("FPS  ");
        sFps.setTextColor(Color.WHITE);
        sFps.setChecked(true);
        sFps.setOnCheckedChangeListener((b, c) -> {
            fpsOn = c;
            if (c) startFps(); else stopFps();
        });
        panel.addView(sFps);

        Button close = new Button(this);
        close.setText("Tutup bubble");
        close.setOnClickListener(v -> stopSelf());
        panel.addView(close);

        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.topMargin = dp(4);
        root.addView(panel, pp);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(8);
        lp.y = dp(120);
        wm.addView(root, lp);
        updateHud();
    }

    private void updateHud() {
        long now = SystemClock.uptimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();

        StringBuilder sb = new StringBuilder();
        if (cpsOn) sb.append("CPS ").append(clicks.size());
        if (fpsOn) {
            if (sb.length() > 0) sb.append("   ");
            sb.append("FPS ").append(fps);
        }
        String s = sb.toString();
        if (!s.equals(lastHud)) {
            lastHud = s;
            hud.setText(s);
            hud.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void startFps() {
        frames = 0;
        fpsStartMs = 0;
        Choreographer.getInstance().removeFrameCallback(frameCb);
        Choreographer.getInstance().postFrameCallback(frameCb);
    }

    private void stopFps() {
        Choreographer.getInstance().removeFrameCallback(frameCb);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        stopFps();
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
          }

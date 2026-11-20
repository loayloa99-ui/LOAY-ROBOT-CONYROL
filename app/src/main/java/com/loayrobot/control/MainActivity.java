package com.loayrobot.control;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ_BT = 42;
    private static final UUID SPP_UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private OutputStream out;
    private volatile boolean reading = false;

    private Spinner devices;
    private TextView connection, modeText, telemetry, message;
    private Button connectBtn, autoBtn, manualBtn;
    private boolean manual = false;

    private final int bg = Color.rgb(16, 24, 32);
    private final int panel = Color.rgb(27, 39, 50);
    private final int mint = Color.rgb(57, 213, 197);
    private final ArrayList<BluetoothDevice> paired = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        if (adapter == null) {
            connection.setText("هذا الهاتف لا يدعم Bluetooth");
            connectBtn.setEnabled(false);
        } else {
            checkPermissionAndLoad();
        }
    }

    private TextView label(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(18, 12, 18, 12);
        l.setBackgroundColor(bg);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        return l;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel));
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = column();
        root.setPadding(18, 24, 18, 24);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = label("LOAY ROBOT CONTROL", 23, true);
        title.setTextColor(mint);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView subtitle = label("تحكم ومراقبة السيارة الروبوت", 14, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        connection = label("● غير متصل", 15, true);
        connection.setGravity(Gravity.CENTER);
        connection.setPadding(0, 18, 0, 8);
        root.addView(connection);

        root.addView(label("اختر وحدة HC-05 المقترنة:", 15, true));
        devices = new Spinner(this);
        root.addView(devices);
        LinearLayout connectRow = row();
        Button refresh = button("تحديث الأجهزة");
        connectBtn = button("اتصال");
        connectRow.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
        connectRow.addView(connectBtn, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(connectRow);
        refresh.setOnClickListener(v -> checkPermissionAndLoad());
        connectBtn.setOnClickListener(v -> connectSelected());

        root.addView(sectionTitle("وضع التشغيل"));
        LinearLayout modes = row();
        autoBtn = button("🤖 AUTO");
        manualBtn = button("🎮 MANUAL");
        modes.addView(autoBtn, new LinearLayout.LayoutParams(0, -2, 1));
        modes.addView(manualBtn, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(modes);
        modeText = label("الوضع الحالي: AUTO", 14, true);
        modeText.setGravity(Gravity.CENTER);
        root.addView(modeText);
        autoBtn.setOnClickListener(v -> { manual = false; send("A"); updateMode(); });
        manualBtn.setOnClickListener(v -> { manual = true; send("M"); updateMode(); });

        root.addView(sectionTitle("التحكم اليدوي — اضغط مطولاً للحركة"));
        LinearLayout pad = column();
        pad.setGravity(Gravity.CENTER);
        Button forward = button("▲");
        pad.addView(forward, new LinearLayout.LayoutParams(150, 58));
        LinearLayout middle = row();
        Button left = button("◀"), stop = button("■"), right = button("▶");
        middle.addView(left, new LinearLayout.LayoutParams(90, 58));
        middle.addView(stop, new LinearLayout.LayoutParams(90, 58));
        middle.addView(right, new LinearLayout.LayoutParams(90, 58));
        pad.addView(middle);
        Button back = button("▼");
        pad.addView(back, new LinearLayout.LayoutParams(150, 58));
        root.addView(pad);

        bindHold(forward, "F"); bindHold(back, "B");
        bindHold(left, "L"); bindHold(right, "R");
        stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(180, 35, 45)));
        stop.setOnClickListener(v -> send("S"));

        Button emergency = button("STOP — إيقاف فوري");
        emergency.setTextColor(Color.WHITE);
        emergency.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(190, 25, 35)));
        root.addView(emergency);
        emergency.setOnClickListener(v -> send("S"));

        root.addView(sectionTitle("قراءات الحساسات"));
        telemetry = label("بانتظار بيانات الأردوينو…", 15, false);
        telemetry.setPadding(14, 16, 14, 16);
        telemetry.setBackgroundColor(panel);
        root.addView(telemetry);
        message = label("ملاحظة: يجب إقران HC-05 من إعدادات Bluetooth أولاً. كلمة المرور الشائعة 1234 أو 0000.", 12, false);
        message.setPadding(0, 16, 0, 0);
        root.addView(message);
        updateMode();
    }

    private TextView sectionTitle(String s) {
        TextView t = label(s, 17, true);
        t.setPadding(0, 22, 0, 8);
        return t;
    }

    private void bindHold(Button b, String command) {
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                if (!manual) {
                    message.setText("حوّل إلى MANUAL قبل تحريك السيارة.");
                } else send(command);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP ||
                e.getAction() == MotionEvent.ACTION_CANCEL) {
                send("S");
                return true;
            }
            return true;
        });
    }

    private void updateMode() {
        modeText.setText("الوضع الحالي: " + (manual ? "MANUAL" : "AUTO"));
        autoBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(manual ? panel : 0xff176e68));
        manualBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(manual ? 0xff176e68 : panel));
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= 31 && !hasBtPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
        } else loadPaired();
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_BT && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadPaired();
        else message.setText("يلزم السماح بصلاحية الأجهزة القريبة لاستخدام Bluetooth.");
    }

    private void loadPaired() {
        try {
            if (!adapter.isEnabled()) {
                connection.setText("فعّل Bluetooth من إعدادات الهاتف");
                return;
            }
            paired.clear();
            Set<BluetoothDevice> set = adapter.getBondedDevices();
            ArrayList<String> names = new ArrayList<>();
            for (BluetoothDevice d : set) {
                paired.add(d);
                String n = d.getName();
                names.add((n == null ? "Bluetooth device" : n) + "  (" + d.getAddress() + ")");
            }
            if (names.isEmpty()) names.add("لا توجد أجهزة مقترنة — اقترن مع HC-05 أولاً");
            devices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        } catch (SecurityException ex) {
            message.setText("تعذر الوصول إلى أجهزة Bluetooth. تحقق من الصلاحيات.");
        }
    }

    private void connectSelected() {
        if (!hasBtPermission()) { checkPermissionAndLoad(); return; }
        int pos = devices.getSelectedItemPosition();
        if (pos < 0 || pos >= paired.size()) {
            message.setText("اقترن مع HC-05 من إعدادات Bluetooth ثم اضغط تحديث الأجهزة.");
            return;
        }
        connectBtn.setEnabled(false);
        connection.setText("● جارٍ الاتصال…");
        new Thread(() -> {
            try {
                closeSocket();
                BluetoothDevice device = paired.get(pos);
                BluetoothSocket s = device.createRfcommSocketToServiceRecord(SPP_UUID);
                s.connect();
                socket = s;
                out = s.getOutputStream();
                reading = true;
                runOnUiThread(() -> {
                    connection.setText("● متصل بـ " + device.getName());
                    connectBtn.setText("متصل");
                    connectBtn.setEnabled(true);
                    message.setText("تم الاتصال. جرّب AUTO أو MANUAL.");
                });
                readLoop(s);
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    connection.setText("● فشل الاتصال");
                    connectBtn.setText("إعادة الاتصال");
                    connectBtn.setEnabled(true);
                    message.setText("تأكد أن HC-05 مقترن وغير متصل بجهاز آخر، ثم أعد المحاولة.");
                });
                closeSocket();
            }
        }).start();
    }

    private void readLoop(BluetoothSocket s) {
        try {
            InputStream in = s.getInputStream();
            byte[] buf = new byte[256];
            StringBuilder line = new StringBuilder();
            while (reading && s.isConnected()) {
                int n = in.read(buf);
                for (int i=0; i<n; i++) {
                    char c = (char)buf[i];
                    if (c == '\n' || c == '\r') {
                        if (line.length() > 0) {
                            final String data = line.toString();
                            line.setLength(0);
                            runOnUiThread(() -> telemetry.setText(data));
                        }
                    } else if (line.length() < 180) line.append(c);
                }
            }
        } catch (Exception ignored) { }
    }

    private void send(String command) {
        new Thread(() -> {
            try {
                OutputStream stream = out;
                if (stream != null) {
                    stream.write((command + "\n").getBytes(StandardCharsets.US_ASCII));
                    stream.flush();
                }
            } catch (Exception e) {
                runOnUiThread(() -> connection.setText("● انقطع الاتصال"));
            }
        }).start();
    }

    private void closeSocket() {
        reading = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null; out = null;
    }

    @Override protected void onDestroy() {
        send("S");
        closeSocket();
        super.onDestroy();
    }
}

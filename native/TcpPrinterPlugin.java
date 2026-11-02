package com.vh3.bangke;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.getcapacitor.JSArray;

/** Gửi lệnh ESC/POS thẳng tới máy in qua TCP cổng 9100 (thay cho cầu nối trên máy tính). */
@CapacitorPlugin(name = "TcpPrinter")
public class TcpPrinterPlugin extends Plugin {

    private Socket open(String host, int port) throws Exception {
        InetAddress a = InetAddress.getByName(host);
        if (!a.isSiteLocalAddress() || port != 9100) {
            throw new Exception("chỉ cho phép IP mạng nội bộ, cổng 9100");
        }
        Socket s = new Socket();
        s.connect(new InetSocketAddress(a, port), 5000);
        return s;
    }

    @PluginMethod
    public void check(final PluginCall call) {
        final String host = call.getString("host", "");
        final int port = call.getInt("port", 9100);
        new Thread(() -> {
            try (Socket s = open(host, port)) {
                JSObject r = new JSObject();
                r.put("msg", "Kết nối được " + host + ":" + port);
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Không kết nối được máy in: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void send(final PluginCall call) {
        final String host = call.getString("host", "");
        final int port = call.getInt("port", 9100);
        final int pace = call.getInt("pace", 0);
        final byte[] data = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
        final int size = pace == 0 ? 8192 : 4096;
        final long gap = pace == 0 ? 0 : (pace == 1 ? 30 : 60);
        new Thread(() -> {
            try (Socket s = open(host, port)) {
                OutputStream o = s.getOutputStream();
                for (int i = 0; i < data.length; i += size) {
                    o.write(data, i, Math.min(size, data.length - i));
                    o.flush();
                    if (gap > 0) Thread.sleep(gap);
                }
                Thread.sleep(1000); // đợi máy in nhận hết trước khi đóng
                call.resolve();
            } catch (Exception e) {
                call.reject("Gửi không được: " + e.getMessage());
            }
        }).start();
    }

    /** Quét dải mạng /24 của điện thoại, tìm thiết bị mở cổng 9100 (máy in). */
    @PluginMethod
    public void scan(final PluginCall call) {
        new Thread(() -> {
            try {
                String base = null;
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                        if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                            String ip = a.getHostAddress();
                            base = ip.substring(0, ip.lastIndexOf('.') + 1);
                        }
                    }
                }
                if (base == null) {
                    call.reject("Điện thoại chưa kết nối Wi-Fi");
                    return;
                }
                final String prefix = base;
                final List<String> found = Collections.synchronizedList(new ArrayList<String>());
                ExecutorService ex = Executors.newFixedThreadPool(64);
                for (int i = 1; i < 255; i++) {
                    final String host = prefix + i;
                    ex.execute(() -> {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(host, 9100), 500);
                            found.add(host);
                        } catch (Exception ignored) {
                        }
                    });
                }
                ex.shutdown();
                ex.awaitTermination(20, TimeUnit.SECONDS);
                List<String> sorted = new ArrayList<String>(found);
                Collections.sort(sorted);
                JSArray arr = new JSArray();
                for (String h : sorted) arr.put(h);
                JSObject r = new JSObject();
                r.put("hosts", arr);
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Không quét được: " + e.getMessage());
            }
        }).start();
    }

    /** Lưu ảnh PNG vào thư viện ảnh (Pictures/BangKeNopTien), cần Android 10 trở lên. */
    @PluginMethod
    public void saveImage(final PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT < 29) {
                call.reject("Cần Android 10 trở lên để lưu ảnh vào thư viện");
                return;
            }
            byte[] data = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
            String name = call.getString("name", "bang-ke.png");
            ContentResolver cr = getContext().getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BangKeNopTien");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("không tạo được tệp ảnh");
            try (OutputStream o = cr.openOutputStream(uri)) {
                if (o == null) throw new Exception("không mở được tệp ảnh");
                o.write(data);
            }
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            call.resolve();
        } catch (Exception e) {
            call.reject("Lưu ảnh không được: " + e.getMessage());
        }
    }
}

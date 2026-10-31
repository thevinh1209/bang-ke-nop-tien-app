package com.vh3.bangke;

import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

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
}

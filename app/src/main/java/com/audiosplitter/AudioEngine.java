package com.audiosplitter.status;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class AudioEngine {

    /**
     * 直接以 Root 权限在应用层执行底层 Linux / Android 命令
     */
    public static String exec(String command) {
        StringBuilder output = new StringBuilder();
        Process p = null;
        DataOutputStream os = null;
        BufferedReader reader = null;
        try {
            // 直接拉起系统的 su 进程，执行完后退出
            p = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(p.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            p.waitFor();
        } catch (Exception e) {
            output.append("Error: ").append(e.getMessage());
        } finally {
            try {
                if (os != null) os.close();
                if (reader != null) reader.close();
                if (p != null) p.destroy();
            } catch (Exception ignored) {}
        }
        return output.toString().trim();
    }

    /**
     * 实时检测当前蓝牙 A2DP 音频输出设备是否已连接
     */
    public static boolean isBluetoothConnected() {
        String res = exec("dumpsys audio | grep -i 'A2DP' | grep -i 'connected=true'");
        return !res.isEmpty();
    }

    /**
     * 强制将媒体音频重定向至蓝牙通道 (FOR_MEDIA = 1, FORCE_BT_A2DP = 1)
     */
    public static void applyRoute() {
        exec("cmd audio set-force-use 1 1");
    }

    /**
     * 恢复默认系统音频路由 (FORCE_NONE = 0)
     */
    public static void resetRoute() {
        exec("cmd audio set-force-use 1 0");
    }
}

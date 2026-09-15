package com.audiosplitter.status;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class AudioEngine {

    // 动态请求 Root 权限
    public static boolean requestRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            boolean isRoot = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("uid=0(root)")) {
                    isRoot = true;
                    break;
                }
            }
            p.waitFor();
            p.destroy();
            return isRoot;
        } catch (Exception e) {
            return false;
        }
    }

    // 执行底层命令
    public static String exec(String command) {
        StringBuilder output = new StringBuilder();
        Process p = null;
        DataOutputStream os = null;
        BufferedReader reader = null;
        try {
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

    // [功能 2] 实时检测蓝牙 A2DP 状态
    public static boolean isBluetoothConnected() {
        String res = exec("dumpsys audio | grep -i 'A2DP' | grep -i 'connected=true'");
        return !res.isEmpty();
    }

    // [功能 1] 开启音频分流总开关
    public static void applyRoute() {
        exec("cmd audio set-force-use 1 1");
    }

    // 关闭音频分流总开关
    public static void resetRoute() {
        exec("cmd audio set-force-use 1 0");
    }
}

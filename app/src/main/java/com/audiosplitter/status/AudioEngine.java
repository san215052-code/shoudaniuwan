package com.audiosplitter.status;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

import java.io.DataOutputStream;
import java.io.IOException;

public class AudioEngine {

    /**
     * 检查并请求 Root 权限 (异步检测)
     */
    public static boolean requestRoot() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            int exitValue = process.waitFor();
            return exitValue == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (IOException ignored) {}
        }
    }

    /**
     * 执行低延迟 Root Shell 指令
     */
    public static void executeRootCommand(String command) {
        new Thread(() -> {
            Process process = null;
            DataOutputStream os = null;
            try {
                process = Runtime.getRuntime().exec("su");
                os = new DataOutputStream(process.getOutputStream());
                os.writeBytes(command + "\n");
                os.writeBytes("exit\n");
                os.flush();
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (os != null) os.close();
                    if (process != null) process.destroy();
                } catch (IOException ignored) {}
            }
        }).start();
    }

    /**
     * 切换音频路由模式
     * 0: 白名单模式 (强制蓝牙)
     * 1: 黑名单模式 (切回默认)
     * 2: 系统原生音频 / 紧急重置 (完全清空路由策略)
     */
    public static void switchRoutingMode(int modeIndex) {
        switch (modeIndex) {
            case 0:
                // 白名单：强制音频指向蓝牙设备 (FOR_MEDIA)
                executeRootCommand("cmd audio set-force-use 1 1");
                break;
            case 1:
                // 黑名单：解除强导，切回系统默认策略
                executeRootCommand("cmd audio set-force-use 1 0");
                break;
            case 2:
                // 紧急重置：重置 通信、媒体、录音 三路强制路由，恢复手机默认状态
                executeRootCommand("cmd audio set-force-use 0 0");
                executeRootCommand("cmd audio set-force-use 1 0");
                executeRootCommand("cmd audio set-force-use 2 0");
                break;
        }
    }

    /**
     * 检测蓝牙音频设备连接状态
     */
    public static boolean isBluetoothConnected() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false;
        }
        int a2dpState = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP);
        int headsetState = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET);
        return a2dpState == BluetoothProfile.STATE_CONNECTED || headsetState == BluetoothProfile.STATE_CONNECTED;
    }
}

package com.audiosplitter.status;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;

import java.io.DataOutputStream;
import java.io.IOException;

public class AudioEngine {

    /**
     * 检查并请求 Root 权限 (异步执行，防止阻塞 UI 线程)
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
     * 执行底层 Root Shell 命令 (采用轻量级管道，确保低延迟)
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
     * 根据当前选择的索引切换音频路由模式
     * 0: 白名单模式
     * 1: 黑名单模式
     * 2: 系统原生音频 / 紧急重置 (清除所有强导，还原手机默认音频路径)
     */
    public static void switchRoutingMode(int modeIndex) {
        switch (modeIndex) {
            case 0: 
                // 白名单模式：强制使用蓝牙音频通路
                executeRootCommand("cmd audio set-force-use 1 1");
                break;
            case 1: 
                // 黑名单模式：强制切回默认（解绑强导）
                executeRootCommand("cmd audio set-force-use 1 0");
                break;
            case 2: 
                // 系统原生音频 & 紧急重置：彻底清除所有系统的强导策略，强制恢复手机默认扬声器
                executeRootCommand("cmd audio set-force-use 0 0"); // FOR_COMMUNICATION reset
                executeRootCommand("cmd audio set-force-use 1 0"); // FOR_MEDIA reset
                executeRootCommand("cmd audio set-force-use 2 0"); // FOR_RECORD reset
                break;
        }
    }

    /**
     * 检测蓝牙音频设备是否处于已连接状态
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

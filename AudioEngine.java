package com.audiosplitter.status;

import java.io.DataOutputStream;

public class AudioEngine {

    /**
     * 执行 Root 命令行（非阻塞异步）
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
                } catch (Exception ignored) {}
            }
        }).start();
    }

    /**
     * 检查设备 Root 权限
     */
    public static boolean checkRootPermission() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 切换音频路由模式
     * 0: 白名单模式
     * 1: 黑名单模式
     * 2: 系统原生模式
     */
    public static void switchRoutingMode(int modeIndex) {
        switch (modeIndex) {
            case 0:
                // 白名单：强制指定路径 1
                executeRootCommand("cmd audio set-force-use 1 1");
                break;
            case 1:
                // 黑名单：屏蔽指定音频输出
                executeRootCommand("cmd audio set-force-use 1 0");
                break;
            case 2:
                // 系统原生：清空所有强制路由，恢复系统默认
                executeRootCommand("cmd audio set-force-use 0 0\ncmd audio set-force-use 1 0\ncmd audio set-force-use 2 0");
                break;
        }
    }
}

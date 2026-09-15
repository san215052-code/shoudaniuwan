package com.audiosplitter.status;

import java.io.DataOutputStream;

public class AudioEngine {

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

    public static void switchRoutingMode(int modeIndex) {
        switch (modeIndex) {
            case 0:
                executeRootCommand("cmd audio set-force-use 1 1");
                break;
            case 1:
                executeRootCommand("cmd audio set-force-use 1 0");
                break;
            case 2:
                executeRootCommand("cmd audio set-force-use 0 0\ncmd audio set-force-use 1 0\ncmd audio set-force-use 2 0");
                break;
        }
    }
}

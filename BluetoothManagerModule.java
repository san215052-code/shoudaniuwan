package com.audiosplitter.status;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class BluetoothManagerModule {

    public interface BluetoothStateListener {
        void onBluetoothDeviceConnected(String deviceName);
        void onBluetoothDeviceDisconnected(String deviceName);
    }

    private final Context context;
    private final BluetoothStateListener listener;
    private String lastConnectedDeviceName = "未连接设备";
    private boolean isConnected = false;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String deviceName = "未知设备";
            if (device != null) {
                try {
                    deviceName = device.getName();
                    if (deviceName == null || deviceName.isEmpty()) {
                        deviceName = device.getAddress();
                    }
                } catch (SecurityException e) {
                    deviceName = "已授权蓝牙设备";
                }
            }

            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                isConnected = true;
                lastConnectedDeviceName = deviceName;
                if (listener != null) {
                    listener.onBluetoothDeviceConnected(deviceName);
                }

            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                isConnected = false;
                lastConnectedDeviceName = "已断开 (" + deviceName + ")";
                
                // 蓝牙断开后自动触发 Root 指令：暂停媒体播放 (keyevent 127)
                AudioEngine.executeRootCommand("input keyevent 127");

                if (listener != null) {
                    listener.onBluetoothDeviceDisconnected(deviceName);
                }
            }
        }
    };

    public BluetoothManagerModule(Context context, BluetoothStateListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void register() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        context.registerReceiver(bluetoothReceiver, filter);
        checkInitialBluetoothState();
    }

    public void unregister() {
        try {
            context.unregisterReceiver(bluetoothReceiver);
        } catch (Exception ignored) {}
    }

    private void checkInitialBluetoothState() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            this.isConnected = false;
            this.lastConnectedDeviceName = "蓝牙已开启 (等待设备连接)";
        } else {
            this.isConnected = false;
            this.lastConnectedDeviceName = "蓝牙已关闭";
        }
    }

    public boolean isConnected() { return isConnected; }
    public String getLastConnectedDeviceName() { return lastConnectedDeviceName; }
}

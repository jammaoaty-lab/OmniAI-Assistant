package com.omniai.assistant.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeviceManager {

    private static volatile DeviceManager instance;
    private final List<DeviceInfo> boundDevices;
    private String currentDeviceId;

    private DeviceManager() {
        boundDevices = new ArrayList<>();
    }

    public static DeviceManager getInstance() {
        if (instance == null) {
            synchronized (DeviceManager.class) {
                if (instance == null) {
                    instance = new DeviceManager();
                }
            }
        }
        return instance;
    }

    public void bindDevice(String userId, DeviceInfo deviceInfo) {
        if (deviceInfo == null || deviceInfo.deviceId == null) {
            return;
        }
        for (int i = 0; i < boundDevices.size(); i++) {
            DeviceInfo existing = boundDevices.get(i);
            if (existing.deviceId.equals(deviceInfo.deviceId)) {
                boundDevices.set(i, deviceInfo);
                return;
            }
        }
        boundDevices.add(deviceInfo);
    }

    public void unbindDevice(String deviceId) {
        if (deviceId == null) {
            return;
        }
        for (int i = 0; i < boundDevices.size(); i++) {
            if (boundDevices.get(i).deviceId.equals(deviceId)) {
                boundDevices.remove(i);
                return;
            }
        }
    }

    public List<DeviceInfo> getBoundDevices() {
        return Collections.unmodifiableList(boundDevices);
    }

    public boolean isDeviceBound(String deviceId) {
        if (deviceId == null) {
            return false;
        }
        for (DeviceInfo device : boundDevices) {
            if (device.deviceId.equals(deviceId)) {
                return true;
            }
        }
        return false;
    }

    public boolean checkRiskDevice(String deviceId) {
        if (deviceId == null) {
            return true;
        }
        DeviceInfo current = getCurrentDeviceInfo();
        if (current == null) {
            return true;
        }
        if (currentDeviceId != null && !currentDeviceId.equals(deviceId)) {
            return true;
        }
        return false;
    }

    public DeviceInfo getCurrentDeviceInfo() {
        if (currentDeviceId == null) {
            return null;
        }
        for (DeviceInfo device : boundDevices) {
            if (device.isCurrent && device.deviceId.equals(currentDeviceId)) {
                return device;
            }
        }
        return null;
    }

    public void setCurrentDeviceId(String currentDeviceId) {
        this.currentDeviceId = currentDeviceId;
        for (DeviceInfo device : boundDevices) {
            device.isCurrent = device.deviceId.equals(currentDeviceId);
        }
    }

    public String getCurrentDeviceId() {
        return currentDeviceId;
    }

    public void clearDevices() {
        boundDevices.clear();
        currentDeviceId = null;
    }

    public static class DeviceInfo {
        public String deviceId;
        public String deviceName;
        public String deviceModel;
        public long lastLoginTime;
        public String ip;
        public boolean isCurrent;

        public DeviceInfo() {
        }

        public DeviceInfo(String deviceId, String deviceName, String deviceModel, long lastLoginTime, String ip, boolean isCurrent) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.deviceModel = deviceModel;
            this.lastLoginTime = lastLoginTime;
            this.ip = ip;
            this.isCurrent = isCurrent;
        }
    }
}

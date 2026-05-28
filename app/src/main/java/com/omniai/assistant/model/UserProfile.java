package com.omniai.assistant.model;

public class UserProfile {

    private String uid;
    private String nickname;
    private String avatar;
    private boolean isVip;
    private String vipExpiry;
    private int deviceCount;
    private String email;
    private String phone;
    private String loginType;

    public UserProfile() {
        this.isVip = false;
        this.deviceCount = 0;
    }

    public UserProfile(String uid, String nickname, String avatar, boolean isVip, String vipExpiry, int deviceCount, String email, String phone, String loginType) {
        this.uid = uid;
        this.nickname = nickname;
        this.avatar = avatar;
        this.isVip = isVip;
        this.vipExpiry = vipExpiry;
        this.deviceCount = deviceCount;
        this.email = email;
        this.phone = phone;
        this.loginType = loginType;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public boolean isVip() {
        return isVip;
    }

    public void setVip(boolean vip) {
        isVip = vip;
    }

    public String getVipExpiry() {
        return vipExpiry;
    }

    public void setVipExpiry(String vipExpiry) {
        this.vipExpiry = vipExpiry;
    }

    public int getDeviceCount() {
        return deviceCount;
    }

    public void setDeviceCount(int deviceCount) {
        this.deviceCount = deviceCount;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }
}

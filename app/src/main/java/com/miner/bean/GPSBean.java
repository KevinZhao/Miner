package com.miner.bean;

/**
 * Created by Mr.Zhang on 2017/11/21.
 * GPS信息
 */

public class GPSBean {

//    经度
    private double longitude;
//    纬度
    private double latitude;
//    海拔
    private double altitude;
//    速度
    private double speed;
//    方向
    private double bearing;


    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getBearing() {
        return bearing;
    }

    public void setBearing(double bearing) {
        this.bearing = bearing;
    }
}

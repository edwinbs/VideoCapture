package com.nus.edu.cs5248.videocapture;

/**
 * Created by IntelliJ IDEA.
 * User: Amila
 * Date: 10/27/12
 * Time: 6:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class TrackDetails {
    
    private double startTime;
    private double endTime;
    private double count;

    public TrackDetails(double startTime, double endTime, double count) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.count = count;
    }

    public double getCount() {
        return count;
    }

    public void setCount(double count) {
        this.count = count;
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getEndTime() {
        return endTime;
    }

    public void setEndTime(double endTime) {
        this.endTime = endTime;
    }
}

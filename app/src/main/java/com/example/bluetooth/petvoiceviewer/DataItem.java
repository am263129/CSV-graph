package com.example.bluetooth.petvoiceviewer;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class DataItem {
    private String label;
    float startTime, endTime;

    public DataItem(String label, float startTime, float endTime){
        this.label = label;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getLabel() {
        return label;
    }

    public float getStartTime() {
        return startTime;
    }

    public float getEndTime() {
        return endTime;
    }
}

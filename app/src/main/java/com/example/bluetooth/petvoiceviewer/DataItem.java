package com.example.bluetooth.petvoiceviewer;

public class DataItem {
    private String label;
    float startTime, endTime;
    int labelIndex;

    public DataItem(int labelIndex, String label, float startTime, float endTime){
        this.label = label;
        this.startTime = startTime;
        this.endTime = endTime;
        this.labelIndex = labelIndex;
    }

    public int getLabelIndex() {
        return labelIndex;
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

    public void setEndTime(float endTime) {
        this.endTime = endTime;
    }

    public void setStartTime(float startTime) {
        this.startTime = startTime;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setLabelIndex(int labelIndex) {
        this.labelIndex = labelIndex;
    }

}

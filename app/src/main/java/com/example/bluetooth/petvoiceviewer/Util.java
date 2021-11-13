package com.example.bluetooth.petvoiceviewer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Util {

    public static final String TAG = "Util";
    private static BufferedWriter logWriter = null;
    private static String logFilePath = null;
    private static String logFileName = null;

    public static Uri draftVideo = null;
    public static Uri draftCsv = null;
    public static int draftTime = 0;

    /**
     * Create a file Uri for saving an image or video
     */

    public static void initLogWriter() {
        logWriterClose();
        setLogFile();
        try {
            logWriter = new BufferedWriter(new FileWriter(logFilePath, true));
        } catch (Exception e) {
            logWriter = null;
            e.printStackTrace();
        }
    }

    public static String getLogFileName() {
        if (logFileName == null) return "";
        return logFileName;
    }

    private static void setLogFile() {
        logFileName = "log_PetVoiceViewer.txt";
        logFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "PVLogger" + File.separator + logFileName;
    }

    public static void logWriterClose() {
        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void printLog(String text) {
        if (logWriter == null) return;
        try {
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEEE,hh-mm-ss a");
            String time = simpleDateFormat.format(calendar.getTime());
            logWriter.append(time);
            logWriter.newLine();
            logWriter.append(text);
            logWriter.newLine();
            String log = time + " :: " + text;
            Log.e(TAG, log);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static void saveDraft(Activity context, Uri videoUri, Uri csvUri, int time) {
        SharedPreferences sharedPref = context.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        if(videoUri!=null);
        editor.putString("VIDEO_URI", String.valueOf(videoUri));
        if(csvUri!=null);
        editor.putString("CSV_URI", String.valueOf(csvUri));
        editor.putInt("PLAY_TIME", time);
        editor.apply();
    }

    public static void loadDraft(Activity context) {
        SharedPreferences sharedPref = context.getPreferences(Context.MODE_PRIVATE);
        String videoUri = sharedPref.getString("VIDEO_URI", null);
        if (videoUri != null)
            draftVideo = Uri.parse(videoUri);
        String csvUri = sharedPref.getString("CSV_URI", null);
        if (csvUri != null)
            draftCsv = Uri.parse(csvUri);
        draftTime = sharedPref.getInt("PLAY_TIME", 0);
    }

}

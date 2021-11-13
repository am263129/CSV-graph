package com.example.bluetooth.petvoiceviewer;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.MPPointD;
import com.github.mikephil.charting.utils.MPPointF;
import com.opencsv.CSVReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Mufasa 2021.10.27
 */

public class MainActivity extends AppCompatActivity {

    private ConstraintLayout btnPointStart, btnPointEnd, btnPointAdd, btnPointDelete, pendingDataArea, axisX, axisY, axisZ;
    private Spinner actionSelector;
    private SurfaceView videoView;
    private SurfaceHolder holder;
    private ActivityResultLauncher<Intent> mainActivityResultLauncher;
    private ProgressBar loadingProgress;
    private TextView statusLabel, pendingLabel, pendingStart, pendingEnd, labelType, nameVidoe, nameCsv;
    private ImageView fileStatus;
    private RecyclerView dataList;
    private DataAdapter dataAdapter;
    private ArrayList<DataItem> dataArray = new ArrayList<>();
    private DataItem pendingData;
    private MediaPlayer player;
    private LineChart chart;
    private SeekBar seekController;
    private Handler playHandler, writeHandler;
    private Thread writeThread;
    private BufferedWriter csvWriter = null;
    private int videoDuration;
    private int dataType = 0;
    private Uri csvUri = null, videoUri = null;
    private List<String[]> csvDump;
    private final String TAG = "MainActivity";
    private final String TEST = "------Test------";
    private boolean videoPrepared = false;
    private boolean loadcsv = false, loadVideo = false;
    private boolean sync = false;
    private int seekTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initUi();
        initLauncher();
        initChart();
        initVideoTracker();
        Log.e(TEST, "Load draft");
        Util.loadDraft(this);
        if (Util.draftVideo != null) {
            Log.e(TEST, "Load video uri :" + Util.draftVideo.toString());
            videoUri = Util.draftVideo;
            nameVidoe.setText(getFileName(videoUri));
        }
        if (Util.draftCsv != null) {
            Log.e(TEST, "Load csv uri :" + Util.draftCsv.toString());
            csvUri = Util.draftCsv;
            nameCsv.setText(getFileName(csvUri));
            Log.e(TEST, "init csv data as graph");
            initData();
        }
        seekTime = Util.draftTime;
    }

    /********************  init Functions  *******************/
    /*********************************************************/

    /**
     * init UI components
     */
    public void initUi() {
        setContentView(R.layout.activity_main);
        btnPointStart = findViewById(R.id.btn_point_start);
        btnPointEnd = findViewById(R.id.btn_point_end);
        btnPointAdd = findViewById(R.id.btn_point_add);
        btnPointDelete = findViewById(R.id.btn_point_delete);
        actionSelector = findViewById(R.id.action_selector);
        pendingDataArea = findViewById(R.id.pending_data_area);
        pendingLabel = findViewById(R.id.pending_label);
        pendingStart = findViewById(R.id.pending_label_start);
        pendingEnd = findViewById(R.id.pending_label_end);
        labelType = findViewById(R.id.label_type);
        axisX = findViewById(R.id.btn_x);
        axisY = findViewById(R.id.btn_y);
        axisZ = findViewById(R.id.btn_z);
        videoView = findViewById(R.id.video_view);
        loadingProgress = findViewById(R.id.loading_progress);
        seekController = findViewById(R.id.seekBar);
        statusLabel = findViewById(R.id.status_label);
        fileStatus = findViewById(R.id.file_status);
        dataList = findViewById(R.id.action_data_list);
        nameVidoe = findViewById(R.id.label_open_video);
        nameCsv = findViewById(R.id.label_open_csv);
        dataList.setLayoutManager(new LinearLayoutManager(this));
        dataAdapter = new DataAdapter(dataArray);
        dataList.setAdapter(dataAdapter);
        seekController.setOnSeekBarChangeListener(new videoControlCallback());
        axisX.setSelected(true);
        axisY.setSelected(true);
        axisZ.setSelected(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public String getMimeType(Uri uri) {
        String mimeType = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            ContentResolver cr = getContentResolver();
            mimeType = cr.getType(uri);

        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri
                    .toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    fileExtension.toLowerCase());
        }
        return mimeType;
    }

    @SuppressLint("Range")
    public String getFileName(Uri uri) {
        String result = "";
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }

            } catch (Exception e) {
                e.printStackTrace();
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /**
     * init file pick launcher and listener.
     */
    public void initLauncher() {
        mainActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            Intent data = result.getData();
                            if (data != null) {
                                getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                String fileType = "";
                                String extension = "";
                                try {
                                    extension = MimeTypeMap.getFileExtensionFromUrl(data.getDataString());
                                    if (data.getDataString().length() < 3) {
                                        showToast(R.string.invalid_filename);
                                        return;
                                    }
                                    fileType = data.getData().getPath().substring(data.getData().getPath().length() - 3);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                Log.e("#############", getFileName(data.getData()));
                                if (fileType.equals("mp4") || fileType.equals("mov") || fileType.equals("avi") || extension.equals("mp4") || extension.equals("mov") || extension.equals("avi") || getMimeType(data.getData()).equals("video/quicktime") || getMimeType(data.getData()).equals("video/mp4")) {
                                    statusLabel.setVisibility(View.INVISIBLE);
                                    loadingProgress.setVisibility(View.VISIBLE);
                                    videoUri = data.getData();
                                    nameVidoe.setText(getFileName(videoUri));
                                } else if (fileType.equals("csv") || extension.equals("csv") || getMimeType(data.getData()).equals("text/comma-separated-values") || getMimeType(data.getData()).equals("text/csv")) {
                                    csvUri = data.getData();
                                    csvDump = null;//so that it will read data array.
                                    loadcsv = false;
                                    nameCsv.setText(getFileName(csvUri));
                                    initData();
                                } else {
                                    showToast(R.string.invalid_filetype);
                                    return;
                                }
                            } else {
                                Log.e("----debug test ----", "Result Null");
                            }
                        }
                    }
                });
    }

    /**
     * init Chart view to draw graph
     */
    public void initChart() {
        chart = findViewById(R.id.chart_view);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);
        chart.setBackgroundColor(Color.LTGRAY);
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        chart.setData(data);
        Legend l = chart.getLegend();
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.WHITE);
        XAxis xl = chart.getXAxis();
        xl.setTextColor(Color.BLUE);
        xl.setAxisMinimum(-5f);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);


        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(10f);
        leftAxis.setAxisMinimum(-10f);
        leftAxis.setDrawGridLines(true);
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            chart.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override
                public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    Log.e("Current X", scrollX + "" + " :: " + oldScrollX + "---" + scrollY);
                }
            });
            chart.setOnDragListener(new View.OnDragListener() {
                @Override
                public boolean onDrag(View v, DragEvent event) {
                    Log.e("x", ":" + v.getLeft() + chart.getX());

                    return false;
                }
            });

            chart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });

            chart.setOnDragListener(new View.OnDragListener() {
                @Override
                public boolean onDrag(View v, DragEvent event) {
                    Log.e("Drag", v.getLeft() + "");
                    return false;
                }
            });

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                chart.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                    @Override
                    public boolean onCapturedPointer(View view, MotionEvent event) {
                        Log.e("Drag", view.getLeft() + "");
                        return false;
                    }
                });
            }
        }
    }

    /**
     * init Video player with selected mp4 file
     *
     * @param uri recorded video file uri
     */
    public void initVideo(Uri uri) {
        try {
            player.setDataSource(this, uri);
            holder = videoView.getHolder();
            holder.addCallback(new videoViewCallback());
            player.prepare();
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    loadingProgress.setVisibility(View.INVISIBLE);
                    videoDuration = player.getDuration();
                    seekController.setMax(videoDuration);
                    videoPrepared = true;
                    fileStatus.setBackgroundResource(R.drawable.ic_play);
                }
            });
            player.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    playHandler.sendEmptyMessage(0);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * init data from selected csv file Uri
     * (this may call multiple time when user change view mode)
     */
    public void initData() {

        LineData data = chart.getData();
        float min = 0f, max = 0f;
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        ArrayList<Entry> valuesX = new ArrayList<>();
        ArrayList<Entry> valuesY = new ArrayList<>();
        ArrayList<Entry> valuesZ = new ArrayList<>();
        if (data != null) {
            try {
                if (csvDump == null || csvDump.size() == 0) {
                    //read csv file data and save it to dump, this dump will used for upcomming process instead of read csv again.
                    InputStream input = getContentResolver().openInputStream(csvUri);
                    CSVReader reader = new CSVReader(new InputStreamReader(input));
                    csvDump = reader.readAll();
                    Log.e(TEST, "dump size :" + csvDump.size());
                    dataArray.clear();
                    dataAdapter.notifyDataSetChanged();
                    DataAdapter.selectedItems.clear();
                    ArrayList<String[]> tempData = new ArrayList<>();
                    for (int i = 0; i < csvDump.size(); i++) {
                        String[] dumpItem = csvDump.get(i);
                        if (dumpItem.length >= 15) {
                            tempData.add(dumpItem);
                        }
                    }

                    if (tempData.size() > 1) {
                        for (int i = 10; i < 15; i++) {
                            DataItem item = null;
                            for (int j = 0; j < tempData.size(); j++) {
                                if (item == null)
                                    item = new DataItem(-1, "", -1, -1);
                                if (tempData.get(j).length < 10) {
                                    continue;
                                }
                                if (tempData.get(j)[i].equals("1")) {
                                    item.setLabelIndex(i - 10);
                                    item.setLabel(getResources().getStringArray(R.array.actions)[i - 10]);
                                    if (item.getStartTime() == -1) {
                                        item.setStartTime(Float.parseFloat(tempData.get(j)[0]));
                                    }
                                }
                                if (tempData.get(j)[i].equals("0") && item.getLabelIndex() != -1) {
                                    item.setEndTime(Float.parseFloat(tempData.get(j - 1)[0]));
                                }
                                if (item.getStartTime() != -1 && item.getEndTime() != -1) {
                                    dataArray.add(item);
                                    item = null;
                                }
                            }
                        }
                        Collections.sort(dataArray, new Comparator<DataItem>() {
                            @Override
                            public int compare(DataItem lhs, DataItem rhs) {
                                return Float.compare(lhs.getStartTime(), rhs.getStartTime());
                            }
                        });
                        dataAdapter.notifyDataSetChanged();
                    }
                    reader.close();
                }
                if (csvDump.size() < 2) {
                    showToast(R.string.msg_emptyCsv);
                    return;
                }
                int offsetIndex = dataType * 3;
                boolean predata = true;
                float lastpoint = 0;
                float lastX = 0, lastY = 0, lastZ = 0;
                for (int i = 1; i < csvDump.size(); i++) {
                    String[] nextLine = csvDump.get(i);
                    if (nextLine.length < 9) {
                        continue;
                    }
                    try {
                        min = Math.min(min, Math.min(Float.parseFloat(nextLine[offsetIndex + 1]), Math.min(Float.parseFloat(nextLine[offsetIndex + 2]), Float.parseFloat(nextLine[offsetIndex + 3]))));
                        max = Math.max(max, Math.max(Float.parseFloat(nextLine[offsetIndex + 1]), Math.max(Float.parseFloat(nextLine[offsetIndex + 2]), Float.parseFloat(nextLine[offsetIndex + 3]))));
                        valuesX.add(new Entry(Float.parseFloat(nextLine[0]), Float.parseFloat(nextLine[offsetIndex + 1])));
                        valuesY.add(new Entry(Float.parseFloat(nextLine[0]), Float.parseFloat(nextLine[offsetIndex + 2])));
                        valuesZ.add(new Entry(Float.parseFloat(nextLine[0]), Float.parseFloat(nextLine[offsetIndex + 3])));
                        lastpoint = Float.parseFloat(nextLine[0]);
                        lastX = Float.parseFloat(nextLine[offsetIndex + 1]);
                        lastY = Float.parseFloat(nextLine[offsetIndex + 2]);
                        lastZ = Float.parseFloat(nextLine[offsetIndex + 3]);
                    } catch (NumberFormatException E) {
                        showToast(R.string.msg_invalidCSVData);
                        Log.e(TAG, E.toString());
                    }
                }
                //insert empty data for last 5 sec
                valuesX.add(new Entry(lastpoint + 5, lastX));
                valuesY.add(new Entry(lastpoint + 5, lastY));
                valuesZ.add(new Entry(lastpoint + 5, lastZ));
                if (axisX.isSelected())
                    dataSets.add(createSet(valuesX, "X"));
                if (axisY.isSelected())
                    dataSets.add(createSet(valuesY, "Y"));
                if (axisZ.isSelected())
                    dataSets.add(createSet(valuesZ, "Z"));
                LineData newdata = new LineData(dataSets);
                chart.setData(newdata);
                YAxis leftAxis = chart.getAxisLeft();
                leftAxis.setTextColor(Color.WHITE);
                leftAxis.setAxisMaximum(max);
                leftAxis.setAxisMinimum(min);
                leftAxis.setDrawGridLines(true);
                data.notifyDataChanged();
                chart.notifyDataSetChanged();
                chart.setVisibleXRangeMaximum(10);
            } catch (Exception e) {
                showToast(R.string.msg_failedInitCSVData);
                e.printStackTrace();
            }
        }
    }

    /**
     * Create graph set with data and type.
     *
     * @param yValue graph data
     * @param type   data type :X, Y, Z
     * @return colored graph dataset
     */
    private LineDataSet createSet(ArrayList<Entry> yValue, String type) {
        LineDataSet set = new LineDataSet(yValue, type);
        int colorId = type.equals("X") ? R.color.blue : type.equals("Y") ? R.color.green : R.color.orange;
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(getResources().getColor(colorId));
        set.setDrawCircles(false);
        set.setLineWidth(2f);
        set.setFillAlpha(65);
        set.setFillColor(getResources().getColor(colorId));
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    /**
     * init handler to seek seekbar automatically belong to video play position.
     */
    public void initVideoTracker() {
        playHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                if (seekController != null && player != null && player.isPlaying()) {
                    seekController.setProgress(player.getCurrentPosition());
                    playHandler.sendEmptyMessageDelayed(0, 100);
                }
            }
        };
        writeThread = new Thread() {
            @Override
            public void run() {
                saveActionData();
            }
        };
    }


    /********************  Control Functions  *******************/
    /************************************************************/

    /**
     * Close application
     *
     * @param view close button
     */
    public void closeApp(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.finish));
        builder.setMessage(getString(R.string.msg_finishApp));
        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * select/control pet's action time
     *
     * @param view start, end, add, delete buttons
     */
    public void pointAction(View view) {
        if (csvDump == null || csvDump.size() == 0) {
            showToast(R.string.msg_noCsvFile);
            return;
        }
        if (!videoPrepared) {
            showToast(R.string.msg_invalidVideoStatus);
            return;
        }
        btnPointStart.setSelected(false);
        btnPointEnd.setSelected(false);
        btnPointAdd.setSelected(false);
        btnPointDelete.setSelected(false);
        MPPointF centerPointPx = chart.getViewPortHandler().getContentCenter();
        MPPointD centerPointValue = chart.getValuesByTouchPoint(centerPointPx.x, centerPointPx.y, YAxis.AxisDependency.LEFT);
        Log.e("Current", centerPointValue.x + "");
        switch (view.getId()) {
            case R.id.btn_point_start:
                btnPointStart.setSelected(true);
                pendingData = new DataItem(actionSelector.getSelectedItemPosition(), getResources().getStringArray(R.array.actions)[actionSelector.getSelectedItemPosition()], (float) centerPointValue.x, -1);
                syncSeekbar(centerPointValue.x);
                break;
            case R.id.btn_point_end:

                btnPointEnd.setSelected(true);
                if (pendingData != null) {
                    pendingData.setEndTime((float) centerPointValue.x);
                }
                syncSeekbar(centerPointValue.x);
                break;
            case R.id.btn_point_add:
                if (pendingData == null) {
                    showToast(R.string.msg_noPendingData);
                    break;
                }
                if (pendingData.getEndTime() == -1) {
                    showToast(R.string.msg_invalidEndTime);
                    break;
                }
                dataArray.add(pendingData);
                dataAdapter.notifyDataSetChanged();
                dataList.scrollToPosition(dataArray.size() - 1);
                pendingData = null;
                break;
            case R.id.btn_point_delete:
                System.out.println(DataAdapter.selectedItems);
                if (DataAdapter.selectedItems.size() > 0) {
                    for (int i = dataArray.size() - 1; i >= 0; i--) {
                        System.out.println(DataAdapter.selectedItems.get(i, false));
                        if (DataAdapter.selectedItems.get(i, false)) {
                            dataArray.remove(i);
                        }
                    }
                    DataAdapter.selectedItems.clear();
                    dataAdapter.notifyDataSetChanged();
                }
                break;
        }
        pendingLabel.setText(pendingData != null ? pendingData.getLabel() : getString(R.string.label));
        pendingStart.setText(pendingData != null ? String.valueOf(pendingData.getStartTime()) : getString(R.string.start_time));
        pendingEnd.setText(pendingData != null ? (pendingData.getEndTime() != -1) ? String.valueOf(pendingData.getEndTime()) : "" : getString(R.string.end_time));
    }

    /**
     * switch showing data graph
     *
     * @param view switch button
     */
    public void switchGraphic(View view) {
        if (csvUri == null) {
            showToast(R.string.msg_noCsvFile);
            return;
        }
        dataType = ++dataType % 3;
        initData();
        chart.invalidate();
        labelType.setText(getResources().getStringArray(R.array.type)[dataType]);
    }

    /**
     * toggle graph axis view
     *
     * @param view x,y,z axis buttons
     */
    public void selectAxis(View view) {
        if (csvUri == null) {
            showToast(R.string.msg_noCsvFile);
            return;
        }
        switch (view.getId()) {
            case R.id.btn_x:
                // unable to remove all graph, at least one axis should be selected.
                if (!axisY.isSelected() && !axisZ.isSelected() && axisX.isSelected()) {
                    showToast(R.string.msg_needOne);
                    break;
                }
                axisX.setSelected(!axisX.isSelected());
                break;
            case R.id.btn_y:
                if (!axisX.isSelected() && !axisZ.isSelected() && axisY.isSelected()) {
                    showToast(R.string.msg_needOne);
                    break;
                }
                axisY.setSelected(!axisY.isSelected());
                break;
            case R.id.btn_z:
                if (!axisY.isSelected() && !axisX.isSelected() && axisZ.isSelected()) {
                    showToast(R.string.msg_needOne);
                    break;
                }
                axisZ.setSelected(!axisZ.isSelected());
                break;
        }
        initData();
        chart.invalidate();
    }

    /********************  Action Functions  *******************/
    /***********************************************************/

    /**
     * Open video/csv file
     *
     * @param view file open button, status button.(only first created.)
     */
    public void openFile(View view) {
        if (player != null && player.isPlaying()) {
            player.stop();
        }
        loadcsv = (view.getId() == R.id.btn_open_csv);
        Intent intent = new Intent()
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, (view.getId() == R.id.btn_open_video || (view.getId() == R.id.file_status)) ? new String[]{"video/*"} : new String[]{"text/csv", "text/comma-separated-values", "application/csv"})
                .setAction(Intent.ACTION_OPEN_DOCUMENT);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/PVLogger")));

        mainActivityResultLauncher.launch(intent);
        if (view.getId() == R.id.btn_open_video || (view.getId() == R.id.file_status))
            seekTime = 0;
    }

    /**
     * action when user click file status button.
     * this button show the status of video file, placed at the center of video view.
     *
     * @param view button on the center of video view.
     */
    public void fileStatusAction(View view) {
        if (videoPrepared) {
            playVideo(view);
            fileStatus.setVisibility(View.INVISIBLE);
        } else {
            openFile(view);
        }
    }

    /**
     * play/pause video
     *
     * @param view video view ( not video view, the mask - same size and same place with video view.)
     */
    public void playVideo(View view) {
        if (player != null && player.isPlaying()) {
            player.pause();
            fileStatus.setVisibility(View.VISIBLE);
        } else if (player != null && !player.isPlaying()) {
            player.start();
            playHandler.sendEmptyMessage(0);
            fileStatus.setVisibility(View.INVISIBLE);
        }
    }

    public void saveActionData() {
        if (csvDump == null || csvDump.size() == 0) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showToast(R.string.msg_noCsvFile);
                }
            });
            return;
        }
        try {
            BufferedWriter csvWriter = new BufferedWriter(new OutputStreamWriter(getContentResolver().openOutputStream(csvUri), "UTF-8"));
            csvWriter.append("time,gFx,gFy,gFz,wx,wy,wz,Bx,By,Bz,水を飲む, 餌を食べる, 歩く, 走る, ジャンプする,");
            csvWriter.newLine();
            for (int i = 1; i < csvDump.size(); i++) {
                for (int j = 0; j < 10; j++) {
                    csvWriter.append(csvDump.get(i)[j]);
                    csvWriter.append(",");
                }
                csvWriter.append(getLabel(Float.parseFloat(csvDump.get(i)[0]), i == (csvDump.size() - 1)));
                csvWriter.newLine();
            }
            csvWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getLabel(float time, boolean last) {
        String[] labelArray = new String[]{"0", "0", "0", "0", "0"};
        if (last) {
            //last '0' will be removed in csv file, so input with below format
            labelArray = new String[]{"=\"0\"", "=\"0\"", "=\"0\"", "=\"0\"", "=\"0\""};
        }
        for (DataItem item : dataArray) {
            if (time >= item.getStartTime() && time <= item.getEndTime()) {
                labelArray[item.getLabelIndex()] = "1";
                //do not return here because future DataItem might override label.
            }
        }
        String result = "";
        for (String label : labelArray) {
            result += label + ",";
        }
        return result;
    }


    public void syncSeekbar(double time) {
        //only when paused
        if (player != null && !player.isPlaying()) {
            sync = true;
            player.seekTo((int) time * 1000);
            seekController.setProgress((int) time * 1000);
        }
    }

    public void showToast(int id) {
        Toast.makeText(this, getString(id), Toast.LENGTH_LONG).show();
    }

    /********************  Callback Functions *******************/
    /************************************************************/
    /**
     * video seekbar change listener.
     */
    private class videoControlCallback implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
//                player.seekTo(seekBar.getProgress());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    player.seekTo(seekBar.getProgress(), MediaPlayer.SEEK_CLOSEST);
                else
                    player.seekTo((int) seekBar.getProgress());
            }
            if (sync) {
                sync = false;
                return;
            }
            chart.moveViewToX((float) progress / 1000f - 5);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            playHandler.removeMessages(0);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private class videoViewCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            player.setDisplay(holder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    }

    /**************  Activity Override Functions  **************/
    /***********************************************************/

    /**
     * init video player, and if there is already selected video file, prepare it.
     */

    @Override
    protected void onStart() {
        super.onStart();
        Log.e(TEST, "A");
        player = new MediaPlayer();
        Log.e(TEST, "B");
        Log.e(TEST, "C");
        Util.initLogWriter();
        Log.e(TEST, "D");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TEST,"resume");
        if (videoUri != null) {
            videoPrepared = false;
            fileStatus.setVisibility(View.VISIBLE);
            Log.e(TEST, "E");
            initVideo(videoUri);
            Log.e(TEST, "F");
            player.seekTo(seekTime);
        }
    }

    /**
     * release media player when activity stop
     */
    @Override
    protected void onStop() {
        int lastPosition = 0;
        Log.e(TEST, "G");
        if (player != null) {
            Log.e(TEST, "H");
            lastPosition = player.getCurrentPosition();
        }
        Log.e(TEST, "I");
        if (player.isPlaying())
            player.stop();
        Log.e(TEST, "J");
        player.release();
        Log.e(TEST, "K");
        player = null;
        saveActionData();
        Log.e(TEST, "L");
        Util.logWriterClose();
        if (videoUri != null) {
            Log.e(TEST, "Video Uri : " + videoUri.toString());
        }
        if (csvUri != null) {
            Log.e(TEST, "csvUri : " + csvUri.toString());
        }
        Log.e(TEST, "Last position : " + lastPosition);
        Util.saveDraft(this, videoUri, csvUri, lastPosition);
        Log.e(TEST, "N");
        super.onStop();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
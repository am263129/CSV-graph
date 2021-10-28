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

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.anggrayudi.storage.SimpleStorageHelper;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mufasa 2021.10.27
 */

public class MainActivity extends AppCompatActivity {

    private ConstraintLayout btnPointStart, btnPointEnd, btnPointAdd, btnPointDelete, axisX, axisY, axisZ;
    private Spinner actionSelector;
    private SurfaceView videoView;
    private SurfaceHolder holder;
    private ActivityResultLauncher<Intent> someActivityResultLauncher;
    private ProgressBar loadingProgress;
    private TextView statusLabel;
    private ImageView fileStatus;
    private RecyclerView dataList;
    private DataAdapter dataAdapter;
    private ArrayList<DataItem> dataArray = new ArrayList<>();
    private DataItem pendingData;
    private MediaPlayer player;
    private LineChart chart;
    private SeekBar seekController;
    private Handler playHandler;
    private BufferedWriter csvWriter = null;
    private int videoDuration;
    private int dataType = 0;
    private Uri csvUri = null, videoUri = null;
    private List<String[]> csvDump;
    private final String TAG = "MainActivity";
    private final int REQUEST_CODE_PICK_FOLDER = 9001;
    private boolean videoPrepared = false;
    private final SimpleStorageHelper storageHelper = new SimpleStorageHelper(this);



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initUi();
        initLauncher();
        initChart();
        initVideoTracker();
        setupSimpleStorage(savedInstanceState);
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
        axisX = findViewById(R.id.btn_x);
        axisY = findViewById(R.id.btn_y);
        axisZ = findViewById(R.id.btn_z);
        videoView = findViewById(R.id.video_view);
        loadingProgress = findViewById(R.id.loading_progress);
        seekController = findViewById(R.id.seekBar);
        statusLabel = findViewById(R.id.status_label);
        fileStatus = findViewById(R.id.file_status);
        dataList = findViewById(R.id.action_data_list);
        dataList.setLayoutManager(new LinearLayoutManager(this));
        dataAdapter = new DataAdapter(dataArray);
        dataList.setAdapter(dataAdapter);
        seekController.setOnSeekBarChangeListener(new videoControlCallback());
        axisX.setSelected(true);
        axisY.setSelected(true);
        axisZ.setSelected(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * init file pick launcher and listener.
     */
    public void initLauncher() {
        someActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            Intent data = result.getData();
                            if (data != null) {
                                final int takeFlags = data.getFlags()
                                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);

                                if (data.getDataString().length() < 3) {
                                    showToast(R.string.invalid_filename);
                                    return;
                                }
                                String fileType = data.getDataString().substring(data.getDataString().length() - 3);
                                Log.e(TAG, fileType);
                                if (fileType.equals("mp4")) {
                                    statusLabel.setVisibility(View.INVISIBLE);
                                    loadingProgress.setVisibility(View.VISIBLE);
                                    videoUri = data.getData();
                                    initVideo(data.getData());
                                } else if (fileType.equals("csv")) {
                                    csvUri = data.getData();
                                    initData();
                                } else {
                                    showToast(R.string.invalid_filetype);
                                    return;
                                }

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
        chart.getDescription().setEnabled(true);
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
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(false);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(10f);
        leftAxis.setAxisMinimum(-10f);
        leftAxis.setDrawGridLines(true);
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    /**
     * init Video player with selected mp4 file
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
                if( csvDump == null || csvDump.size() == 0) {
                    //read csv file data and save it to dump, this dump will used for upcomming process instead of read csv again.
                    InputStream input = getContentResolver().openInputStream(csvUri);
                    CSVReader reader = new CSVReader(new InputStreamReader(input));
                    csvDump = reader.readAll();
                    reader.close();
                }
                if(csvDump.size()<2){
                    showToast(R.string.msg_emptyCsv);
                    return;
                }
                int offsetIndex = dataType * 3;
                boolean predata = true;
                float lastpoint = 0;
                float lastX = 0, lastY = 0, lastZ = 0;
                for(int i = 1; i< csvDump.size(); i++){
                    String[] nextLine = csvDump.get(i);
                    if (predata) {
                        //insert empty data for pre 5 sec
                        valuesX.add(new Entry(0, Float.parseFloat(nextLine[offsetIndex + 1])));
                        valuesY.add(new Entry(0, Float.parseFloat(nextLine[offsetIndex + 2])));
                        valuesZ.add(new Entry(0, Float.parseFloat(nextLine[offsetIndex + 3])));
                        predata = false;
                    }
                    min = Math.min(min, Math.min(Float.parseFloat(nextLine[offsetIndex + 1]), Math.min(Float.parseFloat(nextLine[offsetIndex + 2]), Float.parseFloat(nextLine[offsetIndex + 3]))));
                    max = Math.max(max, Math.max(Float.parseFloat(nextLine[offsetIndex + 1]), Math.max(Float.parseFloat(nextLine[offsetIndex + 2]), Float.parseFloat(nextLine[offsetIndex + 3]))));
                    valuesX.add(new Entry(Float.parseFloat(nextLine[0]) + 5, Float.parseFloat(nextLine[offsetIndex + 1])));
                    valuesY.add(new Entry(Float.parseFloat(nextLine[0]) + 5, Float.parseFloat(nextLine[offsetIndex + 2])));
                    valuesZ.add(new Entry(Float.parseFloat(nextLine[0]) + 5, Float.parseFloat(nextLine[offsetIndex + 3])));
                    lastpoint = Float.parseFloat(nextLine[0]) + 5;
                    lastX = Float.parseFloat(nextLine[offsetIndex + 1]);
                    lastY = Float.parseFloat(nextLine[offsetIndex + 2]);
                    lastZ = Float.parseFloat(nextLine[offsetIndex + 3]);
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
            } catch (Exception e) {
                e.printStackTrace();
            }
            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setTextColor(Color.WHITE);
            leftAxis.setAxisMaximum(max);
            leftAxis.setAxisMinimum(min);
            leftAxis.setDrawGridLines(true);
            data.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.setVisibleXRangeMaximum(10);
        }
    }

    /**
     * Create graph set with data and type.
     * @param yValue graph data
     * @param type data type :X, Y, Z
     * @return colored graph dataset
     */
    private LineDataSet createSet(ArrayList<Entry> yValue, String type) {
        LineDataSet set = new LineDataSet(yValue, type);
        int colorId = type.equals("X")?R.color.blue:type.equals("Y")?R.color.green:R.color.orange;
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
    }

    /**
     * Setup document file storage :: for android 11
     *
     * @param savedState
     */
    private void setupSimpleStorage(Bundle savedState) {
        if (savedState != null) {
            storageHelper.onRestoreInstanceState(savedState);
        }
        storageHelper.setOnStorageAccessGranted((requestCode, root) -> {
            String absolutePath = DocumentFileUtils.getAbsolutePath(root, getBaseContext());
            Toast.makeText(
                    getBaseContext(),
                    getString(R.string.ss_selecting_root_path_success_without_open_folder_picker, absolutePath),
                    Toast.LENGTH_SHORT
            ).show();
            return null;
        });
        storageHelper.setOnFileSelected((requestCode, files) -> {
            String message = "File selected: " + DocumentFileUtils.getFullName(files.get(0));
            Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
            return null;
        });
        storageHelper.setOnFolderSelected((requestCode, folder) -> {
            String message = "Folder selected: " + DocumentFileUtils.getAbsolutePath(folder, getBaseContext());
            Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
            return null;
        });
        storageHelper.setOnFileCreated((requestCode, file) -> {
            String message = "File created: " + file.getName();
            Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
            return null;
        });
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
        btnPointStart.setSelected(false);
        btnPointEnd.setSelected(false);
        btnPointAdd.setSelected(false);
        btnPointDelete.setSelected(false);
        switch (view.getId()) {
            case R.id.btn_point_start:
                btnPointStart.setSelected(true);
                pendingData = new DataItem(getResources().getStringArray(R.array.actions)[actionSelector.getSelectedItemPosition()], (float)player.getCurrentPosition()/1000f,0);
                break;
            case R.id.btn_point_end:
                btnPointEnd.setSelected(true);
                if(pendingData!=null){
                    pendingData.setEndTime((float)player.getCurrentPosition()/1000f);
                    dataArray.add(pendingData);
                    dataAdapter.notifyDataSetChanged();
                    dataList.scrollToPosition(dataArray.size() - 1);
                    pendingData = null;
                }
                break;
            case R.id.btn_point_add:
                saveActionData();
                break;
            case R.id.btn_point_delete:
                break;
        }
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
     * @param view file open button, status button.(only first created.)
     */
    public void openFile(View view) {
//        if (Build.VERSION.SDK_INT >= 28) {
//            storageHelper.openFolderPicker(REQUEST_CODE_PICK_FOLDER);
//            storageHelper.openFilePicker("*");
//        } else {
            Intent intent = new Intent()
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, (view.getId() == R.id.btn_open_video || (view.getId() == R.id.file_status)) ? new String[]{"video/mp4"} : new String[]{"text/csv", "text/comma-separated-values", "application/csv"})
                    .setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/PVLogger")));

            someActivityResultLauncher.launch(intent);
//        }

    }

    /**
     * action when user click file status button.
     * this button show the status of video file, placed at the center of video view.
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

    public void saveActionData(){
        if(csvDump == null || csvDump.size() == 0)
        {
            showToast(R.string.msg_noCsvFile);
            return;
        }
        try {
            BufferedWriter csvWriter = new BufferedWriter(new OutputStreamWriter(getContentResolver().openOutputStream(csvUri),"UTF-8"));
            csvWriter.append("time,gFx,gFy,gFz,wx,wy,wz,Bx,By,Bz,Label");
            csvWriter.newLine();
            for(int i = 1; i<csvDump.size(); i++){
                String appender = "";
                for(String data:csvDump.get(i)){
                    csvWriter.append(appender+data);
                    appender=",";
                }
                csvWriter.append(getLabel(Float.parseFloat(csvDump.get(i)[0])));
                csvWriter.newLine();
            }
            csvWriter.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public String getLabel(float time){
        String label = "";
        for(DataItem item: dataArray){
            if(time>=item.getStartTime() && time<=item.getEndTime()){
                label =  item.getLabel();
                //do not return here because future Dataitem might override label.
            }
        }
        return label;
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
            chart.moveViewToX((float) progress / 1000f);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            playHandler.removeMessages(0);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            player.seekTo(seekBar.getProgress());
            playHandler.sendEmptyMessage(0);
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
        player = new MediaPlayer();
        videoPrepared = false;
        if (videoUri == null) {
            fileStatus.setVisibility(View.VISIBLE);
            return;
        }
        initVideo(videoUri);
    }

    /**
     * release media player when activity stop
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (player.isPlaying())
            player.stop();
        player.release();
        player = null;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoUri = null;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        storageHelper.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        storageHelper.onRestoreInstanceState(savedInstanceState);
    }
}
package com.example.bluetooth.petvoiceviewer;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
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
import com.github.mikephil.charting.utils.ColorTemplate;
import com.opencsv.CSVReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ConstraintLayout btnPointStart, btnPointEnd, btnPointAdd, btnPointDelete, axisX, axisY, axisZ;
    private Spinner actionSelector;
    private SurfaceView videoView;
    private SurfaceHolder holder;
    private ActivityResultLauncher<Intent> someActivityResultLauncher;
    private ProgressBar loadingProgress;
    private TextView statusLabel;
    private MediaPlayer player;
    private LineChart chart;
    private SeekBar seekController;

    private int dataType = 0;
    private Uri csvUri;
    private final String TAG = "MainActivity";
    private final int REQUEST_CODE_PICK_FOLDER = 9001;


    private final SimpleStorageHelper storageHelper = new SimpleStorageHelper(this);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initUi();
        initLauncher();
        initChart();
        setupSimpleStorage(savedInstanceState);
    }

    /********************  init Functions  *******************/

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
        axisX.setSelected(true);
        axisY.setSelected(true);
        axisZ.setSelected(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

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
                                if (data.getDataString().length() < 3) {
                                    Toast.makeText(MainActivity.this, getString(R.string.invalid_filename), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                String fileType = data.getDataString().substring(data.getDataString().length() - 3);
                                Log.e(TAG, fileType);
                                if (fileType.equals("mp4")) {
                                    statusLabel.setVisibility(View.INVISIBLE);
                                    loadingProgress.setVisibility(View.VISIBLE);
                                    initVideo(data.getData());
                                } else if (fileType.equals("csv")) {
                                    csvUri = data.getData();
                                    initData();
                                } else {
                                    Toast.makeText(MainActivity.this, getString(R.string.invalid_filetype), Toast.LENGTH_SHORT).show();
                                    return;
                                }

                            }
                        }
                    }
                });
    }

    public void initChart() {
        chart = findViewById(R.id.chart_view);

        // enable description text
        chart.getDescription().setEnabled(true);

        // enable touch gestures
        chart.setTouchEnabled(true);

        // enable scaling and dragging
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        chart.setPinchZoom(true);

        // set an alternative background color
        chart.setBackgroundColor(Color.LTGRAY);
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        // add empty data
        chart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = chart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.WHITE);

        XAxis xl = chart.getXAxis();
        xl.setTextColor(Color.WHITE);
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
    }

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
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initCsvFile(){

    }

    public void initData() {
        CSVReader reader = null;
        LineData data = chart.getData();
        float min = 0f, max = 0f;
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        ArrayList<Entry> valuesX = new ArrayList<>();
        ArrayList<Entry> valuesY = new ArrayList<>();
        ArrayList<Entry> valuesZ = new ArrayList<>();
        if (data != null) {
            try {
                InputStream input = getContentResolver().openInputStream(csvUri);
                reader = new CSVReader(new InputStreamReader(input));
                String[] nextLine = reader.readNext();
                int offsetIndex = dataType*3;
                while ((nextLine = reader.readNext()) != null) {
                    // nextLine[] is an array of values from the line
                    // set.addEntry(...); // can be called as well
                    min = Math.min(min,Math.min(Float.parseFloat(nextLine[offsetIndex+1]),Math.min(Float.parseFloat(nextLine[offsetIndex+2]),Float.parseFloat(nextLine[offsetIndex+3]))));
                    max = Math.max(max,Math.max(Float.parseFloat(nextLine[offsetIndex+1]),Math.max(Float.parseFloat(nextLine[offsetIndex+2]),Float.parseFloat(nextLine[offsetIndex+3]))));
                    valuesX.add(new Entry(Float.parseFloat(nextLine[0]), Float.parseFloat(nextLine[offsetIndex+1])));
                    valuesY.add(new Entry(Float.parseFloat(nextLine[0]), Float.parseFloat(nextLine[offsetIndex+2])));
                    valuesZ.add(new Entry(Float.parseFloat(nextLine[0]), Float.parseFloat(nextLine[offsetIndex+3])));
//                    data.addEntry(new Entry(Float.parseFloat(nextLine[0]), Float.parseFloat(nextLine[offsetIndex+1]), getResources().getDrawable(R.drawable.ic_graph_item)),0);
                }
                if(axisX.isSelected())
                dataSets.add(createXSet(valuesX));
                if(axisY.isSelected())
                dataSets.add(createYSet(valuesY));
                if(axisZ.isSelected())
                dataSets.add(createZSet(valuesZ));
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

            // let the chart know it's data has changed
            chart.notifyDataSetChanged();

            // limit the number of visible entries
            chart.setVisibleXRangeMaximum(5);
//            chart.getDescription().setText(dataType == 0?getString(R.string.label_acc):dataType==1?getString(R.string.label_gyr):getString(R.string.label_geo));
            // chart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
//            chart.moveViewToX(data.getEntryCount());
        }
    }

    private LineDataSet createXSet(ArrayList<Entry> yValue) {
        LineDataSet set = new LineDataSet(yValue, "X");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(getResources().getColor(R.color.blue));
        set.setDrawCircles(false);
        set.setLineWidth(2f);
        set.setFillAlpha(65);
        set.setFillColor(getResources().getColor(R.color.blue));
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }
    private LineDataSet createYSet(ArrayList<Entry> yValue) {
        LineDataSet set = new LineDataSet(yValue, "Y");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(getResources().getColor(R.color.green));
        set.setDrawCircles(false);
        set.setLineWidth(2f);
        set.setFillAlpha(65);
        set.setFillColor(getResources().getColor(R.color.green));
        set.setHighLightColor(Color.rgb(200, 223, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }
    private LineDataSet createZSet(ArrayList<Entry> yValue) {
        LineDataSet set = new LineDataSet(yValue, "Z");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(getResources().getColor(R.color.orange));
        set.setDrawCircles(false);
        set.setLineWidth(2f);
        set.setFillAlpha(65);
        set.setFillColor(getResources().getColor(R.color.orange));
        set.setHighLightColor(Color.rgb(177, 117, 223));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
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
    /**
     * Close application
     *
     * @param view close button
     */
    public void closeApp(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Exit");
        builder.setMessage("Are you sure to exit app?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
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
                break;
            case R.id.btn_point_end:
                btnPointEnd.setSelected(true);
                break;
            case R.id.btn_point_add:
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
        if(csvUri == null){
            Toast.makeText(this, getString(R.string.msg_nocsvfile),Toast.LENGTH_SHORT).show();
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
        if(csvUri == null){
            Toast.makeText(this, getString(R.string.msg_nocsvfile),Toast.LENGTH_SHORT).show();
            return;
        }
        switch (view.getId()) {
            case R.id.btn_x:
                axisX.setSelected(!axisX.isSelected());
                break;
            case R.id.btn_y:
                axisY.setSelected(!axisY.isSelected());
                break;
            case R.id.btn_z:
                axisZ.setSelected(!axisZ.isSelected());
                break;
        }
        initData();
        chart.invalidate();
    }

    /********************  Action Functions  *******************/


    public void openFile(View view) {
        if (Build.VERSION.SDK_INT >= 28) {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_FOLDER);
            storageHelper.openFilePicker("*");
        } else {
            Intent intent = new Intent()
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, view.getId() == R.id.btn_open_video ? new String[]{"video/mp4"} : new String[]{"text/csv", "text/comma-separated-values", "application/csv"})
                    .setAction(Intent.ACTION_GET_CONTENT);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/PVLogger")));
            someActivityResultLauncher.launch(intent);
        }

    }

    public void playVideo(View view) {
        if (player != null && player.isPlaying()) {
            player.pause();
        } else if (player != null && !player.isPlaying()) {
            player.start();
        }
    }

    public void addData() {

    }

    public void drawGraphics() {
    }

    /********************  Callback Functions *******************/

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

    /********************  Activity Override Functions  *******************/

    @Override
    public void onStart() {
        super.onStart();
        player = new MediaPlayer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player.isPlaying())
            player.stop();
        player.release();
        player = null;
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
package com.example.iperfv2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

public class MainActivity extends AppCompatActivity implements PresetAdapter.ListItemClickListener, ActivityCompat.OnRequestPermissionsResultCallback{

    private ViewFlipper flipper;

    private RecyclerView mRecycler;
    private EditText inputText;
    private ToggleButton toggle;
    //private LineChart chart;
    private ChartHandler chart;
    private TestAdapter testAdapter;
    private TestHandler testHandler;

    public RecyclerView pRecycler;
    private EditText loadText;
    private PresetHandler presetHandler;
    private PresetAdapter presetAdapter;

    private ExecutorService executorService;
    private StringBuilder builder;
    private Process process;
    private ArrayList<String> presets;
    private ArrayList<Float> pair;
    private int interval;

    public static int PICK_FILE = 1;
    public static int PICK_PRESET = 2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Os.setenv("TMPDIR", "/data/data/com.example.iperfv2/cache/", true);
        } catch (ErrnoException e) {
            e.printStackTrace();
        }
        setContentView(R.layout.activity_main);
        initViews();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_about:

                AlertDialog alertDialog = new AlertDialog.Builder(this).create(); //Read Update
                alertDialog.setTitle("Information");
                alertDialog.setMessage("" +
                        "1. Give file access in order to save and import tests and presets \n\n" +
                        "2. Import presets from txt file or input your own command \n\n" +
                        "3. Run test and save or clear dialog \n\n " +
                        "By default '-f m' and '--forceflush' is added if not found in iperf cmd");
                alertDialog.show();  //<-- See This!
                return true;
            case R.id.menu_clear:
                clearView();
                return true;
            case R.id.menu_presets:
                flipper.showNext();
                return true;
            case R.id.menu_save:
                try {
                    saveTest();
                    Toast.makeText(this, "Log saved successfully!", Toast.LENGTH_SHORT);
                } catch (IllegalAccessError e) {
                    throw e;
                }
                return true;
            case R.id.menu_access:
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
                return true;
            case R.id.menu_import:
                Intent intentImport = new Intent(Intent.ACTION_GET_CONTENT);
                intentImport.setType("text/plain");
                startActivityForResult(intentImport, PICK_FILE);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void initViews() {
        flipper = findViewById(R.id.myViewFlipper);
        builder = new StringBuilder();
        presets = new ArrayList<>();
        pair = new ArrayList<>();

        chart = new ChartHandler(MainActivity.this, this);

        mRecycler = (RecyclerView) findViewById(R.id.mRecycler);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRecycler.setLayoutManager(layoutManager);
        testAdapter = new TestAdapter();
        mRecycler.setAdapter(testAdapter);
        testHandler = new TestHandler(this);
        inputText = findViewById(R.id.inputText);

        pRecycler = (RecyclerView) findViewById(R.id.pRecycler);
        LinearLayoutManager layoutManager1 = new LinearLayoutManager(this);
        pRecycler.setLayoutManager(layoutManager1);
        presetAdapter = new PresetAdapter(this);
        pRecycler.setAdapter(presetAdapter);
        presetHandler = new PresetHandler(this);

        toggle = (ToggleButton) findViewById(R.id.toggleButton);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    clearView();
                    String cmd = inputText.getText().toString();

                    if (cmd.length() > 0) {
                        executeTest(formatCmd(cmd));
                        buttonView.setBackgroundColor(Color.GREEN);
                    } else {
                        toggle.setChecked(false);
                    }
                } else {
                    process.destroy();
                    buttonView.setBackgroundColor(Color.RED);
                }
            }
        });

        Button button = (Button) findViewById(R.id.loadButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intentPresets = new Intent(Intent.ACTION_GET_CONTENT);
                intentPresets.setType("text/plain");
                startActivityForResult(intentPresets, PICK_PRESET);
            }
        });
    }


    // //   //  //  //  //  //  //
    //  Process related methods //
    //  //  //  //  //  //  //  //
    public void executeTest(String cmd) {
            executorService = Executors.newSingleThreadExecutor();
            executorService.execute(new Thread(new testTask(cmd, testHandler, 1)));
    }

    @Override
    public void onDestroy() {
        if (testHandler != null) {
            testHandler.removeCallbacksAndMessages(null);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public void onListItemClick(int position) {

        inputText.setText(presets.get(position));
        flipper.showNext();
    }

    private static class TestHandler extends Handler {
        private WeakReference<MainActivity> weakReference;

        public TestHandler(MainActivity activity) {
            this.weakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case 10:
                    String resultMsg = (String) msg.obj;
                    weakReference.get().testAdapter.addString(resultMsg);
                    weakReference.get().mRecycler.scrollToPosition(weakReference.get().testAdapter.getItemCount() - 1);
                    break;
                default:
                    break;
            }
        }
    }

    private static class PresetHandler extends Handler {
        private WeakReference<MainActivity> weakReference;

        public PresetHandler(MainActivity activity) {
            this.weakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case 10:
                    String resultMsg = (String) msg.obj;
                    weakReference.get().presetAdapter.addString(resultMsg);
                    weakReference.get().pRecycler.scrollToPosition(weakReference.get().presetAdapter.getItemCount() - 1);
                    break;
                default:
                    break;
            }
        }
    }

    private class testTask implements Runnable {
        private String cmd;
        private TestHandler testHandler;
        private long delay;

        public testTask(String cmd, TestHandler testHandler, int delay) {
            this.cmd = cmd;
            this.testHandler = testHandler;
            this.delay = delay;
        }

        @Override
        public void run() {
            BufferedReader successReader = null;
            BufferedReader errorReader = null;

            try {
                // test
                process = Runtime.getRuntime().exec(cmd);

                // success
                successReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // error
                errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String lineStr;


                while ((lineStr = successReader.readLine()) != null) {

                    // receive
                    Message msg = testHandler.obtainMessage();
                    msg.obj = lineStr + "\r\n";
                    msg.what = 10;
                    msg.sendToTarget();

                    formatGraph(lineStr + "\r\n");
                    builder.append(lineStr + "\r\n");
                }
                while ((lineStr = errorReader.readLine()) != null) {

                    // receive
                    Message msg = testHandler.obtainMessage();
                    msg.obj = lineStr + "\r\n";
                    msg.what = 10;
                    msg.sendToTarget();
                }
                Thread.sleep(delay * 1000);

                process.waitFor();
                toggle.setChecked(false);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {

                    if (successReader != null) {
                        successReader.close();
                    }
                    if (errorReader != null) {
                        errorReader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (process != null) {
                    process.destroy();
                }
            }
        }
    }

    // //   //  //  //
    //  Misc methods //
    //  //  //  //  //
    public void clearView() {
        chart.getChart().clear();
        LineData data = new LineData();
        chart.getChart().setData(data);

        testAdapter.clear();
        mRecycler.removeAllViewsInLayout();
        builder.setLength(0);
    }

    public String formatCmd(String cmd) {
        ArrayList<Boolean> params = new ArrayList<>();
        StringBuilder newCmd = new StringBuilder();
        newCmd.append(cmd);
        String[] split = cmd.split(" ");
        params.add(0, false);
        params.add(1, false);
        params.add(2, false);
        params.add(3, false);

        for (String param : split) {
            if (param.contains("iperf3")) {
                params.add(0, true);
            }
            if (param.contains("--forceflush")) {
                params.add(1, true);
            }
            if (param.contains("-f")) {
                params.add(2, true);
            }
            if (param.contains("-i")) {
                params.add(3, true);
                int tmp = Integer.parseInt(param.replace("-i",""));
                interval = tmp;
            }
        }
        if(params.get(0) == false) {
            return cmd;
        }
        if(params.get(0) == true && params.get(1) == false) {
            newCmd.append(" --forceflush");
        }
        if(params.get(0) == true && params.get(2) == false) {
            newCmd.append(" -fm");
        }
        if(params.get(0) == true && params.get(3) == false) {
            interval = 1;
        }
        return newCmd.toString();
    }


    public void loadPresets(Uri uri) {
        String preset;
        presetAdapter.clear();
        pRecycler.removeAllViewsInLayout();
        BufferedReader reader = null;
        
        try {
            reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)));

            while ((preset = reader.readLine()) != null) {

                presets.add(preset);
                Message msg = presetHandler.obtainMessage();
                msg.obj = preset + "\r\n";
                msg.what = 10;
                msg.sendToTarget();
            }
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveTest() {

        LocalDateTime time = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd-HH.mm.ss");
        String filename = time.format(formatter);

        String fileContents = builder.toString();

        try {
            File extBaseDir = Environment.getExternalStorageDirectory();
            File file = new File(extBaseDir.getAbsolutePath() + "/iPerf/" + filename );
            if (!file.exists()) {
                file.mkdirs();
            } else {
                file.delete();
                file.mkdirs();
            }

            String filePath = file.getAbsolutePath();
            FileOutputStream out = null;

            out = new FileOutputStream(filePath + "/log.txt");
            out.write(fileContents.getBytes());
            out.flush();
            out.close();

            Message msg = testHandler.obtainMessage();
            msg.obj = "Saved to " + filePath + "\r\n";
            msg.what = 10;
            msg.sendToTarget();

            chart.getChart().saveToPath("graph", "/iPerf/" + filename);

            }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readTextFile(Uri uri){
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)));
            String tmpLine = "";
            clearView();

            while ((tmpLine = reader.readLine()) != null) {

                formatGraph(tmpLine + "\r\n");

                Message msg = testHandler.obtainMessage();
                msg.obj = tmpLine + "\r\n";
                msg.what = 10;
                msg.sendToTarget();
                }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null){
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void formatGraph(String msg) {
        Pattern p = Pattern.compile("(T|R)X-C");
        Matcher m = p.matcher(msg);

        if (m.find()) {
            Pattern pp = Pattern.compile("(\\d*\\.*\\d*)\\d*\\s(\\w*)bits\\/sec");
            Matcher mm = pp.matcher(msg);
            while(mm.find()) {
                if (pair.size() == 0) {
                    pair.add(Float.valueOf(mm.group(1)));

                } else if (pair.size() == 1) {
                    pair.add(Float.valueOf(mm.group(1)));
                    chart.addDualEntry(pair.get(0), pair.get(1), interval);
                    pair.clear();
                }
            }
        } else {
            Pattern pp = Pattern.compile("(\\d*\\.*\\d*)\\d*\\s(\\w*)bits\\/sec");
            Matcher mm = pp.matcher(msg);
            while (mm.find()) {
                float value = Float.valueOf(mm.group(1));
                chart.addEntry(value, interval);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE) {
            if (resultCode == RESULT_OK) {
                // User pick the file
                Uri uri = data.getData();
                readTextFile(uri);
                Toast.makeText(this, "Test imported!", Toast.LENGTH_LONG).show();
            } else {
                System.out.print("testLoad");
            }
        }else if (requestCode == PICK_PRESET) {
            if (resultCode == RESULT_OK) {
                // User pick the file
                Uri uri = data.getData();
                loadPresets(uri);
                Toast.makeText(this, "Presets loaded!", Toast.LENGTH_LONG).show();
            } else {
                System.out.print("testPrint");
            }
        }
    }
}


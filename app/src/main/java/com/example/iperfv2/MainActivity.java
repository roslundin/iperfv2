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

    // Used to rotate main view and preset view
    private ViewFlipper flipper;

    // Main view
    private RecyclerView mRecycler;
    private EditText inputText;
    private ToggleButton toggle;
    private ChartHandler chart;
    private TestAdapter testAdapter;
    private TestHandler testHandler;

    // Preset view
    private RecyclerView pRecycler;
    private PresetHandler presetHandler;
    private PresetAdapter presetAdapter;

    // Misc containers and services
    private TestTask task;
    private ExecutorService executorService;
    private StringBuilder builder;
    private ArrayList<String> presets;
    private ArrayList<Float> pair;
    private int interval;

    public static int PICK_FILE = 1;
    public static int PICK_PRESET = 2;

    // Creates activity and initiates all views and instances used.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Necessary in order for iPerf to create test files containing data to transfer.
            Os.setenv("TMPDIR", "/data/data/com.example.iperfv2/cache/", true);
        } catch (ErrnoException e) {
            e.printStackTrace();
        }
        setContentView(R.layout.activity_main);
        initViews();
    }

    // Creates and inflates menu bar, specified in res/menu/menu.xml
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    // Fills menu bar options with methods
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_about:
                // jcomment:
                // Normalt brukar man lägga alla UI-strängar i en resursfil så det blir enklare att göra eventuell lokalisering
                // Är kanske strunt samma i denna app eftersom den aldrig kommer att släppas på annat språk än engelska
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

    // Initiates views, handlers and containers
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

        //On off process button
        toggle = (ToggleButton) findViewById(R.id.toggleButton);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //On
                if (isChecked) {
                    clearView();
                    String cmd = inputText.getText().toString();

                    // If command is not empty it will run otherwise act like no click happened.
                    if (cmd.length() > 0) {
                        // jcomment:
                        // Oavsett vilken sträng som användaren matar in så kommer executeTest anropas med den strängen.
                        // T e x en sträng som bara innehåller blanksteg eller stollig syntax. Det kanske vore bättre om
                        // formatCmd returnerade true eller false beroende på om strängen är valid eller ej och endast om
                        // den är ok (true), så anropas executeTest och knappen blir grön.
                        executeTest(formatCmd(cmd));
                        buttonView.setBackgroundColor(Color.GREEN);
                    } else {
                        toggle.setChecked(false);
                    }
                //Off
                } else {
                    task.getProcess().destroy();
                    buttonView.setBackgroundColor(Color.RED);
                }
            }
        });
        // Load presets button handler
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
            task = new TestTask(cmd, testHandler, 1, this);
            executorService.execute(new Thread(task));
    }

    //Used to destroy running proccess
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

    // Listens for click on preset RecyclerView and fetches clicked string to input box and switches view
    @Override
    public void onListItemClick(int position) {

        inputText.setText(presets.get(position));
        flipper.showNext();
    }

    // //   //  //  //
    //  Misc methods //
    //  //  //  //  //
    public void clearView() {
        chart.getChart().clear();
        LineData data = new LineData();
        chart.getChart().setData(data);

        TestAdapter adapter = (TestAdapter) mRecycler.getAdapter();
        adapter.clear();
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
        interval = 1;

        for (String param : split) {
            if (param.contains("iperf3")) {
                params.add(0, true);
            }
            if (param.contains("--forceflush")) {
                params.add(1, true);
            }
            // jcomment:
            // Även det långa formatet --format matchas här. Även argumenten --forceflush och
            // --file kommer dock att matchas av detta, vilket inte är önskvärt.
            if (param.contains("-f")) {
                params.add(2, true);
            }
            // jcomment:
            // Även det långa formatet --interval matchas här. OM de någonsin kommer att introducera
            // något annat argument som också har -i som substräng, kommer även den att matcha hör vilket
            // kanske inte är önskvärt (se ovan på -f)
            if (param.contains("-i")) {
                params.add(3, true);
                // jcomment:
                // Detta funkar nog inte som ni har tänkt er. Detta täcker endast in fallet med att användaren
                // anger interval i formatet -i5. Om hen istället använder -i 5 så kommer param bara vara "-i"
                // när ni kommer hit och det finns ingen siffra att parsa ut. Ännu värre är det om användaren
                // använder långformatet "--interval 5" (eller skriver någonting stolligt som -iAPA). Då kraschar
                // applikationen eftersom ni kommer försöka översätta -nterval eller APA till en siffra).
                // Applikationen skall aldrig krascha även om användaren gör någonting idiotiskt.
                String tmp = param.replace("-i","");
                if( tmp != "") {
                    interval = Integer.parseInt(tmp);
                }
            }
        }
        if(params.get(0) == false) {
            return cmd;
        }
        // jcomment:
        // Att fortsätta kolla om params.get(0) == true verkar onödigt. Om den inte är det så har ni
        // redan returnerat enligt koden precis ovan, d v s ni kommer aldrig komma hit om den är false
        if(params.get(0) == true && params.get(1) == false) {
            newCmd.append(" --forceflush");
        }
        if(params.get(0) == true && params.get(2) == false) {
            newCmd.append(" -fm");
        }
        // jcomment:
        // interval är väl redan 1 (om den inte uttryckligen satts till annat av användaren men i så fall
        // är params.get(3) true hursomhelst.
        if(params.get(0) == true && params.get(3) == false) {
            interval = 1;
        }
        return newCmd.toString();

        // jcomment:
        // Jag tror absolut att ni kan fixa till den hör metoden så att den gör exakt vad ni har tänkt
        // men jag känner också att ni kankse komplicerar det hela något med strängsplittningar och
        // bool-arrayer. Här är en alternativ idé på hur man skulle kunna göra (i någon slags c#-meta-
        // syntax för jag orkar inte kolla exakt hur det skulle se ut i java). Har inte heller testat detta
        // men jag tror att idén är sund :)
        // Testa att göra motsvarande i Java om ni har lust...
        //
        // cmd = cmd.trim();
        // if(!cmd.startsWith("iperf3")
        //      return cmd; // eller "" eller "whatever" ...
        // if(!cmd.contains("--forceflush")
        //      cmd += " --forceflush";
        // // Regexpa ut -i och -f; ta hänsyn till både kort- och långformat samt att argumentet
        // // måste särskrivas om långformat: -i 5, -i5, --interval 5 är alla ok, inte --interval5
        // Match m;
        // m = Match(cmd, "(-i\\s*|--interval\\s+)(\\d+)");
        // if(m.success())
        //      interval = Integer.parseInt(m.groups(2));
        // m = Match(cmd, "(-f\\s*|--format\\s+)[kmgtKMGT]");
        // if(!m.success())
        //      cmd += " -fm";
        // return cmd;
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
            // jcomment:
            // getExternalStorageDirectory() verkar vara deprecated fr o m api 29, alternativ?
            File extBaseDir = Environment.getExternalStorageDirectory();
            // jcomment:
            // filename är egentligen namnet på en directory. Filename kommer senare och är då
            // log.txt eller graph.png
            File file = new File(extBaseDir.getAbsolutePath() + "/iPerf/" + filename );
            // jcomment:
            // Jag kanske missar någonting här men kommer det någonsin att existera en mapp med det
            // det här namnet sedan tidigare? Med tanke på hur ni namnger mappen så skull det kräva
            // att man kör save 2ggr på samma sekund vilket inte verkar sannolikt. Dessutom kör ni
            // file.mkdirs(); i alla händelser varför den kan lyftas ut från if-elsen.
            // Nedan borde kunna ersättas med
            // if(file.exists())
            //    file.delete();
            // file.mkdirs();
            // Men som sagt, fundera på om man inte kan nöja sig med bara:
            // file.mkdirs();
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

    public RecyclerView getmRecycler() {
        return mRecycler;
    }

    public RecyclerView getpRecycler() {
        return pRecycler;
    }

    public TestAdapter getTestAdapter() {
        return testAdapter;
    }

    public PresetAdapter getPresetAdapter() {
        return presetAdapter;
    }

    public ToggleButton getToggle() {
        return toggle;
    }

    public StringBuilder getBuilder() {
        return builder;
    }
}


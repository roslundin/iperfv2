package com.example.iperfv2;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

public class ChartHandler {

    private Context context;
    private Activity activity;
    private LineChart chart;

    public ChartHandler(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;

        chart = (LineChart) activity.findViewById(R.id.chart1);
        chart.setBackgroundColor(Color.LTGRAY);
        chart.getDescription().setEnabled(false);
        chart.setNoDataText("Run an iPerf command to graph output");
    }

    public void addEntry(float down, int interval) {

        LineData data = chart.getData();
        chart.setData(data);

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);

            if (set == null) {
                set = createSet(1);
                data.addDataSet(set);
            }
            // jcomment:
            // down är en float redan; behöver inte castas
            data.addEntry(new Entry(set.getEntryCount() * interval, (float) down), 0);
            data.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.invalidate();
        }
    }

    public void addDualEntry(float up, float down, int interval) {

        LineData data = chart.getData();
        chart.setData(data);

        if (data != null) {

            ILineDataSet setDown = data.getDataSetByIndex(0);
            ILineDataSet setUp = data.getDataSetByIndex(1);

            if (setDown == null) {
                setDown = createSet(1);
                data.addDataSet(setDown);
            }
            if (setUp == null) {
                setUp = createSet(2);
                data.addDataSet(setUp);
            }

            // jcomment:
            // up och down är en float redan; behöver inte castas
            setDown.addEntry(new Entry(setDown.getEntryCount() * interval, (float) down));
            setUp.addEntry(new Entry(setUp.getEntryCount() * interval, (float) up));
            data.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.invalidate();

        }
    }

    public LineDataSet createSet(int setType) {
        LineDataSet set = null;
        switch(setType) {
            // jcomment:
            // Allt som är samma i de båda fallen kan lyftas ut så att den koden endast existerar
            // 1 gång OM man inte tror att man i framtiden kommer vilja ha olika värden beroende vilket
            // case som gäller.
            case 1:
                set = new LineDataSet(null, "Download");
                set.setAxisDependency(YAxis.AxisDependency.LEFT);   // samma för case 1 och 2
                set.setColor(Color.BLUE);
                set.setCircleColor(Color.WHITE);
                set.setLineWidth(2f);                               // samma för case 1 och 2
                set.setFillAlpha(65);                               // samma för case 1 och 2
                set.setDrawValues(false);                           // samma för case 1 och 2
                return set;                                         // samma för case 1 och 2 + kommer efter switchen
                                                                    // Ersätt med break; och låt return set; efter switchen göra jobbet.
            case 2:
                set = new LineDataSet(null, "Upload");
                set.setAxisDependency(YAxis.AxisDependency.LEFT);   // samma för case 1 och 2
                set.setColor(Color.RED);
                set.setCircleColor(Color.WHITE);
                set.setLineWidth(2f);                               // samma för case 1 och 2
                set.setFillAlpha(65);                               // samma för case 1 och 2
                set.setDrawValues(false);                           // samma för case 1 och 2
                return set;                                         // samma för case 1 och 2 + kommer efter switchen
                                                                    // Ersätt med break; och låt return set; efter switchen göra jobbet.
        }

        return set;
    }

    public LineChart getChart() {
        return chart;
    }
}

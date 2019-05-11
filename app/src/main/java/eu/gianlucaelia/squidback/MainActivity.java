package eu.gianlucaelia.squidback;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import static java.lang.Math.log;
import static java.lang.Math.log10;

public class MainActivity extends AppCompatActivity {

    private boolean playing = false;
    private int samplerate;
    private int buffersize;

    /*private float[] spectrumFloats;
    private float[] filterDecibels;*/
    private float[] spectrumFrequencies;
    private float[] filterFrequencies;

    private float maxGain;
    private int filterPrecision;

    private final Handler mHandler = new Handler();

    private Runnable mTimer1;
    private Runnable mTimer2;
    GraphView spectrumGraph;
    GraphView filterGraph;
    private LineGraphSeries<DataPoint> spectrumSeries;
    private LineGraphSeries<DataPoint> filterSeries;
    private LineGraphSeries<DataPoint> correctionSeries;
    private LineGraphSeries<DataPoint> gainSeries;
    private PointsGraphSeries<DataPoint> peakSeries;
    private float lastSpectrum[];

    int bgColor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        ((DrawerLayout) findViewById(R.id.drawer_layout)).setScrimColor(Color.TRANSPARENT);

        maxGain = 30;
        initGraphs();

        // Checking permissions.
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO
        };
        for (String s:permissions) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                // Some permissions are not granted, ask the user.
                ActivityCompat.requestPermissions(this, permissions, 0);
                return;
            }
        }

        // Got all permissions, initialize.
        initialize();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        // Called when the user answers to the permission dialogs.
        if ((requestCode != 0) || (grantResults.length < 1) || (grantResults.length != permissions.length)) return;
        boolean hasAllPermissions = true;

        for (int grantResult:grantResults) if (grantResult != PackageManager.PERMISSION_GRANTED) {
            hasAllPermissions = false;
            Toast.makeText(getApplicationContext(), "Please allow all permissions for the app.", Toast.LENGTH_LONG).show();
        }

        if (hasAllPermissions) initialize();
    }

    private void initialize() {
        // Get the device's sample rate and buffer size to enable
        // low-latency Android audio output, if available.
        String samplerateString = null, buffersizeString = null;
        if (Build.VERSION.SDK_INT >= 17) {
            AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                samplerateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                buffersizeString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            }
        }
        if (samplerateString == null) samplerateString = "48000";
        if (buffersizeString == null) buffersizeString = "480";
        samplerate = Integer.parseInt(samplerateString);
        buffersize = Integer.parseInt(buffersizeString);
        System.loadLibrary("FeedbackFilter");    // load native library

        Preferences prefs = new Preferences(this);
        loadPreset(prefs.getPreset());

    }

    private void initGraphs(){
        spectrumGraph = (GraphView) findViewById(R.id.spectrumGraph);
        filterGraph = (GraphView) findViewById(R.id.filterGraph);
        spectrumSeries = new LineGraphSeries<>(new DataPoint[]{});
        filterSeries = new LineGraphSeries<>(new DataPoint[]{});
        correctionSeries = new LineGraphSeries<>(new DataPoint[]{});
        gainSeries = new LineGraphSeries<>(new DataPoint[]{});
        peakSeries = new PointsGraphSeries<>(new DataPoint[]{});
        spectrumGraph.addSeries(spectrumSeries);
        //spectrumGraph.addSeries(peakSeries);
        filterGraph.addSeries(correctionSeries);
        filterGraph.addSeries(gainSeries);
        filterGraph.addSeries(filterSeries);


        spectrumGraph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);// It will remove the background grids
        spectrumGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);// remove horizontal x labels and line
        spectrumGraph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        filterGraph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);// It will remove the background grids
        filterGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);// remove horizontal x labels and line
        filterGraph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        filterGraph.setBackgroundColor(Color.BLACK);
        spectrumGraph.setBackgroundColor(Color.TRANSPARENT);

        spectrumSeries.setThickness(1);
        spectrumSeries.setDrawBackground(true);
        spectrumSeries.setBackgroundColor(Color.argb(120,255,255,255));

        filterSeries.setThickness(1);
        filterSeries.setColor(filterSeries.getBackgroundColor());
        filterSeries.setDrawBackground(true);

        correctionSeries.setThickness(0);
        correctionSeries.setBackgroundColor(Color.rgb(38,38,38));
        correctionSeries.setColor(Color.rgb(0,0,0));
        gainSeries.setThickness(1);
        gainSeries.setColor(gainSeries.getBackgroundColor());
        filterSeries.setDrawBackground(true);
        correctionSeries.setDrawBackground(true);
        gainSeries.setDrawBackground(true);


        peakSeries.setShape(PointsGraphSeries.Shape.POINT);
        peakSeries.setSize(3);
    }

    private void rescaleGraphs(){
        // init visualization
        spectrumFrequencies = getSpectrumFrequencies();
        lastSpectrum = new float[spectrumFrequencies.length];

        filterFrequencies = getFilterFrequencies();

        spectrumGraph.getViewport().setYAxisBoundsManual(true);
        spectrumGraph.getViewport().setMinY(-30);
        spectrumGraph.getViewport().setMaxY(30);
        rescaleFilterGraph();

        spectrumGraph.getViewport().setXAxisBoundsManual(true);
        spectrumGraph.getViewport().setMinX(log2(spectrumFrequencies[0]));
        spectrumGraph.getViewport().setMaxX(log2(spectrumFrequencies[spectrumFrequencies.length-1]));
        filterGraph.getViewport().setXAxisBoundsManual(true);
        if(filterFrequencies.length>0) {
            filterGraph.getViewport().setMinX(log2(filterFrequencies[0]));
            filterGraph.getViewport().setMaxX(log2(filterFrequencies[filterFrequencies.length - 1]));
        }

    }

    private void rescaleFilterGraph(){
        filterGraph.getViewport().setYAxisBoundsManual(true);
        //filterGraph.getViewport().setMinY(0);
        //filterGraph.getViewport().setMaxY(maxGain);
        filterGraph.getViewport().setMinY(0);
        filterGraph.getViewport().setMaxY(0+80);

        /*spectrumGraph.getViewport().setYAxisBoundsManual(true);
        spectrumGraph.getViewport().setMinY(-30);
        spectrumGraph.getViewport().setMaxY(30);*/
    }

    private void resetGraphs(){
        filterSeries.resetData(new DataPoint[]{});
        spectrumSeries.resetData(new DataPoint[]{});
        correctionSeries.resetData(new DataPoint[]{});
        gainSeries.resetData(new DataPoint[]{});

        ((Button) findViewById(R.id.startStop)).setEnabled(true);
        ((Button) findViewById(R.id.startStop)).setVisibility(View.VISIBLE);

    }

    private void initControls() {
        SeekBar sk = (SeekBar) findViewById(R.id.gainMaxBar);
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressVal;
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                maxGain = setMaxGain(progressVal/100.0f);
                rescaleFilterGraph();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
               progressVal = progress;
            }
        });

        sk = (SeekBar) findViewById(R.id.bwBar);
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
                filterPrecision = progress + 4;
                setFilterBw(filterPrecision);
                updatePrecisionText(filterPrecision);
                rescaleGraphs();
            }
        });

        sk = (SeekBar) findViewById(R.id.lopassBar);
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
                setLopass(progress/100.0f);
            }
        });

        sk = (SeekBar) findViewById(R.id.plasticityBar);
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
                setPlasticity(progress/100.0f);
            }
        });sk = (SeekBar) findViewById(R.id.inertiaBar);
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
                setInertia(progress/100.0f);
            }
        });

        sk = (SeekBar) findViewById(R.id.thrBar );
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
                setPeakThr(progress/100.0f);
            }
        });
        ((Switch) findViewById(R.id.memsetSwitch)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setMemsetGlitch(isChecked);
            }
        });
        ((Switch) findViewById(R.id.micOpen)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setMicOpen(isChecked);
            }
        });
    }

    private void updatePrecisionText(int progress) {
        ((TextView) findViewById(R.id.precisionLabel)).setText(
                getResources().getStringArray(R.array.filterPrecisions)[progress]
        );
    }

    public void setAllControls(){

        setLopass(((SeekBar) findViewById(R.id.lopassBar)).getProgress()/100.0f);
        setPlasticity(((SeekBar) findViewById(R.id.plasticityBar)).getProgress()/100.0f);
        setInertia(((SeekBar) findViewById(R.id.inertiaBar)).getProgress()/100.0f);
        setPeakThr(((SeekBar) findViewById(R.id.thrBar)).getProgress()/100.0f);
        filterPrecision = ((SeekBar) findViewById(R.id.bwBar)).getProgress() + 4;
        setFilterBw(filterPrecision);
        updatePrecisionText(filterPrecision);
        setMemsetGlitch(((Switch) findViewById(R.id.memsetSwitch)).isChecked());
        setMicOpen(((Switch) findViewById(R.id.micOpen)).isChecked());
        maxGain = setMaxGain(((SeekBar) findViewById(R.id.gainMaxBar)).getProgress()/100.0f);
        rescaleGraphs();

    }

    public void loadPreset(Preset preset){

        ((SeekBar) findViewById(R.id.lopassBar)).setProgress(preset.lopass);
        ((SeekBar) findViewById(R.id.plasticityBar)).setProgress(preset.plasticity);
        ((SeekBar) findViewById(R.id.inertiaBar)).setProgress(preset.inertia);
        ((SeekBar) findViewById(R.id.thrBar)).setProgress(preset.peakThr);
        ((SeekBar) findViewById(R.id.bwBar)).setProgress((int) (((SeekBar) findViewById(R.id.bwBar)).getMax() * preset.filterPrecision / 100));
        ((SeekBar) findViewById(R.id.gainMaxBar)).setProgress(preset.maxGain);
        ((Switch) findViewById(R.id.memsetSwitch)).setChecked(preset.memset);
        ((Switch) findViewById(R.id.micOpen)).setChecked(preset.micOpen);

    }


    public void stopControls(){

        ((SeekBar) findViewById(R.id.lopassBar)).setOnSeekBarChangeListener(null);
        ((SeekBar) findViewById(R.id.plasticityBar)).setOnSeekBarChangeListener(null);
        ((SeekBar) findViewById(R.id.inertiaBar)).setOnSeekBarChangeListener(null);
        ((SeekBar) findViewById(R.id.thrBar)).setOnSeekBarChangeListener(null);
        ((SeekBar) findViewById(R.id.bwBar)).setOnSeekBarChangeListener(null);
        ((SeekBar) findViewById(R.id.gainMaxBar)).setOnSeekBarChangeListener(null);
        ((Switch) findViewById(R.id.memsetSwitch)).setOnCheckedChangeListener(null);
        ((Switch) findViewById(R.id.micOpen)).setOnCheckedChangeListener(null);


    }

    // Handle Start/Stop button toggle.
    public void ToggleStartStop(View button) {
        Button b = findViewById(R.id.startStop);
        if (playing) {
            stopControls();
            //stopGraphPoll();
            StopAudio();
            playing = false;
            b.setEnabled(false);
            b.setVisibility(View.INVISIBLE);
        } else {
            filterPrecision = ((SeekBar) findViewById(R.id.bwBar)).getProgress() +4;
            StartAudio(samplerate, buffersize, filterPrecision );
            playing = true;
            initControls();
            setAllControls();
            initGraphPoll();

        }
        b.setText(playing ? "Stop" : "Start");
    }

    public void RandomFilter(View button){
        //if(playing){randomFilter();}
    }

    public void initGraphPoll(){
        mTimer1 = new Runnable() {
            @Override
            public void run() {
                updateSpectrumData();
                updateFilterData();
                mHandler.postDelayed(this, 100);
            }
        };
        /*mTimer2 = new Runnable() {
            @Override
            public void run() {
                //Log.i("updating","ctrl");
                updateFilterController();
                mHandler.postDelayed(this, 1000);
            }
        };*/
        mHandler.postDelayed(mTimer1, 100);
        //mHandler.postDelayed(mTimer2, 1000);

        filterGraph.setAlpha(1);
        spectrumGraph.setAlpha(1);
    }

    public void stopGraphPoll(){

        mHandler.removeCallbacks(mTimer1);
        //mHandler.removeCallbacks(mTimer2);

        resetGraphs();

    }

    private void updateSpectrumData() {
        if(!playing) {

            if(!isPlaying()){stopGraphPoll();return;}

            spectrumGraph.setAlpha(getFade());
        }
            float spectrum[] = getSpectrum();
            //int peakIndex = getPeakIndex();
            float testPeak = 0;
            int peakIndex = 0;
            int n_freqs = spectrum.length;
            DataPoint[] values = new DataPoint[n_freqs];
            for (int i = 0; i < n_freqs; i++) {
                DataPoint v = new DataPoint(log2(spectrumFrequencies[i]), (10 * log10(spectrum[i]) + 10 * log10(lastSpectrum[i])) / 2);
                lastSpectrum[i] = spectrum[i];
                if (spectrum[i] > testPeak) {
                    testPeak = spectrum[i];
                    peakIndex = i;
                }
                //Log.i("fr","" + spectrumFrequencies[i] + " "+spectrum[i]);
                values[i] = v;
            }
            DataPoint[] peak = new DataPoint[1];
            peak[0] = new DataPoint(log2(spectrumFrequencies[peakIndex]), 10 * log10(spectrum[peakIndex]));

            //Log.i("frPEAK","" + peakIndex + " "+spectrumFrequencies[peakIndex]);
            spectrumSeries.resetData(values);
            //peakSeries.resetData(peak);

            int newBgColor, invertedBgColor;
            newBgColor = pitchToHsv(spectrumFrequencies[peakIndex], spectrum[peakIndex]);
            bgColor = newBgColor;//mixColors(bgColor,newBgColor);
            invertedBgColor = invertColor(bgColor);
            filterGraph.setBackgroundColor(
                    Color.argb(0,
                            Color.red(bgColor),
                            Color.green(bgColor),
                            Color.blue(bgColor)
                    )

            );
            filterSeries.setBackgroundColor(Color.argb(200,
                    Color.red(bgColor),
                    Color.green(bgColor),
                    Color.blue(bgColor)
            ));
            filterSeries.setColor(bgColor);
            gainSeries.setBackgroundColor(bgColor);
            gainSeries.setColor(bgColor);


            //filterSeries.setColor(chooseBlackOrWhite(bgColor));
            //spectrumSeries.setBackgroundColor(invertedBgColor);
            //spectrumSeries.setColor(chooseBlackOrWhite(bgColor));
            spectrumSeries.setColor(Color.TRANSPARENT);

            //peakSeries.setColor(invertedBgColor);
            //Log.i("amp",""+spectrum[peakIndex]);


    }

    private float log2(float num){
        return (float)(log(num)/log(2));
    }

    private int pitchToHsv(float pitch,float amp){
        float hsv[] = new float[3];
        // cpsmidi: 12*log2(fm/440 Hz) + 69
        float midi = 12 * (float)(log(pitch/440)/log(2)) + 69;
        hsv[0] = midi%12/12 * 360;
        hsv[1] = 1.5f-(midi/120);
        hsv[2] = (float) (log(amp/0.005) / log(1/0.005));
        //explin(0.005,1,0,1):(log(this/inMin)) / (log(inMax/inMin)) * (outMax-outMin) + outMin
        //hsv[2] = (pow(1000, log(amp/0.005)/log(5000)) * 0.001f;
        //expexp: 		^pow(outMax/outMin, log(this/inMin) / log(inMax/inMin)) * outMin;
        return Color.HSVToColor(hsv);
    }

    private int chooseBlackOrWhite(int bg){
        if ((Color.red(bg)*0.299 + Color.green(bg)*0.587 + Color.blue(bg)*0.114) > 186) {
            return Color.BLACK;
        }else{
            return Color.WHITE;
        }
    }

    private int mixColors(int a,int b){
      return Color.rgb(
              Color.red(a)+Color.red(b)/2,
              Color.green(a)+Color.green(b)/2,
              Color.blue(a)+Color.blue(b)/2
              );
    }

    private int invertColor(int color){

        float hsv[] = new float[3];
        Color.colorToHSV(color,hsv);
        hsv[0] = hsv[0] + 180 % 360;
        //hsv[1] = 1;
        //hsv[2] = hsv[2] + 0.5f % 1.0f;
        return Color.HSVToColor(hsv);


    }

    private void updateFilterData() {
        if(!playing) {

            if(!isPlaying()){stopGraphPoll();return;}

            filterGraph.setAlpha(getFade());
        }
            float spectrum[] = getFilterDb();
            float corr[] = getCorrectionDb();
            int n_freqs = spectrum.length;
            float masterGain = getMasterGain();
            //Log.i("gain",""+masterGain);
            while (filterFrequencies.length != n_freqs) {
                filterFrequencies = getFilterFrequencies();
                rescaleGraphs();
            }
            DataPoint[] values = new DataPoint[n_freqs];
            DataPoint[] corrVal = new DataPoint[n_freqs];
            DataPoint[] gainVal = new DataPoint[n_freqs];

            //DataPoint[] corrVal = new DataPoint[n_freqs];
            for (int i = 0; i < n_freqs; i++) {
                double x = log2(filterFrequencies[i]);
                DataPoint v = new DataPoint(x, spectrum[i]+80);
                //Log.i("fr","" + spectrumFrequencies[i] + " "+spectrum[i]);
                values[i] = v;
                gainVal[i]= new DataPoint(x, masterGain/maxGain*(spectrum[i]+80));
                corrVal[i] = new DataPoint(x, corr[i]+80);
            }


            filterSeries.resetData(values);
            gainSeries.resetData(gainVal);
            correctionSeries.resetData(corrVal);



    }

    @Override
    public void onPause() {
        stopGraphPoll();
        super.onPause();
        if (playing) onBackground();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (playing) {
            onForeground();
            initGraphPoll();
        }
    }

    protected void onDestroy() {
        stopGraphPoll();
        super.onDestroy();
        if (playing) StopAudio();
    }

    // Functions implemented in the native library.
    private native void StartAudio(int samplerate, int buffersize, int filterPrec);
    private native void StopAudio();
    private native void updateFilterController();
    private native void onForeground();
    private native void onBackground();
    private native float[] getSpectrum();
    private native float[] getSpectrumFrequencies();
    private native float[] getFilterFrequencies();
    private native float[] getFilterDb();
    private native float[] getCorrectionDb();
    private native int getPeakIndex();
    private native float getMasterGain();
    private native float setMaxGain(float perc);
    private native void setLopass(float perc);
    private native void setPlasticity(float perc);
    private native void setInertia(float perc);
    private native void setPeakThr(float perc);
    private native void setFilterBw(int perc);
    private native void setMemsetGlitch(boolean sw);
    private native void setMicOpen(boolean sw);
    private native boolean isPlaying();
    private native float getFade();

    //private native void randomFilter();




}

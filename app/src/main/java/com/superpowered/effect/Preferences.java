package com.superpowered.effect;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

public class Preferences {

    public Preset defaultPreset;
    private SharedPreferences sharedPref;

    public Preferences(AppCompatActivity activity){

        sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
        defaultPreset = new Preset();
    }

    public Preset getPreset(){
        return defaultPreset;
    }

}

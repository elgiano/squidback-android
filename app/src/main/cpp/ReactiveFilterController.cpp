
//
//  ReactiveFilterController.cpp
//  squidback
//
//  Created by Gnlc Elia on 28/11/2018.
//  Copyright © 2018 imect. All rights reserved.
//

#include <android/log.h>
#include "ReactiveFilterController.h"
#include "ReactiveFilter.h"

const int ReactiveFilterController::MaxTrackedValues = 1000;

void ReactiveFilterController::trackValue(std::string name,float value){
    if(std::find(trackingKeys.begin(), trackingKeys.end(), name) == trackingKeys.end()){
        trackingKeys.push_back(name);
    }
    trackings[name].push_back(value);

    if(trackings[name].size()>ReactiveFilterController::MaxTrackedValues){
        trackings[name].erase(trackings[name].begin());
    }

}

float ReactiveFilterController::average(std::string name, int n_values){
    if(n_values==0 || n_values > trackings[name].size()) n_values = trackings[name].size();

    float sum = std::accumulate(trackings[name].rbegin(),trackings[name].rbegin()+n_values,0.0f);

    return sum/(float)n_values;
};

std::map<float,int> ReactiveFilterController::count(std::string name, int n_values){
    if(n_values==0 || n_values > trackings[name].size()) n_values = trackings[name].size();

    std::map<float,int> counts;
    std::for_each(trackings[name].rbegin(),trackings[name].rbegin()+n_values, [&counts](float &n){
        counts[n]++;
    });

    return counts;
};


void ReactiveFilterController::printAll(){
    // t = 11ms, 10t = 0.11s, 100t = 1.1s, 1000t = 11s
    for(auto& k: trackingKeys){
        //std::cout << k << ": " << average(k,10) << "/" << average(k,100) << "/" << average(k) << "\t";
        __android_log_print(ANDROID_LOG_INFO,"ctrl","%s: %f/%f/%f",k.c_str(),average(k,10),average(k,100),average(k));
    }
    //std::cout << std::endl;
}


void ReactiveFilterController::adjustControls(){

    float avg = average("averageDb",10);
    float avgT = average("averageDb");

    float peak = average("peakDb",100);
    float peakT = average("peakDb");
    float peakness = average("peakness",100);
    float peakThr = target->peakThreshold;
    float inVolDb = average("inVolDb",100);
    float outVolDb = average("outVolDb",100);
    float outVolDbAvg = average("outVolDb");

    float limiterCorrection = average("limiterCorrection",10);
    std::map<float,int> peaks = count("peakI");
    std::map<float,int> recentPeaks = count("peakI",100);


    // raise gain to push out to -10
    float gainMaximize = ((outVolDb+6) + (outVolDbAvg+6)/2) * -0.001;
    // adjust gain to avoid saturation
    float gainLimiter = (limiterCorrection * 4 - 1) ;
    gainLimiter = pow(gainLimiter,5)* 0.1;
    //std::cout << gainMaximize << "\t" << gainLimiter << std::endl;

    float inGain = (inVolDb-outVolDb) * 0.001;
    if(inGain<0) inGain = 0;

    target->masterGain += gainMaximize +inGain;//+ gainLimiter;


    float peakExcess = (peak-peakThr + 1) * -1;
    float dPeak = peak/peakT;
    //__android_log_print(ANDROID_LOG_INFO,"peak","%f %f",peakExcess,dPeak);


    target->peakThreshold = avgT * 0.25;
    //std::cout << peakThr << std::endl;
    //__android_log_print(ANDROID_LOG_INFO,"peakThr","%f %f",peakness,target->peakThreshold);


    //target->plasticity = (pow(2,1-(peakness/30))-1)*10;
    //target->plasticity = (pow(2,1-(peakness/30))-1)*10;

    //target->plasticity = pow(10,peakExcess/20)*500;

    //std::cout << peakExcess << std::endl;
    //std::cout << peakExcess << "\t" << target->plasticity << std::endl;


    auto most_persistent_peak = std::max_element(recentPeaks.begin(), recentPeaks.end(),
                                                 [](const std::pair<float, int>& p1, const std::pair<float, int>& p2) {
                                                     return p1.second < p2.second; });

    peakRegister[target->getBand(most_persistent_peak->first)] += 0.0025;


    /*for(auto& p: peaks){
        target->incrementBand(p.first,p.second/(-500));
    }*/


}

std::vector<float> ReactiveFilterController::getPersistentPeakCorrection(){
    std::vector<float> correction;
    int numBands = target->getNumBands();
    float *bands = target->getBands();
    for(int i=0;i<numBands;i++){
        correction.push_back(target->masterGain-peakRegister[bands[i]]);
    }

    return correction;
}

std::vector<float> ReactiveFilterController::getPersistentPeakCorrectionNoGain(){
    std::vector<float> correction;
    int numBands = target->getNumBands();
    float *bands = target->getBands();
    for(int i=0;i<numBands;i++){
        correction.push_back(-peakRegister[bands[i]]);
    }

    return correction;
}



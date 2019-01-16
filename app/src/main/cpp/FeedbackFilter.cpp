#include <jni.h>
#include <string>
#include <AndroidIO/SuperpoweredAndroidAudioIO.h>
#include <SuperpoweredLimiter.h>
//#include <SuperpoweredBandpassFilterbank.h>
//#include <SuperpoweredNBandEQ.cpp>

#include <android/log.h>

#include <SuperpoweredSimple.h>
#include <malloc.h>
#include <algorithm>    // std::min_element, std::max_element
#include <string.h>
#include <time.h>
#include <math.h>
#include "scales.h"
#include "SpectrumVisualizer.cpp"
#include "ReactiveFilter.h"
#include "ReactiveFilterController.h"

static SuperpoweredAndroidAudioIO *audioIO;
static float *floatBuffer;
static bool micOpen=true;
static bool fadeIn=true;
static bool fadeOut=false;
static float fade=0;
static bool playing=false;

static ReactiveFilter *reactiveFilter;


/* UTILS */

/*static float ampdb(float amp){
    return 10*(float)log10(amp);
}
static float dbAmp(float amp){
    return (float) pow(10,amp/10);
}*/
/*
static void randomFilter(){
    for(unsigned int i = 0; i<analNumBands;i++){
        float randGain = (rand()%101)/100.0f*(-maxGain);
        filter->setBand(i,randGain);
    }
}
 */


static void initAudio(int samplerate, int buffersize,int filterPrec){
    // allocate audio buffer
    floatBuffer = (float *)malloc(sizeof(float) * 2 * buffersize);

    initVisualizer(12,samplerate);

    reactiveFilter = new ReactiveFilter((unsigned) samplerate);
    reactiveFilter->setFilterBands(filterPrecisions[filterPrec]);
    reactiveFilter->enable(true);

}

// SET FILTER RT
/*static void updateFilterCont () {
    float newGain;
    float average = 0;
    currentPeakIndex = std::distance(analMagnitudes, std::max_element(analMagnitudes, analMagnitudes+analNumBands));
    for(int i=0;i<analNumBands;i++){average+=analMagnitudes[i];};
    average = average / analNumBands;

    //__android_log_print(ANDROID_LOG_INFO, "peak", "peak: %f", analBands[currentPeakIndex]);

    for(unsigned int i=0; i<analNumBands; i++){
        newGain = -gainIncrement/2 * (analMagnitudes[i]/analMagnitudes[currentPeakIndex]);
        newGain = (filter->decibels[i]*100 + newGain)/101;
      filter->setBand(i, newGain);
    };

}*/

/*
static void updateFilter () {
    float newGain;
    float average = 0;
    currentPeakIndex = (unsigned int) std::distance(analMagnitudes, std::max_element(analMagnitudes, analMagnitudes+analNumBands));
    for(int i=0;i<analNumBands;i++){
        average+=analMagnitudes[i];
    };
    average = average / analNumBands;

    for(unsigned int i=0; i<analNumBands; i++){
        if(i==currentPeakIndex){
            if(ampdb(analMagnitudes[currentPeakIndex])/*-ampdb(average)*//* >= peakThreshold) {
                newGain = filter->decibels[i] - gainDecrement;
            }
        }else{
            newGain = filter->decibels[i]+gainIncrement;
        };
        newGain = (filter->decibels[i]*filterPlasticity + newGain)/(filterPlasticity+1);
        if(newGain>maxGain){newGain=maxGain;}
        filter->setBand(i, newGain);
    };

}


static void updateFilterAvgPeak () {

    float newGain,averageDb=0;

    currentPeakIndex = (unsigned int) std::distance(analMagnitudes, std::max_element(analMagnitudes, analMagnitudes+analNumBands));
    for(unsigned int i=0; i<analNumBands; i++){
        averageDb += analMagnitudes[i];
    };
    averageDb = ampdb(averageDb/analNumBands);

    for(unsigned int i=0; i<analNumBands; i++){

        float magDb = ampdb(analMagnitudes[i]);
        float peakDelta = peakThreshold - magDb;
        float avgDelta = averageDb - magDb;

        newGain = filter->decibels[i] + (float) (sign(peakDelta+avgDelta)*sqrt(pow(peakDelta,2)+pow(avgDelta,2))/filterPlasticity) ;

        if(abs(newGain)>maxGain){newGain = maxGain * sign(newGain);}

        if(analBands[i]>=lopass){
            newGain = -maxGain*2;
        }

        filter->setBand(i, newGain);


    };


}*/
/*

static bool abs_compare
    (const float& a, const float& b)
    {
        return fabs(a) < fabs(b);
    }


static void correctExpectations(){
    for (unsigned int i = 0; i < analNumBands; i++) {

        levelExpectations[i] = ampdb(levelExpectations[i]);
        levelExpectations[i] -= ampdb(analMagnitudes[i]);
    }


    float largest_abs = (float) fabs(*std::max_element(levelExpectations, levelExpectations+analNumBands, abs_compare));

    for (unsigned int i = 0; i < analNumBands; i++) {
        levelExpectations[i] = levelExpectations[i]/largest_abs;

    }

}

static void storeExpectations(int numberOfFrames) {
    float peak,sum;
    analFilterbank->processNoAdd(floatBuffer,levelExpectations,&peak,&sum,(unsigned int)numberOfFrames);
}
*/

//static void filterWithCorrections(float *floatBuffer,int numberOfFrames){
    /*for (unsigned int i = 0; i < analNumBands; i++) {
        float corrGain = levelExpectations[i];
        if(isnan(corrGain)){corrGain = 0;}
        correctionFilter->setBand(i,corrGain);
    }*/

    //filter->process(floatBuffer,floatBuffer,(unsigned int) numberOfFrames);
    //correctionFilter->process(floatBuffer,floatBuffer,(unsigned int) numberOfFrames);

//}

/*
// ANAL chain
static void analProcessing (
        int numberOfFrames       // number of frames to process
) {
    // anal here: get bands magnitude
    float peak,sum;
    analFilterbank->processNoAdd(floatBuffer,analMagnitudes,&peak,&sum,(unsigned int)numberOfFrames);
    //correctExpectations();
    //__android_log_print(ANDROID_LOG_INFO, "sum", "sum: %f", sum);
    //updateFilter();
    updateFilterAvgPeakNoGain(peak);


}
*/

// This is called periodically by the audio engine.
static bool audioProcessing (
        void * __unused clientdata, // custom pointer
        short int *audio,           // buffer of interleaved samples
        int numberOfFrames,         // number of frames to process
        int __unused samplerate     // sampling rate

) {

    if(micOpen){
        SuperpoweredShortIntToFloat(audio, floatBuffer, (unsigned int)numberOfFrames);
    }
    //visual
    //SuperpoweredVolumeAdd(floatBuffer,floatBuffer,dbAmp(reactiveFilter->masterGain),dbAmp(reactiveFilter->masterGain), (unsigned int)numberOfFrames);

    processVisualizer(floatBuffer, (unsigned int) numberOfFrames);

    reactiveFilter->process(floatBuffer, floatBuffer, (unsigned int) numberOfFrames);

    // limit and out
    //limiter->process(floatBuffer, floatBuffer, (unsigned int)numberOfFrames);

    //storeExpectations(numberOfFrames);
    // fade
    if(fadeIn || fadeOut){
        float oldFade = fade;
        if(fadeIn){
             fade += 0.005;
            if( fade>=1){
                 fade = 1;
                 fadeIn = false;
            }
        }else if( fadeOut){
             fade -= 0.001;
            if( fade<=0){
                 fade = 0;
                // fadeOut = false;
                audioIO->stop();
                destroyVisualizer();
                delete  reactiveFilter;
                free(floatBuffer);
                playing = false;
                return false;
            }

        }

        SuperpoweredVolume(floatBuffer,floatBuffer,oldFade, fade,(unsigned int)numberOfFrames);

    }

    SuperpoweredFloatToShortInt(floatBuffer, audio, (unsigned int)numberOfFrames);


    return true;
}



// StartAudio - Start audio engine.
extern "C" JNIEXPORT void
Java_eu_gianlucaelia_squidback_MainActivity_StartAudio (
        JNIEnv * __unused env,
        jobject  __unused obj,
        jint samplerate,
        jint buffersize,
        jint filterPrec
) {
    initAudio(samplerate,buffersize,filterPrec);

    fadeIn = true;
    fadeOut = false;

    // init audio with audio callback function
    audioIO = new SuperpoweredAndroidAudioIO (
            samplerate,                     // sampling rate
            buffersize,                     // buffer size
            true,                           // enableInput
            true,                           // enableOutput
            audioProcessing,                // process callback function
            NULL,                           // clientData
            -1,                             // inputStreamType (-1 = default)
            -1,                             // outputStreamType (-1 = default)
            buffersize * 2                  // latencySamples
    );

    playing = true;

}

// StopAudio - Stop audio engine and free resources.
extern "C" JNIEXPORT void
Java_eu_gianlucaelia_squidback_MainActivity_StopAudio (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    /*delete audioIO;
    destroyVisualizer();
    delete reactiveFilter;
    free(floatBuffer);*/
    fadeOut = true;
    fadeIn = false;

}

// onBackground - Put audio processing to sleep.
extern "C" JNIEXPORT void
Java_eu_gianlucaelia_squidback_MainActivity_onBackground (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    audioIO->onBackground();

}

// onForeground - Resume audio processing.
extern "C" JNIEXPORT void
Java_eu_gianlucaelia_squidback_MainActivity_onForeground (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    audioIO->onForeground();

}


extern "C"
JNIEXPORT jfloatArray JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_getFilterFrequencies(JNIEnv *env, jobject instance) {

    int analNumBands = reactiveFilter->getNumBands();
    jfloatArray jArray = env->NewFloatArray(analNumBands);


    if (jArray != NULL )
    {
        env->SetFloatArrayRegion(jArray, 0, analNumBands, reactiveFilter->getBands());

    }
    return jArray;


}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_getFilterDb(JNIEnv *env, jobject instance) {

    int analNumBands = reactiveFilter->getNumBands();
    jfloatArray jArray = env->NewFloatArray(analNumBands);


    if (jArray != NULL )
    {

        env->SetFloatArrayRegion(jArray, 0, analNumBands, reactiveFilter->getFilterDbs(false));
    }
    return jArray;

}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_getCorrectionDb(JNIEnv *env, jobject instance) {

    int analNumBands = reactiveFilter->getNumBands();
    jfloat temp[analNumBands];
    jfloatArray jArray = env->NewFloatArray(analNumBands);
    std::vector<float> correction = reactiveFilter->controller->getPersistentPeakCorrectionNoGain();
    std::copy(correction.begin(),correction.end(),temp);

    if (jArray != NULL )
    {

        env->SetFloatArrayRegion(jArray, 0, analNumBands, temp);
    }
    return jArray;

}

/*
extern "C"
JNIEXPORT jfloatArray JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_getCorrectionDb(JNIEnv *env, jobject instance) {
    int analNumBands = reactiveFilter->getNumBands();
    jfloatArray jArray = env->NewFloatArray(analNumBands);

    if (jArray != NULL )
    {
        env->SetFloatArrayRegion(jArray, 0, analNumBands, correctionFilter->decibels);
    }
    return jArray;

}*/


extern "C"
JNIEXPORT jint JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_getPeakIndex(JNIEnv *env, jobject instance) {

    return (jint) reactiveFilter->currentPeakIndex;

}

extern "C"
JNIEXPORT jfloat JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_getMasterGain(JNIEnv *env, jobject instance) {

    if(reactiveFilter){
        return reactiveFilter->masterGain;
    }else{
        return 0;
    }

}

extern "C"
JNIEXPORT jfloat JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_setMaxGain(JNIEnv *env, jobject instance, jfloat perc) {

    if(reactiveFilter){
        reactiveFilter->maxGain = perc *  98 + 2;
        reactiveFilter->adjustLopass(-1, true);
        return (jfloat) reactiveFilter->maxGain;
    }else{
        return 0;
    }

}
extern "C"
JNIEXPORT void JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_setPlasticity(JNIEnv *env, jobject instance,
                                                        jfloat perc) {

    if(reactiveFilter) reactiveFilter->plasticity = (float) pow(10000000,perc)*0.01f;
    // linexp: pow(outMax/outMin, (this-inMin)/(inMax-inMin)) * outMin


}
extern "C"
JNIEXPORT void JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_setInertia(JNIEnv *env, jobject instance, jfloat perc) {

    if(reactiveFilter) reactiveFilter->inertia = (float) pow(1/0.001,1-perc)*0.001f -0.001f;
    // linexp: pow(outMax/outMin, (this-inMin)/(inMax-inMin)) * outMin



}
extern "C"
JNIEXPORT void JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_setPeakThr(JNIEnv *env, jobject instance, jfloat perc) {

    if(reactiveFilter) reactiveFilter->peakThreshold = (1-perc)*(-40)+10;


}extern "C"
JNIEXPORT void JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_setFilterBw(JNIEnv *env, jobject instance, jint prec) {

    // int prec must go from 0 to 11
    if(prec >= 0 && prec< 12 ){
        if(reactiveFilter) reactiveFilter->setFilterBands(filterPrecisions[prec]);
    }

}extern "C"
JNIEXPORT void JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_setLopass(JNIEnv *env, jobject instance, jfloat perc) {

    //perc = 1-perc;
    reactiveFilter->adjustLopass( (float) pow(22000/10000,perc)*10000, true);

    // linexp: pow(outMax/outMin, (this-inMin)/(inMax-inMin)) * outMin

}


/* visual */

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_getSpectrum(JNIEnv *env, jobject instance) {

    //jfloatArray jArray = env->NewFloatArray(analNumBands);

    jfloatArray jArray = env->NewFloatArray(numVisualBands);

    float *visualMagCopy = getVisualSpectrum();

    if (jArray != NULL)
    {
        /*env->SetFloatArrayRegion(jArray, 0, analNumBands, analMagnitudes);*/


        env->SetFloatArrayRegion(jArray, 0, numVisualBands, visualMagCopy);


    }
    return jArray;


}
extern "C"
JNIEXPORT jfloatArray JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_getSpectrumFrequencies(JNIEnv *env, jobject instance) {

    //jfloatArray jArray = env->NewFloatArray(analNumBands);
    jfloatArray jArray = env->NewFloatArray(numVisualBands);


    if (jArray != NULL)
    {
        //env->SetFloatArrayRegion(jArray, 0, analNumBands, analBands);
        env->SetFloatArrayRegion(jArray, 0, numVisualBands, visualBands);

    }
    return jArray;

}
extern "C"
JNIEXPORT void JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_setMemsetGlitch(JNIEnv *env, jobject instance,
                                                            jboolean sw) {
    if(reactiveFilter) reactiveFilter->memsetGlitch = sw;

}

extern "C"
JNIEXPORT void JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_setMicOpen(JNIEnv *env, jobject instance,
                                                            jboolean sw) {
    if(reactiveFilter) micOpen = sw;

}

/*
extern "C"
JNIEXPORT void JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_randomFilter(JNIEnv *env, jobject instance) {

    randomFilter();

}*/extern "C"
JNIEXPORT void JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_updateFilterController(JNIEnv *env, jobject instance) {

    if(reactiveFilter &&  reactiveFilter->controller) reactiveFilter->controller->printAll();

}extern "C"
JNIEXPORT jboolean JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_isPlaying(JNIEnv *env, jobject instance) {

    return playing;


}extern "C"
JNIEXPORT jfloat JNICALL
Java_eu_gianlucaelia_squidback_MainActivity_getFade(JNIEnv *env, jobject instance) {

    return fade;

}
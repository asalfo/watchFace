/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mTempPaint;
        Paint mDatePaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        // xOffset is an Integer as it will be calculated based on the width and the room needed to
        // draw the time
        Float mXOffset;
        String mTimeFormat;

        float mMarginRatio;

        // Weather related stuff
        String mLowTemp;
        String mHighTemp;
        int mIconResource;
        String mDateToDisplay;
        Typeface mRobotoLight;

        int mHeight;
        int mWidth;
        float mXmargin;


        private GoogleApiClient mGoogleApiClient;

        private static final String TAG = "SWFListenerService";

        private static final String WEATHER_FORECAST_PATH = "/Weather/Forecast";
        private static final String WEATHER_ID_KEY = "WEATHER_ID";
        private static final String WEATHER_TEMP_LOW_KEY = "WEATHER_TEMP_LOW";
        private static final String WEATHER_TEMP_HIGH_KEY = "WEATHER_TEMP_HIGH";

        private static final String WEATHER_FORECAST_UPDATE_PATH = "/Weather/Update";
        private static final String WEATHER_FORECAST_UPDATE_KEY = "UPDATE_KEY";


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;


        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mHeight = height;
            mWidth = width;
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mXmargin = resources.getDimension(R.dimen.digital_x_margin);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTempPaint = createTextPaint(resources.getColor(R.color.digital_dark_text));
            mTempPaint.setTextSize(resources.getDimension(R.dimen.digital_temp_text_size));

            mDatePaint = createTextPaint(resources.getColor(R.color.digital_dark_text));
            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mRobotoLight = Typeface.create("sans-serif-light",Typeface.NORMAL);
            mDatePaint.setTypeface(mRobotoLight);

            mTime = new Time();

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            Log.d(TAG,"Listener added");

            mTimeFormat = getString(R.string.time_format);



            SharedPreferences sp = getSharedPreferences();
            mLowTemp = sp.getString(getString(R.string.pref_low_temp),"");
            mHighTemp = sp.getString(getString(R.string.pref_high_temp),"");
            mIconResource = sp.getInt(getString(R.string.pref_icon_resource), -1);


            // Default
            mMarginRatio = getFloat(R.dimen.digital_margin_round_ratio);


            askForWeatherUpdate();

            registerReceiver();
        }

        private void askForWeatherUpdate() {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_FORECAST_UPDATE_PATH);
            Log.d(TAG, "askForWeatherUpdate: asking for update");
            // Data that is different to make sure it get pushed
            putDataMapRequest.getDataMap().putLong(WEATHER_FORECAST_UPDATE_KEY,
                    System.currentTimeMillis());
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataMapRequest.asPutDataRequest());
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);

            SharedPreferences.Editor editor = getSharedPreferences().edit();
            editor.putString(getString(R.string.pref_low_temp), mLowTemp);
            editor.putString(getString(R.string.pref_high_temp),mHighTemp);
            editor.putInt(getString(R.string.pref_icon_resource), mIconResource);
            editor.commit();

            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float textSize;
            if (isRound){
                textSize = resources.getDimension(R.dimen.digital_text_size_round);
                mMarginRatio = getFloat(R.dimen.digital_margin_round_ratio);
            } else {
                textSize = resources.getDimension( R.dimen.digital_text_size);
                mMarginRatio = getFloat(R.dimen.digital_margin_square_ratio);
            }

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            float yMargin = mHeight*mMarginRatio;

            // Draw HH:MM
            mTime.setToNow();
            String format = String.format(mTimeFormat, mTime.hour, mTime.minute);

            if (mXOffset == null){
                mXOffset = getXToDrawTextCentered("00:00",mWidth / 2, mTextPaint);
            }

            canvas.drawText(format, mXOffset, (mHeight * 2 / 5) - yMargin, mTextPaint);

            if (!isInAmbientMode()){
                // Let's handle the date
                mDateToDisplay =  new SimpleDateFormat("EEE, MMM dd yyyy").format(new Date());

                float centeredX = mWidth /2;
                float x = getXToDrawTextCentered(mDateToDisplay,centeredX, mDatePaint);
                float y = mHeight*3/5 - yMargin;

                canvas.drawText(mDateToDisplay,x,y,mDatePaint);



                // Draw line of 1/5 of the X screen at the 3/5 Y of the screen
                float startX = mWidth*2/5;
                float stopX = startX + (mWidth/5);

                float startY = mHeight*3/5;
                float stopY = startY;
                canvas.drawLine(startX, startY, stopX, stopY, mTextPaint);


                // Draw highTemp bottom mid
                x = getXToDrawTextCentered(mHighTemp,centeredX, mTempPaint);


                Rect textBounds = new Rect();
                mTempPaint.getTextBounds(mHighTemp, 0, mHighTemp.length(), textBounds);

                float textHeight = textBounds.height();
                y = Math.round(startY) + yMargin + textHeight;

                canvas.drawText(mHighTemp, x, y, mTempPaint);


                // Draw lowTemp bottom right (Y is the same)

                // First let's put it in Roboto light (high temp is done)
                mTempPaint.setTypeface(mRobotoLight);
                centeredX = mWidth * 3/4;
                x = getXToDrawTextCentered(mLowTemp,centeredX, mTempPaint) + mXmargin;

                textBounds = new Rect();
                mTempPaint.getTextBounds(mLowTemp, 0, mLowTemp.length(), textBounds);

                canvas.drawText(mLowTemp,x,y,mTempPaint);


                // Draw icon bottom left
                if (mIconResource > 0){
                    Bitmap icon = BitmapFactory.decodeResource(getApplicationContext().getResources(),
                            mIconResource);

                    Drawable drawable = getDrawable(mIconResource);
                    float drawableWidth = drawable.getIntrinsicWidth();
                    float drawableHeight = drawable.getIntrinsicHeight();


                    centeredX = mWidth/4;
                    x = centeredX - (drawableWidth/2) - mXmargin;

                    // Our y (which is the top margin) has to be:
                    // The position of the latest Y -
                    // Half the image height - half the letter height
                    y = y - (textHeight/2) - (drawableHeight/2);

                    canvas.drawBitmap(icon, x, y, new Paint());
                }

            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED){
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();

                    Log.d(TAG, "onDataChanged" + path);
                    if (path.equals(WEATHER_FORECAST_PATH)){
                        // Get weather data
                        mLowTemp = dataMap.getString(WEATHER_TEMP_LOW_KEY, "");
                        mHighTemp = dataMap.getString(WEATHER_TEMP_HIGH_KEY, "");
                        mIconResource = Utils.getIconResourceForWeatherCondition(
                                dataMap.getInt(WEATHER_ID_KEY, 0));

                        Log.d(TAG, "Weather update received min =" + mLowTemp + ", max=" + mHighTemp
                                + ", WeatherId=" + mIconResource);
                    }
                }
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "onConnectionSuspended: ");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(TAG, "onConnectionFailed: " + connectionResult.getErrorMessage());
        }


        // Takes the text to draw and the X we want the text to be centered on.
        // Returns the position we have to draw on
        private float getXToDrawTextCentered(String textToDraw, float centeredX, Paint paint){
            float textSize = paint.measureText(textToDraw);
            return centeredX - (textSize / 2);
        }

        private SharedPreferences getSharedPreferences(){
            return getApplicationContext().getSharedPreferences(
                    getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        }

        private float getFloat(int resourceId){
            TypedValue outValue = new TypedValue();
            getResources().getValue(resourceId, outValue, true);
            return outValue.getFloat();
        }
    }
}

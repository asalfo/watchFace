package com.example.android.sunshine.app.wear;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by Mathieu on 2016-04-04.
 */
public class WearDataExchangeHandler implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ResultCallback<DataApi.DataItemResult>
{

    private GoogleApiClient mGoogleApiClient;
    private Context mContext;

    private static final String[] WEAR_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    private static final String WEATHER_FORECAST_PATH = "/Weather/Forecast";
    private static final String WEATHER_ID_KEY = "WEATHER_ID";
    private static final String WEATHER_TEMP_LOW_KEY = "WEATHER_TEMP_LOW";
    private static final String WEATHER_TEMP_HIGH_KEY = "WEATHER_TEMP_HIGH";
    private static final String WEATHER_FORCE_UPDATE_KEY = "WEATHER_FORCE_UPDATE";
    private static final String TAG = "WearDataExchangeHandler";

    public WearDataExchangeHandler(Context context) {
        mContext = context;
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();
    }

    public void pushWeatherUpdate(boolean forceUpdate) {
        String locationQuery = Utility.getPreferredLocation(mContext);

        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        // Get cursor for today and the preferred location
        Cursor cursor = mContext.getContentResolver().query(weatherUri, WEAR_WEATHER_PROJECTION, null, null, null);

        if (cursor.moveToFirst()) {
            int weatherId = cursor.getInt(INDEX_WEATHER_ID);
            double high = cursor.getDouble(INDEX_MAX_TEMP);
            double low = cursor.getDouble(INDEX_MIN_TEMP);

            String formattedLow = Utility.formatTemperature(mContext, low);
            String formattedHigh = Utility.formatTemperature(mContext, high);

            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_FORECAST_PATH);

            putDataMapRequest.getDataMap().putInt(WEATHER_ID_KEY, weatherId);
            putDataMapRequest.getDataMap().putString(WEATHER_TEMP_LOW_KEY, formattedLow);
            putDataMapRequest.getDataMap().putString(WEATHER_TEMP_HIGH_KEY, formattedHigh);

            // Let's verify if we have to force the  update (even if the latest data sent is the same,
            // we want the DataApi to resend it)
            if (forceUpdate){
                Log.d(TAG, "pushWeatherUpdate: forcing update");
                putDataMapRequest.getDataMap().putLong(WEATHER_FORCE_UPDATE_KEY,
                        System.currentTimeMillis());

            }
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataMapRequest.asPutDataRequest())
                    .setResultCallback(this);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected: ");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended: ");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: " + connectionResult.getErrorMessage());
    }

    @Override
    public void onResult(DataApi.DataItemResult dataItemResult) {
        if (dataItemResult.getStatus().isSuccess()) {
            Log.d(TAG, "PutDataItem Success!");
        } else {
            Log.d(TAG, "PutDataItem Failure :" + dataItemResult.getStatus().getStatusMessage());
        }
    }
}

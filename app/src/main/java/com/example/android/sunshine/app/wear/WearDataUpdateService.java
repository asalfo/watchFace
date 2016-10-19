package com.example.android.sunshine.app.wear;

import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

public class WearDataUpdateService extends WearableListenerService {
    private static final String TAG = "WearDataUpdateService";
    private static final String WEATHER_FORECAST_UPDATE_PATH = "/Weather/Update";




    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.d(TAG, "onDataChanged");
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED){
                String path = dataEvent.getDataItem().getUri().getPath();
                if (path.equals(WEATHER_FORECAST_UPDATE_PATH)){
                    Log.d(TAG, "onDataChanged: Requesting sync");
                    WearDataExchangeHandler wearDataExchangeHandler =
                            new WearDataExchangeHandler(this);

                    // Refresh with fresh data (force update)
                    wearDataExchangeHandler.pushWeatherUpdate(true);
                }
            }
        }
    }
}

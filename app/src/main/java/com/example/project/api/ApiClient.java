package com.example.project.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String TAG = "ApiClient";
    private static final String DEFAULT_URL = "http://192.168.0.102:5000";
    private static final String PREFS_NAME = "ApiPrefs";
    private static final String BASE_URL_KEY = "base_url";

    private static Retrofit retrofit = null;

    public static void resetClient() {
        retrofit = null;
    }

    public static String getBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(BASE_URL_KEY, DEFAULT_URL);
    }

    public static void setBaseUrl(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(BASE_URL_KEY, url).apply();
        resetClient();
    }

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            String baseUrl = getBaseUrl(context);

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
    public static CelestialApiService getCelestialApiService(Context context) {
        return getClient(context).create(CelestialApiService.class);
    }

}
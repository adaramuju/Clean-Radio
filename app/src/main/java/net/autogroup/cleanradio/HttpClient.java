package net.autogroup.cleanradio;


import okhttp3.OkHttpClient;

public class HttpClient {
    private static final OkHttpClient clientInstance = new OkHttpClient();

    public static OkHttpClient getInstance() {
        return clientInstance;
    }

    private HttpClient() {
    }
}

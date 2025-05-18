package com.example.smartdatacapturesystem;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface BackendApi {
    @POST("uploadImage")
    @Streaming
    Call<ResponseBody> uploadImage(
            @Body RequestBody image,
            @Query("ts") String timestamp,
            @Query("loc") String location,
            @Query("file_type") String filetype
    );

    @GET("ping")
    Call<ResponseBody> ping();
}

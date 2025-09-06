package com.TDavis.foodie_macrotracker.net;

import com.TDavis.foodie_macrotracker.net.models.FoodSearchResponse;
import com.TDavis.foodie_macrotracker.net.models.BarcodeResult;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface UsdaProxyService {
    @GET("api/foods/search")
    Call<FoodSearchResponse> searchFoods(
            @Query("q") String query,
            @Query("pageSize") Integer pageSize,
            @Query("pageNumber") Integer pageNumber
    );

    @GET("api/barcode/{code}")
    Call<BarcodeResult> getByBarcode(@Path("code") String code);
}

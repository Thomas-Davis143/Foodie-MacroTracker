package com.TDavis.foodie_macrotracker.net;

import com.TDavis.foodie_macrotracker.net.models.FoodSearchResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface UsdaProxyService {
    @GET("api/foods/search")
    Call<FoodSearchResponse> searchFoods(
            @Query("q") String query,
            @Query("pageSize") Integer pageSize,
            @Query("pageNumber") Integer pageNumber
    );
}

package com.TDavis.foodie_macrotracker.net;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * USDA/Open Food Facts proxy API — normalized responses.
 *
 * NOTE:
 * - Your server now returns the normalized shape used by MainActivity's inner classes.
 * - This interface references those public static inner classes directly.
 * - Remove old imports for FoodSearchResponse / BarcodeResult from this file.
 */
public interface UsdaProxyService {

    // Search USDA (normalized): returns { totalHits, pageNumber, items[] }
    @GET("api/foods/search")
    Call<com.TDavis.foodie_macrotracker.MainActivity.FoodSearchResponseV2> searchFoodsNormalized(
            @Query("q") String query,
            @Query("pageSize") Integer pageSize,
            @Query("pageNumber") Integer pageNumber
    );

    // Barcode lookup (normalized): returns { item }
    @GET("api/barcode/{code}")
    Call<com.TDavis.foodie_macrotracker.MainActivity.BarcodeLookupResponse> getByBarcodeNormalized(
            @Path("code") String code
    );
}

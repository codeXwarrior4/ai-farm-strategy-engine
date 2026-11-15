package com.farmengine.controller;

import com.farmengine.dto.market.MaharashtraDistrictDto;
import com.farmengine.service.market.MaharashtraDistricts;
import com.farmengine.service.market.MarketPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public catalog controller for discovering Maharashtra districts, APMC markets, and commodities.
 * Enables client applications and farmers to discover authoritative districts and APMC mandi yards.
 */
@RestController
@RequestMapping("/api/v1/market-prices")
@RequiredArgsConstructor
public class MarketPriceCatalogController {

    private final MarketPriceService marketPriceService;

    /**
     * Retrieves all 36 authoritative districts of Maharashtra with divisions and aliases.
     */
    @GetMapping("/districts/maharashtra")
    public ResponseEntity<List<MaharashtraDistrictDto>> getMaharashtraDistricts() {
        List<MaharashtraDistrictDto> dtos = MaharashtraDistricts.getAllDistricts().stream()
                .map(d -> MaharashtraDistrictDto.builder()
                        .canonicalName(d.getCanonicalName())
                        .primaryApiName(d.getPrimaryApiName())
                        .division(d.getDivision())
                        .aliases(d.getAliases())
                        .build())
                .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieves available APMC mandi yards recorded in database for a specific Maharashtra district.
     */
    @GetMapping("/districts/maharashtra/{district}/markets")
    public ResponseEntity<List<String>> getMarketsForDistrict(@PathVariable String district) {
        List<String> markets = marketPriceService.getAvailableMarkets("Maharashtra", district);
        return ResponseEntity.ok(markets);
    }

    /**
     * Retrieves available commodities recorded in database for a specific Maharashtra district.
     */
    @GetMapping("/districts/maharashtra/{district}/commodities")
    public ResponseEntity<List<String>> getCommoditiesForDistrict(@PathVariable String district) {
        List<String> commodities = marketPriceService.getAvailableCommodities("Maharashtra", district);
        return ResponseEntity.ok(commodities);
    }
}

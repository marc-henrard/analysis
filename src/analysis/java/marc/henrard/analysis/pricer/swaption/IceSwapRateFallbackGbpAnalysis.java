/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.pricer.swaption;


import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.index.IborIndices.GBP_LIBOR_6M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.GBP_FIXED_6M_LIBOR_6M;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.param.CurrencyParameterSensitivitiesBuilder;
import com.opengamma.strata.market.param.CurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.MarketQuoteSensitivityCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.SabrParametersSwaptionVolatilities;
import com.opengamma.strata.pricer.swaption.SabrSwaptionPhysicalProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.analysis.dataset.SabrSwaptionDataSet;
import marc.henrard.murisq.basics.data.export.ExcelExportUtil;

/**
 * PV01 report with a swap delta hedging a swaption. 
 * Illustrate the basis risk underlying incoherent fallback between LIBOR fallback and ICE Swap Rate fallback.
 * Results for GBP.
 * 
 * @author Marc Henrard
 */
public class IceSwapRateFallbackGbpAnalysis {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2020, 10, 28);

  private static final String INTERPOLATOR = "zrlinear"; // "zrlinear"; "zrncs"; 
  
  private static final String PATH_CONFIG = "src/analysis/resources/curve-config/";
  private static final String PATH_QUOTES = "src/analysis/resources/quotes/";
  private static final String PATH_OUTPUT = "src/analysis/resources/output/";
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendar CAL_IMPL = REF_DATA.getValue(GBP_LIBOR_6M.getFixingCalendar());

  private static final String CURVE_GROUP_NAME_ON_I_STR = "GBP-DSCONOIS-L6MIRS";
  private static final CurveGroupName CURVE_GROUP_ON_I_NAME = CurveGroupName.of(CURVE_GROUP_NAME_ON_I_STR);
  private static final ResourceLocator GROUPS_ON_I_FILE = ResourceLocator.of( 
      PATH_CONFIG + CURVE_GROUP_NAME_ON_I_STR +"/" + CURVE_GROUP_NAME_ON_I_STR + "-group.csv");
  private static final ResourceLocator SETTINGS_ON_I_FILE = ResourceLocator.of(
      PATH_CONFIG + CURVE_GROUP_NAME_ON_I_STR 
          + "/" + CURVE_GROUP_NAME_ON_I_STR + "-settings-" + INTERPOLATOR + ".csv");
  private static final ResourceLocator NODES_ON_I_FILE = ResourceLocator.of(
      PATH_CONFIG + CURVE_GROUP_NAME_ON_I_STR + "/" + CURVE_GROUP_NAME_ON_I_STR + "-nodes.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_ON_I = RatesCalibrationCsvLoader
      .load(GROUPS_ON_I_FILE, SETTINGS_ON_I_FILE, NODES_ON_I_FILE).get(CURVE_GROUP_ON_I_NAME);

  private static final String FILE_QUOTES = 
      PATH_QUOTES + "MARKET-DATA-" + VALUATION_DATE.toString() + "-STD.csv"; // Synthetic data
  private static final MarketData MARKET_DATA = MarketData
      .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));
  
  /* Swaption description */
  private static final double NOTIONAL = 100_000_000.0d;
  private static final Period EXPIRY_PERIOD = Period.ofMonths(36);
  private static final Tenor TENOR = Tenor.TENOR_10Y;
  private static final double MONEYNESS = -0.0025;
  public static final LocalTime EXERCISE_TIME = LocalTime.of(11, 0);
  public static final ZoneId EXERCISE_ZONE = ZoneId.of("Europe/London");

  /* Pricers */
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final DiscountingSwapProductPricer PRICER_SWAP =
      DiscountingSwapProductPricer.DEFAULT;
  private static final SabrSwaptionPhysicalProductPricer PRICER_SWPT_SABR =
      SabrSwaptionPhysicalProductPricer.DEFAULT;
  private static final MarketQuoteSensitivityCalculator MQC = MarketQuoteSensitivityCalculator.DEFAULT;
  
  /* Constants */
  private static final double BP1 = 1.0E-4;
  
  /* PV and PV01 of a swaption v swap. Swaption is fixed against ICE Swap Rate. 
   * Swaption risk is different from swap risk due to fallback.*/
  @Test
  public void pv01_swaptionVSwap() throws IOException {
    
    long start, end;

    /* Curves */
    start = System.currentTimeMillis();
    ImmutableRatesProvider multicurve =
        CALIBRATOR.calibrate(GROUP_DEFINITION_ON_I, MARKET_DATA, REF_DATA);
    SabrParametersSwaptionVolatilities sabr = SabrSwaptionDataSet.sabrParametersGbp(multicurve);
    end = System.currentTimeMillis();
    System.out.println("Curves calibrated in: " + (end - start) + " ms.");
    
    /* Swaption and swap */
    start = System.currentTimeMillis();
    LocalDate expiryDate = CAL_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRY_PERIOD));
    ResolvedSwapTrade swap0 = GBP_FIXED_6M_LIBOR_6M
        .createTrade(expiryDate, TENOR, BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
    double parRate = PRICER_SWAP.parRate(swap0.getProduct(), multicurve);
    SwapTrade swap = GBP_FIXED_6M_LIBOR_6M
        .createTrade(expiryDate, TENOR, BuySell.BUY, NOTIONAL, parRate + MONEYNESS, REF_DATA);
    Swaption swaptionPayLong = Swaption.builder()
        .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(EXERCISE_TIME).expiryZone(EXERCISE_ZONE)
        .longShort(LongShort.LONG)
        .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
        .underlying(swap.getProduct()).build();
    ResolvedSwaption swaptionResolved = swaptionPayLong.resolve(REF_DATA);
    end = System.currentTimeMillis();
    System.out.println("Instruments in: " + (end - start) + " ms.");
    
    /* Pricing and PV01 */
    start = System.currentTimeMillis();
    CurrencyAmount pvSwpt = PRICER_SWPT_SABR
        .presentValue(swaptionResolved, multicurve, sabr);
    PointSensitivityBuilder ptsSwpt = PRICER_SWPT_SABR
        .presentValueSensitivityRatesStickyModel(swaptionResolved, multicurve, sabr);
    CurrencyParameterSensitivities psSwpt = multicurve.parameterSensitivity(ptsSwpt.build());    
    CurrencyParameterSensitivities mqsSwpt = MQC.sensitivity(psSwpt, multicurve);
    
    MultiCurrencyAmount pvSwap = PRICER_SWAP.presentValue(swaptionResolved.getUnderlying(), multicurve);
    PointSensitivityBuilder ptsSwap = PRICER_SWAP.presentValueSensitivity(swaptionResolved.getUnderlying(), multicurve);
    CurrencyParameterSensitivities psSwap = multicurve.parameterSensitivity(ptsSwap.build());
    CurrencyParameterSensitivities mqsSwap = MQC.sensitivity(psSwap, multicurve);

    double totalPv01Swaption = mqsSwpt.total().getAmount(GBP).getAmount();
    double totalPv01Swap = mqsSwap.total().getAmount(GBP).getAmount();    
    double delta = totalPv01Swaption / totalPv01Swap;
    end = System.currentTimeMillis();
    System.out.println("Pricing in: " + (end - start) + " ms.");
    
    /* Change name fwd and export */
    CurrencyParameterSensitivitiesBuilder sensiBuilder = CurrencyParameterSensitivities.builder();
    for(CurrencyParameterSensitivity s : mqsSwpt.getSensitivities()) {
      CurveName name = CurveName.of(s.getMarketDataName().getName() + "-ICESR");
      sensiBuilder.add(s.toBuilder().marketDataName(name).build());
    }
    CurrencyParameterSensitivities mqSwptNameChanged = sensiBuilder.build();
    CurrencyParameterSensitivities totalSensitivity = mqSwptNameChanged
        .combinedWith(mqsSwap.multipliedBy(-delta));
    
    ExcelExportUtil.export(ImmutableList.of("Swaption", "Swap-Delta"),
        ImmutableList.of(0.0d, 0.0d), 
        ImmutableList.of(MultiCurrencyAmount.of(pvSwpt), pvSwap),
        totalSensitivity.multipliedBy(BP1), 
        PATH_OUTPUT + "ICESwapRate-GBP-pv01.xlsx");
  }
  
}

/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.pricer.swaption;


import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.tuple.Triple;
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
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.MarketQuoteSensitivityCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.SabrParametersSwaptionVolatilities;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.analysis.dataset.SabrSwaptionDataSet;
import marc.henrard.murisq.basics.data.export.ExcelExportUtil;
import marc.henrard.murisq.pricer.swaption.Sabr2DiscountingSwaptionPhysicalProductPricer;

/**
 * Example of pricing of swaption with 2 discounting/collateral rates.
 * Comparison with swaps at PV01 level.
 * 
 * @author Marc Henrard
 */
public class Swaption2DiscountingEurAnalysis {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2020, 10, 28);

  private static final String INTERPOLATOR = "zrlinear"; // "zrlinear"; "zrncs"; 
  
  private static final String PATH_CONFIG = "src/analysis/resources/curve-config/";
  private static final String PATH_QUOTES = "src/analysis/resources/quotes/";
  private static final String PATH_OUTPUT = "src/analysis/resources/output/";
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_6M.getFixingCalendar());

  private static final String CURVE_GROUP_NAME_ESTR_E6_STR = "EUR-DSCESTROIS-E6MIRS";
  private static final CurveGroupName CURVE_GROUP_ESTR_E6_NAME = CurveGroupName.of(CURVE_GROUP_NAME_ESTR_E6_STR);
  private static final ResourceLocator GROUPS_ESTR_E6_FILE = ResourceLocator.of( 
      PATH_CONFIG + CURVE_GROUP_NAME_ESTR_E6_STR +"/" + CURVE_GROUP_NAME_ESTR_E6_STR + "-group.csv");
  private static final ResourceLocator SETTINGS_ESTR_E6_FILE = ResourceLocator.of(
      PATH_CONFIG + CURVE_GROUP_NAME_ESTR_E6_STR 
          + "/" + CURVE_GROUP_NAME_ESTR_E6_STR + "-settings-" + INTERPOLATOR + ".csv");
  private static final ResourceLocator NODES_ESTR_E6_FILE = ResourceLocator.of(
      PATH_CONFIG + CURVE_GROUP_NAME_ESTR_E6_STR + "/" + CURVE_GROUP_NAME_ESTR_E6_STR + "-nodes.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_ESTR_E6 = RatesCalibrationCsvLoader
      .load(GROUPS_ESTR_E6_FILE, SETTINGS_ESTR_E6_FILE, NODES_ESTR_E6_FILE).get(CURVE_GROUP_ESTR_E6_NAME);

  private static final String CURVE_GROUP_NAME_EONIA_E6_STR = "EUR-DSCEONIAOIS-E6MIRS";
  private static final CurveGroupName CURVE_GROUP_EONIA_E6_NAME = CurveGroupName.of(CURVE_GROUP_NAME_EONIA_E6_STR);
  private static final ResourceLocator GROUPS_EONIA_E6_FILE = ResourceLocator.of( 
      PATH_CONFIG + CURVE_GROUP_NAME_EONIA_E6_STR +"/" + CURVE_GROUP_NAME_EONIA_E6_STR + "-group.csv");
  private static final ResourceLocator SETTINGS_EONIA_E6_FILE = ResourceLocator.of(
      PATH_CONFIG + CURVE_GROUP_NAME_EONIA_E6_STR 
          + "/" + CURVE_GROUP_NAME_EONIA_E6_STR + "-settings-" + INTERPOLATOR + ".csv");
  private static final ResourceLocator NODES_EONIA_E6_FILE = ResourceLocator.of(
      PATH_CONFIG + CURVE_GROUP_NAME_EONIA_E6_STR + "/" + CURVE_GROUP_NAME_EONIA_E6_STR + "-nodes.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_EONIA_E6 = RatesCalibrationCsvLoader
      .load(GROUPS_EONIA_E6_FILE, SETTINGS_EONIA_E6_FILE, NODES_EONIA_E6_FILE).get(CURVE_GROUP_EONIA_E6_NAME);

  private static final String FILE_QUOTES = 
      PATH_QUOTES + "MARKET-DATA-" + VALUATION_DATE.toString() + "-STD.csv";
  private static final MarketData MARKET_DATA = MarketData
      .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));
  
  /* Swaption description */
  private static final double NOTIONAL = 100_000_000.0d;
  private static final Period EXPIRY_PERIOD = Period.ofMonths(36);
  private static final Tenor TENOR = Tenor.TENOR_10Y;
  private static final double MONEYNESS = -0.0025;
  public static final LocalTime EXERCISE_TIME = LocalTime.of(11, 0);
  public static final ZoneId EXERCISE_ZONE = ZoneId.of("Europe/Brussels");

  /* Pricers */
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final DiscountingSwapProductPricer PRICER_SWAP =
      DiscountingSwapProductPricer.DEFAULT;
  private static final Sabr2DiscountingSwaptionPhysicalProductPricer PRICER_SWPT_SABR_2 =
      Sabr2DiscountingSwaptionPhysicalProductPricer.DEFAULT;
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
    ImmutableRatesProvider multicurveEurEstr =
        CALIBRATOR.calibrate(GROUP_DEFINITION_ESTR_E6, MARKET_DATA, REF_DATA);
    SabrParametersSwaptionVolatilities sabr = SabrSwaptionDataSet.sabrParametersEur(multicurveEurEstr);
    DiscountFactors dscEurEstr = multicurveEurEstr.discountFactors(Currency.EUR);
    end = System.currentTimeMillis();
    System.out.println("Curves calibrated in: " + (end - start) + " ms.");
    
    /* Swaption and swap */
    start = System.currentTimeMillis();
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRY_PERIOD));
    ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(expiryDate, TENOR, BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
    double parRate = PRICER_SWAP.parRate(swap0.getProduct(), multicurveEurEstr);
    SwapTrade swap = EUR_FIXED_1Y_EURIBOR_6M
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
    Triple<CurrencyAmount, PointSensitivityBuilder, PointSensitivityBuilder> pvPtsSwpt = PRICER_SWPT_SABR_2
        .presentValueSensitivityRatesStickyModel(swaptionResolved, dscEurEstr, expiryDate, multicurveEurEstr, sabr);
    CurrencyParameterSensitivities psDscEstrSwpt = multicurveEurEstr.parameterSensitivity(pvPtsSwpt.getSecond().build());
    CurrencyParameterSensitivities psFwdEstrSwpt = multicurveEurEstr.parameterSensitivity(pvPtsSwpt.getThird().build());
    
    CurrencyParameterSensitivities mqsDscEstrSwpt = MQC.sensitivity(psDscEstrSwpt, multicurveEurEstr);
    CurrencyParameterSensitivities mqsFwdEstrSwpt = MQC.sensitivity(psFwdEstrSwpt, multicurveEurEstr);
    
    MultiCurrencyAmount pvSwap = PRICER_SWAP.presentValue(swaptionResolved.getUnderlying(), multicurveEurEstr);
    PointSensitivityBuilder ptsSwap = PRICER_SWAP.presentValueSensitivity(swaptionResolved.getUnderlying(), multicurveEurEstr);
    CurrencyParameterSensitivities psEstrSwap = multicurveEurEstr.parameterSensitivity(ptsSwap.build());
    CurrencyParameterSensitivities mqsEstrSwap = MQC.sensitivity(psEstrSwap, multicurveEurEstr);

    double totalPv01Swaption = mqsDscEstrSwpt.combinedWith(mqsFwdEstrSwpt).total().getAmount(EUR).getAmount();
    double totalPv01Swap = mqsEstrSwap.total().getAmount(EUR).getAmount();    
    double delta = totalPv01Swaption / totalPv01Swap;
    end = System.currentTimeMillis();
    System.out.println("Pricing in: " + (end - start) + " ms.");
    
    /* Change name fwd and export */
    CurrencyParameterSensitivitiesBuilder sensiBuilder = CurrencyParameterSensitivities.builder();
    for(CurrencyParameterSensitivity s : mqsFwdEstrSwpt.getSensitivities()) {
      CurveName name = CurveName.of(s.getMarketDataName().getName() + "-ICESR");
      sensiBuilder.add(s.toBuilder().marketDataName(name).build());
    }
    CurrencyParameterSensitivities mqFwdEstrSwptNameChanged = sensiBuilder.build();
    CurrencyParameterSensitivities totalSensitivity = mqsDscEstrSwpt.combinedWith(mqFwdEstrSwptNameChanged)
        .combinedWith(mqsEstrSwap.multipliedBy(-delta));
    
    ExcelExportUtil.export(ImmutableList.of("Swaption", "Swap-Delta"),
        ImmutableList.of(0.0d, 0.0d), 
        ImmutableList.of(MultiCurrencyAmount.of(pvPtsSwpt.getFirst()), pvSwap),
        totalSensitivity.multipliedBy(BP1), 
        PATH_OUTPUT + "ICESwapRate-pv01.xlsx");
  }
  
  /* PV and PV01 of a swaption with double discounting: one for underlying forward rate and one for swaption to expiry.
   * Represent the case of a CCP related rate (ICE Swap Rate or CCP delivery) at one collateral rate and one different
   * collateral rate for the bilateral swaption. */
  @Test
  public void pv01_swaption2dsc() throws IOException {
    
    long start, end;

    /* Curves */
    start = System.currentTimeMillis();
    ImmutableRatesProvider multicurveEurEstr =
        CALIBRATOR.calibrate(GROUP_DEFINITION_ESTR_E6, MARKET_DATA, REF_DATA);
    SabrParametersSwaptionVolatilities sabr = SabrSwaptionDataSet.sabrParametersEur(multicurveEurEstr);
    ImmutableRatesProvider multicurveEurEonia =
        CALIBRATOR.calibrate(GROUP_DEFINITION_EONIA_E6, MARKET_DATA, REF_DATA);
    DiscountFactors dscEurEonia = multicurveEurEonia.discountFactors(Currency.EUR);
    end = System.currentTimeMillis();
    System.out.println("Curves calibrated in: " + (end - start) + " ms.");
    
    /* Swaption and swap */
    start = System.currentTimeMillis();
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRY_PERIOD));
    ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(expiryDate, TENOR, BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
    double parRate = PRICER_SWAP.parRate(swap0.getProduct(), multicurveEurEstr);
    SwapTrade swap = EUR_FIXED_1Y_EURIBOR_6M
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
    Triple<CurrencyAmount, PointSensitivityBuilder, PointSensitivityBuilder> pvPtsSwpt = PRICER_SWPT_SABR_2
        .presentValueSensitivityRatesStickyModel(swaptionResolved, dscEurEonia, expiryDate, multicurveEurEstr, sabr);
    CurrencyParameterSensitivities psDscEoniaSwpt = multicurveEurEonia.parameterSensitivity(pvPtsSwpt.getSecond().build());
    CurrencyParameterSensitivities psFwdEstrSwpt = multicurveEurEstr.parameterSensitivity(pvPtsSwpt.getThird().build());
    CurrencyParameterSensitivities mqsDscEoniaSwpt = MQC.sensitivity(psDscEoniaSwpt, multicurveEurEonia);
    CurrencyParameterSensitivities mqsFwdEstrSwpt = MQC.sensitivity(psFwdEstrSwpt, multicurveEurEstr);
    end = System.currentTimeMillis();
    System.out.println("Pricing in: " + (end - start) + " ms.");
    
    ExcelExportUtil.export(ImmutableList.of("Swaption"),
        ImmutableList.of(0.0d), 
        ImmutableList.of(MultiCurrencyAmount.of(pvPtsSwpt.getFirst())),
        mqsDscEoniaSwpt.combinedWith(mqsFwdEstrSwpt).multipliedBy(BP1), 
        PATH_OUTPUT + "swaption-2dsc-pv01.xlsx");
    
  }
  
}

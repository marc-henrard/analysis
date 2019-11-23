/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.DayCounts.ACT_360;
import static com.opengamma.strata.basics.date.DayCounts.ONE_ONE;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.data.ObservableId;
import com.opengamma.strata.loader.csv.FixingSeriesCsvLoader;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.explain.ExplainMap;
import com.opengamma.strata.market.observable.IndexQuoteId;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.MarketQuoteSensitivityCalculator;
import com.opengamma.strata.pricer.sensitivity.NotionalEquivalentCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapTradePricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.CompoundingMethod;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;
import com.opengamma.strata.product.swap.type.FixedRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.IborRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.ImmutableFixedIborSwapConvention;

import marc.henrard.murisq.basics.data.export.ExcelExportUtil;
import marc.henrard.murisq.market.curve.description.MultiplyFixedCurveDefinition;

/**
 * Examples of impact of seasonality on collateral transition compensation scheme.
 * 
 * @author Marc Henrard
 */
public class OvernightSeasonalityCompensationImpactAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2019, 6, 21);
  private static final HolidayCalendarId CALENDAR_ID = HolidayCalendarIds.USNY;
  private static final HolidayCalendar CALENDAR = REF_DATA.getValue(CALENDAR_ID);
  
  /* Load and calibrate curves */
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final String PATH_CONFIG = "src/analysis/resources/curve-config/USD-DSCONOIS-L3MIRS-FOMC/";
  private static final CurveGroupName GROUP_NAME = CurveGroupName.of("USD-DSCONOIS-L3MIRS");
  private static final ResourceLocator FILE_GROUP = 
      ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-group.csv");
  private static final ResourceLocator FILE_SETTINGS =
      ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-settings-dfll.csv");
  private static final ResourceLocator FILE_NODES =
      ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-nodes-fomc-" + VALUATION_DATE.toString() + ".csv");
  private static final String FILE_QUOTES = 
      "src/analysis/resources/quotes/MARKET-DATA-" + VALUATION_DATE.toString() + ".csv";
  private static final ResourceLocator FIXING_FILE = 
      ResourceLocator.of("src/analysis/resources/fixing/USD-LIBOR-3M.csv");
  private static final String PATH_OUTPUT = "src/analysis/resources/output/";
  
  /* Seasonality */
  private static final double JUMP_LEVEL_MONTH_END = 0.00135d;
  private static final double JUMP_LEVEL_FIRST = 0.0008d;
  private static final double JUMP_LEVEL_2ND = 0.00045d;
  private static final double JUMP_LEVEL_15 = 0.0004d;
  
  /* Pricers */
  private static final DiscountingSwapTradePricer PRICER_SWAP = DiscountingSwapTradePricer.DEFAULT;
  private static final MarketQuoteSensitivityCalculator MQSC = MarketQuoteSensitivityCalculator.DEFAULT;
  private static final NotionalEquivalentCalculator NEC = NotionalEquivalentCalculator.DEFAULT;
  private static final double BP1 = 1.0E-4;
  
  private static final CurveName CURVE_DSC = CurveName.of("USD-DSCON-OIS");
  private static final CurveName CURVE_L3 = CurveName.of("USD-LIBOR3M-IRS");
  
  /**
   * Analysis with a recent short swap.
   * 
   * @throws IOException 
   */
  @Test
  public void compensation_impact_short_swap() throws IOException {
    // Recent short
    String fileName = "pv01-compensation-recent-short.xlsx";
    double notional = 100_000_000;
    double fixedRate = 0.0250;
    LocalDate startDate1 = LocalDate.of(2020, 1, 31);
    LocalDate startDate2 = LocalDate.of(2020, 2, 3);
    LocalDate endDate1 = LocalDate.of(2020, 7, 31);
    LocalDate endDate2 = LocalDate.of(2020, 8, 3);
    FixedIborSwapConvention convention = FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M;
    compensationImpact(notional, fixedRate, startDate1, startDate2, endDate1, endDate2, convention, fileName, false);
  }
  
  /**
   * Analysis with a old long swap.
   * 
   * @throws IOException 
   */
  @Test
  public void compensation_impact_long_swap() throws IOException {
    // Old long tenor
    String fileName = "pv01-compensation-old-long.xlsx";
    double notional = 1_000_000_000;
    double fixedRate = 0.1000;
    LocalDate startDate1 = LocalDate.of(2019, 1, 31);
    LocalDate startDate2 = LocalDate.of(2019, 2, 5);
    LocalDate endDate1 = LocalDate.of(2021, 1, 31);
    LocalDate endDate2 = LocalDate.of(2021, 2, 5);
    FixedIborSwapConvention convention = FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M;
    compensationImpact(notional, fixedRate, startDate1, startDate2, endDate1, endDate2, convention, fileName, true);
  }
  
  /**
   * Analysis with a long zero-coupon swap.
   * 
   * @throws IOException 
   */
  @Test
  public void compensation_impact_zero_swap() throws IOException {
    // Zero-coupon long
    String fileName = "pv01-compensation-zero-long.xlsx";
    double notional = 1_000_000_000;
    double fixedRate = 0.0400;
    LocalDate startDate1 = LocalDate.of(2007, 12, 31);
    LocalDate startDate2 = LocalDate.of(2008, 1, 7);
    LocalDate endDate1 = LocalDate.of(2020, 12, 31);
    LocalDate endDate2 = LocalDate.of(2021, 1, 7);
    FixedRateSwapLegConvention conventionFixed = FixedRateSwapLegConvention.builder()
        .paymentFrequency(Frequency.TERM)
        .accrualFrequency(Frequency.P12M)
        .accrualBusinessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CALENDAR_ID))
        .startDateBusinessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CALENDAR_ID))
        .endDateBusinessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CALENDAR_ID))
        .compoundingMethod(CompoundingMethod.STRAIGHT)
        .dayCount(ONE_ONE)
        .currency(Currency.USD)
        .build();
    IborRateSwapLegConvention conventionIbor = IborRateSwapLegConvention.builder()
        .paymentFrequency(Frequency.TERM)
        .accrualFrequency(Frequency.P3M)
        .accrualBusinessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CALENDAR_ID))
        .startDateBusinessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CALENDAR_ID))
        .endDateBusinessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CALENDAR_ID))
        .compoundingMethod(CompoundingMethod.STRAIGHT)
        .dayCount(ACT_360)
        .currency(Currency.USD)
        .index(IborIndices.USD_LIBOR_3M)
        .build();
    ImmutableFixedIborSwapConvention convention =
        ImmutableFixedIborSwapConvention.of("USD-ZERO-COUPON", conventionFixed, conventionIbor);
    compensationImpact(notional, fixedRate, startDate1, startDate2, endDate1, endDate2, convention, fileName, true);
  }
  
  /**
   * Compute ON forward rate for a curve with piecewise constant overnight rates and a fixed spread representing 
   * intra-month seasonal adjustment.
   * 
   * @throws IOException
   */
  private void compensationImpact(
      double notional,
      double fixedRate,
      LocalDate startDate1,
      LocalDate startDate2,
      LocalDate endDate1,
      LocalDate endDate2,
      FixedIborSwapConvention convention,
      String fileName,
      boolean useHedge)
      throws IOException {

    /* Curves */
    int nbMonths = 36;
    ImmutableMap<ObservableId, LocalDateDoubleTimeSeries> fixings = FixingSeriesCsvLoader.load(FIXING_FILE);
    IborIndex index = IborIndices.USD_LIBOR_3M;
    InterpolatedNodalCurve seasonCurve = SeasonalAdjustmentUtils.seasonalityAdjustment(
        DoubleArray.of(JUMP_LEVEL_FIRST, JUMP_LEVEL_2ND, JUMP_LEVEL_15, JUMP_LEVEL_MONTH_END),
        VALUATION_DATE, CALENDAR, DayCounts.ACT_365F, nbMonths);
    RatesCurveGroupDefinition groupDefinitionNotAdjusted = RatesCalibrationCsvLoader
        .load(FILE_GROUP, FILE_SETTINGS, FILE_NODES).get(GROUP_NAME).toBuilder()
        .computePvSensitivityToMarketQuote(true).build();
    CurveDefinition curveDefDscOn = groupDefinitionNotAdjusted.findCurveDefinition(CURVE_DSC).get();
    MultiplyFixedCurveDefinition curveDefAdjusted = MultiplyFixedCurveDefinition.builder()
        .fixedCurve(seasonCurve)
        .spreadCurveDefinition(curveDefDscOn).build();
    RatesCurveGroupDefinition groupDefinitionAdjusted =
        RatesCurveGroupDefinition.of(GROUP_NAME, groupDefinitionNotAdjusted.getEntries(),
            ImmutableList.of(curveDefAdjusted, groupDefinitionNotAdjusted.findCurveDefinition(CURVE_L3).get()))
        .toBuilder().computePvSensitivityToMarketQuote(true).build();
    MarketData marketData = MarketData
        .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));
    ImmutableRatesProvider multicurveAdjustment =
        CALIBRATOR.calibrate(groupDefinitionAdjusted, marketData, REF_DATA)
            .toBuilder().timeSeries(index, fixings.get(IndexQuoteId.of(index))).build();
    ImmutableRatesProvider multicurveNoAdjustment =
        CALIBRATOR.calibrate(groupDefinitionNotAdjusted, marketData, REF_DATA)
        .toBuilder().timeSeries(index, fixings.get(IndexQuoteId.of(index))).build();
    /* Swaps */
    SwapTrade swap1 = convention.toTrade(startDate1, startDate1, endDate1, BuySell.BUY, notional, fixedRate);
    SwapTrade swap2 = convention.toTrade(startDate2, startDate2, endDate2, BuySell.BUY, notional, fixedRate);
    ResolvedSwapTrade swap1Resolved = swap1.resolve(REF_DATA);
    ResolvedSwapTrade swap2Resolved = swap2.resolve(REF_DATA);
    CurrencyParameterSensitivities pv01Bucket1 = MQSC.sensitivity(multicurveNoAdjustment.parameterSensitivity(
        PRICER_SWAP.presentValueSensitivity(swap1Resolved, multicurveNoAdjustment)), multicurveNoAdjustment);
    double pv01_1 = pv01Bucket1.getSensitivity(CURVE_DSC, Currency.USD).total().getAmount();
    CurrencyParameterSensitivities pv01Bucket2 = MQSC.sensitivity(multicurveNoAdjustment.parameterSensitivity(
        PRICER_SWAP.presentValueSensitivity(swap2Resolved, multicurveNoAdjustment)), multicurveNoAdjustment);
    double pv01_2 = pv01Bucket2.getSensitivity(CURVE_DSC, Currency.USD).total().getAmount();
    double notional2 = notional / pv01_2 * pv01_1; // to obtain the same PV01
    swap2 = convention.toTrade(startDate2, startDate2, endDate2, BuySell.SELL, notional2, fixedRate);
    swap2Resolved = swap2.resolve(REF_DATA);
    pv01Bucket2 = MQSC.sensitivity(multicurveNoAdjustment.parameterSensitivity(
        PRICER_SWAP.presentValueSensitivity(swap2Resolved, multicurveNoAdjustment)), multicurveNoAdjustment);
    MultiCurrencyAmount pv1NoAdj = PRICER_SWAP.presentValue(swap1Resolved, multicurveNoAdjustment);
    MultiCurrencyAmount pv2NoAdj = PRICER_SWAP.presentValue(swap2Resolved, multicurveNoAdjustment);
    MultiCurrencyAmount pv1Adj = PRICER_SWAP.presentValue(swap1Resolved, multicurveAdjustment);
    MultiCurrencyAmount pv2Adj = PRICER_SWAP.presentValue(swap2Resolved, multicurveAdjustment);
    double pvNoAdj = pv1NoAdj.getAmount(Currency.USD).getAmount() + pv2NoAdj.getAmount(Currency.USD).getAmount();
    double pvAdj = pv1Adj.getAmount(Currency.USD).getAmount() + pv2Adj.getAmount(Currency.USD).getAmount();
    CurrencyParameterSensitivities pv01BucketTotal = pv01Bucket1.combinedWith(pv01Bucket2);
    System.out.println("PV no adjustment: " + pvNoAdj);
    System.out.println("PV adjustment: " + pvAdj);
    System.out.println("Compensation difference: " + (pvNoAdj - pvAdj));
    /* Hedging OIS */
    List<Double> rates = new ArrayList<>();
    rates.add(0.0);
    rates.add(0.0);
    List<String> names = new ArrayList<>();
    names.add("No Adjustment");
    names.add("Adjustment");
    List<MultiCurrencyAmount> pv = new ArrayList<>();
    pv.add(pv1NoAdj.plus(pv2NoAdj));
    pv.add(pv1Adj.plus(pv2Adj));
    if (useHedge) {
      CurrencyParameterSensitivities notionalEquivalentNoAdjustment =
          NEC.notionalEquivalent(pv01BucketTotal, multicurveNoAdjustment);
      DoubleArray notionalEquivalentOis = notionalEquivalentNoAdjustment
          .getSensitivity(CURVE_DSC, Currency.USD).getSensitivity(); // Hedging notional
      List<ResolvedSwapTrade> ois = new ArrayList<>(); // Hedges
      for (int looptenor = 0; looptenor < notionalEquivalentOis.size(); looptenor++) {
        ois.add((ResolvedSwapTrade) curveDefDscOn.getNodes().get(looptenor)
            .resolvedTrade(-notionalEquivalentOis.get(looptenor), marketData, REF_DATA));
        rates.add(PRICER_SWAP.parRate(ois.get(looptenor), multicurveNoAdjustment));
        names.add(curveDefDscOn.getNodes().get(looptenor).getLabel());
        pv.add(PRICER_SWAP.presentValue(ois.get(looptenor), multicurveAdjustment));
      }
      CurrencyParameterSensitivities hedgingOis = CurrencyParameterSensitivities.empty();
      for (int looptenor = 0; looptenor < notionalEquivalentOis.size(); looptenor++) {
        hedgingOis = hedgingOis.combinedWith(MQSC.sensitivity(multicurveNoAdjustment.parameterSensitivity(
            PRICER_SWAP.presentValueSensitivity(ois.get(looptenor), multicurveNoAdjustment)), multicurveNoAdjustment));
      }
      pv01BucketTotal = pv01BucketTotal.combinedWith(hedgingOis);
    }
    ExcelExportUtil.export(
        names,
        rates,
        pv,
        pv01BucketTotal.multipliedBy(BP1), PATH_OUTPUT + fileName);
    ExplainMap explain = PRICER_SWAP.explainPresentValue(swap1Resolved, multicurveNoAdjustment);
    System.out.println(explain);
  }
  
  // TODO: create an example with cross-currency and exchange of notional
  
}

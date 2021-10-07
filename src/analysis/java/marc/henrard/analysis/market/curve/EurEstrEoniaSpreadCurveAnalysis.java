/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_ESTR;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeriesBuilder;
import com.opengamma.strata.data.ImmutableMarketData;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveNode;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapLegPricer;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

import marc.henrard.murisq.basics.data.export.ExportUtils;

/**
 * Analyze the impact of having a fixed overnight spread between ESTR and EONIA on curves. 
 * Curves are usually calibrated on swap market rates (OIS) and interpolated. This approach is not coherent with
 * a fixed overnight spread. The analysis calculate the exact impact of this incoherence. 
 * <p>
 * The steps of the analysis are
 *  1) Calibrate ESTR to market using a set of quotes and an interpolator.
 *  2) Compute exact EONIA equivalent quotes by compounding overnight forward plus spread
 *  3) Calibrate EONIA curve on those quotes using the same interpolator.
 *  4) Compare ESTR and EONIA overnight forwards from the two set of curves.
 * Note that this approach is missing the adjustment between overnight forward and discounting curve. 
 * This should be negligible as the spread is fixed on an overnight basis; it would be zero if the spread 
 * was on a continuously compounded basis.
 * The analysis may underestimate the error as most of the market is using EONIA quotes obtained independently from
 * the ESTR quotes. This introduce a rounding error, a bid offer error and potentially a synchronization error. 
 * 
 * @author Marc Henrard
 */
public class EurEstrEoniaSpreadCurveAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2020, 12, 10);
  private static final double SPREAD = 0.00085;

  private static final String PATH_EXPORT = "src/analysis/resources/output/";
  
  /* Load curve descriptions */
  private static final String PATH_CONFIG_MARKET_ESTR = "src/analysis/resources/curve-config/EUR-DSCESTROIS/";
  private static final String GROUP_NAME_MKT_ESTR_STR = "EUR-DSCESTROIS";
  private static final CurveGroupName GROUP_NAME_MKT_ESTR = CurveGroupName.of(GROUP_NAME_MKT_ESTR_STR);
  private static final ResourceLocator FILE_GROUP_MKT_ESTR = 
      ResourceLocator.of(PATH_CONFIG_MARKET_ESTR + GROUP_NAME_MKT_ESTR_STR + "-group.csv");
  private static final ResourceLocator FILE_NODES_MKT_ESTR =
      ResourceLocator.of(PATH_CONFIG_MARKET_ESTR + GROUP_NAME_MKT_ESTR_STR + "-nodes.csv");
  private static final ResourceLocator FILE_SETTINGS_MKT_ESTR_ZRLIN =
      ResourceLocator.of(PATH_CONFIG_MARKET_ESTR + GROUP_NAME_MKT_ESTR_STR + "-settings-zrlinear.csv");
//  private static final ResourceLocator FILE_SETTINGS_MKT_ESTR_ZRNCS =
//      ResourceLocator.of(PATH_CONFIG_MARKET_ESTR + GROUP_NAME_MKT_ESTR_STR + "-settings-zrncs.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_MKT_ESTR_ZRLIN = RatesCalibrationCsvLoader
      .load(FILE_GROUP_MKT_ESTR, FILE_SETTINGS_MKT_ESTR_ZRLIN, FILE_NODES_MKT_ESTR).get(GROUP_NAME_MKT_ESTR);
//  private static final RatesCurveGroupDefinition GROUP_DEFINITION_MKT_ESTR_ZRNCS = RatesCalibrationCsvLoader
//      .load(FILE_GROUP_MKT_ESTR, FILE_SETTINGS_MKT_ESTR_ZRNCS, FILE_NODES_MKT_ESTR).get(GROUP_NAME_MKT_ESTR)
//      .toBuilder().computePvSensitivityToMarketQuote(true).build();
  

  private static final String PATH_CONFIG_MARKET_EONIA = "src/analysis/resources/curve-config/EUR-DSCESTROIS-EONIAOIS/";
  private static final String GROUP_NAME_MKT_EONIA_STR = "EUR-DSCESTROIS-EONIAOIS";
  private static final CurveGroupName GROUP_NAME_MKT_EONIA = CurveGroupName.of(GROUP_NAME_MKT_EONIA_STR);
  private static final ResourceLocator FILE_GROUP_MKT_EONIA = 
      ResourceLocator.of(PATH_CONFIG_MARKET_EONIA + GROUP_NAME_MKT_EONIA_STR + "-group.csv");
  private static final ResourceLocator FILE_NODES_MKT_EONIA =
      ResourceLocator.of(PATH_CONFIG_MARKET_EONIA + GROUP_NAME_MKT_EONIA_STR + "-nodes.csv");
  private static final ResourceLocator FILE_SETTINGS_MKT_EONIA_ZRLIN =
      ResourceLocator.of(PATH_CONFIG_MARKET_EONIA + GROUP_NAME_MKT_EONIA_STR + "-settings-zrlinear.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_MKT_EONIA_ZRLIN = RatesCalibrationCsvLoader
      .load(FILE_GROUP_MKT_EONIA, FILE_SETTINGS_MKT_EONIA_ZRLIN, FILE_NODES_MKT_EONIA).get(GROUP_NAME_MKT_EONIA);
  
  private static final String FILE_QUOTES = 
      "src/analysis/resources/quotes/MARKET-DATA-" + VALUATION_DATE.toString() + ".csv";
  private static final MarketData MARKET_DATA = MarketData
      .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));
  
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final DiscountingSwapLegPricer PRICER_SWAP_LEG = DiscountingSwapLegPricer.DEFAULT;

  @Test
  public void on_comparison() throws IOException {
    
    Period exportPeriod = Period.ofYears(10);

    long start, end;

    /* ESTR Curve */
    start = System.currentTimeMillis();
    ImmutableRatesProvider multicurveEstr =
        CALIBRATOR.calibrate(GROUP_DEFINITION_MKT_ESTR_ZRLIN, MARKET_DATA, REF_DATA);
    end = System.currentTimeMillis();
    System.out.println("Curves calibrated in: " + (end - start) + " ms.");
    
    /* EONIA equivalent quotes */
    start = System.currentTimeMillis();
    HolidayCalendar calendarEstr = REF_DATA.getValue(EUR_ESTR.getFixingCalendar());
    CurveName eoniaCurveName = CurveName.of("EUR-EONIA-OIS");
    CurveName estrCurveName = CurveName.of("EUR-DSCESTR-OIS");
    CurveDefinition defEonia = GROUP_DEFINITION_MKT_EONIA_ZRLIN.findCurveDefinition(eoniaCurveName).get();
    CurveDefinition defEstr = GROUP_DEFINITION_MKT_EONIA_ZRLIN.findCurveDefinition(estrCurveName).get();
    ImmutableList<CurveNode> nodesEonia = defEonia.getNodes();
    ImmutableList<CurveNode> nodesEstr = defEstr.getNodes();
    int nbNodes = nodesEonia.size();
    double[] swapEoniaQuotes = new double[nbNodes];
    for (int loopnode = 0; loopnode < nbNodes; loopnode++) {
      CurveNode node = nodesEonia.get(loopnode);
      ResolvedSwapTrade trade =
          (ResolvedSwapTrade) node.sampleResolvedTrade(VALUATION_DATE, FxMatrix.empty(), REF_DATA);
      ResolvedSwapLeg legOn = trade.getProduct().getLegs(SwapLegType.OVERNIGHT).get(0);
      ResolvedSwapLeg legFixed = trade.getProduct().getLegs(SwapLegType.FIXED).get(0);
      ImmutableList<SwapPaymentPeriod> payPeriods = legOn.getPaymentPeriods();
      int nbPeriods = payPeriods.size();
      double[] forwardRatesOnEonia = new double[nbPeriods];
      double pvFloat = 0.0;
      for (int loopperiod = 0; loopperiod < nbPeriods; loopperiod++) {
        SwapPaymentPeriod payPeriod = payPeriods.get(loopperiod);
        RatePaymentPeriod ratePeriod = (RatePaymentPeriod) payPeriod;
        OvernightCompoundedRateComputation onCompounded =
            (OvernightCompoundedRateComputation) ratePeriod.getAccrualPeriods().get(0).getRateComputation();
        LocalDate startDate = onCompounded.getStartDate();
        LocalDate endDate = onCompounded.getEndDate();
        double rateCompoundedWithSpread = 1.0;
        LocalDate currentDate = startDate;
        while (currentDate.isBefore(endDate)) {
          OvernightIndexObservation obs = OvernightIndexObservation.of(EUR_ESTR, currentDate, REF_DATA);
          rateCompoundedWithSpread *=
              (1.0d + obs.getYearFraction() * (multicurveEstr.overnightIndexRates(EUR_ESTR).rate(obs) + SPREAD));
          currentDate = calendarEstr.next(currentDate);
        }
        double accrualFactor = ratePeriod.getAccrualPeriods().get(0).getYearFraction();
        forwardRatesOnEonia[loopperiod] = (rateCompoundedWithSpread - 1.0d) / accrualFactor;
        double dfPay = multicurveEstr.discountFactor(EUR, payPeriod.getPaymentDate());
        pvFloat += accrualFactor * forwardRatesOnEonia[loopperiod] * dfPay;
      }
      double pvbp = PRICER_SWAP_LEG.pvbp(legFixed, multicurveEstr);
      swapEoniaQuotes[loopnode] = pvFloat / pvbp;
    }
    Map<QuoteId, Double> quotesEonia = new HashMap<>();
    Map<QuoteId, Double> quotesSpread = new HashMap<>();
    for (int loopnode = 0; loopnode < nbNodes; loopnode++) {
      CurveNode nodeEonia = nodesEonia.get(loopnode);
      quotesEonia.put((QuoteId) nodeEonia.requirements().iterator().next(), swapEoniaQuotes[loopnode]);
      CurveNode nodeEstr = nodesEstr.get(loopnode);
      double quoteEstr = (double) MARKET_DATA.getValue(nodeEstr.requirements().iterator().next());
      quotesSpread.put((QuoteId) nodeEonia.requirements().iterator().next(), swapEoniaQuotes[loopnode] - quoteEstr);
    }
    ImmutableMarketData marketDataWithEonia =ImmutableMarketData.builder(VALUATION_DATE)
        .add(MARKET_DATA)
        .addValueMap(quotesEonia).build();
    end = System.currentTimeMillis();
    System.out.println("EONIA rates in: " + (end - start) + " ms.");
    
    StringBuilder quotesEoniaCsvBuilder = new StringBuilder();
    ExportUtils.exportMarketQuotes(VALUATION_DATE, quotesSpread, quotesEoniaCsvBuilder, true);
    ExportUtils.exportString(quotesEoniaCsvBuilder.toString(), PATH_EXPORT + "EONIA-spread-quotes.csv");
    
    /* Calibrate ESTR/EONIA curves */
    start = System.currentTimeMillis();
    ImmutableRatesProvider multicurveEstrEonia =
        CALIBRATOR.calibrate(GROUP_DEFINITION_MKT_EONIA_ZRLIN, marketDataWithEonia, REF_DATA);
    end = System.currentTimeMillis();
    System.out.println("Curves calibrated in: " + (end - start) + " ms.");

    /* Compare ON and export*/
    start = System.currentTimeMillis();
    LocalDate startDateExport = VALUATION_DATE;
    LocalDate endDateExport = VALUATION_DATE.plus(exportPeriod);
    LocalDate currentDate = startDateExport;
    LocalDateDoubleTimeSeriesBuilder tsEstrBuilder = LocalDateDoubleTimeSeries.builder();
    LocalDateDoubleTimeSeriesBuilder tsEoniaBuilder = LocalDateDoubleTimeSeries.builder();
    while (currentDate.isBefore(endDateExport)) {
      OvernightIndexObservation obsEstr = OvernightIndexObservation.of(EUR_ESTR, currentDate, REF_DATA);
      OvernightIndexObservation obsEonia = OvernightIndexObservation.of(EUR_EONIA, currentDate, REF_DATA);
      tsEstrBuilder.put(currentDate, multicurveEstrEonia.overnightIndexRates(EUR_ESTR).rate(obsEstr));
      tsEoniaBuilder.put(currentDate, multicurveEstrEonia.overnightIndexRates(EUR_EONIA).rate(obsEonia));
      currentDate = calendarEstr.next(currentDate);
    }
    LocalDateDoubleTimeSeries tsEstr = tsEstrBuilder.build();
    LocalDateDoubleTimeSeries tsEonia = tsEoniaBuilder.build();

    StringBuilder tsCsvBuilder = new StringBuilder();
    ExportUtils.exportTimeSeries(ImmutableList.of("EUR-ESTR", "EUR-EONIA"),
        ImmutableList.of(tsEstr, tsEonia), tsCsvBuilder);
    ExportUtils.exportString(tsCsvBuilder.toString(),
        PATH_EXPORT + "ESTR-EONIA-forwards.csv");
    end = System.currentTimeMillis();
    System.out.println("ON exported in: " + (end - start) + " ms.");

  }

}

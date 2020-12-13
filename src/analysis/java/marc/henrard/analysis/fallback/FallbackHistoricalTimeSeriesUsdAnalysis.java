/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.fallback;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.math.Quantiles;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeriesBuilder;
import com.opengamma.strata.data.ObservableId;
import com.opengamma.strata.loader.csv.FixingSeriesCsvLoader;
import com.opengamma.strata.market.observable.IndexQuoteId;

import marc.henrard.murisq.basics.data.export.ExportUtils;
import marc.henrard.murisq.basics.index.ComplementIborIndices;

/**
 * Computes spreads between USD-LIBOR and compounded EFFR or SOFR.
 * Estimate potential future paths from past dynamic.
 * 
 * @author Marc Henrard
 */
public class FallbackHistoricalTimeSeriesUsdAnalysis {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate ANALYSIS_DATE = LocalDate.of(2020, 12, 4);
  private static final IborIndex IBOR_INDEX = IborIndices.USD_LIBOR_3M;
  private static final HolidayCalendar CALENDAR_FIXING_IBOR = REF_DATA.getValue(IBOR_INDEX.getFixingCalendar());
//  private static final IborIndex ONCMP_INDEX = ComplementIborIndices.USD_FED_FUNDCMP_3M;
  private static final IborIndex ONCMP_INDEX = ComplementIborIndices.USD_SOFRCMP_3M;
  private static final HolidayCalendar CALENDAR_ON = REF_DATA.getValue(ONCMP_INDEX.getFixingCalendar());
  private static final List<ResourceLocator> FIXING_RESOURCES = ImmutableList.of(
      ResourceLocator.of("src/analysis/resources/fixing/" + IBOR_INDEX.toString() + ".csv"),
//      ResourceLocator.of("src/analysis/resources/fixing/USD-FED-FUNDCMP-"
//        + IBOR_INDEX.getTenor().toString() + ".csv"));
      ResourceLocator.of("src/analysis/resources/fixing/USD-SOFRCMP-OFFSET2-" + IBOR_INDEX.getTenor().toString() + ".csv"));
  private static final Map<ObservableId, LocalDateDoubleTimeSeries> TIME_SERIES =
      FixingSeriesCsvLoader.load(FIXING_RESOURCES);
  private static final List<LocalDate> ANNOUNCEMENT_DATES = ImmutableList.of(
      LocalDate.of(2021, 4, 1),
      LocalDate.of(2021, 7, 1),
      LocalDate.of(2021, 10, 1),
      LocalDate.of(2022, 1, 1));
  private static final int NB_ANNOUNCEMENT_DATES = ANNOUNCEMENT_DATES.size();
  private static final int[] LOOKBACK_PERIODS_STARTDATE = {5, 7, 10};
  private static final int STD_DEV_FUT = 1;
  private static final int LOOKBACK_PERIOD = 5;
//  private static final int BP1 = 10_000;
  private static final int PERCENT = 100;

  private static final String EXPORT_PATH = "src/analysis/resources/output/";

  /**
   * Computes past means and medians with a lookback period.
   * 
   * @throws IOException
   */
  @Test
  public void spread_computation_period() throws IOException {
    long start, end;
    start = System.currentTimeMillis();
    IndexQuoteId idLibor = IndexQuoteId.of(IBOR_INDEX);
    IndexQuoteId idOnCmp = IndexQuoteId.of(ONCMP_INDEX);
    LocalDateDoubleTimeSeries tsLibor = TIME_SERIES.get(idLibor);
    LocalDateDoubleTimeSeries tsOnCmp = TIME_SERIES.get(idOnCmp);
    LocalDateDoubleTimeSeries tsSpread = tsLibor.intersection(tsOnCmp, (l, o) -> l - o);
    System.out.println("Spread for " + tsSpread.getLatestDate() + " is " + tsSpread.getLatestValue());

    LocalDate startDateSpread = tsSpread.getEarliestDate();
    LocalDate endDateSpread = tsSpread.getLatestDate();
    LocalDate startDateMedian = CALENDAR_ON.nextOrSame(startDateSpread.plusYears(LOOKBACK_PERIOD));

    LocalDate currentDateMedian = startDateMedian;
    LocalDateDoubleTimeSeriesBuilder tsMedian = LocalDateDoubleTimeSeries.builder();
    double uncertaintyMax = 0.0;
    while (!currentDateMedian.isAfter(endDateSpread)) {
      LocalDate startLookback = currentDateMedian.minusYears(LOOKBACK_PERIOD);
      LocalDateDoubleTimeSeries tsLookbackPeriod = tsSpread.subSeries(startLookback, currentDateMedian);
      int size = tsLookbackPeriod.size();
      List<Double> values = tsLookbackPeriod.values().boxed().collect(Collectors.toList());
      Collections.sort(values);
      double median = Quantiles.median().compute(tsLookbackPeriod.stream().mapToDouble(pt -> pt.getValue()).toArray());
      double uncertainty = (values.get(size / 2) - values.get(size / 2 - 1));
      if (uncertainty > uncertaintyMax) {
        uncertaintyMax = uncertainty;
        System.out.println(currentDateMedian + ": " + (uncertainty * PERCENT));
      }
      System.out.println(currentDateMedian + ": " + size + ", " + median 
          + ", " + values.get(size / 2 - 1) + ", " + values.get(size / 2) 
          + ", " + (uncertainty * PERCENT));
      tsMedian.put(currentDateMedian, median);
      do {
        currentDateMedian = currentDateMedian.plusDays(1);
      } while (!tsSpread.containsDate(currentDateMedian) && !currentDateMedian.isAfter(endDateSpread));
    }

    StringBuilder tsMedianExport = new StringBuilder();
    String nameMedian = IBOR_INDEX.toString() + "SOFR-RUNNING-MEDIAN-LOOKBACKPERIOD";
    ExportUtils.exportTimeSeries(nameMedian, tsMedian.build(), tsMedianExport);
    ExportUtils.exportString(tsMedianExport.toString(), EXPORT_PATH + nameMedian + ".csv");

    System.out.println("Uncertainty median max (%): " + (uncertaintyMax * PERCENT));
    
    end = System.currentTimeMillis();
    System.out.println("Computation in " + (end - start) + " ms.");

  }

  /**
   * Computes past means and medians with a fixed starting date
   * 
   * @throws IOException
   */
  @Test
  public void spread_computation_startdate() throws IOException {
    long start, end;
    start = System.currentTimeMillis();
    IndexQuoteId idLibor = IndexQuoteId.of(IBOR_INDEX);
    IndexQuoteId idOnCmp = IndexQuoteId.of(ONCMP_INDEX);
    LocalDateDoubleTimeSeries tsLibor = TIME_SERIES.get(idLibor);
    LocalDateDoubleTimeSeries tsOnCmp = TIME_SERIES.get(idOnCmp);
    LocalDateDoubleTimeSeries tsSpread = tsLibor.intersection(tsOnCmp, (l, o) -> l - o);
    System.out.println("Spread for " + tsSpread.getLatestDate() + " is " + tsSpread.getLatestValue());
    for (int looplookback = 0; looplookback < LOOKBACK_PERIODS_STARTDATE.length; looplookback++) {
      List<LocalDateDoubleTimeSeries> tsRunningMean = new ArrayList<>();
      List<LocalDateDoubleTimeSeries> tsRunningMedian = new ArrayList<>();
      for (int loopdate = 0; loopdate < NB_ANNOUNCEMENT_DATES; loopdate++) {
        LocalDate startDate = ANNOUNCEMENT_DATES.get(loopdate).minusYears(LOOKBACK_PERIODS_STARTDATE[looplookback]);
        LocalDateDoubleTimeSeriesBuilder tsMean = LocalDateDoubleTimeSeries.builder();
        LocalDateDoubleTimeSeriesBuilder tsMedian = LocalDateDoubleTimeSeries.builder();
        LocalDate currentDate = startDate.plusMonths(1); // The first month of mean is almost meaningless
        while (currentDate.isBefore(ANALYSIS_DATE)) {
          if (tsSpread.containsDate(currentDate)) {
            LocalDate maturityDate = IBOR_INDEX.calculateMaturityFromEffective(currentDate, REF_DATA);
            LocalDateDoubleTimeSeries tsSpreadLookback = tsSpread.subSeries(startDate, currentDate);
            if (tsSpreadLookback.size() > 10) {
              tsMean.put(maturityDate,
                  tsSpreadLookback.stream().mapToDouble(pt -> pt.getValue()).average().getAsDouble());
              tsMedian.put(maturityDate,
                  Quantiles.median().compute(tsSpreadLookback.stream().mapToDouble(pt -> pt.getValue()).toArray()));
            }
          }
          currentDate = currentDate.plusDays(1);
        }
        LocalDateDoubleTimeSeries tsMeanBuild = tsMean.build();
        tsRunningMean.add(tsMeanBuild);
        tsRunningMedian.add(tsMedian.build());
      }
      /* Export */
      for (int loopdate = 0; loopdate < NB_ANNOUNCEMENT_DATES; loopdate++) {
        StringBuilder tsMeanExport = new StringBuilder();
        String nameMean = IBOR_INDEX.toString() + "SOFRCMP-RUNNING-MEAN-" +
            ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" + LOOKBACK_PERIODS_STARTDATE[looplookback];
        ExportUtils.exportTimeSeries(nameMean, tsRunningMean.get(loopdate), tsMeanExport);
        ExportUtils.exportString(tsMeanExport.toString(), EXPORT_PATH + nameMean + ".csv");
        StringBuilder tsMedianExport = new StringBuilder();
        String nameMedian = IBOR_INDEX.toString() + "SOFRCMP-RUNNING-MEDIAN-" +
            ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" + LOOKBACK_PERIODS_STARTDATE[looplookback];
        ExportUtils.exportTimeSeries(nameMedian, tsRunningMedian.get(loopdate), tsMedianExport);
        ExportUtils.exportString(tsMedianExport.toString(), EXPORT_PATH + nameMedian + ".csv");
      }
    }
    end = System.currentTimeMillis();
    System.out.println("Computation in " + (end - start) + " ms.");
  }

  /**
   * Computes and export future means within one standard deviation.
   * 
   * @throws IOException
   */
  @Test
  public void spread_future_evolution() throws IOException {
    long start, end;
    start = System.currentTimeMillis();
    IndexQuoteId idLibor = IndexQuoteId.of(IBOR_INDEX);
    IndexQuoteId idOnCmp = IndexQuoteId.of(ONCMP_INDEX);
    LocalDateDoubleTimeSeries tsLibor = TIME_SERIES.get(idLibor);
    LocalDateDoubleTimeSeries tsOnCmp = TIME_SERIES.get(idOnCmp);
    LocalDateDoubleTimeSeries tsSpread = tsLibor.intersection(tsOnCmp, (l, o) -> l - o);
    LocalDateDoubleTimeSeries tsSpreadStd = tsSpread.subSeries(LocalDate.of(2014, 1, 1), ANALYSIS_DATE);
    /* Standard deviation */
    double[] spreadValues = tsSpreadStd.values().toArray();
    int nbSpread = spreadValues.length;
    double stdDeviationDaily = 0.0;
    for (int i = 0; i < nbSpread - 1; i++) {
      double spreadDailyReturn = spreadValues[i + 1] - spreadValues[i];
      stdDeviationDaily += spreadDailyReturn * spreadDailyReturn;
    }
    stdDeviationDaily = Math.sqrt(stdDeviationDaily / (nbSpread - 1));
    /* Future */
    /* Note: with respect to paper: nbLookback = n; loopdays = i - (n+1)*/
    LocalDate startDateFuture = tsSpread.getLatestDate();
    for (int looplookback = 0; looplookback < LOOKBACK_PERIODS_STARTDATE.length; looplookback++) {
      List<LocalDateDoubleTimeSeries> tsFutureMeanPlus = new ArrayList<>();
      List<LocalDateDoubleTimeSeries> tsFutureMeanMinus = new ArrayList<>();
      List<LocalDateDoubleTimeSeries> tsFutureMean0 = new ArrayList<>();
      for (int loopdate = 0; loopdate < NB_ANNOUNCEMENT_DATES; loopdate++) {
        LocalDate startDatePast = ANNOUNCEMENT_DATES.get(loopdate).minusYears(LOOKBACK_PERIODS_STARTDATE[looplookback]);
        LocalDate currentDate = startDateFuture;
        LocalDateDoubleTimeSeries tsSpreadLookback =
            tsSpread.subSeries(startDatePast, currentDate);
        double currentSpread = tsSpreadLookback.getLatestValue();
        double currentMeanSpread =
            tsSpreadLookback.stream().mapToDouble(pt -> pt.getValue()).average().getAsDouble();
        int nbLookback = tsSpreadLookback.size();
        double loopdays = 0;
        LocalDateDoubleTimeSeriesBuilder tsFuturePlus = LocalDateDoubleTimeSeries.builder();
        LocalDateDoubleTimeSeriesBuilder tsFutureMinus = LocalDateDoubleTimeSeries.builder();
        LocalDateDoubleTimeSeriesBuilder tsFuture0 = LocalDateDoubleTimeSeries.builder();
        while (currentDate.isBefore(ANNOUNCEMENT_DATES.get(loopdate))) {
          double spreadPart1 = nbLookback * currentMeanSpread + (loopdays + 1) * currentSpread;
          double i = nbLookback + (loopdays + 1);
          double var = 0.0; // Variance of the running mean
          for (int l = 0; l <= loopdays; l++) {
            var += (loopdays - l + 1) * (loopdays - l + 1);
          }
          double spreadPlus =
              (spreadPart1 + Math.sqrt(var) * STD_DEV_FUT * stdDeviationDaily) / i;
          tsFuturePlus.put(currentDate, spreadPlus);
          double spreadMinus =
              (spreadPart1 - Math.sqrt(var) * STD_DEV_FUT * stdDeviationDaily) / i;
          tsFutureMinus.put(currentDate, spreadMinus);
          double spread0 = spreadPart1 / i;
          tsFuture0.put(currentDate, spread0);
          currentDate = CALENDAR_FIXING_IBOR.next(currentDate);
          loopdays++;
        }
        tsFutureMeanPlus.add(tsFuturePlus.build());
        tsFutureMeanMinus.add(tsFutureMinus.build());
        tsFutureMean0.add(tsFuture0.build());
        System.out.println("Current spread, " + LOOKBACK_PERIODS_STARTDATE[looplookback] + ", " +
            ANNOUNCEMENT_DATES.get(loopdate) + ", " + currentSpread);
        System.out.println("Current mean spread, " + LOOKBACK_PERIODS_STARTDATE[looplookback] + ", " +
            ANNOUNCEMENT_DATES.get(loopdate) + ", " + currentMeanSpread);
      }
      System.out.println("Daily standard deviation: " + stdDeviationDaily);
      /* Export */
      for (int loopdate = 0; loopdate < NB_ANNOUNCEMENT_DATES; loopdate++) {
        StringBuilder tsExportPlus = new StringBuilder();
        String namePlus = IBOR_INDEX.toString() + "EFFRCMP-FUTURE-MEAN-P" + STD_DEV_FUT + "STD-" +
            ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" +
            LOOKBACK_PERIODS_STARTDATE[looplookback] + "-" + IBOR_INDEX.getTenor().toString();
        ExportUtils.exportTimeSeries(namePlus, tsFutureMeanPlus.get(loopdate), tsExportPlus);
        ExportUtils.exportString(tsExportPlus.toString(), EXPORT_PATH + namePlus + ".csv");
        StringBuilder tsExportMinus = new StringBuilder();
        String nameMinus = IBOR_INDEX.toString() + "EFFRCMP-FUTURE-MEAN-M" + STD_DEV_FUT + "STD-" +
            ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" +
            LOOKBACK_PERIODS_STARTDATE[looplookback] + "-" + IBOR_INDEX.getTenor().toString();
        ExportUtils.exportTimeSeries(nameMinus, tsFutureMeanMinus.get(loopdate), tsExportMinus);
        ExportUtils.exportString(tsExportMinus.toString(), EXPORT_PATH + nameMinus + ".csv");
        StringBuilder tsExport0 = new StringBuilder();
        String name0 =
            IBOR_INDEX.toString() + "EFFRCMP-FUTURE-MEAN-0-" + ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" +
                LOOKBACK_PERIODS_STARTDATE[looplookback] + "-" + IBOR_INDEX.getTenor().toString();
        ExportUtils.exportTimeSeries(name0, tsFutureMean0.get(loopdate), tsExport0);
        ExportUtils.exportString(tsExport0.toString(), EXPORT_PATH + name0 + ".csv");
      }
    }
    end = System.currentTimeMillis();
    System.out.println("Computation in " + (end - start) + " ms.");
  }

}

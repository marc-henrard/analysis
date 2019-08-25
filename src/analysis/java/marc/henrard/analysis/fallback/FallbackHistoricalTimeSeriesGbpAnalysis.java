/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.fallback;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

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
 * Computes spreads between GBP-LIBOR and compounded SONIA.
 * Estimate potential future paths from past dynamic.
 * Data as of 20 August 2019.
 * 
 * @author Marc Henrard
 */
@Test
public class FallbackHistoricalTimeSeriesGbpAnalysis {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate ANALYSIS_DATE = LocalDate.of(2019, 8, 20);
  private static final IborIndex IBOR_INDEX = IborIndices.GBP_LIBOR_6M;
  private static final HolidayCalendar CALENDAR = REF_DATA.getValue(IBOR_INDEX.getFixingCalendar());
  private static final IborIndex ONCMP_INDEX = ComplementIborIndices.GBP_SONIACMP_6M;
  private static final List<ResourceLocator> FIXING_RESOURCES = ImmutableList.of(
      ResourceLocator.of("src/analysis/resources/fixing/" + IBOR_INDEX.toString() + ".csv"),
      ResourceLocator.of("src/analysis/resources/fixing/GBP-SONIACMP-"
        + IBOR_INDEX.getTenor().toString() + ".csv"));
  private static final Map<ObservableId, LocalDateDoubleTimeSeries> TIME_SERIES =
      FixingSeriesCsvLoader.load(FIXING_RESOURCES);
  private static final List<LocalDate> ANNOUNCEMENT_DATES = ImmutableList.of(
      LocalDate.of(2021, 4, 1),
      LocalDate.of(2021, 7, 1), 
      LocalDate.of(2021, 10, 1),
      LocalDate.of(2022, 1, 1));
  private static final int NB_ANNOUNCEMENT_DATES = ANNOUNCEMENT_DATES.size();
  private static final int[] LOOKBACK_PERIODS = {5, 7, 10, 15};
  private static final int STD_DEV_FUT = 3;
  
  private static final String EXPORT_PATH = "src/analysis/resources/output/";

  /**
   * Computes past means and median
   * 
   * @throws IOException
   */
  public void spread_computation() throws IOException {
    long start, end;
    start = System.currentTimeMillis();
    IndexQuoteId idLibor = IndexQuoteId.of(IBOR_INDEX);
    IndexQuoteId idOnCmp = IndexQuoteId.of(ONCMP_INDEX);
    LocalDateDoubleTimeSeries tsLibor = TIME_SERIES.get(idLibor);
    LocalDateDoubleTimeSeries tsOnCmp = TIME_SERIES.get(idOnCmp);
    LocalDateDoubleTimeSeries tsSpread = tsLibor.intersection(tsOnCmp, (l, o) -> l - o);
    for (int looplookback = 0; looplookback < LOOKBACK_PERIODS.length; looplookback++) {
      List<LocalDateDoubleTimeSeries> tsRunningMean = new ArrayList<>();
      List<LocalDateDoubleTimeSeries> tsRunningMedian = new ArrayList<>();
      for (int loopdate = 0; loopdate < NB_ANNOUNCEMENT_DATES; loopdate++) {
        LocalDate startDate = ANNOUNCEMENT_DATES.get(loopdate).minusYears(LOOKBACK_PERIODS[looplookback]);
        LocalDateDoubleTimeSeriesBuilder tsMean = LocalDateDoubleTimeSeries.builder();
        LocalDateDoubleTimeSeriesBuilder tsMedian = LocalDateDoubleTimeSeries.builder();
        LocalDate currentDate = startDate.plusMonths(1); // The first month of mean is almost meaningless
        while (currentDate.isBefore(ANALYSIS_DATE)) {
          if (tsSpread.containsDate(currentDate)) {
            LocalDateDoubleTimeSeries tsSpreadLookback =
                tsSpread.subSeries(startDate, currentDate);
            tsMean.put(currentDate,
                tsSpreadLookback.stream().mapToDouble(pt -> pt.getValue()).average().getAsDouble());
            tsMedian.put(currentDate,
                Quantiles.median().compute(tsSpreadLookback.stream().mapToDouble(pt -> pt.getValue()).toArray()));
          }
          currentDate = currentDate.plusDays(1);
        }
        tsRunningMean.add(tsMean.build());
        tsRunningMedian.add(tsMedian.build());
      }
      /* Export */
      for (int loopdate = 0; loopdate < NB_ANNOUNCEMENT_DATES; loopdate++) {
        StringBuilder tsMeanExport = new StringBuilder();
        String nameMean = IBOR_INDEX.toString() + "SONIACMP-RUNNING-MEAN-" + ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" +
            LOOKBACK_PERIODS[looplookback] + "-" + IBOR_INDEX.getTenor().toString();
        ExportUtils.exportTimeSeries(nameMean, tsRunningMean.get(loopdate), tsMeanExport);
        ExportUtils.exportString(tsMeanExport.toString(), EXPORT_PATH + nameMean + ".csv");
        StringBuilder tsMedianExport = new StringBuilder();
        String nameMedian = IBOR_INDEX.toString() + "SONIACMP-RUNNING-MEDIAN-" + ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" +
            LOOKBACK_PERIODS[looplookback] + "-" + IBOR_INDEX.getTenor().toString();
        ExportUtils.exportTimeSeries(nameMedian, tsRunningMedian.get(loopdate), tsMedianExport);
        ExportUtils.exportString(tsMedianExport.toString(), EXPORT_PATH + nameMedian + ".csv");
      }
    }
    end = System.currentTimeMillis();
    System.out.println("Computation in " + (end - start) + " ms.");
  }

  /**
   * Computes and export future means within three standard deviations.
   * 
   * @throws IOException
   */
  public void spread_future_evolution() throws IOException {
    long start, end;
    start = System.currentTimeMillis();
    IndexQuoteId idLibor = IndexQuoteId.of(IBOR_INDEX);
    IndexQuoteId idOnCmp = IndexQuoteId.of(ONCMP_INDEX);
    LocalDateDoubleTimeSeries tsLibor = TIME_SERIES.get(idLibor);
    LocalDateDoubleTimeSeries tsOnCmp = TIME_SERIES.get(idOnCmp);
    LocalDateDoubleTimeSeries tsSpread = tsLibor.intersection(tsOnCmp, (l, o) -> l - o);
    /* Standard deviation */
    double[] spreadValues = tsSpread.values().toArray();
    int nbSpread = spreadValues.length;
    double stdDeviationDaily = 0.0;
    for (int i = 0; i < nbSpread - 1; i++) {
      double spreadDailyReturn = spreadValues[i + 1] - spreadValues[i];
      stdDeviationDaily += spreadDailyReturn * spreadDailyReturn;
    }
    stdDeviationDaily = Math.sqrt(stdDeviationDaily / (nbSpread - 1));
    /* Future */

    LocalDate startDateFuture = tsSpread.getLatestDate();
    for (int looplookback = 0; looplookback < LOOKBACK_PERIODS.length; looplookback++) {
      List<LocalDateDoubleTimeSeries> tsFutureMeanPlus = new ArrayList<>();
      List<LocalDateDoubleTimeSeries> tsFutureMeanMinus = new ArrayList<>();
      List<LocalDateDoubleTimeSeries> tsFutureMean0 = new ArrayList<>();
      for (int loopdate = 0; loopdate < NB_ANNOUNCEMENT_DATES; loopdate++) {
        LocalDate startDatePast = ANNOUNCEMENT_DATES.get(loopdate).minusYears(LOOKBACK_PERIODS[looplookback]);
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
          double m = nbLookback + (loopdays + 1);
          double spreadPlus =
              (spreadPart1 + Math.sqrt((loopdays + 2) * (loopdays + 1) * 0.5) * STD_DEV_FUT * stdDeviationDaily) / m;
          tsFuturePlus.put(currentDate, spreadPlus);
          double spreadMinus =
              (spreadPart1 - Math.sqrt((loopdays + 2) * (loopdays + 1) * 0.5) * STD_DEV_FUT * stdDeviationDaily) / m;
          tsFutureMinus.put(currentDate, spreadMinus);
          double spread0 = spreadPart1 / m;
          tsFuture0.put(currentDate, spread0);
          currentDate = CALENDAR.next(currentDate);
          loopdays++;
        }
        tsFutureMeanPlus.add(tsFuturePlus.build());
        tsFutureMeanMinus.add(tsFutureMinus.build());
        tsFutureMean0.add(tsFuture0.build());
        System.out.println("Current spread, " + LOOKBACK_PERIODS[looplookback] + ", " + ANNOUNCEMENT_DATES.get(loopdate) + ", " + currentSpread);
        System.out.println("Current mean spread, " + LOOKBACK_PERIODS[looplookback] + ", " + ANNOUNCEMENT_DATES.get(loopdate) + ", " + currentMeanSpread);
      }
      System.out.println("Daily standard deviation: " + stdDeviationDaily);
      /* Export */
      for (int loopdate = 0; loopdate < NB_ANNOUNCEMENT_DATES; loopdate++) {
        StringBuilder tsExportPlus = new StringBuilder();
        String namePlus = IBOR_INDEX.toString() + "SONIACMP-FUTURE-MEAN-P" + STD_DEV_FUT + "STD-" + ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" +
            LOOKBACK_PERIODS[looplookback] + "-" + IBOR_INDEX.getTenor().toString();
        ExportUtils.exportTimeSeries(namePlus, tsFutureMeanPlus.get(loopdate), tsExportPlus);
        ExportUtils.exportString(tsExportPlus.toString(), EXPORT_PATH + namePlus + ".csv");
        StringBuilder tsExportMinus = new StringBuilder();
        String nameMinus = IBOR_INDEX.toString() + "SONIACMP-FUTURE-MEAN-M" + STD_DEV_FUT + "STD-" + ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" +
            LOOKBACK_PERIODS[looplookback] + "-" + IBOR_INDEX.getTenor().toString();
        ExportUtils.exportTimeSeries(nameMinus, tsFutureMeanMinus.get(loopdate), tsExportMinus);
        ExportUtils.exportString(tsExportMinus.toString(), EXPORT_PATH + nameMinus + ".csv");
        StringBuilder tsExport0 = new StringBuilder();
        String name0 = IBOR_INDEX.toString() + "SONIACMP-FUTURE-MEAN-0-" + ANNOUNCEMENT_DATES.get(loopdate).toString() + "-" +
            LOOKBACK_PERIODS[looplookback] + "-" + IBOR_INDEX.getTenor().toString();
        ExportUtils.exportTimeSeries(name0, tsFutureMean0.get(loopdate), tsExport0);
        ExportUtils.exportString(tsExport0.toString(), EXPORT_PATH + name0 + ".csv");
      }
    }
    end = System.currentTimeMillis();
    System.out.println("Computation in " + (end - start) + " ms.");
  }

}

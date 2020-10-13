/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.analysis.fallback;

import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeriesBuilder;
import com.opengamma.strata.data.ObservableId;
import com.opengamma.strata.loader.csv.FixingSeriesCsvLoader;
import com.opengamma.strata.market.observable.IndexQuoteId;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;

import marc.henrard.murisq.basics.data.export.ExportUtils;
import marc.henrard.murisq.pricer.generic.FallbackUtils;

/**
 * Load historical overnight time series and generate IBOR period compounded rate in arrears.
 * <p>
 * Several computation approaches are proposed:
 * 1) the ON rates are compounded on the LIBOR periods. 
 * 2) the ON rates are compounded on the periods corresponding to LIBOR tenor and offset by 2 business days. 
 * 
 * @author Marc Henrard
 */
public class FallbackCompoundedInArrearsTimeSeriesUsdAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  // The start and end IBOR fixing dates for which the compounded rate is computed
//  private static final LocalDate START_DATE_SOFR = LocalDate.of(2018, 3, 29);
  private static final LocalDate START_DATE_SOFR = LocalDate.of(2014, 8, 22);
  private static final LocalDate END_DATE_SOFR = LocalDate.of(2020, 6, 9);
  private static final LocalDate START_DATE_EFFR = LocalDate.of(2000, 1, 3);
  private static final LocalDate END_DATE_EFFR = LocalDate.of(2020, 1, 29);
  private static final IborIndex IBOR_INDEX = IborIndices.USD_LIBOR_3M;
  private static final OvernightIndex ON_INDEX_EFFR = OvernightIndices.USD_FED_FUND;
  private static final HolidayCalendarId FIXING_CAL_EFFR = ON_INDEX_EFFR.getFixingCalendar();
  private static final HolidayCalendar FIXING_CAL_EFFR_IMPL = REF_DATA.getValue(FIXING_CAL_EFFR);
  private static final OvernightIndex ON_INDEX_SOFR = OvernightIndices.USD_SOFR;
  private static final HolidayCalendarId FIXING_CAL_SOFR = ON_INDEX_SOFR.getFixingCalendar();
  private static final HolidayCalendar FIXING_CAL_SOFR_IMPL = REF_DATA.getValue(FIXING_CAL_SOFR);
  
  private static final HolidayCalendarId FIXING_CAL_IBOR_ID = IBOR_INDEX.getFixingCalendar();
  private static final HolidayCalendar FIXING_CAL_IBOR_IMPL = REF_DATA.getValue(FIXING_CAL_IBOR_ID);
  
  private static final int OFFSET_DAYS = 0;
  private static final DaysAdjustment OFFSET_DAYSADJUST_SOFR = 
      DaysAdjustment.ofBusinessDays(-OFFSET_DAYS, FIXING_CAL_SOFR);
  private static final DaysAdjustment OFFSET_PLUS_DAYSADJUST_SOFR = 
      DaysAdjustment.ofBusinessDays(OFFSET_DAYS, FIXING_CAL_SOFR);
  
  /* Fixings */
  private static final List<ResourceLocator> FIXING_RESOURCES_SOFR = ImmutableList.of(
//      ResourceLocator.of("src/analysis/resources/fixing/USD-SOFR.csv"));
  ResourceLocator.of("src/analysis/resources/fixing/USD-SOFR-2.csv"));
//  ResourceLocator.of("src/analysis/resources/fixing/USD-SOFR-FWD.csv"));
  private static final Map<ObservableId, LocalDateDoubleTimeSeries> TIME_SERIES_SOFR =
      FixingSeriesCsvLoader.load(FIXING_RESOURCES_SOFR);
  private static final LocalDateDoubleTimeSeries SOFR_TS = TIME_SERIES_SOFR.get(IndexQuoteId.of(ON_INDEX_SOFR));
  private static final List<ResourceLocator> FIXING_RESOURCES_EFFR = ImmutableList.of(
      ResourceLocator.of("src/analysis/resources/fixing/USD-FED-FUND.csv"));
  private static final Map<ObservableId, LocalDateDoubleTimeSeries> TIME_SERIES_EFFR =
      FixingSeriesCsvLoader.load(FIXING_RESOURCES_EFFR);
  private static final LocalDateDoubleTimeSeries EFFR_TS = TIME_SERIES_EFFR.get(IndexQuoteId.of(ON_INDEX_EFFR));

  /* Computes the composition for SOFR on the periods corresponding to LIBOR tenor and offset by 2 business days. */
  @Test
  public void on_compounded_sofr_tenor_offset() throws IOException {
    LocalDateDoubleTimeSeriesBuilder builderTs = LocalDateDoubleTimeSeries.builder();
    List<LocalDate> fixingDates = new ArrayList<>();
    List<LocalDate> effectiveDates = new ArrayList<>();
    List<LocalDate> maturityDates = new ArrayList<>();
    List<Double> compInArrears = new ArrayList<>();
    LocalDate currentDate = OFFSET_PLUS_DAYSADJUST_SOFR.adjust(START_DATE_SOFR, REF_DATA);
    while (!currentDate.isAfter(END_DATE_SOFR)) {
      LocalDate effectiveDate = OFFSET_DAYSADJUST_SOFR.adjust(currentDate, REF_DATA);
      LocalDate maturityDate = MODIFIED_FOLLOWING.adjust(effectiveDate.plus(IBOR_INDEX.getTenor()), FIXING_CAL_SOFR_IMPL);
      fixingDates.add(currentDate);
      effectiveDates.add(effectiveDate);
      maturityDates.add(maturityDate);
      OvernightCompoundedRateComputation computation =
          OvernightCompoundedRateComputation.of(ON_INDEX_SOFR, effectiveDate, maturityDate, REF_DATA);
      double rate = FallbackUtils.compoundedInArrears(SOFR_TS, computation);
      compInArrears.add(rate);
      builderTs.put(currentDate, rate);
      currentDate = FIXING_CAL_IBOR_IMPL.next(currentDate);
    }
    /* Export */
    String tsName = ON_INDEX_SOFR.toString() + "CMP-OFFSET0-" + IBOR_INDEX.getTenor().toString();
    StringBuilder tsFileBuilder = new StringBuilder();
    ExportUtils.exportTimeSeries(tsName, builderTs.build(), tsFileBuilder);
    ExportUtils.exportString(tsFileBuilder.toString(), "src/analysis/resources/output/" + tsName + ".csv");
    /* Export with all dates */
    StringBuilder tsFile2Builder = new StringBuilder();
    tsFile2Builder.append("Reference, FixingDate, EffectiveDate, MaturityDate, Value\n");
    for (int i = 0; i < fixingDates.size(); i++) {
      tsFile2Builder.append(tsName);
      tsFile2Builder.append("," + fixingDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + effectiveDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + maturityDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + compInArrears.get(i) + "\n");
    }
    ExportUtils.exportString(tsFile2Builder.toString(), "src/analysis/resources/output/" + tsName + "-dates.csv");
  }

  /* Computes the composition for SOFR on the LIBOR periods. */
  @Test
  public void on_compounded_sofr_libor_period() throws IOException {
    LocalDateDoubleTimeSeriesBuilder builderTs = LocalDateDoubleTimeSeries.builder();
    List<LocalDate> fixingDates = new ArrayList<>();
    List<LocalDate> effectiveDates = new ArrayList<>();
    List<LocalDate> maturityDates = new ArrayList<>();
    List<Double> compInArrears = new ArrayList<>();
    LocalDate currentDate = START_DATE_SOFR;
    while (!currentDate.isAfter(END_DATE_SOFR)) {
      IborIndexObservation iborObs = IborIndexObservation.of(IBOR_INDEX, currentDate, REF_DATA);
      fixingDates.add(currentDate);
      effectiveDates.add(iborObs.getEffectiveDate());
      maturityDates.add(iborObs.getMaturityDate());
      OvernightCompoundedRateComputation computation =
          OvernightCompoundedRateComputation
              .of(ON_INDEX_SOFR, FIXING_CAL_SOFR_IMPL.previousOrSame(iborObs.getEffectiveDate()), 
                  FIXING_CAL_SOFR_IMPL.previousOrSame(iborObs.getMaturityDate()), REF_DATA);
      // Note: the dates of the ON computation need to be adjusted to fit the ON benchmark calendar.
      // Choice here (arbitrary): preceding for both effective and maturity date
      double rate = FallbackUtils.compoundedInArrears(SOFR_TS, computation);
      compInArrears.add(rate);
      builderTs.put(currentDate, rate);
      currentDate = FIXING_CAL_IBOR_IMPL.next(currentDate);
    }
    /* Export */
//    String tsName = ON_INDEX_SOFR.toString() + "CMP-" + IBOR_INDEX.getTenor().toString();
    String tsName = ON_INDEX_SOFR.toString() + "PRECMP-" + IBOR_INDEX.getTenor().toString();
//    String tsName = ON_INDEX_SOFR.toString() + "FWDCMP-" + IBOR_INDEX.getTenor().toString();
    StringBuilder tsFileBuilder = new StringBuilder();
    ExportUtils.exportTimeSeries(tsName, builderTs.build(), tsFileBuilder);
    ExportUtils.exportString(tsFileBuilder.toString(), "src/analysis/resources/output/" + tsName + ".csv");
    /* Export with all dates */
    StringBuilder tsFile2Builder = new StringBuilder();
    tsFile2Builder.append("Reference, FixingDate, EffectiveDate, MaturityDate, Value\n");
    for (int i = 0; i < fixingDates.size(); i++) {
      tsFile2Builder.append(tsName);
      tsFile2Builder.append("," + fixingDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + effectiveDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + maturityDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + compInArrears.get(i) + "\n");
    }
    ExportUtils.exportString(tsFile2Builder.toString(), "src/analysis/resources/output/" + tsName + "-dates.csv");
  }

@Test
  public void on_compounded_effr() throws IOException {
    LocalDateDoubleTimeSeriesBuilder builderTs = LocalDateDoubleTimeSeries.builder();
    List<LocalDate> fixingDates = new ArrayList<>();
    List<LocalDate> effectiveDates = new ArrayList<>();
    List<LocalDate> maturityDates = new ArrayList<>();
    List<Double> compInArrears = new ArrayList<>();
    LocalDate currentDate = START_DATE_EFFR;
    while (!currentDate.isAfter(END_DATE_EFFR)) {
      IborIndexObservation iborObs = IborIndexObservation.of(IBOR_INDEX, currentDate, REF_DATA);
      fixingDates.add(currentDate);
      effectiveDates.add(iborObs.getEffectiveDate());
      maturityDates.add(iborObs.getMaturityDate());
      OvernightCompoundedRateComputation computation =
          OvernightCompoundedRateComputation
              .of(ON_INDEX_EFFR, FIXING_CAL_EFFR_IMPL.previousOrSame(iborObs.getEffectiveDate()), 
                  FIXING_CAL_EFFR_IMPL.previousOrSame(iborObs.getMaturityDate()), REF_DATA);
      // Note: the dates of the ON computation need to be adjusted to fit the ON benchmark calendar.
      // Choice here (arbitrary): preceding for both effective and maturity date
      double rate = FallbackUtils.compoundedInArrears(EFFR_TS, computation);
      compInArrears.add(rate);
      builderTs.put(currentDate, rate);
      currentDate = FIXING_CAL_IBOR_IMPL.next(currentDate);
    }
    /* Export */
    String tsName = ON_INDEX_EFFR.toString() + "CMP-" + IBOR_INDEX.getTenor().toString();
    StringBuilder tsFileBuilder = new StringBuilder();
    ExportUtils.exportTimeSeries(tsName, builderTs.build(), tsFileBuilder);
    ExportUtils.exportString(tsFileBuilder.toString(), "src/analysis/resources/output/" + tsName + ".csv");
    /* Export with all dates */
    StringBuilder tsFile2Builder = new StringBuilder();
    tsFile2Builder.append("Reference, FixingDate, EffectiveDate, MaturityDate, Value\n");
    for (int i = 0; i < fixingDates.size(); i++) {
      tsFile2Builder.append(tsName);
      tsFile2Builder.append("," + fixingDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + effectiveDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + maturityDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + compInArrears.get(i) + "\n");
    }
    ExportUtils.exportString(tsFile2Builder.toString(), "src/analysis/resources/output/" + tsName + "-dates.csv");
  }
  
}

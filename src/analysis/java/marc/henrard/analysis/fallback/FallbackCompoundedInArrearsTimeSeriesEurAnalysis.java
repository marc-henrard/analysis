/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.analysis.fallback;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.basics.index.ImmutableOvernightIndex;
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
 * 
 * @author Marc Henrard
 */
@Test
public class FallbackCompoundedInArrearsTimeSeriesEurAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  // The start and end IBOR fixing dates for which the compounded rate is computed
  private static final LocalDate START_DATE = LocalDate.of(2017, 3, 15);
  private static final LocalDate END_DATE = LocalDate.of(2019, 7, 5);
  private static final IborIndex IBOR_INDEX = IborIndices.EUR_EURIBOR_6M;
  private static final ImmutableOvernightIndex ON_INDEX = (ImmutableOvernightIndex) OvernightIndices.EUR_ESTR;
  private static final HolidayCalendarId FIXING_CAL_IBOR_ID = IBOR_INDEX.getFixingCalendar();
  private static final HolidayCalendarId FIXING_CAL_ON = ON_INDEX.getFixingCalendar();
  private static final HolidayCalendar FIXING_CAL_IBOR_IMPL = REF_DATA.getValue(FIXING_CAL_IBOR_ID);
  private static final HolidayCalendar FIXING_CAL_ON_IMPL = REF_DATA.getValue(FIXING_CAL_ON);
  
  /* Fixings */
  private static final List<ResourceLocator> FIXING_RESOURCES = ImmutableList.of(
      ResourceLocator.of("src/analysis/resources/fixing/EUR-ESTR-PRE.csv"));
  private static final Map<ObservableId, LocalDateDoubleTimeSeries> TIME_SERIES =
      FixingSeriesCsvLoader.load(FIXING_RESOURCES);
  private static final LocalDateDoubleTimeSeries ON_TS = TIME_SERIES.get(IndexQuoteId.of(ON_INDEX));

  /**
   * Computes the compounded rates and export in CSV files.
   * @throws IOException
   */
  public void on_compounded() throws IOException {
    LocalDateDoubleTimeSeriesBuilder builderTs = LocalDateDoubleTimeSeries.builder();
    List<LocalDate> fixingDates = new ArrayList<>();
    List<LocalDate> effectiveDates = new ArrayList<>();
    List<LocalDate> maturityDates = new ArrayList<>();
    List<Double> compInArrears = new ArrayList<>();
    LocalDate currentDate = START_DATE;
    while (!currentDate.isAfter(END_DATE)) {
      IborIndexObservation iborObs = IborIndexObservation.of(IBOR_INDEX, currentDate, REF_DATA);
      fixingDates.add(currentDate);
      effectiveDates.add(iborObs.getEffectiveDate());
      maturityDates.add(iborObs.getMaturityDate());
      OvernightCompoundedRateComputation computation =
          OvernightCompoundedRateComputation
              .of(ON_INDEX, FIXING_CAL_ON_IMPL.previousOrSame(iborObs.getEffectiveDate()), 
                  FIXING_CAL_ON_IMPL.previousOrSame(iborObs.getMaturityDate()), REF_DATA);
      // Note: the dates of the ON computation need to be adjusted to fit the ON benchmark calendar.
      // Choice here (arbitrary): preceding for both effective and maturity date
      double rate = FallbackUtils.compoundedInArrears(ON_TS, computation);
      compInArrears.add(rate);
      builderTs.put(currentDate, rate);
      currentDate = FIXING_CAL_IBOR_IMPL.next(currentDate);
    }
    /* Export */
    String tsName = ON_INDEX.toString() + "CMP-" + IBOR_INDEX.getTenor().toString();
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
      ExportUtils.exportString(tsFile2Builder.toString(), "src/analysis/resources/output/" + tsName + "-dates.csv");
    }
  }
}

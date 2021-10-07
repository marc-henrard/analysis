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
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.basics.index.ImmutableOvernightIndex;
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
 * 
 * @author Marc Henrard
 */
public class FallbackCompoundedInArrearsTimeSeriesEurAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final OvernightIndex ON_INDEX_ESTR = OvernightIndices.EUR_ESTR;
  private static final IborIndex IBOR_INDEX = IborIndices.EUR_EURIBOR_1M;
  private static final HolidayCalendarId FIXING_CAL_ESTR = ON_INDEX_ESTR.getFixingCalendar();
  private static final HolidayCalendar FIXING_CAL_ESTR_IMPL = REF_DATA.getValue(FIXING_CAL_ESTR);
  // The start and end IBOR fixing dates for which the compounded rate is computed
  private static final LocalDate START_DATE_ESTR = LocalDate.of(2017, 3, 15);
  private static final LocalDate LAST_FIXING_ESTR = LocalDate.of(2021, 1, 11);
  private static final LocalDate END_DATE_ESTR =
      FIXING_CAL_ESTR_IMPL.previous(
          FIXING_CAL_ESTR_IMPL.previous(
              FIXING_CAL_ESTR_IMPL.previousOrSame(LAST_FIXING_ESTR.minus(IBOR_INDEX.getTenor()))));
  private static final ImmutableOvernightIndex ON_INDEX = (ImmutableOvernightIndex) OvernightIndices.EUR_ESTR;
  private static final HolidayCalendarId FIXING_CAL_IBOR_ID = IBOR_INDEX.getFixingCalendar();
  private static final HolidayCalendar FIXING_CAL_IBOR_IMPL = REF_DATA.getValue(FIXING_CAL_IBOR_ID);

  private static final int OFFSET_DAYS = 2;
  private static final DaysAdjustment OFFSET_DAYSADJUST_ESTR = 
      DaysAdjustment.ofBusinessDays(-OFFSET_DAYS, FIXING_CAL_ESTR);
  private static final DaysAdjustment OFFSET_PLUS_DAYSADJUST_ESTR = 
      DaysAdjustment.ofBusinessDays(OFFSET_DAYS, FIXING_CAL_ESTR);
  
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
  @Test
  public void on_compounded() throws IOException {
    LocalDateDoubleTimeSeriesBuilder builderTs = LocalDateDoubleTimeSeries.builder();
    List<LocalDate> fixingDates = new ArrayList<>();
    List<LocalDate> effectiveDates = new ArrayList<>();
    List<LocalDate> maturityDates = new ArrayList<>();
    List<Double> compInArrears = new ArrayList<>();
    LocalDate currentDate = OFFSET_PLUS_DAYSADJUST_ESTR.adjust(START_DATE_ESTR, REF_DATA);
    while (!currentDate.isAfter(END_DATE_ESTR)) {
      LocalDate effectiveDate = OFFSET_DAYSADJUST_ESTR.adjust(currentDate, REF_DATA);
      LocalDate maturityDate = MODIFIED_FOLLOWING.adjust(effectiveDate.plus(IBOR_INDEX.getTenor()), FIXING_CAL_ESTR_IMPL);
      fixingDates.add(currentDate);
      effectiveDates.add(effectiveDate);
      maturityDates.add(maturityDate);
      OvernightCompoundedRateComputation computation =
          OvernightCompoundedRateComputation.of(ON_INDEX_ESTR, effectiveDate, maturityDate, REF_DATA);
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

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
 * <p>
 * Several computation approaches are proposed:
 * 1) the ON rates are compounded on the LIBOR periods. 
 * 2) the ON rates are compounded on the periods corresponding to LIBOR tenor and offset by 2 business days. 
 * 
 * @author Marc Henrard
 */
public class FallbackCompoundedInArrearsTimeSeriesChfAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final OvernightIndex ON_INDEX = OvernightIndices.CHF_SARON;
  private static final HolidayCalendarId FIXING_CAL = ON_INDEX.getFixingCalendar();
  private static final HolidayCalendar FIXING_CAL_IMPL = REF_DATA.getValue(FIXING_CAL);
  // The start and end IBOR fixing dates for which the compounded rate is computed
  private static final LocalDate START_DATE_ON = LocalDate.of(2000, 1, 3);
  private static final LocalDate LAST_FIXING_ON = LocalDate.of(2021, 8, 25);
  private static final IborIndex IBOR_INDEX = IborIndices.CHF_LIBOR_6M;
  private static final LocalDate END_DATE_ON =
      FIXING_CAL_IMPL.previous(
          FIXING_CAL_IMPL.previous(
              FIXING_CAL_IMPL.previousOrSame(LAST_FIXING_ON.minus(IBOR_INDEX.getTenor()))));
  private static final ImmutableOvernightIndex ON_INDEX_TMP = (ImmutableOvernightIndex) OvernightIndices.GBP_SONIA;
  private static final HolidayCalendarId FIXING_CAL_IBOR_ID = IBOR_INDEX.getFixingCalendar();
  private static final HolidayCalendarId FIXING_CAL_ON = ON_INDEX_TMP.getFixingCalendar();
  private static final HolidayCalendar FIXING_CAL_IBOR_IMPL = REF_DATA.getValue(FIXING_CAL_IBOR_ID);

  private static final int OFFSET_DAYS = 2;
  private static final DaysAdjustment OFFSET_DAYSADJUST_ON = 
      DaysAdjustment.ofBusinessDays(-OFFSET_DAYS, FIXING_CAL_ON);
  
  /* Fixings */
  private static final List<ResourceLocator> FIXING_RESOURCES = ImmutableList.of(
      ResourceLocator.of("src/analysis/resources/fixing/CHF-SARON.csv"));
  private static final Map<ObservableId, LocalDateDoubleTimeSeries> TIME_SERIES =
      FixingSeriesCsvLoader.load(FIXING_RESOURCES);
  private static final LocalDateDoubleTimeSeries ON_TS = TIME_SERIES.get(IndexQuoteId.of(ON_INDEX));

  /* Computes the composition for SARON on the periods corresponding to LIBOR tenor and offset by 2 business days. 
   * Mechanism to compute ISDA-designed historical spread adjustment. */
  @Test
  public void on_compounded() throws IOException {
    LocalDateDoubleTimeSeriesBuilder builderTs = LocalDateDoubleTimeSeries.builder();
    List<LocalDate> fixingDates = new ArrayList<>(); // LIBOR fixing date
    List<LocalDate> startCompositionDates = new ArrayList<>(); // ON composition start date
    List<LocalDate> endCompositionDates = new ArrayList<>(); // ON composition end date
    List<Double> compInArrears = new ArrayList<>();
    LocalDate currentDate = START_DATE_ON; // LIBOR fixing Date
    while (!currentDate.isAfter(END_DATE_ON)) {
      LocalDate referenceDate = IBOR_INDEX.calculateEffectiveFromFixing(currentDate, REF_DATA);
        // Reference date to compute the ISDA T-2 workaround
      LocalDate startCompositionDate = OFFSET_DAYSADJUST_ON.adjust(referenceDate, REF_DATA);
      LocalDate endCompositionDate = MODIFIED_FOLLOWING.adjust(
          startCompositionDate.plus(IBOR_INDEX.getTenor()), FIXING_CAL_IMPL);
      fixingDates.add(currentDate);
      startCompositionDates.add(startCompositionDate);
      endCompositionDates.add(endCompositionDate);
      OvernightCompoundedRateComputation computation =
          OvernightCompoundedRateComputation.of(ON_INDEX, startCompositionDate, endCompositionDate, REF_DATA);
      double rate = FallbackUtils.compoundedInArrears(ON_TS, computation);
      compInArrears.add(rate);
      builderTs.put(currentDate, rate);
      currentDate = FIXING_CAL_IBOR_IMPL.next(currentDate);
    }
    /* Export */
    String tsName = ON_INDEX.toString() + "CMP-" + IBOR_INDEX.getTenor().toString();
    StringBuilder tsFileBuilder = new StringBuilder();
    ExportUtils.exportTimeSeries(tsName, builderTs.build(), tsFileBuilder);
    String fileName = ON_INDEX.toString() + "CMP-OFFSET" + OFFSET_DAYS + "-" + IBOR_INDEX.getTenor().toString();
    ExportUtils.exportString(tsFileBuilder.toString(), "src/analysis/resources/output/" + fileName + ".csv");
    /* Export with all dates */
    StringBuilder tsFile2Builder = new StringBuilder();
    tsFile2Builder.append("Reference, FixingDate, EffectiveDate, MaturityDate, Value\n");
    for (int i = 0; i < fixingDates.size(); i++) {
      tsFile2Builder.append(tsName);
      tsFile2Builder.append("," + fixingDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + startCompositionDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + endCompositionDates.get(i).format(DateTimeFormatter.ISO_DATE));
      tsFile2Builder.append("," + compInArrears.get(i) + "\n");
      ExportUtils.exportString(tsFile2Builder.toString(), "src/analysis/resources/output/" + fileName + "-dates.csv");
    }
  }
}

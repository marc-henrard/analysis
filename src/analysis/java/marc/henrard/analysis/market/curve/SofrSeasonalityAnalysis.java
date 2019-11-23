/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_SOFR;

import java.time.LocalDate;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.loader.csv.FixingSeriesCsvLoader;
import com.opengamma.strata.market.observable.IndexQuoteId;

/**
 * Estimate intra-month seasonality for SOFR.
 * 
 * @author Marc Henrard
 */
public class SofrSeasonalityAnalysis {
  
  private static final ResourceLocator SOFR_LOCATOR = 
      ResourceLocator.of("src/analysis/resources/fixing/USD-SOFR.csv");
  private static final LocalDateDoubleTimeSeries TS_SOFR = FixingSeriesCsvLoader.load(SOFR_LOCATOR)
      .get(IndexQuoteId.of(USD_SOFR));
  private static final ResourceLocator EFFR_LOCATOR = 
      ResourceLocator.of("src/analysis/resources/fixing/USD-FED-FUND.csv");
  private static final LocalDateDoubleTimeSeries TS_EFFR = FixingSeriesCsvLoader.load(EFFR_LOCATOR)
      .get(IndexQuoteId.of(USD_FED_FUND));

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendarId CALENDAR_ID = HolidayCalendarIds.USGS;
  private static final HolidayCalendar CALENDAR = REF_DATA.getValue(CALENDAR_ID);
  
  /**
   * Analyzes SOFR seasonality. Divide monthly data is several buckets (all buckets in business days):
   * - First day of month
   * - First day on or after 15 month
   * - Second last day of the month
   * - Last month
   * - Other
   */
  @Test
  public void seasonality() {
    LocalDate startDate = TS_SOFR.getEarliestDate();
    LocalDate endDate = TS_SOFR.getLatestDate();

    LocalDate startFullMonths = startDate.equals(CALENDAR.nextOrSame(startDate.withDayOfMonth(1))) 
        ? startDate 
        : CALENDAR.nextOrSame(startDate.plusMonths(1).withDayOfMonth(1));
    System.out.println("Start full months: " + startFullMonths);
    LocalDate endFullMonths = CALENDAR.previous(endDate.withDayOfMonth(1));
    System.out.println("End full months: " + endFullMonths);
    LocalDate currentStartMonth = startFullMonths;
    double monthStartSpreadAverage = 0.0d;
    int nbMonthsStart = 0;
    double monthStart2SpreadAverage = 0.0d;
    int nbMonthsStart2 = 0;
    double month15SpreadAverage = 0.0d;
    int nbMonths15 = 0;
    double month2LastSpreadAverage = 0.0d;
    int nbMonths2Last = 0;
    double monthLastSpreadAverage = 0.0d;
    int nbMonthsLast = 0;
    double monthOtherSpreadAverage = 0.0d;
    int nbDaysOther = 0;
    double monthAllSpreadAverage = 0.0d;
    int nbDaysAll = 0;
    while (currentStartMonth.isBefore(endFullMonths)) {
      LocalDate currentStart2 = CALENDAR.next(currentStartMonth);
      LocalDate current15 = CALENDAR.nextOrSame(currentStartMonth.withDayOfMonth(15));
      LocalDate currentLast = CALENDAR.previous(currentStartMonth.plusMonths(1).withDayOfMonth(1));
      LocalDate current2Last = CALENDAR.previous(currentLast);
      // First of month
      OptionalDouble effrStart = TS_EFFR.get(currentStartMonth);
      OptionalDouble sofrStart = TS_SOFR.get(currentStartMonth);
      if (effrStart.isPresent()) {
        double startMonthSpread = sofrStart.getAsDouble() - effrStart.getAsDouble();
        monthStartSpreadAverage += startMonthSpread;
        nbMonthsStart++;
      }
      // Secong of month
      OptionalDouble effrStart2 = TS_EFFR.get(currentStart2);
      OptionalDouble sofrStart2 = TS_SOFR.get(currentStart2);
      if (effrStart2.isPresent()) {
        double monthStart2Spread = sofrStart2.getAsDouble() - effrStart2.getAsDouble();
        monthStart2SpreadAverage += monthStart2Spread;
        nbMonthsStart2++;
      }
      // 15 of month
      OptionalDouble effr15 = TS_EFFR.get(current15);
      OptionalDouble sofr15 = TS_SOFR.get(current15);
      if (effr15.isPresent()) {
        double month15Spread = sofr15.getAsDouble() - effr15.getAsDouble();
        month15SpreadAverage += month15Spread;
        nbMonths15++;
      }
      // Second last of month
      OptionalDouble effr2Last = TS_EFFR.get(current2Last);
      OptionalDouble sofr2Last = TS_SOFR.get(current2Last);
      if (effr2Last.isPresent()) {
        double month2LastSpread = sofr2Last.getAsDouble() - effr2Last.getAsDouble();
        month2LastSpreadAverage += month2LastSpread;
        nbMonths2Last++;
      }
      // Last of month
      OptionalDouble effrLast = TS_EFFR.get(currentLast);
      OptionalDouble sofrLast = TS_SOFR.get(currentLast);
      if (effrLast.isPresent()) {
        double monthLastSpread = sofrLast.getAsDouble() - effrLast.getAsDouble();
        monthLastSpreadAverage += monthLastSpread;
        nbMonthsLast++;
      }
      // Others and all
      LocalDate currentOther = CALENDAR.next(currentStartMonth);
      while (currentOther.getMonth().equals(currentStartMonth.getMonth())) {
        if (!currentOther.equals(currentStartMonth) && !currentOther.equals(currentStart2) &&
            !currentOther.equals(current15) && !currentOther.equals(current2Last) &&
            !currentOther.equals(currentLast)) {
          OptionalDouble effrOther = TS_EFFR.get(currentOther);
          OptionalDouble sofrOther = TS_SOFR.get(currentOther);
          if (effrOther.isPresent()) {
            double monthOtherSpread = sofrOther.getAsDouble() - effrOther.getAsDouble();
            monthOtherSpreadAverage += monthOtherSpread;
            nbDaysOther++;
          }
        }
        OptionalDouble effrAll = TS_EFFR.get(currentOther);
        OptionalDouble sofrAll = TS_SOFR.get(currentOther);
        if (effrAll.isPresent()) {
          double monthAllSpread = sofrAll.getAsDouble() - effrAll.getAsDouble();
          monthAllSpreadAverage += monthAllSpread;
          nbDaysAll++;
        }
        currentOther = CALENDAR.next(currentOther);
      }
      currentStartMonth = CALENDAR.nextOrSame(currentStartMonth.plusMonths(1).withDayOfMonth(1));
    }
    monthStartSpreadAverage /= nbMonthsStart;
    System.out.println("Average first day month: " + monthStartSpreadAverage);
    monthStart2SpreadAverage /= nbMonthsStart2;
    System.out.println("Average second day month: " + monthStart2SpreadAverage);
    month15SpreadAverage /= nbMonths15;
    System.out.println("Average 15 month: " + month15SpreadAverage);
    month2LastSpreadAverage /= nbMonths2Last;
    System.out.println("Average 2nd last month: " + month2LastSpreadAverage);
    monthLastSpreadAverage /= nbMonthsLast;
    System.out.println("Average last month: " + monthLastSpreadAverage);
    monthOtherSpreadAverage /= nbDaysOther;
    System.out.println("Average other: " + monthOtherSpreadAverage);
    monthAllSpreadAverage /= nbDaysAll;
    System.out.println("Average all: " + monthAllSpreadAverage);
  }

}

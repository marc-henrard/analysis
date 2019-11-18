/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.fallback;

import static com.opengamma.strata.basics.date.DateSequences.MONTHLY_IMM;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarIds;

import marc.henrard.murisq.basics.data.export.ExportUtils;

/**
 * Computes the dates related to the Euro-dollar and SOFR futures and compare to OTC fallback.
 * <p>
 * Code used to produce the table in the blog: https://murisq.blogspot.com/2019/11/cme-ed-futures-fallback.html
 * 
 * @author Marc Henrard
 */
@Test
public class FallbackEurodollarFuturesDatesAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate ANALYSIS_DATE = LocalDate.of(2019, 11, 15);
  private static final LocalDate FINAL_DATE = LocalDate.of(2032, 1, 1);
  
  private static final String EXPORT_PATH = "src/analysis/resources/output/";

  /**
   * Computes dates for Euro-dollar futures, SOFR futures and OTC coupons (IBOR period) and export in csv.
   */
  public void futures_dates() {
    List<LocalDate> edStart = new ArrayList<>();
    List<LocalDate> edEnd = new ArrayList<>();
    List<LocalDate> srStart = new ArrayList<>();
    List<LocalDate> srEnd = new ArrayList<>();
    List<LocalDate> otcStart = new ArrayList<>();
    List<LocalDate> otcEnd = new ArrayList<>();
    DaysAdjustment t2 = DaysAdjustment.ofBusinessDays(-2, HolidayCalendarIds.USGS);
    LocalDate currentImmDate = MONTHLY_IMM.next(ANALYSIS_DATE);
    while (currentImmDate.isBefore(FINAL_DATE)) {
      edStart.add(currentImmDate);
      srStart.add(currentImmDate);
      otcStart.add(t2.adjust(currentImmDate, REF_DATA));
      LocalDate end3M = USD_LIBOR_3M.calculateMaturityFromEffective(currentImmDate, REF_DATA);
      edEnd.add(end3M);
      srEnd.add(MONTHLY_IMM.nth(currentImmDate, 3));
      otcEnd.add(t2.adjust(end3M, REF_DATA));
      currentImmDate = MONTHLY_IMM.next(currentImmDate);
    }
    // Export differences
    StringBuilder builderDiff = new StringBuilder("ED, OTC start, ED end, OTC end, ED length, SOFR length, OTC length\n");
    for(int i=0; i<edStart.size(); i++) {
      builderDiff.append(edStart.get(i).toString() + ",");
      builderDiff.append(ChronoUnit.DAYS.between(edStart.get(i), otcStart.get(i)) + ",");
      builderDiff.append(ChronoUnit.DAYS.between(srEnd.get(i), edEnd.get(i)) + ",");
      builderDiff.append(ChronoUnit.DAYS.between(srEnd.get(i), otcEnd.get(i)) + ",");
      builderDiff.append(ChronoUnit.DAYS.between(edStart.get(i), edEnd.get(i)) + ",");
      builderDiff.append(ChronoUnit.DAYS.between(srStart.get(i), srEnd.get(i)) + ",");
      builderDiff.append(ChronoUnit.DAYS.between(otcStart.get(i), otcEnd.get(i)) + "\n");
    }
    ExportUtils.exportString(builderDiff.toString(), EXPORT_PATH + "ed-sofr-days.csv");
  }

}

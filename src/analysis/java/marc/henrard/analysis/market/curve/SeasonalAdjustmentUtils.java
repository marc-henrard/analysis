/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;

/**
 * Utilities related to the computation of intra-month seasonal adjustments for interest rate curves.
 * 
 * @author Marc Henrard
 */
public class SeasonalAdjustmentUtils {
  
  /**
   * Generate a curve with intra-month seasonal adjustments.
   * 
   * @param adjustments  DoubleArray with 1st, 2nd, 15th and last day of the month adjustments
   * @param calibrationDate  the calibration date
   * @param calendar  the relevant calendar for the non-good business days
   * @param nbMonths  the number of months for which the adjustment curve should be computed
   * @return  the discount factors adjustment curve
   */
  public static InterpolatedNodalCurve seasonalityAdjustment(
      DoubleArray adjustments,
      LocalDate calibrationDate,
      HolidayCalendar calendar,
      DayCount dayCount,
      int nbMonths) {
    
    double jump1st = adjustments.get(0);
    double jump2nd = adjustments.get(1);
    double jump15th = adjustments.get(2);
    double jumpLast = adjustments.get(3);
    LocalDate current1stMonth = calendar.nextOrSame(calibrationDate.withDayOfMonth(1));
    List<Double> xValues = new ArrayList<>();
    List<Double> yValues = new ArrayList<>();
    double currentDiscountFactor = 1.0;
    for (int loopmonth = 0; loopmonth < nbMonths; loopmonth++) {
      LocalDate current2ndMonth = calendar.next(current1stMonth);
      if(!current1stMonth.isBefore(calibrationDate)) {
        xValues.add(dayCount.relativeYearFraction(calibrationDate, current1stMonth));
        yValues.add(currentDiscountFactor);
        currentDiscountFactor *= 1.0d / 
            (1.0d + dayCount.relativeYearFraction(current1stMonth, current2ndMonth) * jump1st);
      }
      LocalDate current3rdMonth = calendar.next(current2ndMonth);
      if(!current2ndMonth.isBefore(calibrationDate)) {
        xValues.add(dayCount.relativeYearFraction(calibrationDate, current2ndMonth));
        yValues.add(currentDiscountFactor);
        currentDiscountFactor *= 1.0d / 
            (1.0d + dayCount.relativeYearFraction(current2ndMonth, current3rdMonth) * jump2nd);
        xValues.add(dayCount.relativeYearFraction(calibrationDate, current3rdMonth));
        yValues.add(currentDiscountFactor);
      }
      LocalDate current15thMonth = calendar.nextOrSame(current1stMonth.withDayOfMonth(15));
      LocalDate current16thMonth = calendar.next(current15thMonth);
      if(!current2ndMonth.isBefore(calibrationDate)) {
        xValues.add(dayCount.relativeYearFraction(calibrationDate, current15thMonth));
        yValues.add(currentDiscountFactor);
        currentDiscountFactor *= 1.0d / 
            (1.0d + dayCount.relativeYearFraction(current15thMonth, current16thMonth) * jump15th);
        xValues.add(dayCount.relativeYearFraction(calibrationDate, current16thMonth));
        yValues.add(currentDiscountFactor);
      }
      LocalDate currentLastMonth = 
          calendar.previousOrSame(current1stMonth.plusMonths(1).withDayOfMonth(1).minusDays(1));
      xValues.add(dayCount.relativeYearFraction(calibrationDate, currentLastMonth));
      yValues.add(currentDiscountFactor);
      current1stMonth = calendar.nextOrSame(current1stMonth.plusMonths(1).withDayOfMonth(1));
      currentDiscountFactor *= 1.0d / 
          (1.0d + dayCount.relativeYearFraction(currentLastMonth, current1stMonth) * jumpLast);
    }
    CurveName curveName = CurveName.of("FixedCurve");
    CurveMetadata metadata = Curves.discountFactors(curveName, dayCount);
    return InterpolatedNodalCurve.of(metadata, 
        DoubleArray.copyOf(xValues), DoubleArray.copyOf(yValues), 
        CurveInterpolators.LINEAR, CurveExtrapolators.FLAT, CurveExtrapolators.FLAT);
  }

}

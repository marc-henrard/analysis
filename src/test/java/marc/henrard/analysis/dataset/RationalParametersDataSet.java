/**
 * Copyright (C) 2017 - Marc Henrard.
 */
package marc.henrard.analysis.dataset;

import java.time.LocalTime;
import java.time.ZoneId;

import com.opengamma.strata.pricer.DiscountFactors;

import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.generic.TimeMeasurement;
import marc.henrard.risq.model.rationalmulticurve.RationalOneFactorSimpleHWShapedParameters;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorHWShapeCstParameters;

/**
 * Generates rational multi-curve model parameters for analysis purposes.
 * 
 * @author Marc Henrard
 */
public class RationalParametersDataSet {
  
  /* Rational model data: HW shaped b0 */
  private static final double A = 0.75;
  private static final double B_0_0 = 0.50;
  private static final double ETA = 0.01;
  private static final double KAPPA = 0.03;

  private static final double A2 = 0.50;
  
  private static final TimeMeasurement TIME_MEAS = ScaledSecondTime.DEFAULT;
  
  /**
   * Creates a one-factor rational model parameter set with a=0.75, b00=0.50, eta=0.01 and kappa =0.03.
   * 
   * @param time  the parameters time
   * @param zone  the parameters zone
   * @param discountFactors  the discount factors
   * @return  the parameters
   */
  public static RationalOneFactorSimpleHWShapedParameters oneFactorHWShaped(
      LocalTime time,
      ZoneId zone,
      DiscountFactors discountFactors) {
    
    return RationalOneFactorSimpleHWShapedParameters.builder()
        .a(A)
        .b00(B_0_0)
        .eta(ETA)
        .kappa(KAPPA)
        .timeMeasure(TIME_MEAS)
        .discountFactors(discountFactors)
        .valuationTime(time)
        .valuationZone(zone)
        .build();
  }
  
  /**
   * Creates a two-factor rational model parameter set with a1=0.75, a2=0.50, correlation=0.0, 
   * b00=0.50, eta=0.01 and kappa =0.03. The two additive spreads c1 and c2 are 0.
   * 
   * @param time  the parameters time
   * @param zone  the parameters zone
   * @param discountFactors  the discount factors
   * @return  the parameters
   */
  public static RationalTwoFactorHWShapeCstParameters twoFactorHWShaped(
  LocalTime time,
  ZoneId zone,
  DiscountFactors discountFactors) {
    
    return RationalTwoFactorHWShapeCstParameters.builder()
        .a1(A)
        .a2(A2)
        .correlation(0.0d)
        .b00(B_0_0)
        .eta(ETA)
        .kappa(KAPPA)
        .c1(0.0d)
        .c2(0.0d)
        .timeMeasure(TIME_MEAS)
        .discountFactors(discountFactors)
        .valuationTime(time)
        .valuationZone(zone).build();
  }

}

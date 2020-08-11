/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.product.overnighttransition;

import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_SOFR;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.math.impl.integration.IntegratorRepeated2D;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.math.impl.matrix.CommonsMatrixAlgebra;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebra;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.DiscountOvernightIndexRates;
import com.opengamma.strata.pricer.rate.IborIndexRates;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.OvernightIndexRates;

import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;
import marc.henrard.murisq.pricer.swap.DiscountingTransition2HWHybridConvexityCalculator;

/**
 * Analysis of the convexity adjustment to be included in the collateral rate/discounting transition.
 * 
 * @author Marc Henrard
 */
public class SofrTransition2HWHybridConvexityAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2019, 10, 4);
  private static final LocalDate BIG_BANG_DATE = LocalDate.of(2020, 10, 16);
  private static final LocalDate FIXING_DATE = LocalDate.of(2030, 10, 16);
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final HolidayCalendar CALENDAR_FIXING_IBOR = REF_DATA.getValue(USD_LIBOR_3M.getFixingCalendar());
  
  private static final String PATH_CONFIG_SO = "src/analysis/resources/curve-config/USD-DSCSOOIS-FFOIS-L3MIRS/";
  private static final CurveGroupName GROUP_NAME_SO = CurveGroupName.of("USD-DSCSOOIS-FFOIS-L3MIRS");
  private static final ResourceLocator FILE_GROUP_SO = 
      ResourceLocator.of(PATH_CONFIG_SO + "USD-DSCSOOIS-FFOIS-L3MIRS-group.csv");
  private static final ResourceLocator FILE_SETTINGS_SO =
      ResourceLocator.of(PATH_CONFIG_SO + "USD-DSCSOOIS-FFOIS-L3MIRS-settings-zrlinear.csv");
  private static final ResourceLocator FILE_NODES_SO =
      ResourceLocator.of(PATH_CONFIG_SO + "USD-DSCSOOIS-FFOIS-L3MIRS-nodes.csv");
  private static final RatesCurveGroupDefinition GROUP_DEF_SO = RatesCalibrationCsvLoader
      .load(FILE_GROUP_SO, FILE_SETTINGS_SO, FILE_NODES_SO).get(GROUP_NAME_SO);
  
  private static final String FILE_QUOTES = 
      "src/analysis/resources/quotes/MARKET-DATA-" + VALUATION_DATE.toString() + ".csv";
  private static final MarketData MARKET_DATA = MarketData
      .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));

  /* Multi-curve SOFR PAI and swap sensitivity */
  private static final ImmutableRatesProvider MULTICURVE_SO =
      CALIBRATOR.calibrate(GROUP_DEF_SO, MARKET_DATA, REF_DATA);
  
  /* Hull-White */
  private static final HullWhiteOneFactorPiecewiseConstantFormulas FORMULAS_HW = 
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  private static final double MEAN_REVERSION = 0.05;
  private static final double SIGMA = 0.01;
  private static final DoubleArray VOLATILITY1_CST = DoubleArray.of(SIGMA);
  private static final DoubleArray VOLATILITY1_CST_TIME = DoubleArray.of();
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAM_1 =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY1_CST, VOLATILITY1_CST_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW1 =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(HW_PARAM_1, DayCounts.ACT_365F, 
          VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels"));
  private static final DoubleArray VOLATILITY2_CST = DoubleArray.of(SIGMA);
  private static final DoubleArray VOLATILITY2_CST_TIME = DoubleArray.of();
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAM_2 =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY2_CST, VOLATILITY2_CST_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW2 =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(HW_PARAM_2, DayCounts.ACT_365F, 
          VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels"));
  private static final double RHO_12 = 0.90;
  private static final double RHO_13 = 0.25;
  private static final double RHO_23 = 0.25;
  private static final double A = 0.50; // Spread volatility
  
  private static final MatrixAlgebra ALGEBRA = new CommonsMatrixAlgebra();
  /** Value related to the numerical integration. */
  private static final double LIMIT_INT = 10.0; // Equivalent to + infinity in normal integrals
  private static final Double[] INTEGRAL_BOUNDS_LOWER = new Double[] {-LIMIT_INT, -LIMIT_INT };
  private static final Double[] INTEGRAL_BOUNDS_UPPER = new Double[] {LIMIT_INT, LIMIT_INT };
//  private static final Double[] INTEGRAL3_BOUNDS_LOWER = new Double[] {-LIMIT_INT, -LIMIT_INT, -LIMIT_INT };
//  private static final Double[] INTEGRAL3_BOUNDS_UPPER = new Double[] {LIMIT_INT, LIMIT_INT, LIMIT_INT };
  private static final double TOL_ABS = 1.0E-1;
  private static final double TOL_REL = 1.0E-6;
  private static final int NB_INTEGRATION_STEPS_DEFAULT = 100;
  private static final RungeKuttaIntegrator1D INTEGRATOR_1D =
      new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, NB_INTEGRATION_STEPS_DEFAULT);
  private static final IntegratorRepeated2D INTEGRATOR_2D = new IntegratorRepeated2D(INTEGRATOR_1D);
  private static final IntegratorRepeated3D INTEGRATOR_3D = new IntegratorRepeated3D(INTEGRATOR_1D);
  private static final DiscountingTransition2HWHybridConvexityCalculator CONVEXITY_CALC = 
      DiscountingTransition2HWHybridConvexityCalculator.DEFAULT;

  /**
   * Convexity adjustment: fixing date impact
   */
  @Test
  public void adjusted_forward_dates(){
    
    /* Description */
    LocalDate iborFixingDateStart = LocalDate.of(2023, 10, 23);
    LocalDate iborFixingDateEnd = LocalDate.of(2050, 10, 23);
    DiscountFactors Pc1 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_FED_FUND)).getDiscountFactors();
    DiscountFactors Pc2 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_SOFR)).getDiscountFactors();
    IborIndexRates libor = MULTICURVE_SO.iborIndexRates(USD_LIBOR_3M);
    OvernightIndexRates sofr = MULTICURVE_SO.overnightIndexRates(USD_SOFR);

    System.out.println("Date, Non-adjusted, adjusted, difference");
    LocalDate iborFixingDate = CALENDAR_FIXING_IBOR.nextOrSame(iborFixingDateStart);
    int loopdate = 0;
    while (!iborFixingDate.isAfter(iborFixingDateEnd)) {
      IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, iborFixingDate, REF_DATA);
      LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
      LocalDate iborEndAccrual = obsIbor.getMaturityDate();
      OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_SOFR, iborStartAccrual, REF_DATA);
      double delta = obsIbor.getYearFraction();
      double Pc2u = Pc2.discountFactor(iborStartAccrual);
      double Pc1v = Pc1.discountFactor(iborEndAccrual);
      double Pc2v = Pc2.discountFactor(iborEndAccrual);
      double iborRatec2 = libor.rate(obsIbor);
      double onRatec2 = sofr.periodRate(obsOn, iborEndAccrual);
      double spread = iborRatec2 - onRatec2;
      double spreadPvc2 = spread * Pc2v;
      DoubleArray c = CONVEXITY_CALC.modelCoefficients(BIG_BANG_DATE, obsIbor, HW1, HW2);
      double theta = HW1.relativeTime(iborFixingDate);
      double forwardAdjusted = adjustedForward(c, Pc2u, Pc1v, Pc2v, iborRatec2, delta, theta, spreadPvc2, RHO_12, RHO_13, RHO_23);
      double adjustment = iborRatec2 - forwardAdjusted;
      System.out.println(iborFixingDate.toString() + ", " + iborRatec2 + ", " + forwardAdjusted + ", " + adjustment);
      loopdate++;
      iborFixingDate = CALENDAR_FIXING_IBOR.nextOrSame(iborFixingDateStart.plusMonths(6*loopdate));
    }
  }
  
  /**
   * Convexity adjustment: correlation between ON
   */
  @Test
  public void adjusted_forward_correlation_on(){
    
    /* Description */
    DiscountFactors Pc1 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_FED_FUND)).getDiscountFactors();
    DiscountFactors Pc2 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_SOFR)).getDiscountFactors();
    IborIndexRates libor = MULTICURVE_SO.iborIndexRates(USD_LIBOR_3M);
    OvernightIndexRates sofr = MULTICURVE_SO.overnightIndexRates(USD_SOFR);
    
    IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, FIXING_DATE, REF_DATA);
    LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
    LocalDate iborEndAccrual = obsIbor.getMaturityDate();
    OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_SOFR, iborStartAccrual, REF_DATA);
    double delta = obsIbor.getYearFraction();
    double Pc2u = Pc2.discountFactor(iborStartAccrual);
    double Pc1v = Pc1.discountFactor(iborEndAccrual);
    double Pc2v = Pc2.discountFactor(iborEndAccrual);
    double iborRatec2 = libor.rate(obsIbor);
    double onRatec2 = sofr.periodRate(obsOn, iborEndAccrual);
    double spread = iborRatec2 - onRatec2;
    double spreadPvc2 = spread * Pc2v;
    System.out.println("Correlation, Non-adjusted, adjusted, difference");
    DoubleArray correlations = DoubleArray.of(20, i -> 0.50 + i * 0.025);
    for(int loopcor=0; loopcor<correlations.size(); loopcor++) {
      DoubleArray c = CONVEXITY_CALC.modelCoefficients(BIG_BANG_DATE, obsIbor, HW1, HW2);
      double theta = HW1.relativeTime(FIXING_DATE);
      double forwardAdjusted = 
          adjustedForward(c, Pc2u, Pc1v, Pc2v, iborRatec2, delta, theta, spreadPvc2, correlations.get(loopcor), RHO_13, RHO_23);
      double adjustment = iborRatec2 - forwardAdjusted;
      System.out.println(correlations.get(loopcor) + ", " + iborRatec2 + ", " + forwardAdjusted + ", " + adjustment);
    }
  }
  
  /**
   * Convexity adjustment: Mean reversion
   */
  @Test
  public void adjusted_forward_meanreversion(){
    
    /* Description */
    DiscountFactors Pc1 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_FED_FUND)).getDiscountFactors();
    DiscountFactors Pc2 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_SOFR)).getDiscountFactors();
    IborIndexRates libor = MULTICURVE_SO.iborIndexRates(USD_LIBOR_3M);
    OvernightIndexRates sofr = MULTICURVE_SO.overnightIndexRates(USD_SOFR);
    
    IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, FIXING_DATE, REF_DATA);
    LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
    LocalDate iborEndAccrual = obsIbor.getMaturityDate();
    OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_SOFR, iborStartAccrual, REF_DATA);
    double delta = obsIbor.getYearFraction();
    double Pc2u = Pc2.discountFactor(iborStartAccrual);
    double Pc1v = Pc1.discountFactor(iborEndAccrual);
    double Pc2v = Pc2.discountFactor(iborEndAccrual);
    double iborRatec2 = libor.rate(obsIbor);
    double onRatec2 = sofr.periodRate(obsOn, iborEndAccrual);
    double spread = iborRatec2 - onRatec2;
    double spreadPvc2 = spread * Pc2v;
    System.out.println("MeanReversion, Non-adjusted, adjusted, difference");
    DoubleArray kappa = DoubleArray.of(20, i -> 0.005 + i * 0.005);
    for (int loopmr = 0; loopmr < kappa.size(); loopmr++) {
      DoubleArray c = CONVEXITY_CALC.modelCoefficients(BIG_BANG_DATE, obsIbor, 
          HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
              HullWhiteOneFactorPiecewiseConstantParameters.of(kappa.get(loopmr), VOLATILITY1_CST, VOLATILITY1_CST_TIME), 
              DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")), 
          HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
              HullWhiteOneFactorPiecewiseConstantParameters.of(kappa.get(loopmr), VOLATILITY2_CST, VOLATILITY2_CST_TIME), 
              DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")));
      double theta = HW1.relativeTime(FIXING_DATE);
      double forwardAdjusted =
          adjustedForward(c, Pc2u, Pc1v, Pc2v, iborRatec2, delta, theta, spreadPvc2, RHO_12, RHO_13, RHO_23);
      double adjustment = iborRatec2 - forwardAdjusted;
      System.out.println(kappa.get(loopmr) + ", " + iborRatec2 + ", " + forwardAdjusted + ", " + adjustment);
    }
  }
  
  /**
   * Convexity adjustment: Volatility same for both rates
   */
  @Test
  public void adjusted_forward_volatilitysame(){
    
    /* Description */
    LocalDate iborFixingDate = LocalDate.of(2030, 10, 16);
    DiscountFactors Pc1 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_FED_FUND)).getDiscountFactors();
    DiscountFactors Pc2 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_SOFR)).getDiscountFactors();
    IborIndexRates libor = MULTICURVE_SO.iborIndexRates(USD_LIBOR_3M);
    OvernightIndexRates sofr = MULTICURVE_SO.overnightIndexRates(USD_SOFR);
    
    IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, iborFixingDate, REF_DATA);
    LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
    LocalDate iborEndAccrual = obsIbor.getMaturityDate();
    OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_SOFR, iborStartAccrual, REF_DATA);
    double delta = obsIbor.getYearFraction();
    double Pc2u = Pc2.discountFactor(iborStartAccrual);
    double Pc1v = Pc1.discountFactor(iborEndAccrual);
    double Pc2v = Pc2.discountFactor(iborEndAccrual);
    double iborRatec2 = libor.rate(obsIbor);
    double onRatec2 = sofr.periodRate(obsOn, iborEndAccrual);
    double spread = iborRatec2 - onRatec2;
    double spreadPvc2 = spread * Pc2v;
    System.out.println("Volatility, Non-adjusted, adjusted, difference");
    DoubleArray vol = DoubleArray.of(21, i -> 0.005 + i * 0.0005);
    for (int loopmr = 0; loopmr < vol.size(); loopmr++) {
      DoubleArray c = CONVEXITY_CALC.modelCoefficients(BIG_BANG_DATE, obsIbor, 
          HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
              HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, DoubleArray.of(vol.get(loopmr)), VOLATILITY1_CST_TIME), 
              DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")), 
          HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
              HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, DoubleArray.of(vol.get(loopmr)), VOLATILITY2_CST_TIME), 
              DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")));
      double theta = HW1.relativeTime(iborFixingDate);
      double forwardAdjusted =
          adjustedForward(c, Pc2u, Pc1v, Pc2v, iborRatec2, delta, theta, spreadPvc2, RHO_12, RHO_13, RHO_23);
      double adjustment = iborRatec2 - forwardAdjusted;
      System.out.println(vol.get(loopmr) + ", " + iborRatec2 + ", " + forwardAdjusted + ", " + adjustment);
    }
  }
  
  /**
   * Convexity adjustment: Volatility different for both rates
   */
  @Test
  public void adjusted_forward_volatilitydiff(){
    
    /* Description */
    DiscountFactors Pc1 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_FED_FUND)).getDiscountFactors();
    DiscountFactors Pc2 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_SOFR)).getDiscountFactors();
    IborIndexRates libor = MULTICURVE_SO.iborIndexRates(USD_LIBOR_3M);
    OvernightIndexRates sofr = MULTICURVE_SO.overnightIndexRates(USD_SOFR);
    
    IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, FIXING_DATE, REF_DATA);
    LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
    LocalDate iborEndAccrual = obsIbor.getMaturityDate();
    OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_SOFR, iborStartAccrual, REF_DATA);
    double delta = obsIbor.getYearFraction();
    double Pc2u = Pc2.discountFactor(iborStartAccrual);
    double Pc1v = Pc1.discountFactor(iborEndAccrual);
    double Pc2v = Pc2.discountFactor(iborEndAccrual);
    double iborRatec2 = libor.rate(obsIbor);
    double onRatec2 = sofr.periodRate(obsOn, iborEndAccrual);
    double spread = iborRatec2 - onRatec2;
    double spreadPvc2 = spread * Pc2v;
    System.out.println("Volatility, Non-adjusted, adjusted, difference");
    DoubleArray vol = DoubleArray.of(20, i -> 0.0075 + i * 0.00025);
    for (int loopmr = 0; loopmr < vol.size(); loopmr++) {
      DoubleArray c = CONVEXITY_CALC.modelCoefficients(BIG_BANG_DATE, obsIbor,
          HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
              HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY1_CST, VOLATILITY1_CST_TIME),
              DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")),
          HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
              HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, DoubleArray.of(vol.get(loopmr)),
                  VOLATILITY2_CST_TIME),
              DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")));
      double theta = HW1.relativeTime(FIXING_DATE);
      double forwardAdjusted =
          adjustedForward(c, Pc2u, Pc1v, Pc2v, iborRatec2, delta, theta, spreadPvc2, RHO_12, RHO_13, RHO_23);
      double adjustment = iborRatec2 - forwardAdjusted;
      System.out.println(vol.get(loopmr) + ", " + iborRatec2 + ", " + forwardAdjusted + ", " + adjustment);
    }
  }
  
  /**
   * Convexity adjustment: Correlation and volatility combination
   */
  @Test
  public void adjusted_forward_corvol(){
    
    /* Description */
    DiscountFactors Pc1 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_FED_FUND)).getDiscountFactors();
    DiscountFactors Pc2 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_SOFR)).getDiscountFactors();
    IborIndexRates libor = MULTICURVE_SO.iborIndexRates(USD_LIBOR_3M);
    OvernightIndexRates sofr = MULTICURVE_SO.overnightIndexRates(USD_SOFR);
    
    IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, FIXING_DATE, REF_DATA);
    LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
    LocalDate iborEndAccrual = obsIbor.getMaturityDate();
    OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_SOFR, iborStartAccrual, REF_DATA);
    double delta = obsIbor.getYearFraction();
    double Pc2u = Pc2.discountFactor(iborStartAccrual);
    double Pc1v = Pc1.discountFactor(iborEndAccrual);
    double Pc2v = Pc2.discountFactor(iborEndAccrual);
    double iborRatec2 = libor.rate(obsIbor);
    double onRatec2 = sofr.periodRate(obsOn, iborEndAccrual);
    double spread = iborRatec2 - onRatec2;
    double spreadPvc2 = spread * Pc2v;
    DoubleArray rho12 = DoubleArray.of(28, i -> 0.30 + i * 0.025).concat(0.99);
    DoubleArray volRatio = DoubleArray.of(30, i -> 0.05 + i * 0.05);
    System.out.print("0.0");
    for (int loopvol = 0; loopvol < volRatio.size(); loopvol++) {
      System.out.print(", " + volRatio.get(loopvol));
    }
    System.out.println();
    for (int looprho = 0; looprho < rho12.size(); looprho++) {
      System.out.print(rho12.get(looprho));
      for (int loopvol = 0; loopvol < volRatio.size(); loopvol++) {
        DoubleArray c = CONVEXITY_CALC.modelCoefficients(BIG_BANG_DATE, obsIbor,
            HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
                HullWhiteOneFactorPiecewiseConstantParameters.of(
                    MEAN_REVERSION, VOLATILITY1_CST, VOLATILITY1_CST_TIME),
                DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")),
            HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
                HullWhiteOneFactorPiecewiseConstantParameters.of(
                    MEAN_REVERSION, DoubleArray.of(SIGMA * volRatio.get(loopvol)), VOLATILITY2_CST_TIME),
                DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")));
        double theta = HW1.relativeTime(FIXING_DATE);
        double forwardAdjusted =
            adjustedForward(c, Pc2u, Pc1v, Pc2v, iborRatec2, delta, theta, spreadPvc2, rho12.get(looprho), RHO_13,
                RHO_23);
        double adjustment = iborRatec2 - forwardAdjusted;
        System.out.print(", " + adjustment);
      }
      System.out.println();
    }
  }
  /**
   * Convexity adjustment: fixing date and mean reversion
   */
  @Test
  public void adjusted_forward_dates_meanreversion(){
    
    /* Description */
    LocalDate iborFixingDateStart = LocalDate.of(2023, 10, 16);
    LocalDate iborFixingDateEnd = LocalDate.of(2050, 10, 16);
    DiscountFactors Pc1 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_FED_FUND)).getDiscountFactors();
    DiscountFactors Pc2 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_SOFR)).getDiscountFactors();
    IborIndexRates libor = MULTICURVE_SO.iborIndexRates(USD_LIBOR_3M);
    OvernightIndexRates sofr = MULTICURVE_SO.overnightIndexRates(USD_SOFR);

    DoubleArray kappa = DoubleArray.of(20, i -> 0.005 + i * 0.005);
    System.out.print("0.0");
    for (int loopmr = 0; loopmr < kappa.size(); loopmr++) {
      System.out.print(", " + kappa.get(loopmr));
    }
    System.out.println();
    LocalDate iborFixingDate = CALENDAR_FIXING_IBOR.nextOrSame(iborFixingDateStart);
    int loopdate = 0;
    while (!iborFixingDate.isAfter(iborFixingDateEnd)) {
      IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, iborFixingDate, REF_DATA);
      LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
      LocalDate iborEndAccrual = obsIbor.getMaturityDate();
      OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_SOFR, iborStartAccrual, REF_DATA);
      double delta = obsIbor.getYearFraction();
      double Pc2u = Pc2.discountFactor(iborStartAccrual);
      double Pc1v = Pc1.discountFactor(iborEndAccrual);
      double Pc2v = Pc2.discountFactor(iborEndAccrual);
      double iborRatec2 = libor.rate(obsIbor);
      double onRatec2 = sofr.periodRate(obsOn, iborEndAccrual);
      double spread = iborRatec2 - onRatec2;
      double spreadPvc2 = spread * Pc2v;
      System.out.print(iborFixingDate.toEpochDay());
      for (int loopmr = 0; loopmr < kappa.size(); loopmr++) {
        DoubleArray c = CONVEXITY_CALC.modelCoefficients(BIG_BANG_DATE, obsIbor,
            HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
                HullWhiteOneFactorPiecewiseConstantParameters.of(kappa.get(loopmr), VOLATILITY1_CST,
                    VOLATILITY1_CST_TIME),
                DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")),
            HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
                HullWhiteOneFactorPiecewiseConstantParameters.of(kappa.get(loopmr), VOLATILITY2_CST,
                    VOLATILITY2_CST_TIME),
                DayCounts.ACT_365F, VALUATION_DATE, LocalTime.NOON, ZoneId.of("Europe/Brussels")));
        double theta = HW1.relativeTime(iborFixingDate);
        double forwardAdjusted =
            adjustedForward(c, Pc2u, Pc1v, Pc2v, iborRatec2, delta, theta, spreadPvc2, RHO_12, RHO_13, RHO_23);
        double adjustment = iborRatec2 - forwardAdjusted;
        System.out.print( ", " + adjustment);
      }
      System.out.println();
      loopdate++;
      iborFixingDate = CALENDAR_FIXING_IBOR.nextOrSame(iborFixingDateStart.plusMonths(6*loopdate));
    }
  }

  /**
   * Computation by 3D integration and semi-analytical for check
   */
  @Test
  public void discount_factors_forwards_semi_analytical(){
    
    long start, end;
    start = System.currentTimeMillis();
    /* Description */
    IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, FIXING_DATE, REF_DATA);
    LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
    LocalDate iborEndAccrual = obsIbor.getMaturityDate();
    OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_SOFR, iborStartAccrual, REF_DATA);
    double delta = obsIbor.getYearFraction();
    DiscountFactors Pc1 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_FED_FUND)).getDiscountFactors();
    DiscountFactors Pc2 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_SOFR)).getDiscountFactors();
    double Pc2u = Pc2.discountFactor(iborStartAccrual);
    double Pc1v = Pc1.discountFactor(iborEndAccrual);
    double Pc2v = Pc2.discountFactor(iborEndAccrual);
    double iborRatec2 = MULTICURVE_SO.iborIndexRates(USD_LIBOR_3M).rate(obsIbor);
    double onRatec2 = MULTICURVE_SO.overnightIndexRates(USD_SOFR).periodRate(obsOn, iborEndAccrual);
    double spread = iborRatec2 - onRatec2;
    double spreadPvc2 = spread * Pc2v;
    /* Model coefficients */
    DoubleArray c = CONVEXITY_CALC.modelCoefficients(BIG_BANG_DATE, obsIbor, HW1, HW2);
    double theta = HW1.relativeTime(FIXING_DATE);
    end = System.currentTimeMillis();
    System.out.println("Init: " + (end - start) + " ms");
    

    System.out.println("Part, non-adjusted, adjustment, result");
    
    /* part 1: Pc2u */
    start = System.currentTimeMillis();
    double notAdj1 = Pc1v / Pc2v * Pc2u / delta;
//    DoubleMatrix sigma1 = DoubleMatrix.ofUnsafe(
//        new double[][] {{c.get(0) * c.get(0), RHO_12 * c.get(4), RHO_12 * c.get(5)},
//            {RHO_12 * c.get(4), c.get(1) * c.get(1), c.get(6)},
//            {RHO_12 * c.get(5), c.get(6), c.get(2) * c.get(2)}});
    DoubleMatrix sigma1 = DoubleMatrix.ofUnsafe( // standard
        new double[][] {{1.0d, RHO_12 * c.get(4) / (c.get(0) * c.get(1)), RHO_12 * c.get(5) / (c.get(0) * c.get(2))},
            {RHO_12 * c.get(4) / (c.get(0) * c.get(1)), 1.0d, c.get(6) / (c.get(1) * c.get(2))},
            {RHO_12 * c.get(5) / (c.get(0) * c.get(2)), c.get(6) / (c.get(1) * c.get(2)), 1.0d}});
    double adjustment1 = CONVEXITY_CALC.convexityAdjustmentNI(sigma1, c.get(0), c.get(1), c.get(2));
    double convexityAdjusted1 = notAdj1 * adjustment1;
    System.out.println("Part 1, " + notAdj1 + ", " + adjustment1 + ", " + convexityAdjusted1);
    end = System.currentTimeMillis();
//    System.out.println("Convexity 1: " + (end - start) + " ms");
    
    /* part 2: Pc2v */ 
    // Note: no adjustment for part 2: simplification
    start = System.currentTimeMillis();
    double notAdj2 = -Pc1v / Pc2v * Pc2v / delta;
//    DoubleMatrix sigma2 = DoubleMatrix.ofUnsafe(
//        new double[][] {{c.get(0) * c.get(0), RHO_12 * c.get(4), RHO_12 * c.get(4)},
//            {RHO_12 * c.get(4), c.get(1) * c.get(1), c.get(7)},
//            {RHO_12 * c.get(4), c.get(7), c.get(3) * c.get(3)}});
    double adjustment2 = 1.0d; // convexityAdjustment(sigma2);
    double convexityAdjusted2 = notAdj2 * adjustment2;
    System.out.println("Part 2, " + notAdj2 + ", " + adjustment2 + ", " + convexityAdjusted2);
    end = System.currentTimeMillis();
//    System.out.println("Convexity 2: " + (end - start) + " ms");
    
    /* part 3: Sc2j */
    start = System.currentTimeMillis();
    double sqtheta = Math.sqrt(theta);
    double notAdj3 = Pc1v / Pc2v * spreadPvc2;
//    DoubleMatrix sigma3 = DoubleMatrix.ofUnsafe(
//        new double[][] {{c.get(0) * c.get(0), RHO_12 * c.get(4), A * RHO_13 * c.get(8)},
//            {RHO_12 * c.get(4), c.get(1) * c.get(1), A * RHO_23 * c.get(9)},
//            {A * RHO_13 * c.get(8), A * RHO_23 * c.get(9), A * A * theta}});
    DoubleMatrix sigma3 = DoubleMatrix.ofUnsafe(
        new double[][] {{1.0d, RHO_12 * c.get(4) / (c.get(0) * c.get(1)), A * RHO_13 * c.get(8) / (c.get(0) * A*sqtheta)},
            {RHO_12 * c.get(4) / (c.get(0) * c.get(1)), 1.0d, A * RHO_23 * c.get(9) / (c.get(1) * A*sqtheta)},
            {A * RHO_13 * c.get(8) / (c.get(0) * A*sqtheta), A * RHO_23 * c.get(9) / (c.get(1) * A*sqtheta), 1.0d}});
    double adjustment3 = CONVEXITY_CALC.convexityAdjustmentNI(sigma3, c.get(0), c.get(1), A*sqtheta);
    double convexityAdjusted3 = notAdj3 * adjustment3;
    System.out.println("Part 3, " + notAdj3 + ", " + adjustment3 + ", " + convexityAdjusted3);
    end = System.currentTimeMillis();
//    System.out.println("Convexity 3: " + (end - start) + " ms");
    
    /* aggregation */
    double forwardAdjusted = (convexityAdjusted1 + convexityAdjusted2 + convexityAdjusted3) / Pc1v;
    double adjustment = iborRatec2 - forwardAdjusted;
    System.out.println("Total, " + iborRatec2 + ", ," + forwardAdjusted + ", " + adjustment);
    
  }

  /**
   * Computation by 3D integration and semi-analytical for check
   */
  @Test
  public void discount_factors_forwards_3Dcheck(){

    double limitInt = 2.0; // Equivalent to + infinity in normal integrals
    int nbIntegrationStep = 20;
    Double[] lower3 = new Double[] {-limitInt, -limitInt, -limitInt };
    Double[] upper3 = new Double[] {limitInt, limitInt, limitInt };
    RungeKuttaIntegrator1D integrator1D =
        new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, nbIntegrationStep);
    IntegratorRepeated3D integrator3D = new IntegratorRepeated3D(integrator1D);
    
    long start, end;
    start = System.currentTimeMillis();
    LocalDate iborFixingDate = LocalDate.of(2030, 10, 23);
    IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, iborFixingDate, REF_DATA);
    LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
    LocalDate iborEndAccrual = obsIbor.getMaturityDate();
    OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_SOFR, iborStartAccrual, REF_DATA);
    double delta = obsIbor.getYearFraction();
    DiscountFactors Pc1 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_FED_FUND)).getDiscountFactors();
    DiscountFactors Pc2 = 
        ((DiscountOvernightIndexRates) MULTICURVE_SO.overnightIndexRates(USD_SOFR)).getDiscountFactors();
    double Pc2u = Pc2.discountFactor(iborStartAccrual);
    double Pc1v = Pc1.discountFactor(iborEndAccrual);
    double Pc2v = Pc2.discountFactor(iborEndAccrual);
    double iborRatec2 = MULTICURVE_SO.iborIndexRates(USD_LIBOR_3M).rate(obsIbor);
    double onRatec2 = MULTICURVE_SO.overnightIndexRates(USD_SOFR).periodRate(obsOn, iborEndAccrual);
    double spreadPvc2 = (iborRatec2 - onRatec2) * Pc2v;
    /*  Ignoring convexity */
    double FnoAdjFormula = Pc1v / Pc2v * (1.0d / delta * (Pc2u - Pc2v) + spreadPvc2) / Pc1v;
    System.out.println("Forward direct: " + iborRatec2);
    System.out.println("Forward formula no convexity: " + FnoAdjFormula);
    System.out.println("Difference: " + (iborRatec2 - FnoAdjFormula));
    /* Model coefficients */
    double t = HW1.relativeTime(BIG_BANG_DATE);
    double theta = HW1.relativeTime(iborFixingDate);
    double u = HW1.relativeTime(iborStartAccrual);
    double v = HW1.relativeTime(iborEndAccrual);
    double alpha1tv = FORMULAS_HW.alphaCashAccount(HW_PARAM_1, 0, t, v);
    double alpha2tv = FORMULAS_HW.alphaCashAccount(HW_PARAM_2, 0, t, v);
    double alpha2thetau = FORMULAS_HW.alphaCashAccount(HW_PARAM_2, 0, theta, u);
    double alpha2thetav = FORMULAS_HW.alphaCashAccount(HW_PARAM_2, 0, theta, v);
    double cross12tvv = FORMULAS_HW.varianceCrossTermCashAccount(HW_PARAM_1, HW_PARAM_2, 0, t, v, v);
    double cross12tvu = FORMULAS_HW.varianceCrossTermCashAccount(HW_PARAM_1, HW_PARAM_2, 0, t, v, u);
    double cross22tvu = FORMULAS_HW.varianceCrossTermCashAccount(HW_PARAM_2, HW_PARAM_2, 0, t, v, u);
    double cross22thetauv = FORMULAS_HW.varianceCrossTermCashAccount(HW_PARAM_2, HW_PARAM_2, 0, theta, u, v);
    end = System.currentTimeMillis();
    System.out.println("Init: " + (end - start) + " ms");
    
    /* part 1: Pc2u */
    start = System.currentTimeMillis();
    double coefNoAdj = Pc1v / Pc2v * Pc2u / delta;
    DoubleMatrix sigma1 = DoubleMatrix.ofUnsafe(
        new double[][] {{alpha1tv * alpha1tv, RHO_12 * cross12tvv, RHO_12 * cross12tvu},
            {RHO_12 * cross12tvv, alpha2tv * alpha2tv, cross22tvu},
            {RHO_12 * cross12tvu, cross22tvu, alpha2thetau * alpha2thetau}});
    DoubleMatrix sigma1_1 = ALGEBRA.getInverse(sigma1);
    double detSigma1 = ALGEBRA.getDeterminant(sigma1);
    double rho11 = sigma1_1.get(0, 0);
    double rho21 = sigma1_1.get(1, 0);
    double rho31 = sigma1_1.get(2, 0);
    DoubleMatrix sigma1_1_reduction = DoubleMatrix.ofUnsafe(new double[][] 
        {{sigma1_1.get(1, 1), sigma1_1.get(1, 2)},
        {sigma1_1.get(2, 1), sigma1_1.get(2, 2)}});
    double omega = 1.0;
    double b = alpha1tv;
    BiFunction<Double, Double, Double> c1fd =
        (x2, x3) -> Math.exp(-0.5 * 
            (x2 * x2 * sigma1_1_reduction.get(0, 0) 
                + 2 * x2 * x3 * sigma1_1_reduction.get(0, 1) 
                + x3 * x3 * sigma1_1_reduction.get(1, 1)
                + omega * b * b 
                - Math.pow(omega + rho21 * x2 + rho31 * x3, 2) / rho11)
            + x2 + 0.5 * alpha2tv * alpha2tv - x3 - 0.5 * alpha2thetau * alpha2thetau
            );
    double adj1 = 1.0 / (Math.sqrt(detSigma1 * rho11) * 2 * Math.PI);
    double adj2 = integration2D(c1fd);
    double adjTot = adj1 * adj2;
    double coefAdj = coefNoAdj * adjTot;
    System.out.println("Part 1 (2D integral): ");
    System.out.println("  -> No adjustment, " + coefNoAdj);
    System.out.println("  -> Adjust, " + adjTot);
    System.out.println("  -> Result, " + coefAdj);
    end = System.currentTimeMillis();
    System.out.println("Convexity 2: " + (end - start) + " ms");

    start = System.currentTimeMillis();
    Function<DoubleArray, Double> c1fd3 =
        array -> {
          DoubleMatrix arrayAsMatrix = DoubleMatrix.of(1, 3, (i,j) -> array.get(j));
          return Math.exp(
              - array.get(0) - 0.5 * alpha1tv * alpha1tv
              + array.get(1) + 0.5 * alpha2tv * alpha2tv 
              - array.get(2) - 0.5 * alpha2thetau * alpha2thetau
               -0.5 * 
              ((DoubleMatrix) ALGEBRA.multiply(ALGEBRA.multiply(arrayAsMatrix, sigma1_1), ALGEBRA.getTranspose(arrayAsMatrix))).get(0, 0));
        };
    double integralAdj = 0.0;
    try {
      integralAdj = integrator3D.integrate(c1fd3, lower3, upper3);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    double adj3 = Math.sqrt(ALGEBRA.getDeterminant(sigma1_1)) / Math.pow(2 * Math.PI, 3.0d/2.0d);
    double part1 = coefNoAdj * adj3 * integralAdj;
    System.out.println("Part 1 (3D integral): ");
    System.out.println("  -> No adjustment, " + coefNoAdj);
    System.out.println("  -> Adjust, " + (adj3 * integralAdj));
    System.out.println("  -> Result, " + part1);
    end = System.currentTimeMillis();
    System.out.println("Convexity 3D: " + (end - start) + " ms");
    
    /* part 2: Pc2v */
    /* part 3: Sc2j */
    
  }
  
  private double adjustedForward(DoubleArray c,
      double Pc2u,
      double Pc1v,
      double Pc2v,
      double iborRatec2,
      double delta,
      double theta,
      double spreadPvc2,
      double rho12,
      double rho13,
      double rho23) {
    
    /* part 1: Pc2u */
    double notAdj1 = Pc1v / Pc2v * Pc2u / delta;
//    DoubleMatrix sigma1 = DoubleMatrix.ofUnsafe(
//        new double[][] {{c.get(0) * c.get(0), RHO_12 * c.get(4), RHO_12 * c.get(5)},
//            {RHO_12 * c.get(4), c.get(1) * c.get(1), c.get(6)},
//            {RHO_12 * c.get(5), c.get(6), c.get(2) * c.get(2)}});
    DoubleMatrix sigma1 = DoubleMatrix.ofUnsafe( // standard
        new double[][] {{1.0d, rho12 * c.get(4) / (c.get(0) * c.get(1)), rho12 * c.get(5) / (c.get(0) * c.get(2))},
            {rho12 * c.get(4) / (c.get(0) * c.get(1)), 1.0d, c.get(6) / (c.get(1) * c.get(2))},
            {rho12 * c.get(5) / (c.get(0) * c.get(2)), c.get(6) / (c.get(1) * c.get(2)), 1.0d}});
    double adjustment1 = CONVEXITY_CALC.convexityAdjustmentNI(sigma1, c.get(0), c.get(1), c.get(2));
    double convexityAdjusted1 = notAdj1 * adjustment1;
    
    /* part 2: Pc2v */ 
    // Note: no adjustment for part 2: simplification
    double notAdj2 = -Pc1v / Pc2v * Pc2v / delta;
    double convexityAdjusted2 = notAdj2;
    
    /* part 3: Sc2j */
    double sqtheta = Math.sqrt(theta);
    double notAdj3 = Pc1v / Pc2v * spreadPvc2;
    DoubleMatrix sigma3 = DoubleMatrix.ofUnsafe(
        new double[][] {{1.0d, rho12 * c.get(4) / (c.get(0) * c.get(1)), A * rho13 * c.get(8) / (c.get(0) * A*sqtheta)},
            {rho12 * c.get(4) / (c.get(0) * c.get(1)), 1.0d, A * rho23 * c.get(9) / (c.get(1) * A*sqtheta)},
            {A * rho13 * c.get(8) / (c.get(0) * A*sqtheta), A * rho23 * c.get(9) / (c.get(1) * A*sqtheta), 1.0d}});
    double adjustment3 = CONVEXITY_CALC.convexityAdjustmentNI(sigma3, c.get(0), c.get(1), A*sqtheta);
    double convexityAdjusted3 = notAdj3 * adjustment3;
    
    /* aggregation */
    double forwardAdjusted = (convexityAdjusted1 + convexityAdjusted2 + convexityAdjusted3) / Pc1v;
    return forwardAdjusted;
  }
  
  private double integration2D(BiFunction<Double, Double, Double> g) {
    double integral = 0.0;
    try {
      integral = INTEGRATOR_2D.integrate(g, INTEGRAL_BOUNDS_LOWER, INTEGRAL_BOUNDS_UPPER);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return integral;
  }

}

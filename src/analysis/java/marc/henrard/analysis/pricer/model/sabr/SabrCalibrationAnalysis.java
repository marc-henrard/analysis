/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.pricer.model.sabr;

import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.BitSet;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConvention;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.math.impl.statistics.leastsquare.LeastSquareResultsWithTransform;
import com.opengamma.strata.pricer.impl.volatility.smile.SabrFormulaData;
import com.opengamma.strata.pricer.impl.volatility.smile.SabrHaganVolatilityFunctionProvider;
import com.opengamma.strata.pricer.swaption.SabrSwaptionCalibrator;

/**
 * Analysis of SABR calibration.
 * EUR data as of 12 August 2020
 * 
 * @author Marc Henrard
 */
public class SabrCalibrationAnalysis {
  
  private static final LocalDate CALIBRATION_DATE = LocalDate.of(2020, 7, 13);
  private static final ZonedDateTime CALIBRATION_DATE_TIME =
      ZonedDateTime.of(CALIBRATION_DATE,  LocalTime.of(11, 00), ZoneId.of("Europe/Brussels"));
  private static final ReferenceData REF_DATA = ReferenceData.standard();

  // Data SABR - data provider
  private static final double BETA = 0.50;
  private static final double RHO = 0.15;
  private static final double NU = 0.50;
  private static final double SHIFT = 0.02;
  private static final double BACHELIER_VOL_ATM = 0.00265;
  private static final double ALPHA_START = 0.05;
  
  private static final Tenor SWAP_TENOR = Tenor.TENOR_1Y;
  private static final Period EXPIRY = Period.ofYears(2);
  
  private static final BusinessDayAdjustment BDA = 
      BusinessDayAdjustment.of(BusinessDayConventions.MODIFIED_FOLLOWING, EUTA);
  
  private static final SabrSwaptionCalibrator SABR_CALIBRATOR = 
      SabrSwaptionCalibrator.DEFAULT;
  private static final SabrHaganVolatilityFunctionProvider SABR_FUNCTION =
      SabrHaganVolatilityFunctionProvider.DEFAULT;

  // Export
  private static final int NB_STRIKES_GRAPH = 41;
  private static final int NB_STRIKES_CALIBRATE = 5;
  private static final double MONEY_START_CALIBRATE = -0.0100;
  private static final double MONEY_START_GRAPH = -0.0125;
  private static final double STRIKE_STEP_CALIBRATE = 0.0050;
  private static final double STRIKE_STEP_GRAPH = 0.0010;


  /* Calibrate SABR alpha to ATM volatility (normal vol) */
  @Test
  public void sabr_calibrate_atm() {
    double forward = -0.0036;
    Pair<Double, Double> result = SABR_CALIBRATOR
        .calibrateAtmShiftedFromNormalVolatilities(BDA, CALIBRATION_DATE_TIME, ACT_365F, 
            EXPIRY, forward, BACHELIER_VOL_ATM, DoubleArray.of(ALPHA_START, BETA, RHO, NU), SHIFT);
    SabrFormulaData sabrParameters = SabrFormulaData.of(result.getFirst(), BETA, RHO, NU);
    
    // print smile
    DoubleArray strikes = DoubleArray.of(NB_STRIKES_GRAPH, i -> forward + MONEY_START_GRAPH + i * STRIKE_STEP_GRAPH);
    LocalDate exerciseDate = BDA.adjust(CALIBRATION_DATE.plus(EXPIRY), REF_DATA);
    double timeToExpiry = ACT_365F.relativeYearFraction(CALIBRATION_DATE, exerciseDate);
    double[] vols = new double[NB_STRIKES_GRAPH];
    for (int i = 0; i < NB_STRIKES_GRAPH; i++) {
      vols[i] = SABR_FUNCTION.volatility(forward + SHIFT, strikes.get(i) + SHIFT, timeToExpiry, sabrParameters);
      System.out.print(vols[i] + ",");
    }
    
  }

  /* Calibrate SABR alpha to ATM volatility (normal vol) */
  @Test
  public void sabr_calibrate_change_beta() {
    double forward = -0.0036;
    DoubleArray startParameters = DoubleArray.of(ALPHA_START, BETA, RHO, NU);
    Pair<Double, Double> result = SABR_CALIBRATOR
        .calibrateAtmShiftedFromNormalVolatilities(BDA, CALIBRATION_DATE_TIME, ACT_365F, 
            EXPIRY, forward, BACHELIER_VOL_ATM, startParameters, SHIFT);
    SabrFormulaData sabrParameters = SabrFormulaData.of(result.getFirst(), BETA, RHO, NU);

    LocalDate exerciseDate = BDA.adjust(CALIBRATION_DATE.plus(EXPIRY), REF_DATA);
    double timeToExpiry = ACT_365F.relativeYearFraction(CALIBRATION_DATE, exerciseDate);
    
    // Sample 
    DoubleArray strikesCalibrate = DoubleArray.of(NB_STRIKES_CALIBRATE, i -> forward + MONEY_START_CALIBRATE + i * STRIKE_STEP_CALIBRATE);
    double[] volsCalibrate = new double[NB_STRIKES_CALIBRATE];
    for (int i = 0; i < NB_STRIKES_CALIBRATE; i++) {
      volsCalibrate[i] = SABR_FUNCTION.volatility(forward + SHIFT, strikesCalibrate.get(i) + SHIFT, timeToExpiry, sabrParameters);
    }
    System.out.print("0.0");
    for (int i = 0; i < NB_STRIKES_CALIBRATE; i++) {
      System.out.print("," + strikesCalibrate.get(i));
    }
    System.out.println();
    System.out.print(BETA);
    for (int i = 0; i < NB_STRIKES_CALIBRATE; i++) {
      System.out.print("," + volsCalibrate[i]);
    }
    System.out.println();
    
    // re-calibrate
    double beta2 = 0.25;
    DoubleArray startParametersRecalibrate = DoubleArray.of(ALPHA_START, beta2, RHO, NU);
    BitSet fixed = new BitSet();
    fixed.set(1); // Beta fixed
    Pair<LeastSquareResultsWithTransform, DoubleArray> recalibrate = SABR_CALIBRATOR
        .calibrateLsShiftedFromBlackVolatilities(BDA, CALIBRATION_DATE_TIME, ACT_365F, EXPIRY, 
            forward, strikesCalibrate, ValueType.STRIKE, DoubleArray.ofUnsafe(volsCalibrate), 
            SHIFT, startParametersRecalibrate, fixed, SHIFT);
    
    System.out.println(recalibrate);

    // print smiles
    SabrFormulaData sabrParametersRecalibrate = 
        SabrFormulaData.of(recalibrate.getFirst().getModelParameters().toArrayUnsafe());
    System.out.println(sabrParametersRecalibrate);
    DoubleArray strikesPrint = DoubleArray.of(NB_STRIKES_GRAPH, i -> forward + MONEY_START_GRAPH + i * STRIKE_STEP_GRAPH);
    double[] volsBeta1 = new double[NB_STRIKES_GRAPH];
    double[] volsBeta2 = new double[NB_STRIKES_GRAPH];
    System.out.print("0.0");
    for (int i = 0; i < NB_STRIKES_GRAPH; i++) {
      System.out.print("," + strikesPrint.get(i));
    }
    System.out.println();
    System.out.print(BETA);
    for (int i = 0; i < NB_STRIKES_GRAPH; i++) {
      volsBeta1[i] = SABR_FUNCTION.volatility(forward + SHIFT, strikesPrint.get(i) + SHIFT, timeToExpiry, sabrParameters);
      System.out.print("," + volsBeta1[i]);
    }
    System.out.println();
    System.out.print(beta2);
    for (int i = 0; i < NB_STRIKES_GRAPH; i++) {
      volsBeta2[i] = SABR_FUNCTION.volatility(forward + SHIFT, strikesPrint.get(i) + SHIFT, timeToExpiry, sabrParametersRecalibrate);
      System.out.print("," + volsBeta2[i]);
    }
    System.out.println();
    
  }
  
}

/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.product.overnighttransition;


import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_SOFR;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.IborIndexRates;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.g2pp.G2ppPiecewiseConstantParameters;
import marc.henrard.murisq.pricer.swap.DiscountingTransitionHWHybridG2ppConvexityCalculator;

/**
 * Analysis of the convexity adjustment to be included in the collateral rate/discounting transition.
 * 
 * @author Marc Henrard
 */
public class SofrTransitionHWHybridG2ppConvexityAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2019, 10, 4);
  private static final LocalTime VALUATION_TIME = LocalTime.NOON;
  private static final ZoneId VALUATION_ZONE = ZoneId.of("America/New_York");
  private static final LocalDate BIG_BANG_DATE = LocalDate.of(2020, 10, 16);
  private static final LocalDate FIXING_DATE = LocalDate.of(2030, 10, 16);
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final HolidayCalendar CALENDAR_FIXING_IBOR = REF_DATA.getValue(USD_LIBOR_3M.getFixingCalendar());
  
  private static final String PATH_CONFIG_FF = "src/analysis/resources/curve-config/USD-DSCFFOIS-SOOIS-L3MIRS/";
  private static final CurveGroupName GROUP_NAME_FF = CurveGroupName.of("USD-DSCFFOIS-SOOIS-L3MIRS");
  private static final ResourceLocator FILE_GROUP_FF = 
      ResourceLocator.of(PATH_CONFIG_FF + "USD-DSCFFOIS-SOOIS-L3MIRS-group.csv");
  private static final ResourceLocator FILE_SETTINGS_FF =
      ResourceLocator.of(PATH_CONFIG_FF + "USD-DSCFFOIS-SOOIS-L3MIRS-settings-zrlinear.csv");
  private static final ResourceLocator FILE_NODES_FF =
      ResourceLocator.of(PATH_CONFIG_FF + "USD-DSCFFOIS-SOOIS-L3MIRS-nodes.csv");
  private static final RatesCurveGroupDefinition GROUP_DEF_FF = RatesCalibrationCsvLoader
      .load(FILE_GROUP_FF, FILE_SETTINGS_FF, FILE_NODES_FF).get(GROUP_NAME_FF);
  
  private static final String FILE_QUOTES = 
      "src/analysis/resources/quotes/MARKET-DATA-" + VALUATION_DATE.toString() + ".csv";
  private static final MarketData MARKET_DATA = MarketData
      .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));

  /* Multi-curve SOFR PAI and swap sensitivity */
  private static final ImmutableRatesProvider MULTICURVE_FF =
      CALIBRATOR.calibrate(GROUP_DEF_FF, MARKET_DATA, REF_DATA);
  
  /* Hull-White and G2++ */
  private static final double MEAN_REVERSION_1 = 0.02;
  private static final double MEAN_REVERSION_2 = 0.15;
  private static final double SIGMA_1 = 0.01;
  private static final double SIGMA_2 = 0.0005;
  private static final double RHO_12 = 0.25;
  private static final double RHO_23 = 0.25;
  
  private static final DoubleArray VOLATILITY1_CST = DoubleArray.of(SIGMA_1);
  private static final DoubleArray VOLATILITY2_CST = DoubleArray.of(SIGMA_2);
  private static final DoubleArray VOLATILITY_CST_TIME = DoubleArray.of();

  private static final TimeMeasurement TIME_MEASUREMENT = ScaledSecondTime.DEFAULT;
  private static final G2ppPiecewiseConstantParameters G2PP_PARAM =
      G2ppPiecewiseConstantParameters.builder()
      .currency(USD)
      .correlation(RHO_12)
      .kappa1(MEAN_REVERSION_1)
      .kappa2(MEAN_REVERSION_2)
      .volatility1(VOLATILITY1_CST)
      .volatility2(VOLATILITY2_CST)
      .volatilityTime(VOLATILITY_CST_TIME)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();
      
  private static final double A = 0.50; // Spread volatility
  
  private static final DiscountingTransitionHWHybridG2ppConvexityCalculator CONVEXITY_CALC = 
      DiscountingTransitionHWHybridG2ppConvexityCalculator.DEFAULT;

  /**
   * Convexity adjustment: big-bang and fixing date impacts
   */
  @Test
  public void adjusted_forward_dates(){
    
    /* Description */
    LocalDate iborFixingDateStart = LocalDate.of(2021, 4, 23);
    LocalDate bigBangDateStart = LocalDate.of(2019, 10, 5);
    int nbFixingDates = 60;
    int nbBigBangDates = 12;
    int fixingStep = 6; // in months
    int bigbangStep = 6; // in months
    IborIndexRates libor = MULTICURVE_FF.iborIndexRates(USD_LIBOR_3M);

    System.out.print("0.0");
    for (int loopbb = 0; loopbb < nbBigBangDates; loopbb++) {
      LocalDate bigbangDate = CALENDAR_FIXING_IBOR.nextOrSame(bigBangDateStart.plusMonths(bigbangStep * loopbb));
      System.out.print(", " + TIME_MEASUREMENT.relativeTime(VALUATION_DATE, bigbangDate));
    }
    System.out.println();
    for (int loopfixing = 0; loopfixing < nbFixingDates; loopfixing++) {
      LocalDate iborFixingDate =
          CALENDAR_FIXING_IBOR.nextOrSame(iborFixingDateStart.plusMonths(fixingStep * loopfixing));
      System.out.print(TIME_MEASUREMENT.relativeTime(VALUATION_DATE, iborFixingDate));
      IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, iborFixingDate, REF_DATA);
      OvernightIndexObservation obsOn =
          OvernightIndexObservation.of(USD_FED_FUND, obsIbor.getEffectiveDate(), REF_DATA);
      double iborRatec1 = libor.rate(obsIbor);
      for (int loopbb = 0; loopbb < nbBigBangDates; loopbb++) {
        double adjustment = 0.0;
        LocalDate bigbangDate = CALENDAR_FIXING_IBOR.nextOrSame(bigBangDateStart.plusMonths(bigbangStep * loopbb));
        if (iborFixingDate.isAfter(bigbangDate.plusMonths(fixingStep))) {
          double forwardAdjusted = CONVEXITY_CALC
              .adjustedForward(obsIbor, obsOn, bigbangDate, MULTICURVE_FF, G2PP_PARAM, RHO_23, A);
          adjustment = iborRatec1 - forwardAdjusted;
        }
        System.out.print(", " + adjustment);
      }
      System.out.println();
    }
  }
  
  /**
   * Convexity adjustment: Correlation W1/W2 and W1/W3
   */
  @Test
  public void adjusted_forward_corcor(){
    IborIndexRates libor = MULTICURVE_FF.iborIndexRates(USD_LIBOR_3M);
    IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, FIXING_DATE, REF_DATA);
    OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_FED_FUND, obsIbor.getEffectiveDate(), REF_DATA);
    DoubleArray rho12 = DoubleArray.of(15, i -> -0.70 + i * 0.10);
    DoubleArray rho23 = DoubleArray.of(15, i -> -0.70 + i * 0.10);
    System.out.print("0.0");
    for (int looprho23 = 0; looprho23 < rho23.size(); looprho23++) {
      System.out.print(", " + rho23.get(looprho23));
    }
    System.out.println();
    for (int looprho12 = 0; looprho12 < rho12.size(); looprho12++) {
      System.out.print(rho12.get(looprho12));
      for (int looprho23 = 0; looprho23 < rho23.size(); looprho23++) {
        G2ppPiecewiseConstantParameters g2ppParam = G2PP_PARAM.toBuilder()
            .correlation(rho12.get(looprho12))
            .volatilityTime(G2PP_PARAM.getVolatilityTime().subArray(1, G2PP_PARAM.getVolatilityTime().size() - 1))
            .build();
        double iborRatec1 = libor.rate(obsIbor);
        double forwardAdjusted = CONVEXITY_CALC
            .adjustedForward(obsIbor, obsOn, BIG_BANG_DATE, MULTICURVE_FF, g2ppParam, rho23.get(looprho23), A);
        double adjustment = iborRatec1 - forwardAdjusted;
        System.out.print(", " + adjustment);
      }
      System.out.println();
    }
  }
  
  /**
   * Convexity adjustment: Volatility 2 and mean reversion 2
   */
  @Test
  public void adjusted_forward_volmr(){
    IborIndexRates libor = MULTICURVE_FF.iborIndexRates(USD_LIBOR_3M);
    IborIndexObservation obsIbor = IborIndexObservation.of(USD_LIBOR_3M, FIXING_DATE, REF_DATA);
    double iborRatec1 = libor.rate(obsIbor);
    OvernightIndexObservation obsOn = OvernightIndexObservation.of(USD_FED_FUND, obsIbor.getEffectiveDate(), REF_DATA);
    DoubleArray vol2 = DoubleArray.of(15, i -> 0.0001 + i * 0.0001);
    DoubleArray mr = DoubleArray.of(15, i -> 0.01 + i * 0.01);
    System.out.print("0.0");
    for (int loopmr = 0; loopmr < mr.size(); loopmr++) {
      System.out.print(", " + mr.get(loopmr));
    }
    System.out.println();
    for (int loopvol2 = 0; loopvol2 < vol2.size(); loopvol2++) {
      System.out.print(vol2.get(loopvol2));
      for (int loopmr = 0; loopmr < mr.size(); loopmr++) {
        G2ppPiecewiseConstantParameters g2ppParam = G2PP_PARAM.toBuilder()
            .volatility2(DoubleArray.of(vol2.get(loopvol2)))
            .kappa2(mr.get(loopmr))
            .volatilityTime(G2PP_PARAM.getVolatilityTime().subArray(1, G2PP_PARAM.getVolatilityTime().size() - 1))
            .build();
        double forwardAdjusted = CONVEXITY_CALC
            .adjustedForward(obsIbor, obsOn, BIG_BANG_DATE, MULTICURVE_FF, g2ppParam, RHO_23, A);
        double adjustment = iborRatec1 - forwardAdjusted;
        System.out.print(", " + adjustment);
      }
      System.out.println();
    }
  }

}

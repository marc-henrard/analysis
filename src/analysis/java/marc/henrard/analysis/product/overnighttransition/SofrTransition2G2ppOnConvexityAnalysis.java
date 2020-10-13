/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.product.overnighttransition;

import static com.opengamma.strata.basics.currency.Currency.USD;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.g2pp.G2ppPiecewiseConstantParameters;

public class SofrTransition2G2ppOnConvexityAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2019, 10, 4);
  private static final LocalTime VALUATION_TIME = LocalTime.NOON;
  private static final ZoneId VALUATION_ZONE = ZoneId.of("America/New_York");
  private static final LocalDate BIG_BANG_DATE = LocalDate.of(2020, 10, 16);
  private static final LocalDate FIXING_DATE = LocalDate.of(2030, 10, 16);
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  
  /* Multi-curve: EFFR discounting, SOFR and LIBOR forward */
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
  private static final ImmutableRatesProvider MULTICURVE_FF =
      CALIBRATOR.calibrate(GROUP_DEF_FF, MARKET_DATA, REF_DATA);
  
  /* Model parameters: G2++ */
  private static final double MEAN_REVERSION_1_1 = 0.02;
  private static final double MEAN_REVERSION_1_2 = 0.20;
  private static final double MEAN_REVERSION_2_1 = 0.02;
  private static final double MEAN_REVERSION_2_2 = 0.20;
  private static final double SIGMA_1_1 = 0.01;
  private static final double SIGMA_1_2 = 0.0001;
  private static final double SIGMA_2_1 = 0.0099;
  private static final double SIGMA_2_2 = 0.0005;
  private static final double RHO_12 = -0.50;
  private static final DoubleArray VOLATILITY_1_1_CST = DoubleArray.of(SIGMA_1_1);
  private static final DoubleArray VOLATILITY_1_2_CST = DoubleArray.of(SIGMA_1_2);
  private static final DoubleArray VOLATILITY_2_1_CST = DoubleArray.of(SIGMA_2_1);
  private static final DoubleArray VOLATILITY_2_2_CST = DoubleArray.of(SIGMA_2_2);
  private static final DoubleArray VOLATILITY_CST_TIME = DoubleArray.of();
  private static final TimeMeasurement TIME_MEASUREMENT = ScaledSecondTime.DEFAULT;
  private static final G2ppPiecewiseConstantParameters G2PP_1 =
      G2ppPiecewiseConstantParameters.builder()
      .currency(USD)
      .correlation(RHO_12)
      .kappa1(MEAN_REVERSION_1_1)
      .kappa2(MEAN_REVERSION_1_2)
      .volatility1(VOLATILITY_1_1_CST)
      .volatility2(VOLATILITY_1_2_CST)
      .volatilityTime(VOLATILITY_CST_TIME)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();
  private static final G2ppPiecewiseConstantParameters G2PP_2 =
      G2ppPiecewiseConstantParameters.builder()
      .currency(USD)
      .correlation(RHO_12)
      .kappa1(MEAN_REVERSION_2_1)
      .kappa2(MEAN_REVERSION_2_2)
      .volatility1(VOLATILITY_2_1_CST)
      .volatility2(VOLATILITY_2_2_CST)
      .volatilityTime(VOLATILITY_CST_TIME)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();

}

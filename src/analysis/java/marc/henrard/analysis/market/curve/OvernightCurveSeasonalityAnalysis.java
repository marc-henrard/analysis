/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;

import java.io.IOException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

import marc.henrard.analysis.dataset.MulticurveConfigDataSet;
import marc.henrard.murisq.market.curve.description.MultiplyFixedCurveDefinition;

/**
 * Impact of intra-month seasonality on curve calibration
 * 
 * @author Marc Henrard
 */
public class OvernightCurveSeasonalityAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2019, 6, 21);
  private static final HolidayCalendarId CALENDAR_ID = HolidayCalendarIds.USNY;
  private static final HolidayCalendar CALENDAR = REF_DATA.getValue(CALENDAR_ID);
  
  /* Load and calibrate curves */
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final String PATH_CONFIG = "src/analysis/resources/curve-config/USD-DSCONOIS-FOMC/";
  private static final CurveGroupName GROUP_NAME = CurveGroupName.of("USD-DSCONOIS");
  private static final ResourceLocator FILE_GROUP = 
      ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-group.csv");
  private static final ResourceLocator FILE_SETTINGS =
      ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-settings-dfll.csv");
  private static final ResourceLocator FILE_NODES =
      ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-nodes-fomc-" + VALUATION_DATE.toString() + ".csv");
  private static final String FILE_QUOTES = 
      "src/analysis/resources/quotes/MARKET-DATA-" + VALUATION_DATE.toString() + ".csv";
  private static final ImmutableRatesProvider MULTICURVE_PIECEWISE_CONSTANT_ON =
      MulticurveConfigDataSet.multicurve(VALUATION_DATE,
          GROUP_NAME,
          FILE_GROUP, FILE_SETTINGS, FILE_NODES, 
          FILE_QUOTES, REF_DATA);
  /* Export */
  private static final String PATH_PIECEWISE_CONSTANT_ON = 
      "src/analysis/resources/output/overnight-piecewise-constant.csv";
  private static final String PATH_PIECEWISE_CONSTANT_ON_SEASONALITY = 
      "src/analysis/resources/output/overnight-piecewise-constant-seasonality.csv";
  
  /**
   * Compute ON forward rate for a curve with piecewise constant overnight rates.
   * 
   * @throws IOException
   */
  @Test
  public void step_overnight_curve() throws IOException {
    CurveExportUtils.exportOvernightCurve(
        MULTICURVE_PIECEWISE_CONSTANT_ON, USD_FED_FUND, 375, REF_DATA, "ON-FWD", PATH_PIECEWISE_CONSTANT_ON);
  }
  
  private static final double JUMP_LEVEL_MONTH_END = 0.00135d;
  private static final double JUMP_LEVEL_FIRST = 0.0008d;
  private static final double JUMP_LEVEL_2ND = 0.00045d;
  private static final double JUMP_LEVEL_15 = 0.0004d;
  
  /**
   * Compute ON forward rate for a curve with piecewise constant overnight rates and a fixed
   * spread.
   * 
   * @throws IOException
   */
  @Test
  public void step_overnight_seasonality_curve() throws IOException {
    int nbMonths = 24;
    InterpolatedNodalCurve seasonCurve = SeasonalAdjustmentUtils.seasonalityAdjustment(
        DoubleArray.of(JUMP_LEVEL_FIRST, JUMP_LEVEL_2ND,JUMP_LEVEL_15, JUMP_LEVEL_MONTH_END), 
        VALUATION_DATE, CALENDAR, DayCounts.ACT_365F, nbMonths);
    RatesCurveGroupDefinition groupDefinitionLoad = RatesCalibrationCsvLoader
        .load(FILE_GROUP, FILE_SETTINGS, FILE_NODES).get(GROUP_NAME);
    CurveDefinition curveDefLoad = groupDefinitionLoad.getCurveDefinitions().get(0);
    MultiplyFixedCurveDefinition curveDefAdjusted = MultiplyFixedCurveDefinition.builder()
        .fixedCurve(seasonCurve)
        .spreadCurveDefinition(curveDefLoad).build();
    RatesCurveGroupDefinition groupDefinitionAdjusted =
        RatesCurveGroupDefinition.of(GROUP_NAME, groupDefinitionLoad.getEntries(), 
            ImmutableList.of(curveDefAdjusted));
    MarketData marketData = MarketData
        .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));
    ImmutableRatesProvider multicurveSeasonality =
        CALIBRATOR.calibrate(groupDefinitionAdjusted, marketData, REF_DATA);
    CurveExportUtils.exportOvernightCurve(
        multicurveSeasonality, USD_FED_FUND, 375, REF_DATA, "ON-FWD", PATH_PIECEWISE_CONSTANT_ON_SEASONALITY);
  }
  
}

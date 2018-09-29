/**
 * Copyright (C) 2016 - Marc Henrard.
 */
package marc.henrard.analysis.dataset;

import java.time.LocalDate;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.ImmutableMarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;


/**
 * Load data and calibrate curves in EUR as of 2015-11-20.
 * Used for tests.
 * 
 * @author Marc Henrard
 */
public class MulticurveEur20151120DataSet {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  public static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);

  /* Load and calibrate curves */
  private static final String PATH_CONFIG = "src/test/resources/curve-config/";
  private static final String FILE_QUOTES = "src/test/resources/quotes/quotes-20151120-eur.csv";

  private static final ResourceLocator GROUPS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "groups-eur.csv");
  private static final ResourceLocator SETTINGS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "settings-eur.csv");
  private static final ResourceLocator NODES_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "nodes-eur.csv");
  private static final ResourceLocator NODES_RESOURCE_FRTB =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "nodes-eur-frtb.csv");
  private static final ImmutableMap<CurveGroupName, RatesCurveGroupDefinition> GROUPS_CONFIG =
      RatesCalibrationCsvLoader.load(GROUPS_RESOURCE, SETTINGS_RESOURCE, NODES_RESOURCE);
  private static final ImmutableMap<CurveGroupName, RatesCurveGroupDefinition> GROUPS_CONFIG_FRTB =
      RatesCalibrationCsvLoader.load(GROUPS_RESOURCE, SETTINGS_RESOURCE, NODES_RESOURCE_FRTB);

  private static final ImmutableMap<QuoteId, Double> QUOTES = QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES));
  private static final ImmutableMarketData MARKET_DATA = ImmutableMarketData.builder(VALUATION_DATE)
		  .addValueMap(QUOTES).build();
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final CurveGroupName GROUP_NAME = CurveGroupName.of("EUR-DSCONOIS-EURIBOR3MIRS-EURIBOR6MIRS");
  public static final ImmutableRatesProvider MULTICURVE = 
      CALIBRATOR.calibrate(GROUPS_CONFIG.get(GROUP_NAME), MARKET_DATA, REF_DATA);
  
  public static final ImmutableRatesProvider MULTICURVE_FRTB = 
      CALIBRATOR.calibrate(GROUPS_CONFIG_FRTB.get(GROUP_NAME), MARKET_DATA, REF_DATA);
  
  private static final int NB_CURVES = GROUPS_CONFIG.get(GROUP_NAME).getCurveDefinitions().size();
  public static final CurveName[] CURVE_NAMES = new CurveName[NB_CURVES];
  static{
    for(int i = 0; i<NB_CURVES; i++){
      CURVE_NAMES[i] = GROUPS_CONFIG.get(GROUP_NAME).getCurveDefinitions().get(i).getName();
    }
  }

}

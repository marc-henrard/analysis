/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.analysis.dataset;

import java.io.File;
import java.time.LocalDate;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Generate a multi-curve with market quotes and curve configurations from csv files.
 * 
 * @author Marc Henrard
 */
public class MulticurveConfigDataSet {

  private static final String PATH_CONFIG = "src/analysis/resources/curve-config/";
  private static final String PATH_QUOTES = "src/analysis/resources/quotes/";
  
  private static final String GROUP_STR = "-group";
  private static final String SETTINGS_STR = "-settings";
  private static final String NODES_STR = "-nodes";

  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();

  /**
   * 
   * Only one group, setting and nodes file should be present in the configuration folder.
   * 
   * @param calibrationDate  the calibration date
   * @param marketQuotesFile  the name of the file with the market quotes
   * @param configFolder  the configuration folder name
   * @param refData  the reference data
   * @return the multi-curve framework
   */
  public static ImmutableRatesProvider multicurve(
      LocalDate calibrationDate, 
      String marketQuotesFile,
      String configFolder,
      ReferenceData refData) {
    
    /* Search for the config and load */
    String folderPathConfig = PATH_CONFIG + configFolder;
    String group = null;
    String settings = null;
    String nodes = null;
    File folder = new File(folderPathConfig);
    ArgChecker.isTrue(folder.isDirectory(), "config " + folderPathConfig + " need to be a directory");
    File[] files = folder.listFiles();
    for (int i = 0; i < files.length; i++) {
      if (files[i].getName().contains(GROUP_STR)) {
        group = folderPathConfig + "/" + files[i].getName();
      }
      if (files[i].getName().contains(SETTINGS_STR)) {
        settings = folderPathConfig + "/" + files[i].getName();
      }
      if (files[i].getName().contains(NODES_STR)) {
        nodes = folderPathConfig + "/" + files[i].getName();
      }
    }
    ImmutableMap<CurveGroupName, RatesCurveGroupDefinition> defns = RatesCalibrationCsvLoader
        .load(ResourceLocator.of(group), ResourceLocator.of(settings), ResourceLocator.of(nodes));
    RatesCurveGroupDefinition curveGroupDefinition = defns.entrySet().iterator().next().getValue();
    /* Load market data and calibrates */
    String filePathQuotes = PATH_QUOTES + marketQuotesFile;
    MarketData marketData = MarketData
        .of(calibrationDate, QuotesCsvLoader.load(calibrationDate, ResourceLocator.of(filePathQuotes)));
    return CALIBRATOR.calibrate(curveGroupDefinition, marketData, refData);
  }

  /**
   * Returns a multi-curve provider calibrated using a given curve group defined in given resources.
   * 
   * @param calibrationDate  the calibration date
   * @param curveGroupName  the curve group name
   * @param groupFile  the file with the group definition
   * @param settingsFile  the file with the settings
   * @param nodesFile  the file with the curves' nodes
   * @param refData  the reference data
   * @return the calibrated curves
   */
  public static ImmutableRatesProvider multicurve(
      LocalDate calibrationDate,
      CurveGroupName curveGroupName,
      ResourceLocator groupFile,
      ResourceLocator settingsFile,
      ResourceLocator nodesFile,
      String fileQuotes,
      ReferenceData refData) {

    RatesCurveGroupDefinition groupDefinition = RatesCalibrationCsvLoader
        .load(groupFile, settingsFile, nodesFile).get(curveGroupName);
    MarketData marketData = MarketData
        .of(calibrationDate, QuotesCsvLoader.load(calibrationDate, ResourceLocator.of(fileQuotes)));
    return CALIBRATOR.calibrate(groupDefinition, marketData, refData);
  }

}

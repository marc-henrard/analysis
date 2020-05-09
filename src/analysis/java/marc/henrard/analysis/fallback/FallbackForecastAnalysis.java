/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.fallback;

import static com.opengamma.strata.basics.index.OvernightIndices.USD_SOFR;

import java.io.IOException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

import marc.henrard.analysis.dataset.MulticurveConfigDataSet;
import marc.henrard.analysis.market.curve.CurveExportUtils;

/**
 * Forecast ON rates for comparison with LIBOR in fallback.
 * 
 * @author Marc Henrard
 */
public class FallbackForecastAnalysis {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate ANALYSIS_DATE = LocalDate.of(2020, 4, 17);
  
  /* Load and calibrate curves */
  private static final String PATH_CONFIG = "src/analysis/resources/curve-config/USD-DSCONOIS-FOMC/";
  private static final CurveGroupName GROUP_NAME = CurveGroupName.of("USD-DSCSOOIS");
  private static final ResourceLocator FILE_GROUP = 
      ResourceLocator.of(PATH_CONFIG + "USD-DSCSOOIS-group.csv");
  private static final ResourceLocator FILE_SETTINGS =
      ResourceLocator.of(PATH_CONFIG + "USD-DSCSOOIS-settings-dfll.csv");
  private static final ResourceLocator FILE_NODES =
      ResourceLocator.of(PATH_CONFIG + "USD-DSCSOOIS-nodes-fomc-" + ANALYSIS_DATE.toString() + ".csv");
  private static final String FILE_QUOTES = 
      "src/analysis/resources/quotes/MARKET-DATA-" + ANALYSIS_DATE.toString() + ".csv";
  private static final ImmutableRatesProvider MULTICURVE_PIECEWISE_CONSTANT_ON =
      MulticurveConfigDataSet.multicurve(ANALYSIS_DATE,
          GROUP_NAME,
          FILE_GROUP, FILE_SETTINGS, FILE_NODES, 
          FILE_QUOTES, REF_DATA);

  /* Export */
  private static final String PATH_PIECEWISE_CONSTANT_ON = 
      "src/analysis/resources/output/overnight-piecewise-constant-" + ANALYSIS_DATE.toString() +".csv";
  
  /**
   * Calibrate the ON curve with piecewise constant forward rate and export to a csv file.
   * 
   * @throws IOException
   */
  @Test
  public void step_overnight_curve() throws IOException {
    int nbDaysExport = 100;
    CurveExportUtils.exportOvernightCurve(
        MULTICURVE_PIECEWISE_CONSTANT_ON, USD_SOFR, nbDaysExport, REF_DATA, "USD-SOFR", PATH_PIECEWISE_CONSTANT_ON);
  }
  

}

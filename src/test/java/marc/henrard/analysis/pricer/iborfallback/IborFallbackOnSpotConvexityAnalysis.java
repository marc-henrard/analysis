/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.analysis.pricer.iborfallback;


import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_6M;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeriesBuilder;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

import marc.henrard.analysis.dataset.MulticurveConfigDataSet;
import marc.henrard.murisq.basics.data.export.ExportUtils;
import marc.henrard.risq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;

/**
 * Analysis of the convexity adjustment for an overnight rate paid on a tenor period (3-month).
 * <p>
 * This analysis is linked to the working paper:
 * Henrard, Marc. (2018) A quant perspective on IBOR fallback proposals, Market infrastructure 
 * developments analysis, muRisQ. August 2018. Available at SSRN: 
 * <a href="https://ssrn.com/abstract=3226183">https://ssrn.com/abstract=3226183</a> 
 * 
 * @author Marc Henrard
 */
@Test
public class IborFallbackOnSpotConvexityAnalysis {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2018, 8, 30);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");

  public static final ImmutableRatesProvider MULTICURVE = MulticurveConfigDataSet
      .multicurve(VALUATION_DATE, 
          "MARKET-DATA-2018-08-30.csv", 
          "USD-DSCONOIS-L3MIRS",
          REF_DATA);
  
  private static final double MEAN_REVERSION = 0.02;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.0080);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of();
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(HW_PARAMETERS, DayCounts.ACT_365F, 
          VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
  private static final HullWhiteOneFactorPiecewiseConstantFormulas HW_FORMULAS =
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;

  /**
   * Compute the overnight forwards and the convexity adjusted forwards for payment 
   * at the end of a 3-month period. Forwards for monthly fixing dates up to 30 years.
   * 
   * @throws IOException
   */
  public void convexity_adjustment_overnightspot() throws IOException {
    int nbDates = 540;
    List<LocalDate> fixingDates = new ArrayList<>();
    List<IborIndexObservation> obsIbor = new ArrayList<>();
    List<OvernightIndexObservation> obsOn = new ArrayList<>();
    for (int i = 0; i < nbDates; i++) {
      LocalDate fixingDate = VALUATION_DATE.plusMonths(i + 1);
      fixingDates.add(fixingDate);
      obsIbor.add(IborIndexObservation.of(USD_LIBOR_6M, fixingDate, REF_DATA));
      obsOn.add(OvernightIndexObservation.of(USD_FED_FUND, fixingDate, REF_DATA));
    }
    LocalDateDoubleTimeSeriesBuilder builderOnFwds = LocalDateDoubleTimeSeries.builder();
    LocalDateDoubleTimeSeriesBuilder builderOnFwdsAdj = LocalDateDoubleTimeSeries.builder();
    for (int i = 0; i < nbDates; i++) {
      double onFwd = MULTICURVE.overnightIndexRates(USD_FED_FUND).rate(obsOn.get(i));
      builderOnFwds.put(fixingDates.get(i), onFwd);
      double v = HW_PROVIDER.relativeTime(obsIbor.get(i).getMaturityDate());
      double t0 = HW_PROVIDER.relativeTime(obsOn.get(i).getEffectiveDate());
      double p0t0 = MULTICURVE.discountFactor(USD, fixingDates.get(i));
      double t1 = HW_PROVIDER.relativeTime(obsOn.get(i).getMaturityDate());
      double p0t1 = MULTICURVE.discountFactor(USD, obsOn.get(i).getMaturityDate());
      double kappa = MEAN_REVERSION;
      double gamma = Math.exp(
          (Math.exp(-kappa * t0) - Math.exp(-kappa * t1)) * (Math.exp(-kappa * v) - Math.exp(-kappa * t1)) /
              (kappa * kappa) * HW_FORMULAS.alpha2ForwardGPart(HW_PARAMETERS, 0, t0));
      // TODO: include this formula in a method
      builderOnFwdsAdj.put(fixingDates.get(i), 
          onFwd + p0t0 / p0t1 * (gamma - 1.0d) / obsOn.get(i).getYearFraction());
    }
    StringBuilder builderStrOnFwds = new StringBuilder();
    ExportUtils.exportTimeSeries("ONFWD", builderOnFwds.build(), builderStrOnFwds);
    System.out.println(builderStrOnFwds.toString());
    StringBuilder builderStrOnFwdsAdj = new StringBuilder();
    ExportUtils.exportTimeSeries("ONFWD-ADJ", builderOnFwdsAdj.build(), builderStrOnFwdsAdj);
    System.out.println(builderStrOnFwdsAdj.toString());
  }
  
}

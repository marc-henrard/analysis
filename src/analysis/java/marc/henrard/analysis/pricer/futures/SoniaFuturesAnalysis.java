/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.analysis.pricer.futures;

import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.index.OvernightIndices.GBP_SONIA;
import static com.opengamma.strata.product.index.type.IborFutureConventions.GBP_LIBOR_3M_QUARTERLY_IMM;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.SecurityId;

import marc.henrard.analysis.dataset.MulticurveStandardGbpDataSet;
import marc.henrard.murisq.pricer.indexfutures.HullWhiteOneFactorCompoundedOvernightFuturesProductPricer;
import marc.henrard.murisq.product.futures.CompoundedOvernightFutures;
import marc.henrard.murisq.product.futures.CompoundedOvernightFuturesResolved;

/**
 * Analysis of SONIA futures pricing and convexity adjustment.
 * <p>
 * This analysis is linked to the working paper:
 * Henrard, Marc. (2018) Overnight based futures: convexity adjustment estimation
 * 
 * @author Marc Henrard
 */
@Test
public class SoniaFuturesAnalysis {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2017, 12, 29);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");

  public static final ImmutableRatesProvider MULTICURVE =
      MulticurveStandardGbpDataSet.multicurve(VALUATION_DATE, REF_DATA);
  
  private static final double MEAN_REVERSION = 0.03;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.0065);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of();
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(HW_PARAMETERS, DayCounts.ACT_365F, 
          VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
  private static final HullWhiteOneFactorCompoundedOvernightFuturesProductPricer PRICER_FUT =
      HullWhiteOneFactorCompoundedOvernightFuturesProductPricer.DEFAULT;
  
  private static final double NOTIONAL = 500_000;

  public void convexityAdjustment() {
    int nbContracts = 25;
    List<LocalDate> immDates = new ArrayList<>();
    for (int i = 0; i <= nbContracts; i++) {
      LocalDate immDate = GBP_LIBOR_3M_QUARTERLY_IMM
          .calculateReferenceDateFromTradeDate(VALUATION_DATE, Period.ofWeeks(1), i + 1, REF_DATA);
      immDates.add(immDate);
    }
    System.out.println("Date, Adj0, Adj, Fwd");
    for (int loopcontract = 0; loopcontract < nbContracts; loopcontract++) { // Start loop on contracts
      LocalDate ts = immDates.get(loopcontract);
      LocalDate te = immDates.get(loopcontract + 1);
      CompoundedOvernightFutures onFutures = CompoundedOvernightFutures.builder()
          .securityId(SecurityId.of(StandardId.of("muRisQ", "SONIA" + ts.getYear() + ts.getMonth().toString())))
          .notional(NOTIONAL)
          .startAccrualDate(ts)
          .endAccrualDate(te)
          .index(GBP_SONIA).build();
      CompoundedOvernightFuturesResolved onFuturesResolved = onFutures.resolve(REF_DATA);
      List<Double> gammas = PRICER_FUT.convexityAdjustmentGammas(onFuturesResolved, MULTICURVE, HW_PROVIDER);
      double productGamma = 1.0;
      for (int i = 0; i < gammas.size(); i++) {
        productGamma *= gammas.get(i);
      }
      double delta = onFuturesResolved.getAccrualFactor();
      double PcTs = MULTICURVE.discountFactor(GBP, onFuturesResolved.getStartAccrualDate());
      double PcTe = MULTICURVE.discountFactor(GBP, onFuturesResolved.getEndAccrualDate());
      double adj0 = PcTs/PcTe * (gammas.get(0) - 1) / delta;
      System.out.print(ts.toString() + "," + adj0);
      double adjT = PcTs/PcTe * (productGamma - 1) / delta;
      System.out.print("," + adjT);
      System.out.println("," + ((PcTs/PcTe - 1) / delta));
    } // End loop on contracts
    
  }
  
}

/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.analysis.pricer.fra;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.fra.DiscountingFraProductPricer;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.fra.ResolvedFra;
import com.opengamma.strata.product.fra.type.FraConventions;

import marc.henrard.risq.model.dataset.MulticurveStandardEurDataSet;
import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactor2HWShapePlusCstParameters;
import marc.henrard.risq.pricer.fra.RationalFraProductNumericalIntegrationPricer;

/**
 * Analysis of the adjustment to fair rate for forward rate agreement. 
 * <p>
 * The adjustment is the difference between the forward rate and the FRA fair rate.
 * <p>
 * This analysis is linked to the working paper:
 * Henrard, Marc. (2017) Rational multi-curve interest rate model: pricing of caps and FRAs and calibration.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalFraAdjustmentAnalysis {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2017, 9, 6);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");

  public static final ImmutableRatesProvider MULTICURVE =
      MulticurveStandardEurDataSet.multicurve(VALUATION_DATE, REF_DATA);

  /* Sets of parameters for the model. */
  public static final List<DoubleArray> PARAMETERS_SETS = ImmutableList.of(
      DoubleArray.of(0.75, 0.5, 0.0, 0.5, 0.021090487185022332, 0.011, 0.020216227883781312, 0.105, -0.001, 0.001),
      DoubleArray.of(0.75, 0.5, 0.0, 0.5, 0.02014990353736895, 0.011, 0.020008755848594954, 0.105, 0.0, 0.001),
      DoubleArray.of(0.75, 0.5, 0.0, 0.5, 0.025480137903385856, 0.011, 0.02535467974489671, 0.07865181716413608, 0.0, 0.002),
      DoubleArray.of(0.75, 0.5, 0.0, 0.5, 0.024035795027718503, 0.011, 0.024654718460905844, 0.08014975721847423, 0.001, 0.002),
      DoubleArray.of(0.75, 0.5, 0.0, 0.5, 0.014717685558378898, 0.011, 0.016969256736841597, 0.15868636008580464, 0.0, 0.004),
      DoubleArray.of(0.75, 0.5, 0.0, 0.5, 0.01981630358310232, 0.011, 0.02227634426849015, 0.105, 0.001, 0.004));

  /* Pricer */
  private static final DiscountingFraProductPricer PRICER_FRA_PRODUCT_DSC =
      DiscountingFraProductPricer.DEFAULT;
  private static final RationalFraProductNumericalIntegrationPricer PRICER_FRA_PRODUCT_RAT =
      RationalFraProductNumericalIntegrationPricer.DEFAULT;

  /* FRA description */
  private static final double NOTIONAL = 100_000_000;
  private static final double FIXED_RATE = 0.00;

  /* Par rate: compare rational model price with zero volatility with discounted value. */
  public void fra_rat_rate_adj() {
    int nbParameterSets = PARAMETERS_SETS.size();
    int nbStart = 20; // Quarterly
    for (int loopset = 0; loopset < nbParameterSets; loopset++) {
      System.out.println("Running parameters: " + PARAMETERS_SETS.get(loopset));
      RationalTwoFactor2HWShapePlusCstParameters RATIONAL_2HW_CAL =
          RationalTwoFactor2HWShapePlusCstParameters.of(PARAMETERS_SETS.get(loopset),
              ScaledSecondTime.DEFAULT, MULTICURVE.discountFactors(EUR), VALUATION_TIME,
              VALUATION_ZONE);
      System.out.println("Date,Forward,FRA adjusted,Difference");
      for (int loopstart = 0; loopstart < nbStart; loopstart++) {
        ResolvedFra fra = FraConventions.of(EUR_EURIBOR_6M).createTrade(VALUATION_DATE,
            Period.ofMonths(3 * (1 + loopstart)), BuySell.BUY, NOTIONAL, FIXED_RATE, REF_DATA).resolve(REF_DATA)
            .getProduct();
        double parRateDsc = PRICER_FRA_PRODUCT_DSC.parRate(fra, MULTICURVE);
        double parRateRat = PRICER_FRA_PRODUCT_RAT.parRate(fra, MULTICURVE, RATIONAL_2HW_CAL);
        System.out
            .println(fra.getPaymentDate() + "," + parRateDsc + "," + parRateRat + "," + (parRateDsc - parRateRat));
      }
    }
  }

}

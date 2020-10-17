/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.pricer.lmm;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.analysis.dataset.MulticurveStandardEurDataSet;
import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LmmdddSwaptionRootBachelierVolatility1Calibrator;
import marc.henrard.murisq.model.lmm.LmmdddUtils;
import marc.henrard.murisq.pricer.swaption.LmmdddSwaptionPhysicalProductExplicitApproxPricer;

/**
 * Analysis of the skew generated by the LMM with displaced diffusion.
 * 
 * @author Marc Henrard
 */
public class LmmdddSwaptionSkewAnalysis {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_3M.getFixingCalendar());
  private static final LocalDate VALUATION_DATE = LocalDate.of(2017, 9, 6);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");

  private static final ImmutableRatesProvider MULTICURVE_EUR =
      MulticurveStandardEurDataSet.multicurve(VALUATION_DATE, REF_DATA);

  /* LMM two-factor */
  private static final double MEAN_REVERTION = 0.02;
  private static final List<LocalDate> IBOR_DATES = new ArrayList<>();
  static {
    ResolvedSwapTrade swapMax = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(VALUATION_DATE, Tenor.TENOR_40Y, BuySell.BUY, 1.0, 0.0d, REF_DATA).resolve(REF_DATA);
    ImmutableList<SwapPaymentPeriod> iborLeg = swapMax.getProduct().getLegs().get(1).getPaymentPeriods();
    IBOR_DATES.add(iborLeg.get(0).getStartDate());
    for (SwapPaymentPeriod period : iborLeg) {
      IBOR_DATES.add(period.getEndDate());
    }
  }
  private static final double VOL2_LEVEL_1 = 0.09;
  private static final double VOL2_ANGLE = Math.PI * 0.5;
  private static final double VOL2_LEVEL_2 = 0.06;

  /* Swaptions */
  private static final Period[] EXPIRIES = 
      new Period[] {Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60), Period.ofMonths(120)};
  private static final Tenor[] TENORS = 
      new Tenor[] {Tenor.TENOR_2Y, Tenor.TENOR_10Y, Tenor.TENOR_30Y};
  private static final double[] MONEYNESS = 
      new double[] {-0.0150, -0.0100, -0.0050, -0.0025, 0, 0.0025, 0.0050, 0.0100, 0.0150};
  private static final double[] DISPLACEMENTS =
      new double[] {0.0250, 0.0500, 0.1000, 0.5000};
  private static final double NOTIONAL = 1_000_000.0d;
  
  /* Pricers */
  private static final DiscountingSwapProductPricer PRICER_SWAP =
      DiscountingSwapProductPricer.DEFAULT;
  private static final LmmdddSwaptionPhysicalProductExplicitApproxPricer PRICER_SWAPTION_LMM_APPROX =
      LmmdddSwaptionPhysicalProductExplicitApproxPricer.DEFAULT;
  
  /* Calibration */
  private static final double IV_TARGET = 0.0100;

  /* Computes and exports skew for different displacements.
   * All models are calibrated to have the same ATM swaption implied volatility.
   * Swaptions with different expiries and tenors. */
  @Test
  public void skew_displacements() {
    System.out.print(", " + ", ");
    for (int loopmoney = 0; loopmoney < MONEYNESS.length; loopmoney++) {
      System.out.print(", " + MONEYNESS[loopmoney]);
    }
    System.out.println();
    for (int loopdis = 0; loopdis < DISPLACEMENTS.length; loopdis++) {
      for (int loopexp = 0; loopexp < EXPIRIES.length; loopexp++) {
        LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES[loopexp]));
        for (int looptenor = 0; looptenor < TENORS.length; looptenor++) {
          System.out.print(DISPLACEMENTS[loopdis] + ", " + EXPIRIES[loopexp] + ", " + TENORS[looptenor]);
          ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
              .createTrade(expiryDate, TENORS[looptenor], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
          double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EUR);
          SwapTrade swapATM = EUR_FIXED_1Y_EURIBOR_3M
              .createTrade(expiryDate, TENORS[looptenor], BuySell.BUY, NOTIONAL, parRate, REF_DATA);
          Swaption swaptionAtm = Swaption.builder()
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
              .longShort(LongShort.LONG)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swapATM.getProduct()).build();
          ResolvedSwaption swaptionAtmResolved = swaptionAtm.resolve(REF_DATA);
          // Calibration ATM
          LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmDis =
              LmmdddUtils.lmm2Angle(MEAN_REVERTION, VOL2_LEVEL_1, VOL2_ANGLE, VOL2_LEVEL_2, DISPLACEMENTS[loopdis],
                  IBOR_DATES, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
                  VALUATION_ZONE, VALUATION_TIME, REF_DATA);
          LmmdddSwaptionRootBachelierVolatility1Calibrator lmmCalibrator =
              LmmdddSwaptionRootBachelierVolatility1Calibrator.of(lmmDis);
          LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersCalibrated =
              lmmCalibrator.calibrate(swaptionAtmResolved, IV_TARGET, MULTICURVE_EUR);
          for (int loopmoney = 0; loopmoney < MONEYNESS.length; loopmoney++) {
            SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
                .createTrade(expiryDate, TENORS[looptenor], BuySell.BUY, NOTIONAL, parRate + MONEYNESS[loopmoney],
                    REF_DATA);
            Swaption swaption = Swaption.builder()
                .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
                .longShort(LongShort.LONG)
                .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
                .underlying(swap.getProduct()).build();
            ResolvedSwaption swaptionResolved = swaption.resolve(REF_DATA);
            double iv = PRICER_SWAPTION_LMM_APPROX
                .impliedVolatilityBachelier(swaptionResolved, MULTICURVE_EUR, parametersCalibrated);
            System.out.print(", " + iv);
          } // end loopmoney
          System.out.println();
        } // end looptenor
      } // end loopexp
    }
  }
  
}
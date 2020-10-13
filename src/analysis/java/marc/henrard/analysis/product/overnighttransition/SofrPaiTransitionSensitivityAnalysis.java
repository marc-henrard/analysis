/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.product.overnighttransition;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.collect.array.Matrix;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.math.impl.matrix.CommonsMatrixAlgebra;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.MarketQuoteSensitivityCalculator;
import com.opengamma.strata.pricer.sensitivity.NotionalEquivalentCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapTradePricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;

import marc.henrard.murisq.basics.data.export.ExcelExportUtil;
import marc.henrard.murisq.product.swap.type.OvernightOvernightSwapConventions;

/**
 * Compare sensitivities computed with EFFR and SOFR sensitivities.
 * 
 * @author Marc Henrard
 */
public class SofrPaiTransitionSensitivityAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2019, 10, 4);
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();

  private static final String PATH_EXPORT = "src/analysis/resources/output/";
  
  private static final String PATH_CONFIG_FF = "src/analysis/resources/curve-config/USD-DSCFFOIS-L3MIRS/";
  private static final CurveGroupName GROUP_NAME_FF = CurveGroupName.of("USD-DSCFFOIS-L3MIRS");
  private static final ResourceLocator FILE_GROUP_FF = 
      ResourceLocator.of(PATH_CONFIG_FF + "USD-DSCFFOIS-L3MIRS-group.csv");
  private static final ResourceLocator FILE_SETTINGS_FF =
      ResourceLocator.of(PATH_CONFIG_FF + "USD-DSCFFOIS-L3MIRS-settings-zrlinear.csv");
  private static final ResourceLocator FILE_NODES_FF =
      ResourceLocator.of(PATH_CONFIG_FF + "USD-DSCFFOIS-L3MIRS-nodes.csv");
  private static final ResourceLocator FILE_NODES_FF_LCH =
      ResourceLocator.of(PATH_CONFIG_FF + "USD-DSCFFOIS-L3MIRS-nodes-lch.csv");
  private static final RatesCurveGroupDefinition GROUP_DEF_FF = RatesCalibrationCsvLoader
      .load(FILE_GROUP_FF, FILE_SETTINGS_FF, FILE_NODES_FF).get(GROUP_NAME_FF);
  private static final RatesCurveGroupDefinition GROUP_DEF_FF_LCH = RatesCalibrationCsvLoader
      .load(FILE_GROUP_FF, FILE_SETTINGS_FF, FILE_NODES_FF_LCH).get(GROUP_NAME_FF);
  
  private static final String PATH_CONFIG_SO = "src/analysis/resources/curve-config/USD-DSCSOOIS-FFOIS-L3MIRS/";
  private static final CurveGroupName GROUP_NAME_SO = CurveGroupName.of("USD-DSCSOOIS-FFOIS-L3MIRS");
  private static final ResourceLocator FILE_GROUP_SO = 
      ResourceLocator.of(PATH_CONFIG_SO + "USD-DSCSOOIS-FFOIS-L3MIRS-group.csv");
  private static final ResourceLocator FILE_SETTINGS_SO =
      ResourceLocator.of(PATH_CONFIG_SO + "USD-DSCSOOIS-FFOIS-L3MIRS-settings-zrlinear.csv");
  private static final ResourceLocator FILE_NODES_SO =
      ResourceLocator.of(PATH_CONFIG_SO + "USD-DSCSOOIS-FFOIS-L3MIRS-nodes.csv");
  private static final ResourceLocator FILE_NODES_SO_LCH =
      ResourceLocator.of(PATH_CONFIG_SO + "USD-DSCSOOIS-FFOIS-L3MIRS-nodes-lch.csv");
  private static final RatesCurveGroupDefinition GROUP_DEF_SO = RatesCalibrationCsvLoader
      .load(FILE_GROUP_SO, FILE_SETTINGS_SO, FILE_NODES_SO).get(GROUP_NAME_SO);
  private static final RatesCurveGroupDefinition GROUP_DEF_SO_LCH = RatesCalibrationCsvLoader
      .load(FILE_GROUP_SO, FILE_SETTINGS_SO, FILE_NODES_SO_LCH).get(GROUP_NAME_SO);
  
  private static final String FILE_QUOTES = 
      "src/analysis/resources/quotes/MARKET-DATA-" + VALUATION_DATE.toString() + ".csv";
  private static final MarketData MARKET_DATA = MarketData
      .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));
  
  private static final DiscountingSwapTradePricer PRICER_SWAP = DiscountingSwapTradePricer.DEFAULT;
  private static final MarketQuoteSensitivityCalculator MQC = MarketQuoteSensitivityCalculator.DEFAULT;
  private static final NotionalEquivalentCalculator NEC = NotionalEquivalentCalculator.DEFAULT;
  
  private static final double BP1 = 1.0E-4;
  private static final double HEDGE_NOTIONAL = 1.0E+0;

  /**
   * Computes sensitivities for a LIBOR swap with EFFR PAI, LIBOR swap with SOFR PAI, 
   * and for a EFFR-SOFR basis swap.
   * the notional amounts for hedging and the P
   * 
   * @throws IOException
   */
  @Test
  public void sensitivity_one_swap() throws IOException {
    /* Swap description */
    double notional = 100_000_000;
    double fixedRate = 0.05;
    ResolvedSwapTrade swap = FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M
        .createTrade(VALUATION_DATE, Period.ofMonths(11), Tenor.TENOR_10Y, BuySell.BUY, notional, fixedRate, REF_DATA)
        .resolve(REF_DATA);
    /* Multi-curve EFFR PAI and swap sensitivity */
    ImmutableRatesProvider multicurveFf =
        CALIBRATOR.calibrate(GROUP_DEF_FF.toBuilder().computePvSensitivityToMarketQuote(true).build(), 
            MARKET_DATA, REF_DATA);
    double parRateFf = PRICER_SWAP.parRate(swap, multicurveFf);
    MultiCurrencyAmount pvFf = PRICER_SWAP.presentValue(swap, multicurveFf);
    PointSensitivities ptsFf = PRICER_SWAP.presentValueSensitivity(swap, multicurveFf);
    CurrencyParameterSensitivities psFf = multicurveFf.parameterSensitivity(ptsFf);
    CurrencyParameterSensitivities mqFf = MQC.sensitivity(psFf, multicurveFf);
    ExcelExportUtil.export(ImmutableList.of("IRS-11Mx10Y"), 
        ImmutableList.of(parRateFf), 
        ImmutableList.of(pvFf), 
        mqFf.multipliedBy(BP1), 
        PATH_EXPORT + "irs-ff-dsc-pv01.xlsx");
    /* Multi-curve SOFR PAI and swap sensitivity */
    ImmutableRatesProvider multicurveSo =
        CALIBRATOR.calibrate(GROUP_DEF_SO, MARKET_DATA, REF_DATA);
    double parRateSo = PRICER_SWAP.parRate(swap, multicurveSo);
    MultiCurrencyAmount pvSo = PRICER_SWAP.presentValue(swap, multicurveSo);
    PointSensitivities ptsSo = PRICER_SWAP.presentValueSensitivity(swap, multicurveSo);
    CurrencyParameterSensitivities psSo = multicurveSo.parameterSensitivity(ptsSo);
    CurrencyParameterSensitivities mqSo = MQC.sensitivity(psSo, multicurveSo);
    ExcelExportUtil.export(ImmutableList.of("IRS-6Mx10Y"), 
        ImmutableList.of(parRateSo), 
        ImmutableList.of(pvSo), 
        mqSo.multipliedBy(BP1), 
        PATH_EXPORT + "irs-so-dsc-pv01.xlsx");
    /* Basis swap sensitivity */
    ResolvedSwapTrade basis0 = OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M
        .createTrade(VALUATION_DATE, Tenor.TENOR_10Y, BuySell.BUY, notional, 0.0, REF_DATA)
        .resolve(REF_DATA);
    double parRateBasis = PRICER_SWAP.parSpread(basis0, multicurveSo);
    ResolvedSwapTrade basis = OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M
        .createTrade(VALUATION_DATE, Tenor.TENOR_10Y, BuySell.BUY, notional, parRateBasis, REF_DATA)
        .resolve(REF_DATA);
    MultiCurrencyAmount pvBasis = PRICER_SWAP.presentValue(basis, multicurveSo);
    PointSensitivities ptsBasis = PRICER_SWAP.presentValueSensitivity(basis, multicurveSo);
    CurrencyParameterSensitivities psBasis = multicurveSo.parameterSensitivity(ptsBasis);
    CurrencyParameterSensitivities mqBasis = MQC.sensitivity(psBasis, multicurveSo);
    ExcelExportUtil.export(ImmutableList.of("BASIS-10Y"), 
        ImmutableList.of(parRateBasis), 
        ImmutableList.of(pvBasis), 
        mqBasis.multipliedBy(BP1), 
        PATH_EXPORT + "basis-so-dsc-pv01.xlsx");
    /* Hedging notional */
    CurrencyParameterSensitivities notionalHedging = NEC.notionalEquivalent(mqFf, multicurveFf);
    ExcelExportUtil.export(ImmutableList.of("IRS-6Mx10Y"), 
        ImmutableList.of(parRateFf), 
        ImmutableList.of(pvFf), 
        notionalHedging, 
        PATH_EXPORT + "irs-ff-dsc-hedging.xlsx");
  }

  /**
   * Compute basis swaps that produce the same sensitivities for the benchmark tenors (as proposed by LCH).
   * 
   * @throws IOException
   */
  @Test
  public void sensitivity_hedging() throws IOException {
    /* Trade/portfolio */
    double notional = 100_000_000;
    double fixedRate = 0.05;
    ResolvedSwapTrade swap = FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M
        .createTrade(VALUATION_DATE, Period.ofMonths(11), Tenor.TENOR_10Y, BuySell.BUY, notional, fixedRate, REF_DATA)
        .resolve(REF_DATA);
    /* Benchmark tenors */
    Period[] basistenor = new Period[] 
        {Period.ofYears(30), Period.ofYears(20), Period.ofYears(15), 
            Period.ofYears(10), Period.ofYears(5), Period.ofYears(2)};
    int nbHedge = basistenor.length;
    /* Multi-curve with EFFR and SOFR */
    ImmutableRatesProvider multicurveFf =
        CALIBRATOR.calibrate(GROUP_DEF_FF_LCH.toBuilder().computePvSensitivityToMarketQuote(true).build(), 
            MARKET_DATA, REF_DATA);
    ImmutableRatesProvider multicurveSo =
        CALIBRATOR.calibrate(GROUP_DEF_SO_LCH, MARKET_DATA, REF_DATA);
    /* Trade sensitivities */
    PointSensitivities ptsFf = PRICER_SWAP.presentValueSensitivity(swap, multicurveFf);
    CurrencyParameterSensitivities psFf = multicurveFf.parameterSensitivity(ptsFf);
    CurrencyParameterSensitivities mqFf = MQC.sensitivity(psFf, multicurveFf);
    PointSensitivities ptsSo = PRICER_SWAP.presentValueSensitivity(swap, multicurveSo);
    CurrencyParameterSensitivities psSo = multicurveSo.parameterSensitivity(ptsSo);
    CurrencyParameterSensitivities mqSo = MQC.sensitivity(psSo, multicurveSo);
    /* Basis swaps sensitivities */
    List<CurrencyParameterSensitivities> basisSensitivities = new ArrayList<>();
    for (int looptenor = 0; looptenor < nbHedge; looptenor++) {
      ResolvedSwapTrade basis0 = OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M
          .createTrade(VALUATION_DATE, Tenor.of(basistenor[nbHedge - 1 - looptenor]), BuySell.BUY, HEDGE_NOTIONAL, 0.0,
              REF_DATA)
          .resolve(REF_DATA);
      double parRateBasis = PRICER_SWAP.parSpread(basis0, multicurveSo);
      ResolvedSwapTrade basis = OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M
          .createTrade(VALUATION_DATE, Tenor.of(basistenor[nbHedge - 1 - looptenor]), BuySell.BUY, HEDGE_NOTIONAL, parRateBasis, REF_DATA)
          .resolve(REF_DATA);
      PointSensitivities ptsBasis = PRICER_SWAP.presentValueSensitivity(basis, multicurveSo);
      CurrencyParameterSensitivities psBasis = multicurveSo.parameterSensitivity(ptsBasis);
      basisSensitivities.add(MQC.sensitivity(psBasis, multicurveSo));
    }
    /* Hedge with basis swaps (matrix solving) */
    CommonsMatrixAlgebra algebraCommon = new CommonsMatrixAlgebra();
    DoubleArray mqPortfolioFf =
        mqFf.getSensitivity(CurveName.of("USD-DSCFF-OIS"), Currency.USD).getSensitivity();
    double[][] basisArray = new double[nbHedge][nbHedge];
    for (int looptenor = 0; looptenor < nbHedge; looptenor++) {
      CurrencyParameterSensitivities mqBasis = basisSensitivities.get(looptenor);
      basisArray[looptenor] =
          mqBasis.getSensitivity(CurveName.of("USD-FF-OIS"), Currency.USD).getSensitivity().toArray();
    }
    DoubleMatrix basisMatrix = algebraCommon.getTranspose(DoubleMatrix.ofUnsafe(basisArray));
    DoubleMatrix basisMatrixInverse = algebraCommon.getInverse(basisMatrix);
    Matrix quantity = algebraCommon.multiply(basisMatrixInverse, mqPortfolioFf);
    DoubleArray quantity2 = ((DoubleMatrix) quantity).column(0);
    CurrencyParameterSensitivities mqPortfolioHedged = mqSo;
    for (int looptenor = nbHedge - 1; looptenor >= 0; looptenor--) {
      CurrencyParameterSensitivities mqBasis = basisSensitivities.get(looptenor);
      double quantityTenor = quantity2.get(looptenor);
      mqPortfolioHedged = mqPortfolioHedged.combinedWith(mqBasis.multipliedBy(quantityTenor));
    }
    /* Export */
    ExcelExportUtil.export(ImmutableList.of("Hedged portfolio"),
        ImmutableList.of(0.0),
        ImmutableList.of(MultiCurrencyAmount.of(Currency.USD, 0.0)),
        mqPortfolioHedged.multipliedBy(BP1),
        PATH_EXPORT + "hedged-portfolio-pv01.xlsx");
    System.out.println(quantity2);
  }

  @Test
  public void impact_sofr_error() throws IOException {
    
  }
  
}

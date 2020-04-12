/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.AddFixedCurve;
import com.opengamma.strata.market.curve.CombinedCurve;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveInfoType;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveParameterSize;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.JacobianCalibrationMatrix;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.math.impl.matrix.CommonsMatrixAlgebra;
import com.opengamma.strata.pricer.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.DiscountOvernightIndexRates;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.OvernightIndexRates;
import com.opengamma.strata.pricer.sensitivity.MarketQuoteSensitivityCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapTradePricer;
import com.opengamma.strata.product.ResolvedTrade;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;

import marc.henrard.analysis.dataset.MulticurveConfigDataSet;
import marc.henrard.murisq.basics.data.export.ExcelExportUtil;
import marc.henrard.murisq.market.curve.description.AddFixedCurveDefinition;
import marc.henrard.murisq.pricer.curve.CalibrationSplitMeasures;

/**
 * Calibrate a LIBOR curve as a spread to the overnight curve based on zero-rates.
 * Overnight curve build with FOMC date log-linear interpolation on discount factors; spread linear interpolation on 
 * 
 * @author Marc Henrard
 */
public class OvernightLiborSpreadCurveAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2019, 6, 21);
  
  private static final OvernightIndex INDEX_ON = OvernightIndices.USD_FED_FUND;
  private static final IborIndex INDEX_IBOR = IborIndices.USD_LIBOR_3M;
  
  /* Load and calibrate curves */
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final String PATH_CONFIG_ON = "src/analysis/resources/curve-config/USD-DSCONOIS-FOMC/";
  private static final CurveGroupName GROUP_NAME_ON = CurveGroupName.of("USD-DSCONOIS");
  private static final ResourceLocator FILE_GROUP = 
      ResourceLocator.of(PATH_CONFIG_ON + "USD-DSCONOIS-group.csv");
  private static final ResourceLocator FILE_SETTINGS_DFLL =
      ResourceLocator.of(PATH_CONFIG_ON + "USD-DSCONOIS-settings-dfll.csv");
  private static final ResourceLocator FILE_SETTINGS_ZRPRODLIN =
      ResourceLocator.of(PATH_CONFIG_ON + "USD-DSCONOIS-settings-zrprodlin.csv");
  private static final ResourceLocator FILE_NODES =
      ResourceLocator.of(PATH_CONFIG_ON + "USD-DSCONOIS-nodes-fomc-" + VALUATION_DATE.toString() + ".csv");
  private static final String PATH_CONFIG_IBOR = "src/analysis/resources/curve-config/USD-DSCONOIS-L3MIRS/";
  private static final CurveGroupName GROUP_NAME_IBOR = CurveGroupName.of("USD-DSCONOIS-L3MIRS");
  private static final ResourceLocator FILE_GROUP_IBOR = 
      ResourceLocator.of(PATH_CONFIG_IBOR + "USD-DSCONOIS-L3MIRS-group.csv");
  private static final ResourceLocator FILE_SETTINGS_IBOR_ZRPRODLIN =
      ResourceLocator.of(PATH_CONFIG_IBOR + "USD-DSCONOIS-L3MIRS-settings-zrprodlin-zrlin.csv");
  private static final ResourceLocator FILE_NODES_IBOR =
      ResourceLocator.of(PATH_CONFIG_IBOR + "USD-DSCONOIS-L3MIRS-nodes-fomc-" + VALUATION_DATE.toString() + ".csv");
  private static final String FILE_QUOTES = 
      "src/analysis/resources/quotes/MARKET-DATA-" + VALUATION_DATE.toString() + ".csv";
  private static final MarketData MARKET_DATA = MarketData
      .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));
  private static final CurveName ON_CURVE_NAME = CurveName.of("USD-DSCON-OIS");
  private static final CurveName IBOR_CURVE_NAME = CurveName.of("USD-LIBOR3M-IRS");
  /* Export */
  private static final String EXPORT_PATH = "src/analysis/resources/output/";
  /* Pricing */
  private static final DiscountingSwapTradePricer PRICER_SWAP = DiscountingSwapTradePricer.DEFAULT;
  private static final MarketQuoteSensitivityCalculator MQSC = MarketQuoteSensitivityCalculator.DEFAULT;
  private static final CalibrationSplitMeasures CALIB_MEAS = CalibrationSplitMeasures.PAR_SPREAD;
  private static final CommonsMatrixAlgebra ALGEBRA = new CommonsMatrixAlgebra();

  /**
   * Calibrate the DSC/ON curve to FOMC dates with Discount Factor log-linear interpolation and 
   * ProductLinear on zero rates. Rates should be the same, up to Java rounding.
   * 
   * @throws IOException 
   */
  @Test
  public void step_overnight_settings() throws IOException{
    ImmutableRatesProvider multicurveOnDfll =
        MulticurveConfigDataSet.multicurve(VALUATION_DATE,
            GROUP_NAME_ON,
            FILE_GROUP, FILE_SETTINGS_DFLL, FILE_NODES,
            FILE_QUOTES, REF_DATA);
    ImmutableRatesProvider multicurveOnZrProdLin =
        MulticurveConfigDataSet.multicurve(VALUATION_DATE,
            GROUP_NAME_ON,
            FILE_GROUP, FILE_SETTINGS_ZRPRODLIN, FILE_NODES,
            FILE_QUOTES, REF_DATA);
    CurveExportUtils.exportOvernightCurve(multicurveOnDfll, 
        OvernightIndices.USD_FED_FUND, 375, REF_DATA, "ON-FWD-DFLL", EXPORT_PATH + "overnight-dfll.csv");
    CurveExportUtils.exportOvernightCurve(multicurveOnZrProdLin, 
        OvernightIndices.USD_FED_FUND, 375, REF_DATA, "ON-FWD-ZRPRODLIN", EXPORT_PATH + "overnight-zrprodlin.csv");
  }

  /**
   * Calibrate the DSC/ON curve to FOMC dates with ProductLinear on zero rates. Calibrate Ibor curve outright
   * and with spread to ON. Export Ibor implied forward rates.
   * 
   * @throws IOException 
   */
  @Test
  public void step_overnight_ibor_spread() throws IOException{
    
    ImmutableRatesProvider multicurveOnZrProdLin =
        MulticurveConfigDataSet.multicurve(VALUATION_DATE,
            GROUP_NAME_ON,
            FILE_GROUP, FILE_SETTINGS_ZRPRODLIN, FILE_NODES,
            FILE_QUOTES, REF_DATA);
    Curve curveOn = multicurveOnZrProdLin.getCurves().get(ON_CURVE_NAME);
    /* IBOR Curve definition */
    RatesCurveGroupDefinition groupOutrightDefinition = RatesCalibrationCsvLoader
        .load(FILE_GROUP_IBOR, FILE_SETTINGS_IBOR_ZRPRODLIN, FILE_NODES_IBOR).get(GROUP_NAME_IBOR);
    List<CurveDefinition> listCurveDefIbor = groupOutrightDefinition.getCurveDefinitions();
    List<CurveDefinition> addDefinitionList = new ArrayList<>();
    CurveDefinition iborCurveDefinition = null;
    for(CurveDefinition def: listCurveDefIbor) {
      if(def.getName().equals(IBOR_CURVE_NAME)) {
        iborCurveDefinition = def;
      } else {
        addDefinitionList.add(def);
      }
    }
    AddFixedCurveDefinition iborAddDefinition = 
        AddFixedCurveDefinition.of(curveOn, iborCurveDefinition);
    addDefinitionList.add(iborAddDefinition);
    RatesCurveGroupDefinition groupAddDefinition = 
        RatesCurveGroupDefinition.of(CurveGroupName.of("USD-DSCONOIS-L3MIRS-ADD"), 
            groupOutrightDefinition.getEntries(), addDefinitionList);
    ImmutableRatesProvider multicurveIborAdd =
        CALIBRATOR.calibrate(groupAddDefinition, MARKET_DATA, REF_DATA);
    ImmutableRatesProvider multicurveIborOutright =
        CALIBRATOR.calibrate(groupOutrightDefinition, MARKET_DATA, REF_DATA);
    /* Export rate ON implied + Ibor spread and outright */
    int nbForwardDate = 1250;
    CurveExportUtils.exportIborCurve(multicurveIborAdd, INDEX_IBOR, 
        nbForwardDate, REF_DATA, "IBOR-FWD-SPREAD", EXPORT_PATH + "ibor-spread.csv");
    CurveExportUtils.exportIborCurve(multicurveIborOutright, INDEX_IBOR, 
        nbForwardDate, REF_DATA, "IBOR-FWD-OUTRIGHT", EXPORT_PATH + "ibor-outright.csv");
    CurveExportUtils.exportOvernightCurveOnIborPeriods(multicurveIborAdd, INDEX_ON, INDEX_IBOR, 
        nbForwardDate, REF_DATA, "IBOR-FWD-ON", EXPORT_PATH + "ibor-overnight.csv");
    /* Transform Ibor in ON */
    Curve iborOutrightCurve = multicurveIborOutright.getCurves().get(IBOR_CURVE_NAME);
    OvernightIndexRates iborOutrightCurveOn = DiscountOvernightIndexRates.of(OvernightIndices.USD_FED_FUND, 
        ZeroRateDiscountFactors.of(Currency.USD, VALUATION_DATE, iborOutrightCurve));
    CurveExportUtils.exportOvernightCurve(iborOutrightCurveOn, 
        INDEX_ON, nbForwardDate, REF_DATA, "ON-FWD-IBOROUTRIGHT", EXPORT_PATH + "overnight-ibor-outright.csv");
    Curve iborSpreadCurve = multicurveIborAdd.getCurves().get(IBOR_CURVE_NAME);
    OvernightIndexRates iborSpreadCurveOn = DiscountOvernightIndexRates.of(OvernightIndices.USD_FED_FUND, 
        ZeroRateDiscountFactors.of(Currency.USD, VALUATION_DATE, iborSpreadCurve));
    CurveExportUtils.exportOvernightCurve(iborSpreadCurveOn, 
        INDEX_ON, nbForwardDate, REF_DATA, "ON-FWD-IBORSPREAD", EXPORT_PATH + "overnight-ibor-spread.csv");
  }

  /**
   * Calibrate the DSC/ON curve to FOMC dates with ProductLinear on zero rates. Calibrate Ibor curve outright
   * and with spread to ON. Computes sensitivities to the market quotes.
   * 
   * @throws IOException 
   */
  @Test
  public void step_overnight_ibor_spread_sensitivity() throws IOException{
    
    ImmutableRatesProvider multicurveOnZrProdLin =
        MulticurveConfigDataSet.multicurve(VALUATION_DATE,
            GROUP_NAME_ON,
            FILE_GROUP, FILE_SETTINGS_ZRPRODLIN, FILE_NODES,
            FILE_QUOTES, REF_DATA);
    Curve curveOn = multicurveOnZrProdLin.getCurves().get(ON_CURVE_NAME);
    /* IBOR Curve definition */
    RatesCurveGroupDefinition groupOutrightDefinition = RatesCalibrationCsvLoader
        .load(FILE_GROUP_IBOR, FILE_SETTINGS_IBOR_ZRPRODLIN, FILE_NODES_IBOR).get(GROUP_NAME_IBOR);
    List<CurveDefinition> listCurveDefIbor = groupOutrightDefinition.getCurveDefinitions();
    List<CurveDefinition> addDefinitionList = new ArrayList<>();
    CurveDefinition iborCurveDefinition = null;
    int iborIndex = 0;
    for(int i=0; i<listCurveDefIbor.size(); i++) {
      CurveDefinition def = listCurveDefIbor.get(i);
      if(def.getName().equals(IBOR_CURVE_NAME)) {
        iborCurveDefinition = def;
        iborIndex = i;
      } else {
        addDefinitionList.add(def);
      }
    }
    AddFixedCurveDefinition iborAddDefinition = 
        AddFixedCurveDefinition.of(curveOn, iborCurveDefinition);
    addDefinitionList.add(iborAddDefinition);
    RatesCurveGroupDefinition groupAddDefinition = 
        RatesCurveGroupDefinition.of(CurveGroupName.of("USD-DSCONOIS-L3MIRS-ADD"), 
            groupOutrightDefinition.getEntries(), addDefinitionList);
    ImmutableRatesProvider multicurveIborAdd1 =
        CALIBRATOR.calibrate(groupAddDefinition, MARKET_DATA, REF_DATA);
    ImmutableRatesProvider multicurveIborOutright =
        CALIBRATOR.calibrate(groupOutrightDefinition, MARKET_DATA, REF_DATA);
    /* Ibor curve as Combined curve and compute Jacobian */
    AddFixedCurve iborCurveAdd1 = (AddFixedCurve) multicurveIborAdd1.getCurves().get(IBOR_CURVE_NAME);
    CombinedCurve iborCurveCombined1 = CombinedCurve.of(iborCurveAdd1.getFixedCurve(), iborCurveAdd1.getSpreadCurve());
    ImmutableRatesProvider multicurveIborCombined1 = multicurveIborAdd1.toBuilder()
        .iborIndexCurve(INDEX_IBOR, iborCurveCombined1).build();
    RatesCurveGroupDefinition groupDefnBound =
        groupAddDefinition.bindTimeSeries(VALUATION_DATE, new HashMap<>());
    ImmutableList<ResolvedTrade> trades = groupDefnBound.resolvedTrades(MARKET_DATA, REF_DATA);
    ImmutableList<CurveParameterSize> orderGroup = ImmutableList.of(
        listCurveDefIbor.get(0).toCurveParameterSize(),
        listCurveDefIbor.get(1).toCurveParameterSize());
    int nbParamTotal = orderGroup.stream().mapToInt(e -> e.getParameterCount()).sum();
    DoubleMatrix jacobianInverse = DoubleMatrix.ofArrayObjects(
        trades.size(),
        nbParamTotal,
        i -> CALIB_MEAS.derivative(trades.get(i), multicurveIborCombined1, orderGroup));
    DoubleMatrix jacobian = ALGEBRA.getInverse(jacobianInverse);
    int nbParamIbor = orderGroup.get(iborIndex).getParameterCount();
    double[][] jacobianIborMatrix = new double[nbParamIbor][nbParamTotal];
    int startIndex = (iborIndex == 0) ? 0 : listCurveDefIbor.get(0).getParameterCount();
    for (int p = 0; p < nbParamIbor; p++) {
      System.arraycopy(jacobian.rowArray(startIndex + p), 0, jacobianIborMatrix[p], 0, nbParamTotal);
    }
    DoubleMatrix jacobianIbor = DoubleMatrix.ofUnsafe(jacobianIborMatrix);
    JacobianCalibrationMatrix jacobianMatrixIbor = JacobianCalibrationMatrix.of(orderGroup, jacobianIbor);
    CurveMetadata metadataIbor = ((DefaultCurveMetadata) iborCurveAdd1.getMetadata()
        .withInfo(CurveInfoType.JACOBIAN, jacobianMatrixIbor)).toBuilder().curveName(IBOR_CURVE_NAME).build();
    AddFixedCurve iborCurveAdd2 = iborCurveAdd1.withMetadata(metadataIbor);
    ImmutableRatesProvider multicurveIborAdd2 = multicurveIborAdd1.toBuilder()
        .iborIndexCurve(INDEX_IBOR, iborCurveAdd2).build();
    /* Swap sensitivities */
    ResolvedSwapTrade swap = FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M.createTrade(
        VALUATION_DATE, Period.ofMonths(6), Tenor.ofYears(4), BuySell.BUY, 1_000_000, 0.025, REF_DATA).resolve(REF_DATA);
    MultiCurrencyAmount pv1 = PRICER_SWAP.presentValue(swap, multicurveIborOutright);
    double pr1 = PRICER_SWAP.parRate(swap, multicurveIborOutright);
    PointSensitivities pts1 = PRICER_SWAP.presentValueSensitivity(swap, multicurveIborOutright);
    CurrencyParameterSensitivities ps1 = multicurveIborOutright.parameterSensitivity(pts1);
    CurrencyParameterSensitivities mq1 = MQSC.sensitivity(ps1, multicurveIborOutright);
    MultiCurrencyAmount pv2 = PRICER_SWAP.presentValue(swap, multicurveIborAdd1);
    double pr2 = PRICER_SWAP.parRate(swap, multicurveIborAdd1);
    PointSensitivities pts2 = PRICER_SWAP.presentValueSensitivity(swap, multicurveIborCombined1);
    CurrencyParameterSensitivities ps2 = multicurveIborCombined1.parameterSensitivity(pts2);
    CurrencyParameterSensitivities ps2Split = ps2.split();
    CurrencyParameterSensitivities mq2 = MQSC.sensitivity(ps2Split, multicurveIborAdd2);
    /* Export */
    ExcelExportUtil.export(
        ImmutableList.of("Swap"), 
        ImmutableList.of(pr1), 
        ImmutableList.of(pv1), 
        mq1, 
        EXPORT_PATH + "ibor-sensitivity-outright.xlsx");
    ExcelExportUtil.export(
        ImmutableList.of("Swap"), 
        ImmutableList.of(pr2), 
        ImmutableList.of(pv2), 
        mq2, 
        EXPORT_PATH + "ibor-sensitivity-spread.xlsx");
  }

}

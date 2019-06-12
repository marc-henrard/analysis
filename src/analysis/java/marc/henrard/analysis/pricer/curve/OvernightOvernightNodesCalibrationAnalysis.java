/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.pricer.curve;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.MarketQuoteSensitivityCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapTradePricer;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.FixedOvernightSwapConventions;

import marc.henrard.analysis.dataset.MulticurveConfigDataSet;
import marc.henrard.murisq.basics.data.export.ExcelExportUtil;
import marc.henrard.murisq.loader.csv.RatesCalibrationCsvLoader2;

/**
 * Curve calibration based on overnight-overnight nodes.
 * <p>
 * Analysis of potential delta when collateral rate is shifted to SOFR.
 * 
 * @author Marc Henrard
 */
@Test
public class OvernightOvernightNodesCalibrationAnalysis {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2019, 6, 12);

  public static final String MARKET_QUOTES_FILE = "MARKET-DATA-2019-06-12.csv";
  public static final String GROUP_EFFR = "USD-DSCONOISEFFR";
  public static final ImmutableRatesProvider MULTICURVE_EFFR = MulticurveConfigDataSet
      .multicurve(VALUATION_DATE, 
          MARKET_QUOTES_FILE, 
          GROUP_EFFR,
          REF_DATA);
  public static final String GROUP_SOFR_BS = "USD-DSCONOISSOFR-ONBSEFFR";
  public static final ImmutableRatesProvider MULTICURVE_SOFR_BS;
  private static final String PATH_CONFIG = "src/analysis/resources/curve-config/";
  private static final String PATH_QUOTES = "src/analysis/resources/quotes/";
  private static final String GROUP_STR = "-group";
  private static final String SETTINGS_STR = "-settings";
  private static final String NODES_STR = "-nodes";
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  static { // Local version required to incorporate Overnight/Overnight nodes
    /* Search for the config and load */
    String folderPathConfig = PATH_CONFIG + GROUP_SOFR_BS;
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
    ImmutableMap<CurveGroupName, RatesCurveGroupDefinition> defns = RatesCalibrationCsvLoader2
        .load(ResourceLocator.of(group), ResourceLocator.of(settings), ResourceLocator.of(nodes));
    RatesCurveGroupDefinition curveGroupDefinition = defns.entrySet().iterator().next().getValue();
    /* Load market data and calibrates */
    String filePathQuotes = PATH_QUOTES + MARKET_QUOTES_FILE;
    MarketData marketData = MarketData
        .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(filePathQuotes)));
    MULTICURVE_SOFR_BS = CALIBRATOR.calibrate(curveGroupDefinition, marketData, REF_DATA);
  }

  /* Swap */
  public static final Period FORWARD = Period.ofMonths(10);
  public static final Period TENOR = Period.ofYears(5);
  public static final double COUPON = 0.0300;
  public static final SwapTrade TRADE_EFFR =
      FixedOvernightSwapConventions.USD_FIXED_1Y_FED_FUND_OIS
      .createTrade(VALUATION_DATE, FORWARD, Tenor.of(TENOR), BuySell.BUY, 
          10_000_000, COUPON, REF_DATA)
      .withInfo(TradeInfo.builder()
          .tradeDate(VALUATION_DATE)
          .id(StandardId.of("muRisQId", "OIS-EFFR-5Y")).build());
  
  /* Pricers */
  public static final DiscountingSwapTradePricer PRICER_SWAP = 
      DiscountingSwapTradePricer.DEFAULT;
  public static final MarketQuoteSensitivityCalculator MQC =
      MarketQuoteSensitivityCalculator.DEFAULT;
  
  /* Export */
  public static final String OUTPUT_FOLDER = "src/analysis/resources/output/";
  public static final double BP1 = 1.0E-4;
  
  /**
   * Simple OIS delta with OIS unique curve for discounting and forward.
   * 
   * @throws IOException
   */
  public void delta_effr() throws IOException {
    ResolvedSwapTrade swap = TRADE_EFFR.resolve(REF_DATA);
    PointSensitivities pts = PRICER_SWAP.presentValueSensitivity(swap, MULTICURVE_EFFR);
    CurrencyParameterSensitivities ps = MULTICURVE_EFFR.parameterSensitivity(pts);
    CurrencyParameterSensitivities mq = MQC.sensitivity(ps, MULTICURVE_EFFR);
    MultiCurrencyAmount pv = PRICER_SWAP.presentValue(swap, MULTICURVE_EFFR);
    double parRate = PRICER_SWAP.parRate(swap, MULTICURVE_EFFR);
    ExcelExportUtil.export(ImmutableList.of(TRADE_EFFR.getId().get().toString()), 
        ImmutableList.of(parRate), ImmutableList.of(pv), 
        mq.multipliedBy(BP1), OUTPUT_FOLDER + "delta-effr.xlsx");
  }
  
  /**
   * Simple OIS-EFFR delta with discounting curve from SOFR and forward on EFFR.
   * The EFFR curves is build with ON-ON basis swaps.
   * 
   * @throws IOException
   */
  public void delta_sofr_bs_effr() throws IOException {
    ResolvedSwapTrade swap = TRADE_EFFR.resolve(REF_DATA);
    PointSensitivities pts = PRICER_SWAP.presentValueSensitivity(swap, MULTICURVE_SOFR_BS);
    CurrencyParameterSensitivities ps = MULTICURVE_SOFR_BS.parameterSensitivity(pts);
    CurrencyParameterSensitivities mq = MQC.sensitivity(ps, MULTICURVE_SOFR_BS);
    MultiCurrencyAmount pv = PRICER_SWAP.presentValue(swap, MULTICURVE_SOFR_BS);
    double parRate = PRICER_SWAP.parRate(swap, MULTICURVE_SOFR_BS);
    ExcelExportUtil.export(ImmutableList.of(TRADE_EFFR.getId().get().toString()), 
        ImmutableList.of(parRate), ImmutableList.of(pv), 
        mq.multipliedBy(BP1), OUTPUT_FOLDER + "delta-sofr-bs-effr.xlsx");
  }
  
}

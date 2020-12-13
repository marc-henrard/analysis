/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import static com.opengamma.strata.basics.index.OvernightIndices.EUR_ESTR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.result.ValueWithFailures;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.loader.csv.TradeCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveInfoType;
import com.opengamma.strata.market.curve.JacobianCalibrationMatrix;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.curve.SyntheticRatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.MarketQuoteSensitivityCalculator;
import com.opengamma.strata.pricer.sensitivity.NotionalEquivalentCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapTradePricer;
import com.opengamma.strata.product.Trade;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapTrade;

import marc.henrard.murisq.basics.data.export.ExcelExportUtil;

/**
 * Calibrate curves to market data. Create a synthetic calibration to standardized instruments.
 * Produces bucketed PV01 for both.
 * 
 * @author Marc Henrard
 */
public class PV01ReportsEurAnalysis {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2020, 10, 28);
  
  /* Load curve descriptions */
  private static final String PATH_CONFIG_MARKET_BS = "src/analysis/resources/curve-config/EUR-DSCESTROIS-E3MBS-E6MIRS/";
  private static final CurveGroupName GROUP_NAME_MKT_BS = CurveGroupName.of("EUR-DSCESTROIS-E3MBS-E6MIRS");
  private static final ResourceLocator FILE_GROUP_MKT_BS = 
      ResourceLocator.of(PATH_CONFIG_MARKET_BS + "EUR-DSCESTROIS-E3MBS-E6MIRS-group.csv");
  private static final ResourceLocator FILE_NODES_MKT_BS =
      ResourceLocator.of(PATH_CONFIG_MARKET_BS + "EUR-DSCESTROIS-E3MBS-E6MIRS-nodes.csv");
  private static final ResourceLocator FILE_SETTINGS_MKT_BS_ZRLIN =
      ResourceLocator.of(PATH_CONFIG_MARKET_BS + "EUR-DSCESTROIS-E3MBS-E6MIRS-settings-zrlinear.csv");
  private static final ResourceLocator FILE_SETTINGS_MKT_BS_ZRNCS =
      ResourceLocator.of(PATH_CONFIG_MARKET_BS + "EUR-DSCESTROIS-E3MBS-E6MIRS-settings-zrncs.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_MKT_BS_ZRLIN = RatesCalibrationCsvLoader
      .load(FILE_GROUP_MKT_BS, FILE_SETTINGS_MKT_BS_ZRLIN, FILE_NODES_MKT_BS).get(GROUP_NAME_MKT_BS)
      .toBuilder().computePvSensitivityToMarketQuote(true).build();
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_MKT_BS_ZRNCS = RatesCalibrationCsvLoader
      .load(FILE_GROUP_MKT_BS, FILE_SETTINGS_MKT_BS_ZRNCS, FILE_NODES_MKT_BS).get(GROUP_NAME_MKT_BS)
      .toBuilder().computePvSensitivityToMarketQuote(true).build();

  private static final String PATH_CONFIG_MARKET_FUT = "src/analysis/resources/curve-config/EUR-DSCESTROIS-E3MFUTBS-E6MIRS/";
  private static final CurveGroupName GROUP_NAME_MKT_FUT = CurveGroupName.of("EUR-DSCESTROIS-E3MFUTBS-E6MIRS");
  private static final ResourceLocator FILE_GROUP_MKT_FUT = 
      ResourceLocator.of(PATH_CONFIG_MARKET_FUT + "EUR-DSCESTROIS-E3MFUTBS-E6MIRS-group.csv");
  private static final ResourceLocator FILE_NODES_MKT_FUT =
      ResourceLocator.of(PATH_CONFIG_MARKET_FUT + "EUR-DSCESTROIS-E3MFUTBS-E6MIRS-nodes.csv");
  private static final ResourceLocator FILE_SETTINGS_MKT_FUT_ZRLIN =
      ResourceLocator.of(PATH_CONFIG_MARKET_FUT + "EUR-DSCESTROIS-E3MFUTBS-E6MIRS-settings-zrlinear.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_MKT_FUT_ZRLIN = RatesCalibrationCsvLoader
      .load(FILE_GROUP_MKT_FUT, FILE_SETTINGS_MKT_FUT_ZRLIN, FILE_NODES_MKT_FUT).get(GROUP_NAME_MKT_FUT)
      .toBuilder().computePvSensitivityToMarketQuote(true).build();
  
  private static final String PATH_CONFIG_MARKET_IRS = "src/analysis/resources/curve-config/EUR-DSCESTROIS-E3MIRS-E6MIRS/";
  private static final CurveGroupName GROUP_NAME_MKT_IRS = CurveGroupName.of("EUR-DSCESTROIS-E3MIRS-E6MIRS");
  private static final ResourceLocator FILE_GROUP_MKT_IRS = 
      ResourceLocator.of(PATH_CONFIG_MARKET_IRS + "EUR-DSCESTROIS-E3MIRS-E6MIRS-group.csv");
  private static final ResourceLocator FILE_NODES_MKT_IRS =
      ResourceLocator.of(PATH_CONFIG_MARKET_IRS + "EUR-DSCESTROIS-E3MIRS-E6MIRS-nodes.csv");
  private static final ResourceLocator FILE_SETTINGS_MKT_IRS_ZRLIN =
      ResourceLocator.of(PATH_CONFIG_MARKET_IRS + "EUR-DSCESTROIS-E3MIRS-E6MIRS-settings-zrlinear.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_MKT_IRS_ZRLIN = RatesCalibrationCsvLoader
      .load(FILE_GROUP_MKT_IRS, FILE_SETTINGS_MKT_IRS_ZRLIN, FILE_NODES_MKT_IRS).get(GROUP_NAME_MKT_IRS)
      .toBuilder().computePvSensitivityToMarketQuote(true).build();
  
  private static final List<RatesCurveGroupDefinition> GROUP_DEFINITIONS_MKT = new ArrayList<>();
  static {
    GROUP_DEFINITIONS_MKT.add(GROUP_DEFINITION_MKT_BS_ZRLIN);
    GROUP_DEFINITIONS_MKT.add(GROUP_DEFINITION_MKT_FUT_ZRLIN);
    GROUP_DEFINITIONS_MKT.add(GROUP_DEFINITION_MKT_IRS_ZRLIN);
  }

  private static final String PATH_CONFIG_STD = "src/analysis/resources/curve-config/EUR-DSCESTROIS-E3MIRS-E6MIRS-STD/";
  private static final CurveGroupName GROUP_NAME_STD = CurveGroupName.of("EUR-DSCESTROIS-E3MIRS-E6MIRS-STD");
  private static final ResourceLocator FILE_GROUP_STD = 
      ResourceLocator.of(PATH_CONFIG_STD + "EUR-DSCESTROIS-E3MIRS-E6MIRS-STD-group.csv");
  private static final ResourceLocator FILE_SETTINGS_STD =
      ResourceLocator.of(PATH_CONFIG_STD + "EUR-DSCESTROIS-E3MIRS-E6MIRS-STD-settings-zrlinear.csv");
  private static final ResourceLocator FILE_NODES_STD =
      ResourceLocator.of(PATH_CONFIG_STD + "EUR-DSCESTROIS-E3MIRS-E6MIRS-STD-nodes-standard.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_STD = RatesCalibrationCsvLoader
      .load(FILE_GROUP_STD, FILE_SETTINGS_STD, FILE_NODES_STD).get(GROUP_NAME_STD);
  
  private static final String FILE_QUOTES = 
      "src/analysis/resources/quotes/MARKET-DATA-" + VALUATION_DATE.toString() + "-STD.csv";
  private static final MarketData MARKET_DATA = MarketData
      .of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));
  private static final String PORTFOLIO = 
      "src/analysis/resources/portfolios/eur-irs-estrois-report-std.csv";
  
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final SyntheticRatesCurveCalibrator CALIBRATOR_SYNTHETIC = SyntheticRatesCurveCalibrator.standard();
  
  private static final DiscountingSwapTradePricer PRICER_SWAP = DiscountingSwapTradePricer.DEFAULT;
  private static final MarketQuoteSensitivityCalculator MQSC = MarketQuoteSensitivityCalculator.DEFAULT;
  private static final NotionalEquivalentCalculator NEC = NotionalEquivalentCalculator.DEFAULT;
  
  private static final String PATH_EXPORT = "src/analysis/resources/output/";
  private static final double BP1 = 1.0E-4;

  @Test
  public void pv01_synthetic_forward() throws IOException {

    long start, end;
    
    /* Portfolio */
    start = System.currentTimeMillis();
    TradeCsvLoader loader = TradeCsvLoader.of(REF_DATA);
    ValueWithFailures<List<Trade>> tradesWithFailure = loader.load(ResourceLocator.of(PORTFOLIO));
    List<Trade> trades = tradesWithFailure.getValue();
    end = System.currentTimeMillis();
    System.out.println("Failure load portfolio: " + tradesWithFailure.getFailures());
    System.out.println("Portfolio loaded in: " + (end-start) + " ms.");

    /* Curves */
    start = System.currentTimeMillis();
    ImmutableRatesProvider multicurveMarket =
        CALIBRATOR.calibrate(GROUP_DEFINITION_MKT_BS_ZRLIN, MARKET_DATA, REF_DATA);
    RatesCurveGroupDefinition groupDefinitionForward = ForwardRatesCurveGroupDefinitions
        .createForwardCurveDefinition(EUR_ESTR, ImmutableList.of(EUR_EURIBOR_3M, EUR_EURIBOR_6M))
        .toBuilder().computePvSensitivityToMarketQuote(true).build();
    ImmutableRatesProvider multicurveForward = 
        CALIBRATOR_SYNTHETIC.calibrate(groupDefinitionForward, multicurveMarket, REF_DATA);  
    end = System.currentTimeMillis();
    System.out.println("Curves calibrated in: " + (end - start) + " ms.");
    
    /* Sensitivity */
    start = System.currentTimeMillis();
    int nbTrades = trades.size();
    CurrencyParameterSensitivities pv01TotalMarket = CurrencyParameterSensitivities.empty();
    CurrencyParameterSensitivities pv01TotalForward = CurrencyParameterSensitivities.empty();
    for(int looptrade=0; looptrade<nbTrades; looptrade++) {
      ResolvedSwapTrade swap = ((SwapTrade) trades.get(looptrade)).resolve(REF_DATA);
      CurrencyParameterSensitivities mqMkt = MQSC.sensitivity(
          multicurveMarket.parameterSensitivity(PRICER_SWAP.presentValueSensitivity(swap, multicurveMarket)), 
          multicurveMarket);
      pv01TotalMarket = pv01TotalMarket.combinedWith(mqMkt);

      CurrencyParameterSensitivities mqFwd = MQSC.sensitivity(
          multicurveForward.parameterSensitivity(PRICER_SWAP.presentValueSensitivity(swap, multicurveForward)), 
          multicurveForward);
      pv01TotalForward = pv01TotalForward.combinedWith(mqFwd);
    }
    CurrencyParameterSensitivities notionalEquivalent = NEC.notionalEquivalent(pv01TotalForward, multicurveForward); 
    end = System.currentTimeMillis();  
    System.out.println("PV01 computed in: " + (end-start) + " ms."); 
    
    /* Export */
    ExcelExportUtil.export(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), 
        pv01TotalMarket.multipliedBy(BP1), PATH_EXPORT + "pv01-market.xlsx");
    ExcelExportUtil.export(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), 
        pv01TotalForward.multipliedBy(BP1), PATH_EXPORT + "pv01-forward.xlsx");
    ExcelExportUtil.export(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), 
        notionalEquivalent, PATH_EXPORT + "notional-equivalent-forward.xlsx");
    
    System.out.println("Done!");
  }

  @Test
  public void pv01_interpolation_matrix() throws IOException {

    long start, end;

    /* Curves */
    start = System.currentTimeMillis();
    ImmutableRatesProvider multicurveZrLin =
        CALIBRATOR.calibrate(GROUP_DEFINITION_MKT_BS_ZRLIN, MARKET_DATA, REF_DATA);
    ImmutableRatesProvider multicurveZrNcs =
        CALIBRATOR.calibrate(GROUP_DEFINITION_MKT_BS_ZRNCS, MARKET_DATA, REF_DATA);
    end = System.currentTimeMillis();
    System.out.println("Curves calibrated in: " + (end - start) + " ms.");

    JacobianCalibrationMatrix jacobianZrLin =
        multicurveZrLin.getIndexCurves().get(IborIndices.EUR_EURIBOR_6M).getMetadata().getInfo(CurveInfoType.JACOBIAN);
    DoubleMatrix matrixZrLin = jacobianZrLin.getJacobianMatrix();
    System.out.println(jacobianZrLin.getOrder());
    System.out.println(matrixZrLin);
    DoubleMatrix jacobianZrNcs =
        multicurveZrNcs.getIndexCurves().get(IborIndices.EUR_EURIBOR_6M).getMetadata().getInfo(CurveInfoType.JACOBIAN)
            .getJacobianMatrix();
    System.out.println(jacobianZrNcs);
  }

  @Test
  public void pv01_synthetic_report_eur_bs() throws IOException {
    
    long start, end;
    
    /* Portfolio */
    start = System.currentTimeMillis();
    TradeCsvLoader loader = TradeCsvLoader.of(REF_DATA);
    ValueWithFailures<List<Trade>> tradesWithFailure = loader.load(ResourceLocator.of(PORTFOLIO));
    List<Trade> trades = tradesWithFailure.getValue();
    end = System.currentTimeMillis();
    System.out.println("Failure load portfolio: " + tradesWithFailure.getFailures());
    System.out.println("Portfolio loaded in: " + (end-start) + " ms.");
    
    /* Curves */
    start = System.currentTimeMillis();
    ImmutableRatesProvider multicurveMarket =
        CALIBRATOR.calibrate(GROUP_DEFINITION_MKT_BS_ZRLIN, MARKET_DATA, REF_DATA);
    ImmutableRatesProvider multicurveReport = 
        CALIBRATOR_SYNTHETIC.calibrate(GROUP_DEFINITION_STD, multicurveMarket, REF_DATA);  
    end = System.currentTimeMillis(); 
    System.out.println("Curves calibrated in: " + (end-start) + " ms."); 
    
    /* Sensitivity */
    start = System.currentTimeMillis();
    int nbTrades = trades.size();
    CurrencyParameterSensitivities pv01TotalMarket = CurrencyParameterSensitivities.empty();
    CurrencyParameterSensitivities pv01TotalStandard = CurrencyParameterSensitivities.empty();
    for(int looptrade=0; looptrade<nbTrades; looptrade++) {
      ResolvedSwapTrade swap = ((SwapTrade) trades.get(looptrade)).resolve(REF_DATA);
      CurrencyParameterSensitivities mqMkt = MQSC.sensitivity(
          multicurveMarket.parameterSensitivity(PRICER_SWAP.presentValueSensitivity(swap, multicurveMarket)), 
          multicurveMarket);
      pv01TotalMarket = pv01TotalMarket.combinedWith(mqMkt);

      CurrencyParameterSensitivities mqStd = MQSC.sensitivity(
          multicurveReport.parameterSensitivity(PRICER_SWAP.presentValueSensitivity(swap, multicurveReport)), 
          multicurveReport);
      pv01TotalStandard = pv01TotalStandard.combinedWith(mqStd);
    }
    CurrencyParameterSensitivities notionalEquivalent = NEC.notionalEquivalent(pv01TotalMarket, multicurveMarket); 
    end = System.currentTimeMillis();  
    System.out.println("PV01 computed in: " + (end-start) + " ms."); 
    
    /* Export */
    ExcelExportUtil.export(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), 
        pv01TotalMarket.multipliedBy(BP1), PATH_EXPORT + "pv01-market.xlsx");
    ExcelExportUtil.export(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), 
        pv01TotalStandard.multipliedBy(BP1), PATH_EXPORT + "pv01-standard.xlsx");
    ExcelExportUtil.export(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), 
        notionalEquivalent, PATH_EXPORT + "notional-equivalent.xlsx");
    
    System.out.println("Done!");

  }
  
  @Test
  public void pv01_synthetic_report_eur_3_calib() throws IOException {
    
    long start, end;
    
    /* Portfolio */
    start = System.currentTimeMillis();
    TradeCsvLoader loader = TradeCsvLoader.of(REF_DATA);
    ValueWithFailures<List<Trade>> tradesWithFailure = loader.load(ResourceLocator.of(PORTFOLIO));
    List<Trade> trades = tradesWithFailure.getValue();
    end = System.currentTimeMillis();
    System.out.println("Failure load portfolio: " + tradesWithFailure.getFailures());
    System.out.println("Portfolio loaded in: " + (end - start) + " ms.");

    /* Curves */
    start = System.currentTimeMillis();
    List<ImmutableRatesProvider> multicurvesMarket = new ArrayList<>();
    multicurvesMarket.add(CALIBRATOR.calibrate(GROUP_DEFINITION_MKT_BS_ZRLIN, MARKET_DATA, REF_DATA));
    multicurvesMarket
        .add(CALIBRATOR_SYNTHETIC.calibrate(GROUP_DEFINITION_MKT_FUT_ZRLIN, multicurvesMarket.get(0), REF_DATA));
    multicurvesMarket
        .add(CALIBRATOR_SYNTHETIC.calibrate(GROUP_DEFINITION_MKT_IRS_ZRLIN, multicurvesMarket.get(0), REF_DATA));
    int nbMC = multicurvesMarket.size();
    end = System.currentTimeMillis();
    System.out.println("Curves calibrated in: " + (end - start) + " ms.");

    /* Sensitivity */
    start = System.currentTimeMillis();
    int nbTrades = trades.size();
    List<CurrencyParameterSensitivities> pv01TotalMarket = new ArrayList<>();
    for (int loopmc = 0; loopmc < nbMC; loopmc++) {
      pv01TotalMarket.add(CurrencyParameterSensitivities.empty());
    }
    CurrencyParameterSensitivities.empty();
    for (int looptrade = 0; looptrade < nbTrades; looptrade++) {
      ResolvedSwapTrade swap = ((SwapTrade) trades.get(looptrade)).resolve(REF_DATA);
      for (int loopmc = 0; loopmc < nbMC; loopmc++) {
        CurrencyParameterSensitivities mqMkt = MQSC.sensitivity(
            multicurvesMarket.get(loopmc)
                .parameterSensitivity(PRICER_SWAP.presentValueSensitivity(swap, multicurvesMarket.get(loopmc))),
            multicurvesMarket.get(loopmc));
        pv01TotalMarket.set(loopmc, pv01TotalMarket.get(loopmc).combinedWith(mqMkt));
      }
    }
    List<CurrencyParameterSensitivities> notionalsEquivalent = new ArrayList<>();
    for (int loopmc = 0; loopmc < nbMC; loopmc++) {
      notionalsEquivalent.add(NEC.notionalEquivalent(pv01TotalMarket.get(loopmc), multicurvesMarket.get(loopmc)));
    }
    end = System.currentTimeMillis();
    System.out.println("PV01 computed in: " + (end - start) + " ms.");

    /* Export */
    for (int loopmc = 0; loopmc < nbMC; loopmc++) {
      ExcelExportUtil.export(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
          pv01TotalMarket.get(loopmc).multipliedBy(BP1),
          PATH_EXPORT + "pv01-market-" + GROUP_DEFINITIONS_MKT.get(loopmc).getName() + ".xlsx");
      ExcelExportUtil.export(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
          notionalsEquivalent.get(loopmc),
          PATH_EXPORT + "notional-equivalent" + GROUP_DEFINITIONS_MKT.get(loopmc).getName() + ".xlsx");
    }
    System.out.println("Done!");
  }

}

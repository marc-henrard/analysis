/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.pricer.model.sabr;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.impl.volatility.smile.SabrFormulaData;
import com.opengamma.strata.pricer.impl.volatility.smile.SabrHaganVolatilityFunctionProvider;

import org.junit.jupiter.api.Test;

/**
 * Analysis of the smile in the SABR model with Hagan & Al formula.
 * 
 * @author Marc Henrard
 */
public class SabrSmileAnalysis {
  
  // Data SABR
  private static final double ALPHA = 0.05;
  private static final double BETA = 0.50;
  private static final double RHO = 0.00;
  private static final double NU = 0.50;
  private static final SabrFormulaData SABR = SabrFormulaData.of(ALPHA, BETA, RHO, NU);
  // Data underlying
  private static final double FORWARD = 0.02;
  private static final int NB_STRIKES = 31;
  private static final double STRIKE_START = 0.01;
  private static final double STRIK_STEP = 0.0010;
  private static final double TIME_EXPIRY = 2.00;
  
  private static final SabrHaganVolatilityFunctionProvider SABR_FUNCTION =
      SabrHaganVolatilityFunctionProvider.DEFAULT;
  

  /* Smile for a set of parameters */
  @Test
  public void sabr_smile() {
    DoubleArray strikes = DoubleArray.of(NB_STRIKES, i -> STRIKE_START + i * STRIK_STEP);
    double[] vols = new double[NB_STRIKES];
    for (int i = 0; i < NB_STRIKES; i++) {
      vols[i] = SABR_FUNCTION.volatility(FORWARD, strikes.get(i), TIME_EXPIRY, SABR);
      System.out.print(vols[i] + ",");
    }
  }
  
  /* Smile change with alpha */
  @Test
  public void sabr_alpha() {
    int nbAlphas = 5;
    double alphaStart = 0.03;
    double alphaStep = 0.01;
    DoubleArray alphas = DoubleArray.of(nbAlphas, i -> alphaStart + i * alphaStep);
    DoubleArray strikes = DoubleArray.of(NB_STRIKES, i -> STRIKE_START + i * STRIK_STEP);
    double[][] vols = new double[nbAlphas][NB_STRIKES];
    System.out.print("0.0");
    for (int j = 0; j < NB_STRIKES; j++) {
      System.out.print("," + strikes.get(j));
    }
    System.out.println();
    for (int i = 0; i < nbAlphas; i++) {
      SabrFormulaData sabr = SabrFormulaData.of(alphas.get(i), BETA, RHO, NU);
      System.out.print(alphas.get(i));
      for (int j = 0; j < NB_STRIKES; j++) {
        vols[i][j] = SABR_FUNCTION.volatility(FORWARD, strikes.get(j), TIME_EXPIRY, sabr);
        System.out.print("," + vols[i][j]);
      }
      System.out.println();
    }
  }
  
  /* Smile change with rho */
  @Test
  public void sabr_rho() {
    int nbRhos = 5;
    double rhoStart = -0.40;
    double rhoStep = 0.20;
    DoubleArray rhos = DoubleArray.of(nbRhos, i -> rhoStart + i * rhoStep);
    DoubleArray strikes = DoubleArray.of(NB_STRIKES, i -> STRIKE_START + i * STRIK_STEP);
    double[][] vols = new double[nbRhos][NB_STRIKES];
    System.out.print("0.0");
    for (int j = 0; j < NB_STRIKES; j++) {
      System.out.print("," + strikes.get(j));
    }
    System.out.println();
    for (int i = 0; i < nbRhos; i++) {
      SabrFormulaData sabr = SabrFormulaData.of(ALPHA, BETA, rhos.get(i), NU);
      System.out.print(rhos.get(i));
      for (int j = 0; j < NB_STRIKES; j++) {
        vols[i][j] = SABR_FUNCTION.volatility(FORWARD, strikes.get(j), TIME_EXPIRY, sabr);
        System.out.print("," + vols[i][j]);
      }
      System.out.println();
    }
  }
  
  /* Smile change with nu */
  @Test
  public void sabr_nu() {
    int nbNus = 5;
    double nuStart = 0.30;
    double nuStep = 0.10;
    DoubleArray nus = DoubleArray.of(nbNus, i -> nuStart + i * nuStep);
    DoubleArray strikes = DoubleArray.of(NB_STRIKES, i -> STRIKE_START + i * STRIK_STEP);
    double[][] vols = new double[nbNus][NB_STRIKES];
    System.out.print("0.0");
    for (int j = 0; j < NB_STRIKES; j++) {
      System.out.print("," + strikes.get(j));
    }
    System.out.println();
    for (int i = 0; i < nbNus; i++) {
      SabrFormulaData sabr = SabrFormulaData.of(ALPHA, BETA, RHO, nus.get(i));
      System.out.print(nus.get(i));
      for (int j = 0; j < NB_STRIKES; j++) {
        vols[i][j] = SABR_FUNCTION.volatility(FORWARD, strikes.get(j), TIME_EXPIRY, sabr);
        System.out.print("," + vols[i][j]);
      }
      System.out.println();
    }
  }
  
  /* Smile change with beta */
  @Test
  public void sabr_beta() {
    int nbBetas = 5;
    double betaStart = 0.00;
    double betaStep = 0.20;
    DoubleArray betas = DoubleArray.of(nbBetas, i -> betaStart + i * betaStep);
    DoubleArray strikes = DoubleArray.of(NB_STRIKES, i -> STRIKE_START + i * STRIK_STEP);
    double[][] vols = new double[nbBetas][NB_STRIKES];
    System.out.print("0.0");
    for (int j = 0; j < NB_STRIKES; j++) {
      System.out.print("," + strikes.get(j));
    }
    System.out.println();
    for (int i = 0; i < nbBetas; i++) {
      SabrFormulaData sabr = SabrFormulaData.of(ALPHA, betas.get(i), RHO, NU);
      System.out.print(betas.get(i));
      for (int j = 0; j < NB_STRIKES; j++) {
        vols[i][j] = SABR_FUNCTION.volatility(FORWARD, strikes.get(j), TIME_EXPIRY, sabr);
        System.out.print("," + vols[i][j]);
      }
      System.out.println();
    }
  }

}

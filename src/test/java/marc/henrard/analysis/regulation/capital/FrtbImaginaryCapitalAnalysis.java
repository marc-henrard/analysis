/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.analysis.regulation.capital;

import static com.opengamma.strata.product.swap.type.FixedOvernightSwapConventions.EUR_FIXED_1Y_EONIA_OIS;
import static com.opengamma.strata.basics.currency.Currency.EUR;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

import marc.henrard.analysis.dataset.MulticurveEur20151120DataSet;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.param.CurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebra;
import com.opengamma.strata.math.impl.matrix.OGMatrixAlgebra;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.MarketQuoteSensitivityCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwap;

/**
 * Creating positions with imaginary capital according to Minimum Capital
 * Requirements for Market Risk.
 * <p>
 * The 'Minimum Capital Requirements for Market Risk' was previously refereed to
 * as 'Fundamental Review of the Trading Book' (FRTB).
 * <p>
 * Actually the number under the square root is floored at 0 in the final text.
 * The capital is not imaginary anymore in the mathematical sense, but it
 * becomes imaginary in the common sense. You need a lot of imagination to
 * convince yourself that a capital of 0 is enough for comfort when you are
 * running a risky position.
 * 
 * @author Marc Henrard
 */
@Test
public class FrtbImaginaryCapitalAnalysis {

	private static final ReferenceData REF_DATA = ReferenceData.standard();
	private static final LocalDate VALUATION_DATE = MulticurveEur20151120DataSet.VALUATION_DATE;
	private static final ImmutableRatesProvider MULTICURVE = MulticurveEur20151120DataSet.MULTICURVE_FRTB;
	public static final CurveName[] CURVE_NAMES = MulticurveEur20151120DataSet.CURVE_NAMES;

	private static final DiscountingSwapProductPricer PRICER_SWAP = DiscountingSwapProductPricer.DEFAULT;
	private static final MarketQuoteSensitivityCalculator MQSC = MarketQuoteSensitivityCalculator.DEFAULT;
	private static final MatrixAlgebra ALGEBRA = new OGMatrixAlgebra();

	private static final double[] VERTICES = { 0.25, 0.50, 1, 2, 3, 5, 10, 15, 20, 30 };
	private static final DoubleArray RISK_WEIGHTS = DoubleArray
			.ofUnsafe(new double[] { 0.024, 0.024, 0.0225, 0.0188, 0.0173, 0.0150, 0.0150, 0.0150, 0.0150, 0.0150 });
	private static final double THETA = 0.03;
	private static final double MIN_CORRELATION = 0.40;

	private static final int NB_VERTICES = VERTICES.length;
	private static final DoubleMatrix CORRELATIONS;
	static {
		double[][] cor = new double[NB_VERTICES][NB_VERTICES];
		for (int i = 0; i < NB_VERTICES; i++) {
			for (int j = 0; j < NB_VERTICES; j++) {
				cor[i][j] = Math.max(
						Math.exp(-THETA * Math.abs(VERTICES[i] - VERTICES[j]) / Math.min(VERTICES[i], VERTICES[j])),
						MIN_CORRELATION);
			}
		}
		CORRELATIONS = DoubleMatrix.ofUnsafe(cor);
	}

	private static final Period[] TENORS_POSITION = new Period[] { Period.ofMonths(6), Period.ofYears(3),
			Period.ofYears(15) };
	private static final int NB_POSITIONS = TENORS_POSITION.length;
	private static final double[] NOTIONALS_POSITION = new double[] { -400_000_000, 150_000_000, -20_000_000 };
	private static final ResolvedSwap[] POSITIONS = new ResolvedSwap[NB_POSITIONS];
	static {
		for (int i = 0; i < NB_POSITIONS; i++) {
			POSITIONS[i] = EUR_FIXED_1Y_EONIA_OIS.createTrade(VALUATION_DATE, Tenor.of(TENORS_POSITION[i]),
					(NOTIONALS_POSITION[i] > 0) ? BuySell.BUY : BuySell.SELL, Math.abs(NOTIONALS_POSITION[i]), 0.0,
					REF_DATA).getProduct().resolve(REF_DATA);
			double parRate = PRICER_SWAP.parRate(POSITIONS[i], MULTICURVE);
			POSITIONS[i] = EUR_FIXED_1Y_EONIA_OIS.createTrade(VALUATION_DATE, Tenor.of(TENORS_POSITION[i]),
					(NOTIONALS_POSITION[i] > 0) ? BuySell.BUY : BuySell.SELL, Math.abs(NOTIONALS_POSITION[i]), parRate,
					REF_DATA).getProduct().resolve(REF_DATA);
		}
	}

	@SuppressWarnings("unused")
	public void sensitivity() {
		CurveName oisCurveName = null;
		for (CurveName c : CURVE_NAMES) {
			if (c.toString().contains("OIS")) {
				oisCurveName = c;
			}
		}
		PointSensitivityBuilder pts = PointSensitivityBuilder.none();
		for (int i = 0; i < NB_POSITIONS; i++) {
			pts = pts.combinedWith(PRICER_SWAP.presentValueSensitivity(POSITIONS[i], MULTICURVE));
		}
		CurrencyParameterSensitivities ps = MULTICURVE.parameterSensitivity(pts.build());
		CurrencyParameterSensitivities mqs = MQSC.sensitivity(ps, MULTICURVE);
		CurrencyParameterSensitivity mqsOis = mqs.getSensitivity(oisCurveName, EUR);
		DoubleArray sensi = mqsOis.getSensitivity();
		DoubleArray ws = DoubleArray.of(NB_VERTICES, i -> sensi.get(i) * RISK_WEIGHTS.get(i));
		DoubleArray wsV = (DoubleArray) ALGEBRA.multiply(ws, CORRELATIONS);
		double frtbCaptial2 = wsV.multipliedBy(ws).sum();
		Optional<Double> frtbCapital = frtb(frtbCaptial2);
	}

	Optional<Double> frtb(double capital2) {
		if (capital2 >= 0.0d) {
			return Optional.of(Math.sqrt(capital2));
		}
		System.out.println("FRTB capital computed involves the square root of a negative number");
		System.out.println("  |--> Partial capital is: " + 0.0 + " + " + Math.sqrt(-capital2) + " i");
		return Optional.empty();
	}

}

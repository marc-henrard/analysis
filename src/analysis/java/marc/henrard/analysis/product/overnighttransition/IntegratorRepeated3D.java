package marc.henrard.analysis.product.overnighttransition;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.integration.Integrator1D;
import com.opengamma.strata.math.impl.integration.Integrator2D;
import com.opengamma.strata.math.impl.integration.IntegratorRepeated2D;

/**
 * Three dimensional integration by repeated one dimensional integration using {@link Integrator1D}.
 */
public class IntegratorRepeated3D{

  /**
   * The 1-D integrator to be used for each repeated integral.
   */
  private final Integrator1D<Double, Double> integrator1D;
  private final Integrator2D<Double, Double> integrator2D;

  /**
   * Constructor.
   * 
   * @param integrator1D  the 1-D integrator to be used for each repeated integral
   */
  public IntegratorRepeated3D(Integrator1D<Double, Double> integrator1D) {
    this.integrator1D = integrator1D;
    this.integrator2D = new IntegratorRepeated2D(integrator1D);
  }

  //-------------------------------------------------------------------------
  public Double integrate(Function<DoubleArray, Double> f, Double[] lower, Double[] upper) {
    return integrator2D.integrate(innerIntegral(f, lower[0], upper[0]), 
        new Double[] {lower[1], lower[2]}, new Double[] {upper[1], upper[2]});
  }

  /**
   * The inner integral function of the repeated 1-D integrations.
   * For a given $(y,z)$ it returns $\int_{x_1}^{x_2} f(x,y,z) dx$.
   * 
   * @param f  the bi-function
   * @param lower  the lower bound (for the inner-first variable)
   * @param upper  the upper bound (for the inner-first variable)
   * @return the inner integral function
   */
  private BiFunction<Double, Double, Double> innerIntegral(Function<DoubleArray, Double> f, Double lower, Double upper) {
    return (y,z) -> integrator1D.integrate(x -> f.apply(DoubleArray.of(x, y, z)), lower, upper);
  }

}

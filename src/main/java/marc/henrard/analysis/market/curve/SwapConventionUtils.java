/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_ESTR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;

import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;
import com.opengamma.strata.product.swap.type.FixedOvernightSwapConvention;
import com.opengamma.strata.product.swap.type.FixedOvernightSwapConventions;
import com.opengamma.strata.product.swap.type.SingleCurrencySwapConvention;

import marc.henrard.murisq.product.swap.type.ComplementFixedOvernightSwapConventions;

/**
 * Utility class for acquiring default {@link SingleCurrencySwapConvention} for limited currencies.
 */
public final class SwapConventionUtils {

  /** Private constructor */
  private SwapConventionUtils() {
  }

  private static final Map<IborIndex, FixedIborSwapConvention> IBOR_CONVENTIONS =
      ImmutableMap.<IborIndex, FixedIborSwapConvention>builder()
          .put(EUR_EURIBOR_3M, FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M)
          .put(EUR_EURIBOR_6M, FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M)
          .put(USD_LIBOR_3M, FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M)
          .build();

  private static final Map<OvernightIndex, FixedOvernightSwapConvention> OIS_CONVENTIONS =
      ImmutableMap.<OvernightIndex, FixedOvernightSwapConvention>builder()
          .put(EUR_EONIA, FixedOvernightSwapConventions.EUR_FIXED_1Y_EONIA_OIS)
          .put(EUR_ESTR, ComplementFixedOvernightSwapConventions.EUR_FIXED_1Y_ESTR_OIS)
          .build();

  /**
   * Returns the Fixed-Ibor Swap Convention associated to a IBOR index.
   *
   * @param index  the index 
   * @return the convention
   */
  public static Optional<FixedIborSwapConvention> findFixedIborSwapConvention(IborIndex index) {
    return Optional.ofNullable(IBOR_CONVENTIONS.get(index));
  }

  /**
   * Returns the Fixed-Ibor Swap Convention associated to a IBOR index.
   *
   * @param index  the index 
   * @return the convention
   */
  public static Optional<FixedOvernightSwapConvention> findFixedOvrnightSwapConvention(OvernightIndex index) {
    return Optional.ofNullable(OIS_CONVENTIONS.get(index));
  }

}

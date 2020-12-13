/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import java.time.Period;

import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.market.observable.QuoteId;

/**
 * Uniform set of tickers for curve nodes.
 * <p>
 * Available for OIS and IRS.
 */
public final class TickerUtils {

  /** Private constructor. */
  private TickerUtils() {
  }

  /**
   * Ticker for IRS.
   *
   * @param index  the index of the swap
   * @param swapTenor  the tenor of the swap
   * @return the ticker
   */
  public static QuoteId irs(IborIndex index, Tenor swapTenor) {
    String id = index.toString() + "-IRS-" + swapTenor;
    return QuoteId.of(StandardId.of("muRisQ-Ticker", id));
  }

  /**
   * Ticker for IRS.
   *
   * @param index  the index of the swap
   * @param periodToStart  the period to the swap start
   * @param swapTenor  the tenor of the swap
   * @return the ticker
   */
  public static QuoteId irs(IborIndex index, Period periodToStart, Tenor swapTenor) {
    String id = index.toString() + "-IRS-" + periodToStart + "x" + swapTenor;
    return QuoteId.of(StandardId.of("muRisQ-Ticker", id));
  }

  /**
   * Ticker for OIS.
   *
   * @param index  the index of the swap
   * @param swapTenor  the tenor of the swap
   * @return the ticker
   */
  public static QuoteId ois(OvernightIndex index, Tenor swapTenor) {
    String id = index.toString() + "-OIS-" + swapTenor;
    return QuoteId.of(StandardId.of("muRisQ-Ticker", id));
  }

  /**
   * Ticker for OIS.
   *
   * @param index  the index of the swap
   * @param periodToStart  the period to the swap start
   * @param swapTenor  the tenor of the swap
   * @return the ticker
   */
  public static QuoteId ois(OvernightIndex index, Period periodToStart, Tenor swapTenor) {
    String id = index.toString() + "-OIS-" + periodToStart + "x" + swapTenor;
    return QuoteId.of(StandardId.of("muRisQ-Ticker", id));
  }

}

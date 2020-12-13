/*
 * Copyright (C) 2020 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package marc.henrard.analysis.market.curve;

import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.CurveDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveNode;
import com.opengamma.strata.market.curve.InterpolatedNodalCurveDefinition;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinitionBuilder;
import com.opengamma.strata.market.curve.RatesCurveGroupEntry;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.market.curve.node.FixedIborSwapCurveNode;
import com.opengamma.strata.market.curve.node.FixedOvernightSwapCurveNode;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedIborSwapTemplate;
import com.opengamma.strata.product.swap.type.FixedOvernightSwapConvention;
import com.opengamma.strata.product.swap.type.FixedOvernightSwapTemplate;

/**
 * Generates curve group definitions without needing lots of definition files.
 */
public class ForwardRatesCurveGroupDefinitions {

  private static final int DEFAULT_FORWARD_PERIOD = 1;
  private static final int DEFAULT_FINAL_YEAR = 30;

  /** Private constructor */
  private ForwardRatesCurveGroupDefinitions() {
  }

  /**
   * Create the forward nodes curve definitions for one Overnight index and a set of IBOR indices.
   * <p>
   * The forwards have one year length and the maximum period is 30 years.
   * 
   * @param onIndex  the overnight index
   * @param iborIndices  the IBOR indices
   * @return the group definition
   */
  public static RatesCurveGroupDefinition createForwardCurveDefinition(
      OvernightIndex onIndex,
      List<IborIndex> iborIndices) {
    
    return createForwardCurveDefinition(DEFAULT_FORWARD_PERIOD, DEFAULT_FINAL_YEAR, onIndex, iborIndices);
  }

  /**
   * Create the forward nodes curve definitions for one Overnight index and a set of IBOR indices.
   * 
   * @param step  the length of the forward periods in years
   * @param maxYear  the maximum period for the nodes
   * @param onIndex  the overnight index
   * @param iborIndices  the IBOR indices
   * @return the group definition
   */
  public static RatesCurveGroupDefinition createForwardCurveDefinition(
      int step, 
      int maxYear,
      OvernightIndex onIndex,
      List<IborIndex> iborIndices) {
    
    RatesCurveGroupDefinitionBuilder groupDef = RatesCurveGroupDefinition.builder();
    groupDef.name(CurveGroupName.of(onIndex.getCurrency() + "-DSCONOIS-IBORIRS"));
    groupDef.computeJacobian(true);
    
    RatesCurveGroupDefinition ois = createOisCurveDefinition(onIndex, step, maxYear);
    CurveDefinition oisCurveDef = 
        ois.findCurveDefinition(ois.findDiscountCurveName(onIndex.getCurrency()).get()).get();
    groupDef.addCurve(oisCurveDef,onIndex.getCurrency(), onIndex);
    for(IborIndex iborIndex : iborIndices) {
      RatesCurveGroupDefinition irs = createIrsCurveDefinition(iborIndex, step, maxYear);
      CurveDefinition irsCurveDef = 
          irs.findCurveDefinition(irs.findForwardCurveName(iborIndex).get()).get();
      groupDef.addForwardCurve(irsCurveDef, iborIndex);
    }
    return groupDef.build();
  }

  /**
   * Generates the forward curve and discounting group definition for the specified overnight index.
   *
   * @param index the index to generate
   * @return the curve definition
   */
  public static RatesCurveGroupDefinition createOisCurveDefinition(OvernightIndex index, int step, int maxYear) {
    Currency currency = index.getCurrency();
    CurveGroupName groupName = CurveGroupName.of(currency + "-DSC");
    CurveName curveName = CurveName.of(currency + "-DSCON-OIS");
    RatesCurveGroupEntry curveEntry = RatesCurveGroupEntry.builder()
        .curveName(curveName)
        .discountCurrencies(currency)
        .indices(index)
        .build();
    List<CurveNode> nodes = new ArrayList<>();
    int currentYear = 0;
    while (currentYear + step <= maxYear) {
      nodes.add(createOisNode(currentYear, step, index));
      currentYear += step;
    }
    CurveDefinition curveDefinition = createIrCurveDefinition(curveName, nodes);
    return RatesCurveGroupDefinition.of(groupName, ImmutableList.of(curveEntry), ImmutableList.of(curveDefinition));
  }

  private static CurveNode createOisNode(int startYear, int step, OvernightIndex index) {
    FixedOvernightSwapConvention convention = SwapConventionUtils.findFixedOvrnightSwapConvention(index)
        .orElseThrow(() -> new IllegalArgumentException("No FixedIbnorSwapConvention defined for " + index));
    Period start = Period.ofYears(startYear);
    Tenor tenor = Tenor.of(Period.ofYears(step));
    FixedOvernightSwapTemplate template = FixedOvernightSwapTemplate.of(start, tenor, convention);
    QuoteId quoteId = TickerUtils.ois(index, start, tenor);
    return FixedOvernightSwapCurveNode.builder()
        .template(template)
        .rateId(quoteId)
        .label(index + "-FWD-" + start.toString().substring(1) + "x" + tenor)
        .build();
  }

  /**
   * Generates the forward curve group definition for the specified Ibor index.
   *
   * @param index the index to generate
   * @return the curve definition
   */
  public static RatesCurveGroupDefinition createIrsCurveDefinition(IborIndex index, int step, int maxYear) {
    CurveGroupName groupName = CurveGroupName.of(index.getCurrency() + "-IRS");
    CurveName curveName =
        CurveName.of(index.toString() + "-IRS");
    RatesCurveGroupEntry curveEntry = RatesCurveGroupEntry.builder()
        .curveName(curveName)
        .indices(index)
        .build();
    List<CurveNode> nodes = new ArrayList<>();
    int currentYear = 0;
    while (currentYear + step <= maxYear) {
      nodes.add(createIrsNode(currentYear, step, index));
      currentYear += step;
    }
    CurveDefinition curveDefinition = createIrCurveDefinition(curveName, nodes);
    return RatesCurveGroupDefinition.of(groupName, ImmutableList.of(curveEntry), ImmutableList.of(curveDefinition));
  }

  private static CurveNode createIrsNode(int startYear, int step, IborIndex index) {
    FixedIborSwapConvention convention = SwapConventionUtils.findFixedIborSwapConvention(index)
        .orElseThrow(() -> new IllegalArgumentException("No FixedIbnorSwapConvention defined for " + index));
    Period start = Period.ofYears(startYear);
    Tenor tenor = Tenor.of(Period.ofYears(step));
    FixedIborSwapTemplate template = FixedIborSwapTemplate.of(start, tenor, convention);
    QuoteId quoteId = TickerUtils.irs(index, start, tenor);
    return FixedIborSwapCurveNode.builder()
        .template(template)
        .rateId(quoteId)
        .label(index + "-FWD-" + start.toString().substring(1) + "x" + tenor)
        .build();
  }
  
  /**
   * Create a generic curve definition from nodes with linear interpolation on zero rates.
   * 
   * @param curveName  the curve name
   * @param nodes  the nodes
   * @return the definition
   */
  private static CurveDefinition createIrCurveDefinition(CurveName curveName, List<CurveNode> nodes) {
    return InterpolatedNodalCurveDefinition.builder()
        .name(curveName)
        .xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE)
        .dayCount(DayCounts.ACT_365F)
        .nodes(nodes)
        .interpolator(CurveInterpolators.LINEAR)
        .extrapolatorLeft(CurveExtrapolators.FLAT)
        .extrapolatorRight(CurveExtrapolators.FLAT)
        .build();
  }

}

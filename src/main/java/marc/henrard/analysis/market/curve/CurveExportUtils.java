/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.analysis.market.curve;

import java.io.IOException;
import java.time.LocalDate;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeriesBuilder;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.OvernightIndexRates;

import marc.henrard.murisq.basics.data.export.ExportUtils;

/**
 * Utilities to export visualization of curves.
 * 
 * @author Marc Henrard
 */
public class CurveExportUtils {
  
  public static void exportOvernightCurve(
      OvernightIndexRates overnightRates,
      OvernightIndex index,
      int nbFwdRates,
      ReferenceData refData,
      String timeSeriesName,
      String exportFileName) throws IOException {
    
    LocalDate currentDate = overnightRates.getValuationDate();
    HolidayCalendar calendar = refData.getValue(index.getFixingCalendar());
    LocalDateDoubleTimeSeriesBuilder builderOn = LocalDateDoubleTimeSeries.builder();
    for (int loopdate = 0; loopdate < nbFwdRates; loopdate++) {
      OvernightIndexObservation obs = OvernightIndexObservation.of(index, currentDate, refData);
      double rate = overnightRates.rate(obs);
      builderOn.put(currentDate, rate);
      currentDate = calendar.next(currentDate);
    }
    StringBuilder fileContent = new StringBuilder();
    ExportUtils.exportTimeSeries(timeSeriesName, builderOn.build(), fileContent);
    ExportUtils.exportString(fileContent.toString(), exportFileName);
  }
  
  /**
   * Export the daily forward rate associated to an overnight index.
   * 
   * @param multicurve  the multi-curve containing the overnight curve
   * @param index  the overnight index
   * @param nbFwdRates  the number of forward rates to export
   * @param refData  the reference data
   * @param timeSeriesName  the name of the time series to export
   * @param exportFileName  the name of the export file
   * @throws IOException  in case of export problem
   */
  public static void exportOvernightCurve(
      ImmutableRatesProvider multicurve,
      OvernightIndex index,
      int nbFwdRates,
      ReferenceData refData,
      String timeSeriesName,
      String exportFileName) throws IOException {

    OvernightIndexRates overnightRates = multicurve.overnightIndexRates(index);
    exportOvernightCurve(overnightRates, index, nbFwdRates, refData, timeSeriesName, exportFileName);
  }
  
  /**
   * Export the daily forward rate associated to an overnight index.
   * 
   * @param multicurve  the multi-curve containing the overnight curve
   * @param index  the overnight index
   * @param nbFwdRates  the number of forward rates to export
   * @param refData  the reference data
   * @param timeSeriesName  the name of the time series to export
   * @param exportFileName  the name of the export file
   * @throws IOException  in case of export problem
   */
  public static void exportOvernightCurveOnIborPeriods(
      ImmutableRatesProvider multicurve,
      OvernightIndex indexOvernight,
      IborIndex indexIbor,
      int nbFwdRates,
      ReferenceData refData,
      String timeSeriesName,
      String exportFileName) throws IOException {
    
    LocalDate currentDate = multicurve.getValuationDate();
    HolidayCalendar calendar = refData.getValue(indexOvernight.getFixingCalendar());
    LocalDateDoubleTimeSeriesBuilder builderOn = LocalDateDoubleTimeSeries.builder();
    for (int loopdate = 0; loopdate < nbFwdRates; loopdate++) {
      IborIndexObservation obsIbor = IborIndexObservation.of(indexIbor, currentDate, refData);
      OvernightIndexObservation obsOn = OvernightIndexObservation.of(indexOvernight, obsIbor.getEffectiveDate(), refData);
      double rate = multicurve.overnightIndexRates(indexOvernight).periodRate(obsOn, obsIbor.getMaturityDate());
      builderOn.put(currentDate, rate);
      currentDate = calendar.next(currentDate);
    }
    StringBuilder fileContent = new StringBuilder();
    ExportUtils.exportTimeSeries(timeSeriesName, builderOn.build(), fileContent);
    ExportUtils.exportString(fileContent.toString(), exportFileName);
  }

  /**
   * Export the daily forward rate associated to an Ibor index.
   * 
   * @param multicurve  the multi-curve containing the overnight curve
   * @param index  the Ibor index
   * @param nbFwdRates  the number of forward rates to export
   * @param refData  the reference data
   * @param timeSeriesName  the name of the time series to export
   * @param exportFileName  the name of the export file
   * @throws IOException  in case of export problem
   */
  public static void exportIborCurve(
      ImmutableRatesProvider multicurve,
      IborIndex index,
      int nbFwdRates,
      ReferenceData refData,
      String timeSeriesName,
      String exportFileName) throws IOException {
    
    LocalDate currentDate = multicurve.getValuationDate();
    HolidayCalendar calendar = refData.getValue(index.getFixingCalendar());
    LocalDateDoubleTimeSeriesBuilder builderOn = LocalDateDoubleTimeSeries.builder();
    for (int loopdate = 0; loopdate < nbFwdRates; loopdate++) {
      IborIndexObservation obs = IborIndexObservation.of(index, currentDate, refData);
      double rate = multicurve.iborIndexRates(index).rate(obs);
      builderOn.put(currentDate, rate);
      currentDate = calendar.next(currentDate);
    }
    StringBuilder fileContent = new StringBuilder();
    ExportUtils.exportTimeSeries(timeSeriesName, builderOn.build(), fileContent);
    ExportUtils.exportString(fileContent.toString(), exportFileName);
  }

}

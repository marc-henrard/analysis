/**
 * Copyright (C) 2016 - Marc Henrard.
 */
package marc.henrard.analysis.data.exporter;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Map.Entry;

import com.opengamma.strata.market.observable.QuoteId;

/**
 * Utilities to export objects (typically in csv files or in the console).
 * 
 * @author Marc Henrard
 */
public class ExportUtils {
  
  /**
   * Append a map of market quotes to a csv-like destination in the standard Strata format.
   * 
   * @param date  the date for which the quotes are valid
   * @param quotes  the quotes as a map
   * @param destination  the destination to which the csv-like string is appended
   * @throws IOException 
   */
  public static void exportMarketQuotes(
      LocalDate date,
      Map<QuoteId, Double> quotes,
      Appendable destination,
      boolean exportHeader) throws IOException {
    if (exportHeader) {
      String header = "Valuation Date, Symbology, Ticker, Field Name, Value\n";
      destination.append(header);
    }
    String dateStr = date.toString();
    for (Entry<QuoteId, Double> entry : quotes.entrySet()) {
      String row = dateStr + ", ";
      row = row + entry.getKey().getStandardId().getScheme() + ", ";
      row = row + entry.getKey().getStandardId().getValue() + ", ";
      row = row + entry.getKey().getFieldName().toString() + ", ";
      row = row + entry.getValue() + "\n";
      destination.append(row);
    }
  }

  /**
   * Append a set of values in an array to a csv-like destination.
   * 
   * @param headers  the header of each column
   * @param values  the values in array format
   * @param destination  the destination to which the csv-like string is appended
   * @throws IOException 
   */
  public static void exportArray(
      String[] headers,
      double[][] values,
      Appendable destination) throws IOException {
    int nbColumns = headers.length;
    int nbRows = values.length;
    destination.append(headers[0]);
    for (int c = 1; c < nbColumns; c++) {
      destination.append(", " + headers[c]);
    }
    destination.append("\n");
    for (int r = 0; r < nbRows; r++) {
      destination.append("" + values[r][0]);
      for (int c = 1; c < values[r].length; c++) {
        destination.append("," + values[r][c]);
      }
      destination.append("\n");
    }
  }

  /**
   * Exports a string to a file. Useful in particular for CSV, XML and beans.
   * @param string  the string to export
   * @param fileName  the name of the file
   */
  public static void exportString(
      String string,
      String fileName) {
    try (FileWriter writer = new FileWriter(fileName)) {
      writer.append(string);
      writer.close();
    } catch (final IOException e) {
      e.printStackTrace();
    }
  }

}

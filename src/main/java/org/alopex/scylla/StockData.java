package org.alopex.scylla;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.encog.ml.data.basic.BasicMLDataSet;

import yahoofinance.Stock;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

public class StockData {
	
	private Stock stock;
	
	private List<HistoricalQuote> hq;
	
	private ArrayList<Double> openPrices;
	private ArrayList<Double> closePrices;
	private ArrayList<Double> highPrices;
	private ArrayList<Double> lowPrices;
	private ArrayList<Double> volumes;
	
	private double closePriceMin;
	private double closePriceMax;
	
	public StockData(Stock stock) {
		try {
			this.stock = stock;
			
			openPrices  = new ArrayList<Double> ();
			closePrices = new ArrayList<Double> ();
			highPrices  = new ArrayList<Double> ();
			lowPrices   = new ArrayList<Double> ();
			volumes     = new ArrayList<Double> ();
					
			//Fetch data points
			fetchData();
			
			//Normalize data
			normalize();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	/**
	 * Iterates through history, compiling all lists
	 */
	public void fetchData() {
		try {
			Calendar from = Calendar.getInstance();
			Calendar to = Calendar.getInstance();
			from.add(Calendar.YEAR, -5);
			hq = stock.getHistory(from, to, Interval.DAILY);
			for(int i = 0; i < hq.size(); i++) {
				HistoricalQuote thq = hq.get(i);
				openPrices.add(thq.getOpen().doubleValue());
				closePrices.add(thq.getClose().doubleValue());
				highPrices.add(thq.getHigh().doubleValue());
				lowPrices.add(thq.getLow().doubleValue());
				volumes.add((double) thq.getVolume());
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public void normalize() {
		normalize(openPrices);
		normalize(closePrices);
		normalize(highPrices);
		normalize(lowPrices);
		normalize(volumes);
	}
	
	private void normalize(ArrayList<Double> input) {
		//Detection of largest value
		double min = input.get(0);
		double max = input.get(0);
		for(int i = 0; i < input.size(); i++) {
			if(input.get(i) > max) {
				max = input.get(i);
			} else if(input.get(i) < min) {
				min = input.get(i);
			}
		}
		
		//Map value to this input
		if(input.equals(closePrices)) {
			closePriceMin = min;
			closePriceMax = max;
		}
		
		//Normalize all data
		for(int i = 0; i < input.size(); i++) {
			double x = input.get(i);
			double normalized = ((x - min) 
								  / (max - min) 
								  * (Settings.normalizedHigh - Settings.normalizedLow) 
								  + Settings.normalizedLow);
			input.set(i, normalized);
		}
	}
	
	public double denorm(double x) {
		return ((closePriceMin - closePriceMax) * x - Settings.normalizedHigh
				 * closePriceMin + closePriceMax * Settings.normalizedLow)
				 / (Settings.normalizedLow - Settings.normalizedHigh);
	}
	
	public double[][] getInputs() {
		int dataPoints = hq.size();
		double[][] tinputs = new double[dataPoints][Settings.inputs];
		for(int i = 0; i < dataPoints; i++) {
			//Set each input for this datapoint
			tinputs[i][0] = openPrices.get(i).doubleValue();
			tinputs[i][1] = highPrices.get(i).doubleValue();
			tinputs[i][2] = lowPrices.get(i).doubleValue();
			tinputs[i][3] = volumes.get(i).doubleValue();
		}
		return tinputs;
	}
	
	public double[][] getOutputs() {
		int dataPoints = hq.size();
		double[][] toutputs = new double[dataPoints][Settings.outputs];
		for(int i = 0; i < dataPoints; i++) {
			//Set each input for this datapoint
			toutputs[i][0] = closePrices.get(i).doubleValue();
		}
		return toutputs;
	}
	
	public BasicMLDataSet getDataSet() {
		int dataPoints = hq.size();
		double[][] tinputs = new double[dataPoints - 1][Settings.inputs];
		double[][] toutputs = new double[dataPoints - 1][Settings.outputs];
		//Loop through dataPoints
		for(int i = 0; i < dataPoints - 1; i++) {
			//Set each input for this datapoint
			tinputs[i][0] = openPrices.get(i).doubleValue();
			tinputs[i][1] = highPrices.get(i).doubleValue();
			tinputs[i][2] = lowPrices.get(i).doubleValue();
			tinputs[i][3] = volumes.get(i).doubleValue();
			
			toutputs[i][0] = closePrices.get(i + 1).doubleValue();
		}
		return new BasicMLDataSet(tinputs, toutputs);
	}
	
	public String toString() {
		int numEntries = hq.size();
		return numEntries + " entries... \n"
			   + openPrices.toString() + "\n" + closePrices + "\n" + highPrices + "\n" + lowPrices + "\n";
	}
	
	public double getScaleFactor() {
		return closePriceMax;
	}
}

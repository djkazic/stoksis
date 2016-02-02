package org.alopex.scylla;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import yahoofinance.Stock;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

public class StockMiner {

	private MinerController mc;
	private Stock stock;
	
	private List<HistoricalQuote> hq;
	
	private ArrayList<Double> openPrices;
	private ArrayList<Double> closePrices;
	private ArrayList<Double> highPrices;
	private ArrayList<Double> lowPrices;
	private ArrayList<Double> volumes;
	
	public StockMiner(MinerController mc, Stock stock) {
		System.out.println("StockMiner instance started for " + stock.getSymbol());
		try {
			this.mc = mc;
			this.stock = stock;
			
			openPrices  = new ArrayList<> ();
			closePrices = new ArrayList<> ();
			highPrices  = new ArrayList<> ();
			lowPrices   = new ArrayList<> ();
			volumes     = new ArrayList<> ();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	/**
	 * Iterates through history, compiling all lists
	 */
	public void fetchData(Calendar from, Calendar to) {
		try {
			hq = stock.getHistory(from, to, Interval.DAILY);
			for(int i = 0; i < hq.size(); i++) {
				HistoricalQuote thq = hq.get(i);
				openPrices.add(thq.getOpen().doubleValue());
				
				//DEBUG
				//SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
				//System.out.println("Open price logged: " + sdf.format(thq.getDate().getTime()) + " | " + thq.getOpen().doubleValue());
				
				closePrices.add(thq.getClose().doubleValue());
				
				//DEBUG
				//System.out.println("\tClose price logged: " + sdf.format(thq.getDate().getTime()) + " | " + thq.getClose().doubleValue());
				
				highPrices.add(thq.getHigh().doubleValue());
				lowPrices.add(thq.getLow().doubleValue());
				volumes.add((double) thq.getVolume());
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		if(mc != null) {
			mc.sendData(this.getInputs(), this.getOutputs());
		}
	}

	public double[][] testData() {
		double[][] data = new double[hq.size() - 1][Settings.inputs];
		System.out.println("Packing " + hq.size() + " frames for test data...");
		for(int i = 0; i < data.length; i++) {
			data[i][0] = openPrices.get(i);
			data[i][1] = highPrices.get(i);
			data[i][2] = lowPrices.get(i);
			data[i][3] = volumes.get(i);
		}
		return data;
	}
	
	public double[][] getInputs() {
		int dataPoints = hq.size();
		double[][] tinputs = new double[dataPoints - 1][Settings.inputs];
		for(int i = 0; i < dataPoints - 1; i++) {
			//Set each input for this datapoint
			tinputs[i][0] = openPrices.get(i);
			tinputs[i][1] = highPrices.get(i);
			tinputs[i][2] = lowPrices.get(i);
			tinputs[i][3] = volumes.get(i);
		}
		return tinputs;
	}
	
	public double[][] getOutputs() {
		int dataPoints = hq.size();
		double[][] toutputs = new double[dataPoints - 1][Settings.outputs];
		for(int i = 0; i < dataPoints - 1; i++) {
			//Set each input for this datapoint
			toutputs[i][0] = closePrices.get(i + 1).doubleValue();
		}
		return toutputs;
	}
	
	public String toString() {
		int numEntries = hq.size();
		return numEntries + " entries... \n"
			   + openPrices.toString() + "\n" + closePrices + "\n" + highPrices + "\n" + lowPrices + "\n";
	}
}

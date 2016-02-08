package org.alopex.scylla;

import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.persist.EncogDirectoryPersistence;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Scanner;

/**
 * @author Kevin Cai on 2/1/2016.
 */
public class MinerController {

	private ArrayList<String> tickers;
	private ArrayList<double[][]> unprocIn;
	private ArrayList<double[][]> unprocOut;

	private double closePriceMin = 0;
	private double closePriceMax = Settings.staticHigh;

	public MinerController() {
		tickers = new ArrayList<> ();
		unprocIn = new ArrayList<> ();
		unprocOut = new ArrayList<> ();
	}

	public void train(BasicNetwork network) {
		try {
			tickers.add("HUBS");
			tickers.add("FB");
			tickers.add("AAPL");
			tickers.add("UA");
			tickers.add("GOOG");
			tickers.add("CSCO");
			tickers.add("UNH");
			tickers.add("MSFT");
			tickers.add("ORCL");
			tickers.add("CFC");
			tickers.add("HD");
			tickers.add("TWX");
			tickers.add("WMT");
			tickers.add("AMZN");
			tickers.add("NFLX");
			tickers.add("XOM");
			tickers.add("BP");
			tickers.add("CVX");
			tickers.add("TM");
			tickers.add("F");
			tickers.add("GM");
			
			makeThreads();

			normalizeFragments();

			double[][] mergedInput = merge(unprocIn);
			double[][] mergedOutput = merge(unprocOut);
			
			System.out.println("Assembling training dataset...");

			BasicMLDataSet trainSet = new BasicMLDataSet(mergedInput, mergedOutput);
			final ResilientPropagation train = new ResilientPropagation(network, trainSet);
			int epoch = 1;
			long startTrain = System.currentTimeMillis();
			String lastError = "";
			int countReset = 0;
			do {
				if(epoch == Integer.MAX_VALUE - 1) {
					System.out.println("Maximum epochs reached. Stopping training...");
					break;
				}

				train.iteration();

				//Epoch print check
				if(epoch % 1000 == 0) {
					double thisError = train.getError();
					String thisErrorStr = String.format("%.8f", thisError);

					if(thisErrorStr.equals(lastError)) {
						countReset++;
					} else {
						countReset = 0;
					}

					//countReset check
					if(countReset == 25) {
						System.out.println("countReset (" + lastError + " x " + countReset + ") triggered. Stopping training...");
						break;
					}

					lastError = thisErrorStr;
					System.out.println("SET " + epoch + " | @ " + String.format("%.8f", thisError));
				}

				epoch++;
			} while(train.getError() > Settings.maxError);
			train.finishTraining();
			long stopTrain = System.currentTimeMillis();
			if(Settings.debug) {
				System.out.println();
				System.out.println("Train time: " + ((double) (stopTrain - startTrain)) / 1000.0 + "s");
				System.out.println();
				System.out.println("Verifying net learning");
				int counterTest = 0;
				for(MLDataPair pair : trainSet) {
					counterTest++;
					if(counterTest == 20) {
						break;
					}
					final MLData output = network.compute(pair.getInput());
					System.out.println("Ideal = " + String.format("%.3f", denorm(pair.getIdeal().getData(0)))
							+ " | Network = " + String.format("%.3f", output.getData(0)));
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		promptSave(network);
	}
	
	public void eval(BasicNetwork network) {
		try {
			Stock hubspot = YahooFinance.get("MSFT", true);
			
			Thread.sleep(100); //TODO: move sleep to StockMiner
			StockMiner hubspotData = new StockMiner(null, hubspot);

			Calendar from = Calendar.getInstance();
			Calendar to = Calendar.getInstance();
			from.add(Calendar.MONTH, -1);
			//to.add(Calendar.DAY_OF_MONTH, -1);
			hubspotData.fetchData(from, to);

			double[][] inputNormalize = new double[1][Settings.inputs];
			inputNormalize = hubspotData.testData();
			double[][] testInput = normalize(inputNormalize);
			System.out.println("\t" + Arrays.toString(testInput[0]));
			MLData output = network.compute(new BasicMLData(testInput[0]));
			for(int i = 0; i < output.size(); i++) {
				SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
				double lastClose = hubspotData.lastClose();
				double res = output.getData(i);
				String endString = "";
				if(res < 0) {
					endString = "lower than " + lastClose + " @ " + (((res / -1)) * 100) + "% confidence";
				} else if(res > 0) {
					endString = "higher than " + lastClose + " @ " + ((res) * 100);
				}
				System.out.println(sdf.format(to.getTime()) + " close prediction: " + endString);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private void promptSave(BasicNetwork network) {
		System.out.println();
		System.out.println("Save core.dat?");
		Scanner saveScan = new Scanner(System.in);
		while(saveScan.hasNext()) {
			String answer = saveScan.nextLine();
			if(answer.equalsIgnoreCase("Y")) {
				EncogDirectoryPersistence.saveObject(new File("core.dat"), network);
				System.out.println("Core saved.");
				break;
			} else if(answer.equalsIgnoreCase("N")) {
				System.out.println("Core rejected.");
				break;
			} else {
				System.out.println("Unrecognized reply.");
			}
		}
		saveScan.close();
	}

	private void makeThreads() {
		try {
			//Make threads to get data
			for(int i = 0; i < tickers.size(); i++) {
				final String thisTicker = tickers.get(i);
				final MinerController mc = this;

				(new Thread(new Runnable() {
					public void run() {
						try {
							System.out.println("Pulling stock data for [" + thisTicker + "]...");

							Stock stock = YahooFinance.get(thisTicker, true);
							StockMiner stockMiner = new StockMiner(mc, stock);

							Calendar from = Calendar.getInstance();
							Calendar to = Calendar.getInstance();

							from.add(Calendar.YEAR, -5);
							to.add(Calendar.MONTH, -1);

							stockMiner.fetchData(from, to);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				})).start();
			}
			Thread.sleep(900 * tickers.size());
			System.out.println();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private void normalizeFragments() {
		System.out.println("Normalizing data fragments...");
		System.out.println();
		for(int i = 0; i < unprocIn.size(); i++) {
			unprocIn.set(i, normalize(unprocIn.get(i)));
		}

		/**
		for(int i = 0; i < unprocOut.size(); i++) {
			unprocOut.set(i, normalize(unprocOut.get(i)));
		}
		 */
	}

	public void sendData(double[][] input, double[][] output) {
		unprocIn.add(input);
		//System.out.println("Received input of length " + input.length);
		unprocOut.add(output);
		//System.out.println("Received output of length " + output.length);
	}

	/**
	 * Normalizes an input double array across all primary subs
	 *
	 * @param input
	 */
	private double[][] normalize(double[][] input) {
		//a = place of inputs
		for(int place = 0; place < input[0].length; place++) {
			//b = round of input

			//Container for cross-frame places
			double[] thisSet = new double[input.length];

			for(int set = 0; set < thisSet.length; set++) {
				thisSet[set] = input[set][place];
			}

			//Detection of largest value
			double min = 0;
			double max = Settings.staticHigh;

			//Normalize all data
			for(int i = 0; i < thisSet.length; i++) {
				double x = thisSet[i];
				double normalized = ((x - min)
						/ (max - min)
						* (Settings.normalizedHigh - Settings.normalizedLow)
						+ Settings.normalizedLow);
				thisSet[i] = normalized;
			}

			//Reinsert data
			for(int set = 0; set < thisSet.length; set++) {
				input[set][place] = thisSet[set];
			}
		}
		return input;
	}

	public double denorm(double x) {
		return ((closePriceMin - closePriceMax) * x - Settings.normalizedHigh
				* closePriceMin + closePriceMax * Settings.normalizedLow)
				/ (Settings.normalizedLow - Settings.normalizedHigh);
	}

	public static double[][] merge(ArrayList<double[][]> arrays) {
		// Count the number of arrays passed for merging and the total size of resulting array
		int count = 0;
		for(double[][] array : arrays) {
			count += array.length;
		}

		// Create new array and copy all array contents
		double[][] mergedArray = (double[][]) java.lang.reflect.Array.newInstance(arrays.get(0)[0].getClass(), count);
		int start = 0;
		for(double[][] array : arrays) {
			System.arraycopy(array, 0, mergedArray, start, array.length);
			start += array.length;
		}
		return mergedArray;
	}
}
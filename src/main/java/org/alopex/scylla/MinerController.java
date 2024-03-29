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
	
	@SuppressWarnings("unused")
	private int inputLatchCount = 0;

	private double closePriceMin = 0;
	private double closePriceMax = Settings.staticHigh;
	
	private boolean abort = false;
	private Thread abortThread;

	public MinerController() {
		tickers = new ArrayList<> ();
		unprocIn = new ArrayList<> ();
		unprocOut = new ArrayList<> ();
	}

	public void train(final BasicNetwork network) {
		try {
			//Technology
			tickers.add("AAPL");
			tickers.add("GOOG");
			tickers.add("MSFT");
			tickers.add("ORCL");
			tickers.add("FB");
			tickers.add("AMZN");
			tickers.add("NFLX");
			
			//Auto
			tickers.add("TM");
			tickers.add("F");
			tickers.add("GM");
			tickers.add("HMC");
			tickers.add("TSLA");

			//Health Care
			tickers.add("HNT");
			tickers.add("CNC");
			tickers.add("ANTM");
			tickers.add("UNH");
			tickers.add("HUM");
			
			//Oil
			tickers.add("XOM");
			tickers.add("BP");
			tickers.add("CVX");
			tickers.add("COP");
			
			//Gold
			tickers.add("CDE");
			tickers.add("NEM");
			tickers.add("ABX");
			tickers.add("FNV");
			
			makeThreads();

			normalizeFragments();

			double[][] mergedInput = merge(unprocIn);
			double[][] mergedOutput = merge(unprocOut);

			System.out.println("Assembling training dataset...");
			System.out.println();

			final BasicMLDataSet trainSet = new BasicMLDataSet(mergedInput, mergedOutput);
			final ResilientPropagation train = new ResilientPropagation(network, trainSet);

			//Listener for key P
			abortThread = (new Thread(new Runnable() {
				public void run() {
					System.out.println("Starting listener for training abort...");
					System.out.println();
					Scanner keyboard = new Scanner(System.in);
					while(true) {
						try {
							if(keyboard.hasNext()) {
								String input = keyboard.nextLine();
								if(input.equalsIgnoreCase("AB")) {
									System.out.println("Aborting training...");
									abort = true;
								}
							}
							Thread.sleep(500);
						} catch (Exception ex) {
						} finally {
							keyboard.close();
						}
					}
				}
			}));
			abortThread.start();

			Thread trainThread = (new Thread(new Runnable() {
				public void run() {

					int epoch = 1;
					long startTrain = System.currentTimeMillis();
					String lastError = "";
					int countReset = 0;
					do {
						if(abort) {
							break;
						}
						
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
					abortThread.interrupt();
					
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
							
							//TODO: clean up string processing here
							double ideal = pair.getIdeal().getData(0);
							String idealAlign = String.format("%.3f", pair.getIdeal().getData(0));
							if(ideal >= 0) {
								idealAlign = " " + String.format("%.3f", pair.getIdeal().getData(0));
							}
							System.out.println("Ideal = " + idealAlign
									+ " | Network = " + String.format("%.3f", output.getData(0)));
						}
					}
					System.out.println();
					save(network);
				}
			}));
			trainThread.start();
			trainThread.join();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void eval(BasicNetwork network, String ticker, int daysAgo) {
		try {
			int daysAgoEnd = daysAgo;
			
			daysAgoEnd *= -1;
			
			Stock stock = YahooFinance.get(ticker, true);

			Thread.sleep(100); //TODO: move sleep to StockMiner
			StockMiner stockData = new StockMiner(null, stock);

			Calendar from = Calendar.getInstance();
			Calendar to = Calendar.getInstance();
			
			System.out.println("Evaluating " + ticker + " from " + daysAgoEnd + " days ago");
			
			from.add(Calendar.DAY_OF_MONTH, daysAgoEnd - 5);
			to.add(Calendar.DAY_OF_MONTH, daysAgoEnd);
			
			if(daysAgo > 0) {
				stockData.fetchData(from, to);
			} else {
				stockData.fetchTestData();
			}

			double[][] inputNormalize = new double[1][Settings.inputs];
			inputNormalize = stockData.testData();

			double[][] testInput = normalize(inputNormalize);
			System.out.println("\t" + Arrays.toString(testInput[0]));
			System.out.println();
			
			MLData output = network.compute(new BasicMLData(testInput[0]));
			for(int i = 0; i < output.size(); i++) {
				SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
				double lastClose = stockData.lastClose();
				double res = output.getData(i);
				String endString = "";
				
				if(res < 0.5) {
					endString = " lower than " + lastClose + " @ " + String.format("%.2f", ((0.5 - res) * 2 * 100)) + "% confidence";
				} else if(res > 0.5) {
					endString = " higher than " + lastClose + " @ " + String.format("%.2f", ((res - 0.5) * 2 * 100)) + "% confidence";
				}
				System.out.println(sdf.format(to.getTime()) + " close prediction: " + endString);
				
				double actualValue = 0;
				if(daysAgo > 0) {
					actualValue = stockData.actualClose();
					System.out.println("Actual close price for " + stock.getSymbol() + ": " + actualValue + " || EVAL_STATUS: " + verify(res, lastClose, actualValue));
				} else {
					actualValue = stock.getQuote().getPrice().doubleValue();
					System.out.println("Current share price for " + stock.getSymbol() + ": " + actualValue + " || EVAL_STATUS: " + verify(res, lastClose, actualValue));
				}
				System.out.println();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private boolean verify(double res, double lastClose, double actualValue) {
		if(res < 0.5) {
			return (actualValue < lastClose);
		} else if(res > 0.5) {
			return (actualValue > lastClose);
		} else {
			return (actualValue == lastClose);
		}
	}

	private void save(BasicNetwork network) {
		String coreName = "core-" + System.currentTimeMillis() + ".dat";
		System.out.println("Saving " + coreName + "...");

		EncogDirectoryPersistence.saveObject(new File(coreName), network);
		System.out.println("Core saved.");
		System.out.println();
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

							from.add(Calendar.YEAR, -1);
							to.add(Calendar.DAY_OF_MONTH, -7);

							stockMiner.fetchData(from, to);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				})).start();
			}
			Thread.sleep(1000 * tickers.size());
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
		inputLatchCount++;
		unprocOut.add(output);
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
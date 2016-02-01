package org.alopex.scylla;

import java.io.File;
import java.util.Scanner;
import java.util.logging.LogManager;

import org.encog.engine.network.activation.ActivationTANH;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.persist.EncogDirectoryPersistence;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;

/**
 * @author Kevin Cai on 1/30/2016.
 */
public class ScyllaCore {

	private static BasicNetwork network;

	public static void main(String[] args) {
		LogManager.getLogManager().reset();

		if(!new File("core.dat").exists()) {
			network = new BasicNetwork();
			initNetworkArch();
		} else {
			network = (BasicNetwork) EncogDirectoryPersistence.loadObject(new File("core.dat"));
		}

		try {
			Stock hubspot = YahooFinance.get("HUBS", true);
			StockData hubspotData = new StockData(hubspot);
			
			//TODO: have more than just one training set
			BasicMLDataSet trainSet = hubspotData.getDataSet();

			final ResilientPropagation train = new ResilientPropagation(network, trainSet);
			int epoch = 1;			
			long startTrain = System.currentTimeMillis();
			String lastError = "";
			int countReset = 0;
			do {
				if(epoch == 250000) {
					System.out.println("Maximum epochs reached. Stopping training...");
					break;
				}
		
				train.iteration();
				double thisError = train.getError();
				if(("" + thisError).equals(lastError)) {
					countReset++;
				} else {
					countReset = 0;
				}
				lastError = "" + thisError;
				if(epoch % 1000 == 0) {
					System.out.println("Iteration " + epoch + "| Error: " + thisError);
				}

				if(countReset == 5) {
					System.out.println("countReset triggered. Stopping training...");
					break;
				}

				epoch++;
			} while(train.getError() > Settings.maxError);
			train.finishTraining();
			long stopTrain = System.currentTimeMillis();
			if(Settings.debug) {
				System.out.println();
				System.out.println("Train time: " + ((double)(stopTrain - startTrain)) / 1000.0 + "s");
				System.out.println();
				System.out.println("Verifying net learning");
				for(MLDataPair pair : trainSet) {
					final MLData output = network.compute(pair.getInput());
					System.out.println("Ideal = $" + hubspotData.denorm(pair.getIdeal().getData(0))
							+ " | Actual = $" + hubspotData.denorm(output.getData(0)));
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		System.out.println();
		System.out.println("Save core.dat?");
		Scanner saveScan = new Scanner(System.in);
		if(saveScan.hasNext()) {
			String answer = saveScan.nextLine();
			if(answer.equalsIgnoreCase("Y")) {
				EncogDirectoryPersistence.saveObject(new File("core.dat"), network);
				System.out.println("Core saved.");
			}
		}
		saveScan.close();
	}

	private static void initNetworkArch() {
		network.addLayer(new BasicLayer(null, true, Settings.inputs));
		for(int i = 0; i < Settings.hiddenLayers; i++) {
			network.addLayer(new BasicLayer(new ActivationTANH(), true, Settings.hiddens));
		}
		network.addLayer(new BasicLayer(new ActivationTANH(), false, Settings.outputs));
		network.getStructure().finalizeStructure();
		network.reset();
	}

	//TODO: concatenate multiple StockData
	/**
	private static double[][] concat(double[][] a, double[][] b) {
		int aLen = a.length;
		int bLen = b.length;
		double[][] c = new double[aLen + bLen][];
		System.arraycopy(a, 0, c, 0, aLen);
		System.arraycopy(b, 0, c, aLen, bLen);
		return c;
	}
	 */
}

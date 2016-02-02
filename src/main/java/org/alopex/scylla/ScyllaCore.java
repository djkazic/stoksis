package org.alopex.scylla;

import java.io.File;
import java.util.logging.LogManager;

import org.encog.engine.network.activation.ActivationTANH;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.persist.EncogDirectoryPersistence;

/**
 * @author Kevin Cai on 1/30/2016.
 */
public class ScyllaCore {

	private static BasicNetwork network;
	private static MinerController mc;

	public static void main(String[] args) {
		LogManager.getLogManager().reset();

		if(!new File("core.dat").exists()) {
			network = new BasicNetwork();
			initNetworkArch();
		} else {
			System.out.println("Existing core detected. Loading...");
			network = (BasicNetwork) EncogDirectoryPersistence.loadObject(new File("core.dat"));
		}

		mc = new MinerController();
		
		boolean train = false;
		boolean eval = false;
		
		for(String str : args) {
			String lower = str.toLowerCase();
			
			switch(lower) {
				case "--train":
					train = true;
					break;
					
				case "--eval":
					eval = true;
					break;
			}
		}
		
		if(train)
			mc.train(network);
		
		if(eval)
			mc.eval(network);
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

	/**
	private static void eval() {
		try {
			Stock hubspot = YahooFinance.get("FB", true);
			Thread.sleep(100); //TODO: move sleep to StockMiner
			StockMiner hubspotData = new StockMiner(hubspot);

			Calendar from = Calendar.getInstance();
			Calendar to = Calendar.getInstance();
			from.add(Calendar.MONTH, -1);
			hubspotData.fetchData(from, to);
			hubspotData.normalize();

			double[] testInput = hubspotData.testData();
			MLData output = network.compute(new BasicMLData(testInput));
			for(int i = 0; i < output.size(); i++) {
				SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
				System.out.println(sdf.format(to.getTime()) + " close prediction: " + String.format("%.4f", hubspotData.denorm(output.getData(i))));
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	**/

	//TODO: concatenate multiple StockMiner
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
